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

/** Uses the actual DiPlay Controller's wireless lifecycle; credentials remain external. */
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
    private var activeSession: AirPlaySession? = null
    private var bluetoothReady = false
    private var tunnelReady = false
    private var selectedAddress: String? = null
    private var initialized = false
    private var closed = false
    @Volatile private var initialization = "NOT_INITIALIZED"

    fun initializationStatus(): String = initialization
    /** Constructor/start smoke reaches upstream WaitingForMfi without radios, assets or bus access. */
    fun initialize() = synchronized(lock) {
        if (closed || initialized) return
        try {
            createController(VideoProfile.DEFAULT, false).start()
            initialized = true
            initialization = "DIPLAY_CORE_READY_AUTH_BLOCKED"
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

    override fun connect(profile: VideoProfile, listener: ReceiverCore.Listener) = synchronized(lock) {
        disconnect()
        lastProfile = profile
        if (closed) return
        epoch = gate.begin(provider)
        this.listener = listener
        if (!hasLawfulAuthentication()) { fail(SessionMachine.Reason.BLOCKED_BY_AUTHENTICATION_REQUIREMENT); return }
        // Never let an old asynchronous service.detach() tear down a fresh controller.
        // No UI-thread wait: reject reconnect until its predecessor has really closed.
        if (closingController?.awaitClosed(0) == false) { fail(SessionMachine.Reason.SESSION_LOST); return }
        closingController = null
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            selectedAddress == null) { fail(SessionMachine.Reason.INPUT_REJECTED); return }
        bridge = DiPlayMediaBridge(epoch, gate, listener) { fail(SessionMachine.Reason.INPUT_REJECTED) }
        try { createController(profile, true).start() }
        catch (_: Exception) { fail(SessionMachine.Reason.SESSION_LOST) }
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
                serialNumber = java.util.UUID.randomUUID().toString(), firmwareVersion = "0.2-alpha",
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
                override fun onSessionEnded(session: AirPlaySession) = synchronized(lock) {
                    if (gate.current(captured) && activeSession === session) fail(SessionMachine.Reason.SESSION_LOST)
                }
                override fun onTransportError(message: String) = synchronized(lock) {
                    if (gate.current(captured)) fail(SessionMachine.Reason.NETWORK_LOSS)
                }
            },
            CarPlayMediaEngine(bridge ?: object : MediaSink {}, microphoneEnabled = false),
            { status -> synchronized(lock) {
                if (gate.current(captured)) when (status) {
                    CarPlayStatus.BluetoothBootstrapAuthenticated -> {
                        bluetoothReady = true
                        if (gate.bootstrapConfirmed(captured)) listener?.bluetoothBootstrapConfirmed()
                        advance(captured)
                    }
                    CarPlayStatus.WifiTunnelAuthenticated -> { tunnelReady = true; advance(captured) }
                    is CarPlayStatus.Failed, CarPlayStatus.ControlEnded -> fail(SessionMachine.Reason.SESSION_LOST)
                    else -> Unit
                }
            } },
            if (authorized) ProviderAuthenticator(provider, providerProtocolMajor) else null,
        ).also { controller = it }
    }
    private fun advance(captured: Long) {
        if (!gate.current(captured) || !bluetoothReady || !tunnelReady) return
        if (gate.carplayNetworkReady(captured)) listener?.wifiSessionLinkConfirmed()
        gate.authenticationSucceeded(captured) // tunnel onReady follows real AA05, not provider success text.
        if (activeSession?.authenticatedForMedia == true && gate.sessionEstablished(captured))
            listener?.authenticatedSessionStarted()
    }
    override fun videoSinkReady() = synchronized(lock) {
        if (gate.videoSinkReady(epoch)) bridge?.requestKeyframe()
        Unit
    }
    override fun frameRendered(): Boolean = synchronized(lock) { bridge?.frameRendered() ?: false }
    override fun requestKeyframe(): Boolean = synchronized(lock) { bridge?.requestKeyframe() ?: false }
    override fun touch(action: Int, x: Float, y: Float): Boolean = synchronized(lock) {
        if (gate.state() != ProtocolGate.State.STREAMING || !x.isFinite() || !y.isFinite() ||
            x !in 0f..1f || y !in 0f..1f || action !in listOf(0, 1, 2)) return false
        controller?.sendTouch(listOf(AirPlayContact(0, x.toDouble(), y.toDouble(), action != 1))) ?: false
    }
    override fun reconnect(): Boolean = synchronized(lock) {
        val previousListener = listener ?: return false
        if (!hasLawfulAuthentication()) return false
        // Current preview has no authorized provider. A future reconnect needs the selected profile.
        connect(lastProfile, previousListener)
        gate.current(epoch)
    }
    private var lastProfile = VideoProfile.DEFAULT
    private fun fail(reason: SessionMachine.Reason) {
        val notify = listener
        disconnect()
        notify?.disconnected(reason)
    }
    override fun disconnect() = synchronized(lock) {
        gate.stop()
        epoch = gate.epoch()
        listener = null
        controller?.let { it.close(); closingController = it }
        controller = null
        bridge?.clear()
        bridge = null
        identity?.privateKey?.fill(0)
        identity = null
        pairings.clear()
        activeSession = null
        bluetoothReady = false
        tunnelReady = false
        initialized = false
    }
    override fun close() = synchronized(lock) { closed = true; disconnect(); provider.close() }
}
