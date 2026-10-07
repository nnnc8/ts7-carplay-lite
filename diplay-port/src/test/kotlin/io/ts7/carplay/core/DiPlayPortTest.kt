// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.iap2.trace.*
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import io.ts7.carplay.ReceiverCore
import io.ts7.carplay.SessionMachine
import io.ts7.carplay.auth.FakeAuthenticationProvider
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private var checks = 0
private fun expect(ok: Boolean) { checks++; check(ok) }
private fun rejects(block: () -> Unit) {
    checks++
    try { block() } catch (_: Exception) { return }
    error("malformed fixture accepted")
}
private fun put64(bytes: ByteArray, at: Int, value: Long) {
    for (i in 0..7) bytes[at + i] = (value ushr (56 - 8 * i)).toByte()
}
fun main() {
    val original = linkedMapOf("fps" to 30L, "audio" to listOf("LPCM", true))
    val encoded = BplistCodec.encode(original)
    expect(BplistCodec.decode(encoded) == original)
    val trailer = encoded.size - 32
    for (value in listOf(0L, 4097L, 0x100000001L, Long.MIN_VALUE)) {
        rejects { BplistCodec.decode(encoded.copyOf().also { put64(it, trailer + 8, value) }) }
    }
    rejects { BplistCodec.decode(encoded.copyOf().also { put64(it, trailer + 16, 0x100000000L) }) }
    val cycle = BplistCodec.encode(listOf(1L)).also { it[9] = 0 }
    rejects { BplistCodec.decode(cycle) }
    val aliasedReference = ByteArray(50)
    "bplist00".toByteArray().copyInto(aliasedReference)
    aliasedReference[8] = 0xa1.toByte(); put64(aliasedReference, 9, 0x100000000L)
    aliasedReference[17] = 8; aliasedReference[24] = 1; aliasedReference[25] = 8
    put64(aliasedReference, 26, 1); put64(aliasedReference, 34, 0); put64(aliasedReference, 42, 17)
    rejects { BplistCodec.decode(aliasedReference) }
    val random = java.util.Random(7)
    repeat(400) {
        val bad = ByteArray(40 + random.nextInt(200)).also(random::nextBytes)
        "bplist00".toByteArray().copyInto(bad)
        rejects { BplistCodec.decode(bad) }
    }
    val request = "POST /info RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 3\r\n\r\nabc".toByteArray()
    expect(RtspMessage.parseMessages(request).messages.single().body.contentEquals("abc".toByteArray()))
    expect(RtspMessage.parseMessages(request.copyOf(request.size - 1)).messages.isEmpty())
    for (length in listOf("-1", "999999", "2147483648", "0x20")) {
        rejects { RtspMessage.parseMessages("POST /info RTSP/1.0\r\nContent-Length: $length\r\n\r\n".toByteArray()) }
    }
    rejects { RtspMessage.parseMessages("POST /info RTSP/1.0\r\nContent-Length: 0\r\nContent-Length: 1\r\n\r\n".toByteArray()) }
    rejects { RtspMessage.parseMessages(ByteArray(16 * 1024 + 1) { 65 }) }
    rejects { RtspMessage.parseMessages(ByteArray(256 * 1024 + 1)) }

    // Cipher availability cannot retroactively authenticate plaintext in a parsed batch.
    val verify = "POST /pair-verify RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 0\r\n\r\n".toByteArray()
    val record = "RECORD / RTSP/1.0\r\nCSeq: 2\r\n\r\n".toByteArray()
    val pipelined = RtspMessage.parseMessages(verify + record)
    expect(pipelined.messages.size == 2)
    rejects { ControlRequestProof.requireCleanTransition(pipelined.messages.size - 1, pipelined.rest.size) }
    val partialRecord = RtspMessage.parseMessages(verify + record.copyOf(8))
    expect(partialRecord.messages.size == 1 && partialRecord.rest.isNotEmpty())
    rejects { ControlRequestProof.requireCleanTransition(0, partialRecord.rest.size) }
    ControlRequestProof.requireCleanTransition(0, 0)
    for (encrypted in listOf(false, true)) for (sap in listOf(false, true)) for (paired in listOf(false, true))
        expect(ControlRequestProof.mediaAllowed(encrypted, sap, paired) == (encrypted && sap && paired))

    val pairing = PairingStore()
    val publicKey = ByteArray(32) { 7 }
    pairing.save("local", publicKey); publicKey[0] = 0
    expect(pairing.get("local")!![0].toInt() == 7)
    pairing.get("local")!!.fill(0)
    expect(pairing.get("local")!![0].toInt() == 7)
    repeat(7) { pairing.save("peer-$it", ByteArray(32)) }
    rejects { pairing.save("ninth", ByteArray(32)) }
    rejects { pairing.save("wrong-size", ByteArray(33)) }
    pairing.clear(); expect(pairing.get("local") == null)

    val secret = "private-ssid-passphrase-certificate"
    val frame = Iap2Frame(0x5702, secret.toByteArray())
    val formatted = Iap2FrameFormatter.format(Iap2TraceDirection.RX, secret, frame)
    expect(!formatted.contains(secret))
    expect(!Iap2FrameFormatter.formatFailure(Iap2TraceDirection.RX, secret, 1, Exception(secret)).contains(secret))
    val crypto = AirPlayCrypto.ed25519Generate()
    val signed = AirPlayCrypto.ed25519Sign(crypto.privateKey, byteArrayOf(1))
    expect(AirPlayCrypto.ed25519Verify(crypto.publicKey, byteArrayOf(1), signed))
    expect(!AirPlayCrypto.ed25519Verify(crypto.publicKey, byteArrayOf(2), signed))
    crypto.privateKey.fill(0)
    val key = ByteArray(32) { 3 }; val nonce = AirPlayCrypto.nonce64(2)
    val sealed = AirPlayCrypto.chachaSeal(key, nonce, byteArrayOf(9))
    expect(AirPlayCrypto.chachaOpen(key, nonce, sealed).contentEquals(byteArrayOf(9)))
    rejects { AirPlayCrypto.chachaOpen(key, nonce, sealed.also { it[0] = (it[0].toInt() xor 1).toByte() }) }

    // Actual source tunnel on loopback only; no Android radios, phone or authentication claim.
    val loop = java.net.InetAddress.getByName("127.0.0.1")
    val packetReceived = CountDownLatch(1)
    var received: ByteArray? = null
    val tunnel = IapTunnel(ByteArray(32) { 4 }, loop, loop)
    val port = tunnel.listen(object : IapTunnel.Listener {
        override fun onIap(bytes: ByteArray) { received = bytes; packetReceived.countDown() }
    })
    java.net.Socket(loop, port).use { socket ->
        expect(tunnel.awaitPeerConnection(2000))
        val plain = ByteArray(34); plain[3] = 34
        "comm".toByteArray().copyInto(plain, 16); plain[32] = 5; plain[33] = 6
        val header = byteArrayOf(34, 0)
        val encrypted = AirPlayCrypto.chachaSeal(ByteArray(32) { 4 }, AirPlayCrypto.nonce64(0), plain, header)
        socket.getOutputStream().write(header + encrypted)
        expect(packetReceived.await(2, TimeUnit.SECONDS))
        expect(received!!.contentEquals(byteArrayOf(5, 6)))
    }
    tunnel.close()
    val wrongPeer = IapTunnel(ByteArray(32), loop, java.net.InetAddress.getByName("127.0.0.2"))
    val peerPort = wrongPeer.listen(object : IapTunnel.Listener {})
    java.net.Socket(loop, peerPort).use { socket -> socket.soTimeout = 2000; expect(socket.getInputStream().read() == -1) }
    expect(!wrongPeer.awaitPeerConnection(10)); wrongPeer.close()
    rejects { IapTunnel(ByteArray(32), java.net.InetAddress.getByName("0.0.0.0"), loop) }
    rejects { AudioStream(ByteArray(32)).listen(object : AudioStream.Listener {}, loop, null) }
    rejects { ScreenStream(ByteArray(32)).listen(object : ScreenStream.Listener {}, loop, null) }

    // Actual failed event writer must release its monitor before teardown callbacks.
    var closureOutsideWriteLock = false
    var writeLock: Any? = null
    val failedSocket = object : java.net.Socket() {
        override fun getOutputStream(): java.io.OutputStream = throw java.io.IOException("fixture write failure")
    }
    val writeSession = AirPlaySession(java.net.Socket(), AirPlayConfig("fixture", "02:00:00:00:00:01",
        "02:00:00:00:00:01", "220.68", AirPlayDisplayConfig(1280, 720)),
        AirPlayIdentity.generate(), PairingStore(), null, object : AirPlaySessionListener {},
        object : AirPlayMediaHandler {
            override fun onSessionClosed(session: AirPlaySession) {
                closureOutsideWriteLock = !Thread.holdsLock(writeLock!!)
            }
        })
    fun sessionField(name: String) = AirPlaySession::class.java.getDeclaredField(name).apply { isAccessible = true }
    writeLock = sessionField("eventWriteLock").get(writeSession)
    sessionField("eventSocket").set(writeSession, failedSocket)
    sessionField("eventCipher").set(writeSession, ControlCipher(ByteArray(32), ByteArray(32)))
    expect(!writeSession.sendCommand(mapOf("type" to "forceKeyFrame")) && closureOutsideWriteLock)
    expect(!writeSession.authenticatedForMedia)

    // Actual encrypted SETUP -> RECORD with generated host pairing keys, not an iPhone/MFi test.
    java.net.ServerSocket(0, 1, loop).use { server ->
        java.net.Socket(loop, server.localPort).use { client ->
            val accepted = server.accept()
            val testPairings = PairingStore()
            val phoneSigning = AirPlayCrypto.ed25519Generate()
            val phoneEphemeral = AirPlayCrypto.x25519Generate()
            val controllerId = "host-fixture".toByteArray()
            testPairings.save(controllerId.toString(Charsets.UTF_8), phoneSigning.publicKey)
            var activations = 0
            val engine = CarPlayMediaEngine(object : MediaSink {})
            val setupSession = AirPlaySession(accepted, AirPlayConfig("fixture", "02:00:00:00:00:01",
                "02:00:00:00:00:01", "220.68", AirPlayDisplayConfig(1280, 720)),
                AirPlayIdentity.generate(), testPairings, null, object : AirPlaySessionListener {
                    override fun onSessionActive(session: AirPlaySession) { activations++ }
                }, engine)
            try {
                val verification = sessionField("pairVerify").get(setupSession) as PairVerify
                val m2 = Tlv8Codec.decode(verification.handle(Tlv8Codec.encode(listOf(
                    Tlv8Item(6, byteArrayOf(1)), Tlv8Item(3, phoneEphemeral.publicKey)))))
                val accessoryEphemeral = m2[3]!!
                val shared = AirPlayCrypto.x25519Shared(phoneEphemeral.privateKey, accessoryEphemeral)
                val verifyKey = AirPlayCrypto.hkdfSha512(shared, "Pair-Verify-Encrypt-Salt".toByteArray(),
                    "Pair-Verify-Encrypt-Info".toByteArray())
                val signature = AirPlayCrypto.ed25519Sign(phoneSigning.privateKey,
                    phoneEphemeral.publicKey + controllerId + accessoryEphemeral)
                val proof = Tlv8Codec.encode(listOf(Tlv8Item(1, controllerId), Tlv8Item(10, signature)))
                val m4 = Tlv8Codec.decode(verification.handle(Tlv8Codec.encode(listOf(
                    Tlv8Item(6, byteArrayOf(3)), Tlv8Item(5,
                        AirPlayCrypto.chachaSeal(verifyKey, AirPlayCrypto.nonceLabel("PV-Msg03"), proof))))))
                expect(m4[6]!!.contentEquals(byteArrayOf(4)) && verification.verifiedControllerId != null)
                val keys = verification.controlKeys!!
                val accessoryCipher = ControlCipher(keys.readKey, keys.writeKey)
                val phoneCipher = ControlCipher(keys.writeKey, keys.readKey)
                sessionField("cipher").set(setupSession, accessoryCipher)
                // Only host test injects SAP completion; no fake provider/credential ships in APK.
                sessionField("sapAuthenticated").setBoolean(setupSession, true)
                expect(setupSession.authenticatedControl && !setupSession.authenticatedForMedia)
                val handle = AirPlaySession::class.java.getDeclaredMethod("handle",
                    RtspMessage.Request::class.java, java.lang.Boolean.TYPE).apply { isAccessible = true }
                val body = BplistCodec.encode(mapOf("streams" to listOf(mapOf("type" to 110L, "streamConnectionID" to 1L))))
                val setupWire = "SETUP / RTSP/1.0\r\nCSeq: 3\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray() + body
                fun dispatch(wire: ByteArray, encrypted: Boolean): RtspMessage.Response {
                    val plain = if (encrypted) accessoryCipher.decrypt(phoneCipher.encrypt(wire)).data else wire
                    return handle.invoke(setupSession, RtspMessage.parseMessages(plain).messages.single(), encrypted) as RtspMessage.Response
                }
                expect(dispatch(setupWire, false).status == 403)
                val setupResponse = dispatch(setupWire, true)
                val responseBody = BplistCodec.decode(setupResponse.body) as Map<*, *>
                val returnedStream = (responseBody["streams"] as List<*>).single() as Map<*, *>
                expect((setupResponse.status ?: 200) == 200 && (returnedStream["dataPort"] as Number).toInt() > 0)
                expect(!setupSession.authenticatedForMedia && activations == 0)
                expect(dispatch(record, false).status == 403 && activations == 0)
                expect(dispatch(record, true).status == 200 && setupSession.authenticatedForMedia && activations == 1)
            } finally {
                setupSession.close(); accepted.close()
                phoneSigning.privateKey.fill(0); phoneEphemeral.privateKey.fill(0); testPairings.clear()
            }
        }
    }

    val avcc = byteArrayOf(1, 66, 0, 30, -1, -31, 0, 2, 0x67, 0x11, 1, 0, 2, 0x68, 0x22)
    expect(AvcParameterSets.annexB(avcc).contentEquals(byteArrayOf(0,0,0,1,0x67,0x11,0,0,0,1,0x68,0x22)))
    rejects { AvcParameterSets.annexB(avcc.copyOf(9)) }
    rejects { AvcParameterSets.annexB(avcc.copyOf().also { it[4] = 0 }) }
    val gate = ProtocolGate(); val epoch = gate.begin(FakeAuthenticationProvider())
    var videos = 0; var pcm: ByteArray? = null; var errors = 0
    val listener = object : ReceiverCore.Listener {
        override fun bluetoothBootstrapConfirmed() {}
        override fun wifiSessionLinkConfirmed() {}
        override fun authenticatedSessionStarted() {}
        override fun videoAccessUnit(bytes: ByteArray, offset: Int, length: Int, timestampUs: Long): Boolean { videos++; return true }
        override fun streamReset() {}
        override fun disconnected(reason: SessionMachine.Reason) {}
        override fun audioFormat(sampleRate: Int, channels: Int): Boolean = true
        override fun audioPcm(buffer: ByteBuffer, bytes: Int): Int { pcm = ByteArray(bytes).also { buffer.get(it) }; return bytes }
    }
    val bridge = DiPlayMediaBridge(epoch, gate, listener) { errors++ }
    val idr = byteArrayOf(0,0,0,1,0x65,0x11)
    bridge.onVideoConfig(110, avcc); bridge.onVideoFrame(110, idr)
    expect(videos == 0 && !bridge.frameRendered())
    gate.bootstrapConfirmed(epoch); gate.carplayNetworkReady(epoch); gate.authenticationSucceeded(epoch)
    gate.sessionEstablished(epoch); gate.videoSinkReady(epoch)
    bridge.onVideoFrame(110, idr)
    expect(videos == 2 && bridge.frameRendered())
    val format = AudioFormat(AudioCodecKind.LPCM, 48000, 2, 96)
    bridge.onAudioStarted(100, format, 0)
    val rtp = ByteArray(16); rtp[0] = 0x80.toByte()
    rtp[12] = 1; rtp[13] = 2; rtp[14] = 3; rtp[15] = 4
    bridge.onAudioRtp(100, format, rtp, 0)
    expect(pcm == null) // Explicit UI acknowledgment is required even after protocol SESSION.
    bridge.mediaSinkReady()
    bridge.onAudioRtp(100, format, rtp, 0)
    expect(pcm!!.contentEquals(byteArrayOf(2,1,4,3)))
    gate.stop(); val before = videos; pcm = null
    bridge.onVideoFrame(110, idr); bridge.onAudioRtp(100, format, rtp, 0)
    expect(videos == before && pcm == null && !bridge.frameRendered())
    bridge.clear()

    // Regression: reject must never hold the bridge monitor while entering the owner monitor.
    val lock = Any(); val rejected = CountDownLatch(1); val ownerReady = CountDownLatch(1)
    val raceGate = ProtocolGate(); val token = raceGate.begin(FakeAuthenticationProvider())
    lateinit var raceBridge: DiPlayMediaBridge
    raceBridge = DiPlayMediaBridge(token, raceGate, listener) {
        rejected.countDown(); synchronized(lock) { errors++ }
    }
    val owner = Thread { synchronized(lock) {
        ownerReady.countDown(); check(rejected.await(2, TimeUnit.SECONDS)); raceBridge.clear()
    } }.apply { isDaemon = true }
    val decoder = Thread { check(ownerReady.await(2, TimeUnit.SECONDS)); raceBridge.onVideoConfig(110, byteArrayOf(1)) }.apply { isDaemon = true }
    owner.start(); decoder.start(); owner.join(3000); decoder.join(3000)
    expect(!owner.isAlive && !decoder.isAlive && errors == 1)

    // Recovery and rendered-frame callbacks also run outside the bridge monitor.
    val recoverEntered = CountDownLatch(1); val recoverOwnerReady = CountDownLatch(1)
    raceBridge.setVideoRecoveryHandler(110) {
        recoverEntered.countDown(); synchronized(lock) { }
    }
    val recoverOwner = Thread { synchronized(lock) {
        recoverOwnerReady.countDown(); check(recoverEntered.await(2, TimeUnit.SECONDS)); raceBridge.clear()
    } }.apply { isDaemon = true }
    val recoverWriter = Thread {
        check(recoverOwnerReady.await(2, TimeUnit.SECONDS)); raceBridge.requestKeyframe()
    }.apply { isDaemon = true }
    recoverOwner.start(); recoverWriter.start(); recoverOwner.join(3000); recoverWriter.join(3000)
    expect(!recoverOwner.isAlive && !recoverWriter.isAlive)
    raceGate.bootstrapConfirmed(token); raceGate.carplayNetworkReady(token); raceGate.authenticationSucceeded(token)
    raceGate.sessionEstablished(token); raceGate.videoSinkReady(token)
    raceBridge.onVideoFrame(110, idr)
    var diagnosticOutsideMonitor = false
    raceBridge.setVideoDiagnosticHandler(110) { diagnosticOutsideMonitor = !Thread.holdsLock(raceBridge) }
    expect(raceBridge.frameRendered() && diagnosticOutsideMonitor)
    raceGate.stop(); raceGate.begin(FakeAuthenticationProvider())
    raceBridge.onVideoConfig(110, byteArrayOf(1))
    expect(errors == 1 && !raceBridge.requestKeyframe()) // Stale bridge cannot reject a replacement.

    val earlyGate = ProtocolGate(); val earlyEpoch = earlyGate.begin(FakeAuthenticationProvider())
    var uiReady = false; var formatAttempts = 0; var earlyPcm = 0
    val earlyBridge = DiPlayMediaBridge(earlyEpoch, earlyGate, object : ReceiverCore.Listener by listener {
        override fun audioFormat(sampleRate: Int, channels: Int): Boolean { formatAttempts++; return uiReady }
        override fun audioPcm(buffer: ByteBuffer, bytes: Int): Int { earlyPcm++; return bytes }
    }) { error("unexpected early-audio rejection") }
    earlyBridge.onAudioStarted(100, format, 0)
    earlyBridge.onAudioRtp(100, format, rtp, 0)
    expect(formatAttempts == 0 && earlyPcm == 0)
    earlyGate.bootstrapConfirmed(earlyEpoch); earlyGate.carplayNetworkReady(earlyEpoch)
    earlyGate.authenticationSucceeded(earlyEpoch); earlyGate.sessionEstablished(earlyEpoch)
    earlyBridge.onAudioRtp(100, format, rtp, 0)
    expect(formatAttempts == 0 && earlyPcm == 0)
    earlyBridge.mediaSinkReady()
    expect(formatAttempts == 1 && earlyPcm == 0)
    uiReady = true; earlyBridge.mediaSinkReady(); earlyBridge.onAudioRtp(100, format, rtp, 0)
    expect(formatAttempts == 2 && earlyPcm == 1)
    earlyBridge.onAudioStopped(100); earlyBridge.onAudioRtp(100, format, rtp, 0)
    expect(earlyPcm == 1)
    earlyGate.stop(); earlyBridge.clear()

    // A delayed retired-bridge clear/notification and a queued UI callback are epoch scoped.
    val deliveryGate = ProtocolGate(); deliveryGate.begin(FakeAuthenticationProvider())
    deliveryGate.stop(); val stoppedEpoch = deliveryGate.epoch()
    val queued = ArrayList<() -> Unit>(); var failuresDelivered = 0
    val deferred = object : ReceiverCore.Listener by listener {
        override fun disconnected(reason: SessionMachine.Reason, attemptCurrent: java.util.function.BooleanSupplier) {
            queued.add { if (attemptCurrent.getAsBoolean()) failuresDelivered++ }
        }
    }
    val clearEntered = CountDownLatch(1); val clearContinue = CountDownLatch(1)
    val delayedFailure = Thread {
        clearEntered.countDown(); check(clearContinue.await(2, TimeUnit.SECONDS))
        deferred.disconnected(SessionMachine.Reason.SESSION_LOST) { deliveryGate.epoch() == stoppedEpoch }
    }.apply { isDaemon = true }
    delayedFailure.start(); expect(clearEntered.await(2, TimeUnit.SECONDS))
    val replacementEpoch = deliveryGate.begin(FakeAuthenticationProvider())
    clearContinue.countDown(); delayedFailure.join(3000)
    expect(!delayedFailure.isAlive)
    queued.forEach { it() }; queued.clear()
    expect(failuresDelivered == 0 && deliveryGate.current(replacementEpoch))
    deliveryGate.stop(); val currentStoppedEpoch = deliveryGate.epoch()
    deferred.disconnected(SessionMachine.Reason.SESSION_LOST) { deliveryGate.epoch() == currentStoppedEpoch }
    queued.forEach { it() }; queued.clear()
    expect(failuresDelivered == 1)
    println("DiPlay port PASS: $checks bounded parser/crypto/privacy/media/lifecycle fixtures; not iPhone evidence")
}
