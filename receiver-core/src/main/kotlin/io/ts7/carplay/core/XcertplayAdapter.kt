// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import android.bluetooth.BluetoothSocket
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.media.MediaCodecSupport
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationException
import com.shilapi.xcertplay.transport.*
import io.ts7.carplay.auth.AuthenticationProvider
import java.net.Socket

/** Minimal source core; every entry/callback carries the captured connection epoch. */
class XcertplayAdapter(private val provider: AuthenticationProvider, private val sink: Sink) {
    interface Sink {
        fun bootstrapConfirmed(epoch: Long)
        fun carplayNetworkReady(epoch: Long)
        fun authenticationSucceeded(epoch: Long)
        fun sessionEstablished(epoch: Long)
        fun video(epoch: Long, bytes: ByteArray, timestampUs: Long): Boolean
        fun failed(epoch: Long, reason: ProtocolGate.Failure)
    }
    private data class Context(val epoch: Long, val identification: Iap2IdentificationConfig,
        val endpoint: Iap2WirelessCarPlayEndpoint, val major: Int, val identity: AirPlayIdentity)
    private val owner = Any()
    private val gate = ProtocolGate()
    @Volatile private var token = 0L
    private var bootstrap: Iap2Session? = null
    private var tunnel: Iap2Session? = null
    private var airplay: AirPlaySession? = null
    private var context: Context? = null
    private var phoneAuthenticatedEpoch = -1L
    private var networkReadyEpoch = -1L
    private var keyframe: Pair<Long, () -> Boolean>? = null
    private var configBytes: Pair<Long, ByteArray>? = null
    private var pairingIdentity: AirPlayIdentity? = null
    private val pairings = PairingStore() // RAM-only generated app pairing keys, never MFi identity.
    fun begin(): Boolean {
        stop()
        synchronized(owner) {
            token = gate.begin(provider)
            return gate.current(token)
        }
    }
    fun epoch(): Long = token
    fun state(): ProtocolGate.State = gate.state()
    fun failure(): ProtocolGate.Failure = gate.failure()
    /** Generated app public key for the same endpoint/session, not an Apple/MFi identity. */
    fun pairingPublicKey(epoch: Long): String = synchronized(owner) {
        check(gate.current(epoch)) { "STALE_SESSION" }
        val identity = pairingIdentity ?: AirPlayIdentity.generate().also { pairingIdentity = it }
        identity.publicKeyHex
    }
    /** Worker-only; owns an explicitly selected, connected RFCOMM socket. */
    fun runBootstrap(epoch: Long, socket: BluetoothSocket, identification: Iap2IdentificationConfig,
        endpoint: Iap2WirelessCarPlayEndpoint, protocolMajor: Int) {
        val ctx = synchronized(owner) {
            val identity = pairingIdentity
            if (!gate.current(epoch) || identity == null || identification.wireless == null
                    || identification.locationInformationEnabled || protocolMajor !in 1..255
                    || endpoint.publicKey != identity.publicKeyHex) null
            else Context(epoch, identification, endpoint, protocolMajor, identity).also { context = it }
        }
        if (ctx == null) { socket.close(); return }
        val session = Iap2Session.openWireless(BluetoothRfcommDuplexStream(socket))
        synchronized(owner) {
            if (!gate.current(epoch)) { session.close(); return }
            bootstrap = session
        }
        try {
            if (!session.awaitReady(15_000) || !gate.bootstrapConfirmed(epoch))
                throw java.io.IOException("BOOTSTRAP_FAILED")
            sink.bootstrapConfirmed(epoch)
            synchronized(owner) { if (networkReadyEpoch == epoch) confirmNetwork(epoch) }
            runControl(ctx, session) {
                synchronized(owner) {
                    if (gate.current(epoch)) {
                        phoneAuthenticatedEpoch = epoch
                        confirmAuthentication(epoch)
                    }
                }
            }
            // Normal Bluetooth EOF after a real tunnel attachment must not destroy Wi-Fi handoff.
            val handedOff = synchronized(owner) { gate.current(epoch) && tunnel != null }
            if (!handedOff) fail(epoch, ProtocolGate.Failure.SESSION_CLOSED)
        } catch (_: Iap2MfiAuthenticationException) { fail(epoch, ProtocolGate.Failure.AUTH_FAILED) }
        catch (_: com.shilapi.xcertplay.mfi.MfiException) { fail(epoch, ProtocolGate.Failure.AUTH_FAILED) }
        catch (_: Exception) {
            val handedOff = synchronized(owner) { gate.current(epoch) && tunnel != null }
            if (!handedOff) fail(epoch, ProtocolGate.Failure.SESSION_CLOSED)
        } finally {
            session.close()
            synchronized(owner) { if (bootstrap === session) bootstrap = null }
        }
    }
    private fun runControl(ctx: Context, session: Iap2Session, authenticated: () -> Unit) {
        Iap2WirelessControlClient(session,
            Iap2MfiAuthenticationClient(ProviderAuthenticator(provider, ctx.major))).run(
            ctx.identification, ctx.endpoint, bringUpTimeoutMillis = 30_000,
            onAuthenticated = authenticated, locationProvider = null, onProgress = {}, onIncoming = {},
        )
    }
    /** Caller verifies the explicit WirelessTransport Network/address and captures epoch at start. */
    fun boundNetworkReady(epoch: Long) = synchronized(owner) {
        if (gate.current(epoch)) {
            networkReadyEpoch = epoch
            confirmNetwork(epoch)
            confirmAuthentication(epoch)
        }
    }
    private fun confirmNetwork(epoch: Long) {
        if (gate.carplayNetworkReady(epoch)) sink.carplayNetworkReady(epoch)
    }
    private fun confirmAuthentication(epoch: Long) {
        if (phoneAuthenticatedEpoch == epoch && gate.authenticationSucceeded(epoch))
            sink.authenticationSucceeded(epoch)
    }
    /** Owns a socket accepted on that transport's exact address after phone AA05. */
    fun acceptAirPlay(epoch: Long, socket: Socket, config: AirPlayConfig) {
        synchronized(owner) {
            val ctx = context
            if (!gate.current(epoch) || ctx?.epoch != epoch || phoneAuthenticatedEpoch != epoch
                    || networkReadyEpoch != epoch || airplay != null
                    || config.deviceId != ctx.endpoint.deviceIdentifier
                    || config.btMac != ctx.identification.wireless?.bluetoothMac
                    || config.hevc || config.microphone || config.main.widthPixels != 1280
                    || config.main.heightPixels != 720 || config.main.fps !in 20..30) {
                socket.close(); return
            }
            socket.soTimeout = 10_000
            val media = CarPlayMediaEngine(object : MediaSink {
                override fun onVideoCodec(type: Int, codec: VideoCodec) {
                    if (codec != VideoCodec.H264) fail(epoch, ProtocolGate.Failure.INPUT_REJECTED)
                }
                override fun onVideoRecoveryHandler(type: Int, requestKeyFrame: (() -> Boolean)?) {
                    synchronized(owner) {
                        if (type == 110 && gate.current(epoch))
                            keyframe = requestKeyFrame?.let { epoch to it }
                    }
                }
                override fun onVideoConfig(type: Int, codecData: ByteArray) {
                    synchronized(owner) {
                        if (type != 110 || !gate.current(epoch) || codecData.size > 65536) return
                        val (sps, pps) = MediaCodecSupport.avcParameterSets(codecData)
                        if (sps.isEmpty() || pps.isEmpty()) {
                            fail(epoch, ProtocolGate.Failure.INPUT_REJECTED); return
                        }
                        configBytes = epoch to (byteArrayOf(0, 0, 0, 1) + sps + byteArrayOf(0, 0, 0, 1) + pps)
                    }
                }
                override fun onVideoFrame(type: Int, naluBytes: ByteArray) {
                    synchronized(owner) {
                        if (type != 110 || !gate.videoAllowed(epoch, naluBytes, 0, naluBytes.size)) return
                        configBytes?.takeIf { it.first == epoch }?.let {
                            // Config is consumed even if the unchanged queue returns false until IDR.
                            sink.video(epoch, it.second, System.nanoTime() / 1000)
                            configBytes = null
                        }
                        gate.deliverVideo(epoch, naluBytes, 0, naluBytes.size) {
                            sink.video(epoch, naluBytes, System.nanoTime() / 1000)
                        }
                    }
                }
                // Audio output deferred until LPCM endian/RTP layout is verified; never guess/capture.
            }, microphoneEnabled = false)
            media.setIapTunnelHandler { stream -> startTunnel(ctx, stream) }
            val session = AirPlaySession(socket, config, ctx.identity, pairings,
                ProviderAuthenticator(provider, ctx.major), object : AirPlaySessionListener {
                    override fun onSessionActive(session: AirPlaySession) {
                        if (gate.sessionEstablished(epoch)) sink.sessionEstablished(epoch)
                    }
                    override fun onSessionEnded(session: AirPlaySession) {
                        fail(epoch, ProtocolGate.Failure.SESSION_CLOSED)
                    }
                }, media)
            airplay = session
            session.start()
        }
    }
    private fun startTunnel(ctx: Context, stream: BlockingDuplexByteStream): Boolean {
        val session = synchronized(owner) {
            if (!gate.current(ctx.epoch) || tunnel != null) { stream.close(); return false }
            Iap2Session.openTunnel(stream).also { tunnel = it }
        }
        Thread({
            try {
                runControl(ctx, session) {} // Reauthenticates same legal provider; AA05 required internally.
                fail(ctx.epoch, ProtocolGate.Failure.SESSION_CLOSED)
            } catch (_: Iap2MfiAuthenticationException) { fail(ctx.epoch, ProtocolGate.Failure.AUTH_FAILED) }
            catch (_: Exception) { fail(ctx.epoch, ProtocolGate.Failure.SESSION_CLOSED) }
            finally {
                session.close()
                synchronized(owner) { if (tunnel === session) tunnel = null }
            }
        }, "ts7-iap-tunnel").apply { isDaemon = true; start() }
        return true
    }
    fun videoSinkReady() {
        val epoch = token
        if (gate.videoSinkReady(epoch)) requestKeyframe()
    }
    fun renderedFrame(): Boolean = gate.renderedFrame(token)
    fun requestKeyframe(): Boolean {
        val handler = synchronized(owner) { keyframe?.takeIf { gate.current(it.first) }?.second }
        return handler?.invoke() ?: false
    }
    fun touch(action: Int, x: Float, y: Float): Boolean {
        if (gate.state() != ProtocolGate.State.STREAMING || !x.isFinite() || !y.isFinite()
                || x !in 0f..1f || y !in 0f..1f || action !in listOf(0, 1, 2)) return false
        val session = synchronized(owner) { airplay }
        return session?.sendTouch(listOf(AirPlayContact(0, x.toDouble(), y.toDouble(), action != 1))) ?: false
    }
    fun decoderFailed(epoch: Long) { fail(epoch, ProtocolGate.Failure.DECODER_FAILED) }
    fun videoStalled(epoch: Long) { fail(epoch, ProtocolGate.Failure.VIDEO_STALL) }
    fun networkLost(epoch: Long) { fail(epoch, ProtocolGate.Failure.NETWORK_LOST) }
    private fun fail(epoch: Long, reason: ProtocolGate.Failure) {
        if (gate.fail(epoch, reason)) { sink.failed(epoch, reason); closeConnections() }
    }
    fun stop() {
        synchronized(owner) {
            gate.stop()
            token = gate.epoch()
            phoneAuthenticatedEpoch = -1; networkReadyEpoch = -1; configBytes = null; keyframe = null
            pairingIdentity?.privateKey?.fill(0)
            pairingIdentity = null; context = null; pairings.clear()
        }
        closeConnections()
    }
    private fun closeConnections() {
        val old = synchronized(owner) {
            val all = Triple(bootstrap, tunnel, airplay)
            bootstrap = null; tunnel = null; airplay = null
            all
        }
        // Closing third-party socket ownership is never a vendor wait on the Activity thread.
        if (old.first != null || old.second != null || old.third != null)
            Thread({
                try { old.third?.close() } finally {
                    try { old.second?.close() } finally { old.first?.close() }
                }
            }, "ts7-core-close").apply { isDaemon = true; start() }
    }
}
