package com.shilapi.xcertplay.airplay

import java.io.Closeable
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Receive-only iAP2-over-CarPlay DataStream tunnel (stream type 130).
 *
 * The TCP stream is NetSocketChaCha20Poly1305 framed, then carries APTransportPackage records.
 * iAP2 bodies (messageType "comm") are emitted verbatim for the wired iAP2 relay.
 */
class IapTunnel(
    private val readKey: ByteArray,
    private val bindAddress: InetAddress,
    private val peer: InetAddress,
) : Closeable {
    interface Listener {
        fun onOpen(remoteAddress: String?) {}
        fun onIap(bytes: ByteArray) {}
        fun onDebug(message: String) {}
        fun onClosed(cause: Throwable?) {}
    }

    private val closed = AtomicBoolean(false)
    private val readCounter = AtomicLong(0)
    private fun debug(message: String) {} // never export protocol metadata or exception text
    private val servers = mutableListOf<ServerSocket>()
    private var socket: Socket? = null
    private val threads = mutableListOf<Thread>()
    private val peerConnected = CountDownLatch(1)
    @Volatile private var listener: Listener = object : Listener {}

    fun listen(listener: Listener): Int {
        this.listener = listener
        val bound = bindAny()
        servers += bound
        debug("AirPlay iAP tunnel listener bound=${bound.localSocketAddress}")
        servers.forEach { server ->
            threads += Thread({ accept(server) }, "airplay-iap-tunnel").apply {
                isDaemon = true
                start()
            }
        }
        return bound.localPort
    }

    private fun bindAny(): ServerSocket =
        ServerSocket().apply {
            reuseAddress = true
            require(!bindAddress.isAnyLocalAddress) { "EXPLICIT_ADDRESS_REQUIRED" }
            bind(InetSocketAddress(bindAddress, 0), 1)
            soTimeout = 15_000
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        safeClose(socket)
        servers.toList().forEach(::safeClose)
        servers.clear()
        threads.toList().forEach(Thread::interrupt)
        threads.clear()
        peerConnected.countDown()
    }

    /** Waits until the iPhone has connected to the advertised dataPort. */
    fun awaitPeerConnection(timeoutMillis: Long): Boolean {
        require(timeoutMillis >= 0) { "timeoutMillis must not be negative" }
        return try {
            peerConnected.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun accept(bound: ServerSocket) {
        debug(
            "AirPlay iAP tunnel accepting local=${bound.localSocketAddress}",
        )
        while (!closed.get()) {
            val accepted = try {
                bound.accept()
            } catch (error: Exception) {
                if (!closed.get()) listener.onClosed(error)
                return
            }
            if (closed.get()) {
                safeClose(accepted)
                return
            }
            if (accepted.inetAddress != peer) { safeClose(accepted); continue }
            accepted.soTimeout = 10_000
            accepted.setSoLinger(true, 0)
            socket = accepted
            readCounter.set(0)
            peerConnected.countDown()
            listener.onOpen(null)
            run(accepted)
            return // never reuse a DataStream key/nonce sequence for a new peer connection
        }
    }

    private fun run(sock: Socket) {
        var ciphertext = ByteArray(0)
        var plaintext = ByteArray(0)
        var failure: Throwable? = null
        var announcedData = false
        try {
            val input = sock.getInputStream()
            val buffer = ByteArray(READ_CHUNK_BYTES)
            while (!closed.get()) {
                val read = input.read(buffer)
                if (read < 0) {
                    debug("AirPlay iAP tunnel peer EOF")
                    break
                }
                if (!announcedData) {
                    announcedData = true
                    debug("AirPlay iAP tunnel received data")
                }
                require(ciphertext.size + read <= MAX_PACKAGE) { "IAP_BUFFER_LIMIT" }
                ciphertext += buffer.copyOf(read)
                val decrypted = decryptFrames(ciphertext)
                require(plaintext.size + decrypted.first.size <= MAX_PACKAGE) { "IAP_BUFFER_LIMIT" }
                plaintext += decrypted.first
                ciphertext = decrypted.second
                plaintext = parsePackages(plaintext)
            }
        } catch (error: Exception) {
            failure = error
        } finally {
            if (socket === sock) socket = null
            safeClose(sock)
            if (!closed.get()) listener.onClosed(failure)
        }
    }

    private fun decryptFrames(buffer: ByteArray): Pair<ByteArray, ByteArray> {
        val output = ArrayList<ByteArray>()
        var offset = 0
        while (buffer.size - offset >= FRAME_HEADER_LEN) {
            val length = readU16Le(buffer, offset)
            val frameLength = FRAME_HEADER_LEN + length + TAG_SIZE
            if (buffer.size - offset < frameLength) break
            val aad = buffer.copyOfRange(offset, offset + FRAME_HEADER_LEN)
            val sealed = buffer.copyOfRange(offset + FRAME_HEADER_LEN, offset + frameLength)
            val plain = AirPlayCrypto.chachaOpen(
                readKey, AirPlayCrypto.nonce64(readCounter.get()), sealed, aad,
            )
            readCounter.incrementAndGet()
            output.add(plain)
            offset += frameLength
        }
        return concatBytes(*output.toTypedArray()) to buffer.copyOfRange(offset, buffer.size)
    }

    private fun parsePackages(buffer: ByteArray): ByteArray {
        var offset = 0
        while (buffer.size - offset >= PACKAGE_HEADER_LEN) {
            val size = readU32Be(buffer, offset)
            require(size in PACKAGE_HEADER_LEN..MAX_PACKAGE) { "IAP_PACKAGE_LIMIT" }
            if (buffer.size - offset < size) break
            val messageType = readU32Be(buffer, offset + MESSAGE_TYPE_OFFSET)
            if (messageType == MSG_TYPE_COMM) {
                debug(
                    "AirPlay iAP tunnel package type=comm body=${size - PACKAGE_HEADER_LEN}",
                )
                listener.onIap(buffer.copyOfRange(offset + PACKAGE_HEADER_LEN, offset + size))
            }
            offset += size
        }
        return buffer.copyOfRange(offset, buffer.size)
    }

    private fun readU16Le(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xff) or ((source[offset + 1].toInt() and 0xff) shl 8)

    private fun readU32Be(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
            ((source[offset + 1].toInt() and 0xff) shl 16) or
            ((source[offset + 2].toInt() and 0xff) shl 8) or
            (source[offset + 3].toInt() and 0xff)

    private companion object {
        const val FRAME_HEADER_LEN = 2
        const val TAG_SIZE = 16
        const val PACKAGE_HEADER_LEN = 32
        const val MESSAGE_TYPE_OFFSET = 16
        const val MSG_TYPE_COMM = 0x636f6d6d
        const val MAX_PACKAGE = 256 * 1024
        const val READ_CHUNK_BYTES = 16 * 1024
    }
}
