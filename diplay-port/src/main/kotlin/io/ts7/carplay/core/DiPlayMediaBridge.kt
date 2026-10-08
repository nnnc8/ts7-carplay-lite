// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import com.shilapi.xcertplay.airplay.*
import io.ts7.carplay.ReceiverCore
import java.nio.ByteBuffer

/** Small compressed-video/PCM seam, not a replacement protocol core or a second decoder. */
class DiPlayMediaBridge(
    private val epoch: Long,
    private val gate: ProtocolGate,
    private val listener: ReceiverCore.Listener,
    private val reject: () -> Unit,
) : MediaSink {
    private var parameters: ByteArray? = null
    private var recovery: (() -> Unit)? = null
    private var diagnostic: ((String) -> Unit)? = null
    private var audioReady = false
    private var sinkReady = false
    private var audioFormat: AudioFormat? = null
    private val pcm = ByteBuffer.allocate(4096)

    override fun onVideoCodec(type: Int, codec: VideoCodec) {
        if (gate.current(epoch) && (type != 110 || codec != VideoCodec.H264)) reject()
    }
    override fun onVideoConfig(type: Int, codecData: ByteArray) {
        if (!gate.current(epoch) || type != 110) return
        try {
            val converted = AvcParameterSets.annexB(codecData)
            synchronized(this) { if (gate.current(epoch)) parameters = converted }
        }
        catch (_: IllegalArgumentException) { if (gate.current(epoch)) reject() }
    }
    @Synchronized override fun onVideoFrame(type: Int, naluBytes: ByteArray) {
        if (type != 110 || !gate.videoAllowed(epoch, naluBytes, 0, naluBytes.size)) return
        val nowUs = System.nanoTime() / 1000
        parameters?.let {
            if (!listener.videoAccessUnit(it, 0, it.size, nowUs)) return
            parameters = null
        }
        gate.deliverVideo(epoch, naluBytes, 0, naluBytes.size) {
            listener.videoAccessUnit(naluBytes, 0, naluBytes.size, nowUs)
        }
    }
    @Synchronized override fun setVideoRecoveryHandler(type: Int, handler: () -> Unit) {
        if (gate.current(epoch) && type == 110) recovery = handler
    }
    @Synchronized override fun setVideoDiagnosticHandler(type: Int, handler: (String) -> Unit) {
        if (gate.current(epoch) && type == 110) diagnostic = handler
    }
    fun requestKeyframe(): Boolean {
        val request = synchronized(this) { if (gate.current(epoch)) recovery else null } ?: return false
        return try { request(); true } catch (_: Exception) { false }
    }
    fun frameRendered(): Boolean {
        val callback = synchronized(this) {
            if (!gate.renderedFrame(epoch)) return false
            diagnostic
        }
        callback?.invoke("first frame rendered")
        return true
    }
    @Synchronized override fun onAudioStarted(type: Int, format: AudioFormat, firstSample: Int) {
        if (!gate.current(epoch) || type != 100 || !supportedAudio(format)) return
        audioFormat = format // One bounded format only; never retain early PCM.
        audioReady = false
        prepareAudio()
    }
    /** Explicit UI acknowledgment, after authenticated session/renderer startup. */
    @Synchronized fun mediaSinkReady() {
        if (!gate.current(epoch)) return
        sinkReady = true
        prepareAudio()
    }
    private fun prepareAudio() {
        val format = audioFormat ?: return
        if (!audioReady && sinkReady && gate.current(epoch) &&
            gate.state() in listOf(ProtocolGate.State.SESSION, ProtocolGate.State.STREAMING))
            audioReady = listener.audioFormat(format.sampleRate, format.channels)
    }
    @Synchronized override fun onAudioRtp(type: Int, format: AudioFormat, rtp: ByteArray, sample: Int) {
        if (!audioReady || !sinkReady || !gate.current(epoch) || type != 100 || audioFormat != format ||
            !supportedAudio(format) || rtp.size !in 14..4096) return
        // DiPlay/LIVI's decrypted audio wire has a 12-byte RTP header, big-endian S16 LPCM.
        if ((rtp[0].toInt() and 255) != 0x80) return // no unsupported CSRC/extension/padding.
        val size = rtp.size - 12
        if (size % (format.channels * 2) != 0) return
        pcm.clear()
        for (offset in 12 until rtp.size step 2) { pcm.put(rtp[offset + 1]); pcm.put(rtp[offset]) }
        pcm.flip()
        listener.audioPcm(pcm, size) // nonblocking AudioTrack; no indefinite PCM backlog.
    }
    @Synchronized override fun onAudioStopped(type: Int) {
        if (!gate.current(epoch) || type != 100) return
        audioReady = false
        audioFormat = null
        listener.audioStopped()
    }
    private fun supportedAudio(format: AudioFormat): Boolean =
        format.codec == AudioCodecKind.LPCM && format.sampleRate in listOf(44100, 48000) &&
            format.channels in 1..2 && format.audioType in listOf("default", "media")
    @Synchronized fun clear() {
        parameters = null; recovery = null; diagnostic = null; audioReady = false
        sinkReady = false; audioFormat = null
        pcm.clear()
        while (pcm.hasRemaining()) pcm.put(0)
    }
}

/** Bounded avcC→AnnexB parameter conversion; never copies decoded pixels. */
object AvcParameterSets {
    fun annexB(bytes: ByteArray): ByteArray {
        require(bytes.size in 9..16384 && bytes[0].toInt() == 1 && bytes[4].toInt() and 3 == 3)
        var cursor = 6
        val parts = ArrayList<ByteArray>()
        fun take(count: Int, type: Int) {
            require(count in 1..8)
            repeat(count) {
                require(cursor + 2 <= bytes.size)
                val length = ((bytes[cursor].toInt() and 255) shl 8) or (bytes[cursor + 1].toInt() and 255)
                cursor += 2
                require(length in 2..4096 && cursor <= bytes.size - length && bytes[cursor].toInt() and 31 == type)
                parts.add(byteArrayOf(0, 0, 0, 1) + bytes.copyOfRange(cursor, cursor + length))
                cursor += length
            }
        }
        take(bytes[5].toInt() and 31, 7)
        require(cursor < bytes.size)
        val pps = bytes[cursor++].toInt() and 255
        take(pps, 8)
        require(parts.sumOf { it.size } <= 16384)
        val output = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) { part.copyInto(output, offset); offset += part.size }
        return output
    }
}
