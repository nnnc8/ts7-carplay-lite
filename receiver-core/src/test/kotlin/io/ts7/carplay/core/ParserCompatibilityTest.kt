package io.ts7.carplay.core

import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.media.MediaCodecSupport
import java.net.ServerSocket
import java.net.Socket
import java.util.Random

/** Locally generated parser/crypto fixtures, not a CarPlay simulator or authentication bypass. */
object ParserCompatibilityTest {
    private var checks = 0
    private fun verify(value: Boolean) { checks++; check(value) }
    private fun rejected(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        verify(failed)
    }
    @JvmStatic fun main(args: Array<String>) {
        val request = "POST /info RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 2\r\n\r\nOK".toByteArray()
        verify(RtspMessage.parseMessages(request).messages.single().body.contentEquals("OK".toByteArray()))
        verify(RtspMessage.parseMessages(request.copyOf(request.size - 1)).messages.isEmpty())
        for (length in listOf("-1", "2147483647", "4294967296", "bogus", " 1 2"))
            rejected { RtspMessage.parseMessages("POST / RTSP/1.0\r\nContent-Length: $length\r\n\r\n".toByteArray()) }
        rejected { RtspMessage.parseMessages(ByteArray(RtspMessage.MAX_BUFFER + 1)) }
        rejected { RtspMessage.parseMessages(ByteArray(16 * 1024 + 1)) }
        rejected { Tlv8Codec.decode(byteArrayOf(1, 5, 0)) }
        rejected { Tlv8Codec.decode(byteArrayOf(1)) }
        val value = linkedMapOf("streams" to listOf(110L), "name" to "fixture")
        verify(BplistCodec.decode(BplistCodec.encode(value)) == value)
        val corrupt = BplistCodec.encode(value)
        corrupt[corrupt.size - 26] = 0
        rejected { BplistCodec.decode(corrupt) }
        val random = Random(27)
        repeat(1500) {
            val malformed = ByteArray(40 + random.nextInt(128))
            random.nextBytes(malformed)
            rejected { BplistCodec.decode(malformed) }
        }
        verify(MediaCodecSupport.toAnnexB(byteArrayOf(0, 0, 0, 2, 0x65, 1), allowAnnexB = false)
            .contentEquals(byteArrayOf(0, 0, 0, 1, 0x65, 1)))
        verify(MediaCodecSupport.toAnnexB(byteArrayOf(0, 0, 0, 3, 0x65, 1), allowAnnexB = false).isEmpty())
        val key = ByteArray(32) { it.toByte() }
        val nonce = AirPlayCrypto.nonce64(0)
        val payload = byteArrayOf(0x65, 1, 2)
        val sealed = AirPlayCrypto.chachaSeal(key, nonce, payload, ByteArray(128))
        verify(ScreenCodec.decryptFrame(key, 0, ByteArray(128), sealed).contentEquals(payload))
        rejected { ScreenCodec.decryptFrame(key, 0, ByteArray(128), ByteArray(8)) }
        verify(AirPlayIdentity.generate().publicKey.size == 32)
        // Real socket framing test: unauthenticated RECORD is rejected, not reported as an active session.
        ServerSocket(0).use { server ->
            val client = Socket("127.0.0.1", server.localPort)
            val accepted = server.accept()
            client.soTimeout = 3000
            var activated = false
            val session = AirPlaySession(accepted,
                AirPlayConfig("fixture", "fixture", "fixture", "1", AirPlayDisplayConfig(1280, 720, fps = 30)),
                AirPlayIdentity.generate(), PairingStore(), null,
                object : AirPlaySessionListener {
                    override fun onSessionActive(session: AirPlaySession) { activated = true }
                }, object : AirPlayMediaHandler {})
            session.start()
            client.getOutputStream().write("RECORD / RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray())
            val response = ByteArray(256)
            val count = client.getInputStream().read(response)
            verify(String(response, 0, count).contains("403"))
            verify(!activated)
            session.close()
            client.close()
        }
        println("Parser/API-27 source fixtures PASS: $checks checks (not real iPhone evidence)")
    }
}
