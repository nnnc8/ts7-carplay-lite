// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.*
import io.ts7.carplay.ReceiverCore
import io.ts7.carplay.SessionMachine
import io.ts7.carplay.VideoProfile
import io.ts7.carplay.auth.AuthenticationProvider

/** Uses the actual DiPlay wireless lifecycle; source and explicitly supplied runtime inputs stay separate. */
class DiPlayReceiverCore @JvmOverloads constructor(
    context: Context,
    private val provider: AuthenticationProvider,
    private val providerProtocolMajor: Int = 0,
) : ReceiverCore, AutoCloseable {
    private val context = context.applicationContext
    private val lock = Any()
    private val gate = ProtocolGate()
    private var epoch = 0L
    private var controller: CarPlayController? = null
    private var closingController: CarPlayController? = null
    private var identity: AirPlayIdentity? = null
    private val pairings = PairingStore()
    private var bridge: DiPlayMediaBridge? = null
    private var listener: ReceiverCore.Listener? = null
    private var desiredListener: ReceiverCore.Listener? = null
    private var lastProfile = VideoProfile.DEFAULT
    private var activeSession: AirPlaySession? = null
    private var bluetoothReady = false
    private var tunnelReady = false
    private var selectedAddress: String? = null
    private var initialized = false
    @Volatile private var closed = false
    @Volatile private var initialization = "NOT_INITIALIZED"

    fun initializationStatus(): String = initialization
    /** Constructor/start smoke reaches upstream WaitingForMfi without radios, assets or bus access. */
    fun initialize() = synchronized(lock) {
        if (closed || initialized || controller != null) return
        try {
            createController(VideoProfile.DEFAULT, false).start()
            initialized = true
            initialization = if (hasAuthenticationProvider()) "DIPLAY_CORE_READY_EXPERIMENTAL"
                else "DIPLAY_CORE_READY_AUTH_BLOCKED"
        } catch (_: Exception) {
            initialization = "DIPLAY_CORE_INITIALIZATION_FAILED"
            disconnect()
        } catch (_: LinkageError) {
            initialization = "DIPLAY_CORE_INITIALIZATION_FAILED"
            disconnect()
        }
    }
    fun selectPairedAddress(address: String) = synchronized(lock) {
        require(Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$").matches(address))
        check(!gate.current(epoch))
        selectedAddress = address // process-local protocol input, never diagnostics or disk.
    }
    override fun hasLawfulAuthentication(): Boolean =
        !closed && provider.isAvailable && provider.info.isAuthorized && providerProtocolMajor in 1..255

    override fun hasAuthenticationProvider(): Boolean =
        !closed && provider.isAvailable && provider.info.canUseForConnection() && providerProtocolMajor in 1..255

    override fun connect(profile: VideoProfile, listener: ReceiverCore.Listener) {
        startConnection(profile, listener, false)
    }
    private fun startConnection(profile: VideoProfile, target: ReceiverCore.Listener, retry: Boolean): Boolean {
        var pending: CarPlayController? = null
        var error: SessionMachine.Reason? = null
        val retired: DiPlayMediaBridge?
        val captured: Long
        synchronized(lock) {
            if (closed || (retry && desiredListener !== target)) return false
            retired = invalidateLocked()
            desiredListener = target
            lastProfile = profile
            epoch = gate.begin(provider)
            captured = epoch
            listener = target
            error = when {
                !hasAuthenticationProvider() -> SessionMachine.Reason.BLOCKED_BY_AUTHENTICATION_REQUIREMENT
                // No UI-thread wait; retain retry intent until the old service has detached.
                closingController?.awaitClosed(0) == false -> SessionMachine.Reason.SESSION_LOST
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    selectedAddress == null -> SessionMachine.Reason.INPUT_REJECTED
                else -> null
            }
            if (error == null) {
                closingController = null
                bridge = DiPlayMediaBridge(captured, gate, target) { fail(captured, SessionMachine.Reason.INPUT_REJECTED) }
                try { pending = createController(profile, true) }
                catch (_: Exception) { error = SessionMachine.Reason.SESSION_LOST }
            }
        }
        // Never acquire a media monitor or perform event I/O under the connection-owner lock.
        retired?.clear()
        if (error != null) { fail(captured, error!!); return false }
        try { pending?.start() }
        catch (_: Exception) { fail(captured, SessionMachine.Reason.SESSION_LOST) }
        return synchronized(lock) { gate.current(captured) }
    }
    private fun createController(profile: VideoProfile, authorized: Boolean): CarPlayController {
        val captured = epoch
        val localIdentity = AirPlayIdentity.generate().also { identity = it }
        val randomId = "02:" + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
            .chunked(2).joinToString(":")
        val config = AirPlayConfig(
            deviceName = "TS7 CarPlay Lite", deviceId = randomId, btMac = "02:00:00:00:00:00",
            sourceVersion = "220.68", main = AirPlayDisplayConfig(1280, 720, fps = profile.fps),
            hevc = false, microphone = false, cluster = null, icons = emptyList(),
            manufacturer = "TS7 CarPlay Lite", model = "TS7 Android8.1", oemLabel = "TS7",
        )
        val runtime = CarPlayRuntimeConfig(
            identification = com.shilapi.xcertplay.transport.Iap2IdentificationConfig(
                name = "TS7 CarPlay Lite", modelIdentifier = "TS7.Android8.1", manufacturer = "TS7 CarPlay Lite",
                serialNumber = java.util.UUID.randomUUID().toString(), firmwareVersion = "1.0.0-dev",
                hardwareVersion = "TS7.API27", carPlayUsbInterfaceNumber = 0,
                externalAccessoryProtocol = "io.ts7.carplay"),
            wirelessBluetoothDeviceAddress = selectedAddress,
        )
        return CarPlayController(context, runtime, config, localIdentity, pairings,
            object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) = synchronized(lock) {
                    if (gate.current(captured) && session.authenticatedForMedia) {
                        activeSession = session
                        advance(captured)
                    }
                }
                override fun onSessionEnded(session: AirPlaySession) {
                    val ended = synchronized(lock) { gate.current(captured) && activeSession === session }
                    if (ended) fail(captured, SessionMachine.Reason.SESSION_LOST)
                }
                override fun onTransportError(message: String) {
                    fail(captured, SessionMachine.Reason.NETWORK_LOSS)
                }
            },
            CarPlayMediaEngine(bridge ?: object : MediaSink {}, microphoneEnabled = false),
            { status -> if (status is CarPlayStatus.Failed || status == CarPlayStatus.ControlEnded) {
                fail(captured, SessionMachine.Reason.SESSION_LOST)
            } else synchronized(lock) {
                if (gate.current(captured)) when (status) {
                    CarPlayStatus.BluetoothBootstrapAuthenticated -> {
                        bluetoothReady = true
                        if (gate.bootstrapConfirmed(captured)) listener?.bluetoothBootstrapConfirmed { gate.current(captured) }
                        advance(captured)
                    }
                    CarPlayStatus.WifiTunnelAuthenticated -> { tunnelReady = true; advance(captured) }
                    else -> Unit
                }
            } },
            if (authorized) ProviderAuthenticator(provider, providerProtocolMajor) else null,
        ).also { controller = it }
    }
    private fun advance(captured: Long) {
        if (!gate.current(captured) || !bluetoothReady || !tunnelReady) return
        if (gate.carplayNetworkReady(captured)) listener?.wifiSessionLinkConfirmed { gate.current(captured) }
        gate.authenticationSucceeded(captured) // tunnel onReady follows real AA05, not provider success text.
        if (activeSession?.authenticatedForMedia == true && gate.sessionEstablished(captured))
            listener?.authenticatedSessionStarted { gate.current(captured) }
    }
    override fun videoSinkReady() {
        val sink = synchronized(lock) { if (gate.videoSinkReady(epoch)) bridge else null }
        sink?.mediaSinkReady()
        sink?.requestKeyframe()
    }
    override fun frameRendered(): Boolean = synchronized(lock) { bridge }?.frameRendered() ?: false
    override fun requestKeyframe(): Boolean = synchronized(lock) { bridge }?.requestKeyframe() ?: false
    override fun touch(action: Int, x: Float, y: Float): Boolean = synchronized(lock) {
        if (gate.state() != ProtocolGate.State.STREAMING || !x.isFinite() || !y.isFinite() ||
            x !in 0f..1f || y !in 0f..1f || action !in listOf(0, 1, 2)) return false
        controller?.sendTouch(listOf(AirPlayContact(0, x.toDouble(), y.toDouble(), action != 1))) ?: false
    }
    override fun reconnect(): Boolean {
        val retry = synchronized(lock) { desiredListener?.let { lastProfile to it } } ?: return false
        if (!hasAuthenticationProvider()) return false
        return startConnection(retry.first, retry.second, true)
    }
    private fun fail(captured: Long, reason: SessionMachine.Reason) {
        val stopped = synchronized(lock) {
            // Check and invalidate atomically: a late parser/transport failure cannot stop a new epoch.
            if (captured != epoch || listener == null) return
            val notify = listener!!
            val retired = invalidateLocked()
            Triple(notify, retired, epoch)
        }
        stopped.second?.clear()
        stopped.first.disconnected(reason) { gate.epoch() == stopped.third }
    }
    /** Caller owns lock; no bridge callbacks and no event-write lock acquisition here. */
    private fun invalidateLocked(): DiPlayMediaBridge? {
        gate.stop()
        epoch = gate.epoch()
        listener = null
        controller?.let { it.close(); closingController = it }
        controller = null
        val retired = bridge
        bridge = null
        identity?.privateKey?.fill(0)
        identity = null
        pairings.clear()
        activeSession = null
        bluetoothReady = false
        tunnelReady = false
        initialized = false
        return retired
    }
    override fun disconnect() {
        val retired = synchronized(lock) { desiredListener = null; invalidateLocked() }
        retired?.clear()
    }
    override fun close() {
        synchronized(lock) { closed = true }
        disconnect()
        provider.close()
    }
}
