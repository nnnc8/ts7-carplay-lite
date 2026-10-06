package io.ts7.carplay;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.nio.ByteBuffer;

/** Future core's media PCM sink. No sample conversion, DSP or unbounded backlog. */
public final class PcmAudioOutput {
    private AudioTrack track;
    private final EventRing events;
    private int sampleRate;
    private int channels;

    public PcmAudioOutput(EventRing events) { this.events = events; }

    public synchronized void start(int rate, int channelCount) {
        if ((rate != 44100 && rate != 48000) || (channelCount != 1 && channelCount != 2)) {
            throw new IllegalArgumentException("AUDIO_FORMAT_UNSUPPORTED");
        }
        stop();
        int mask = channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minimum = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IllegalStateException("AUDIO_BUFFER_UNAVAILABLE");
        track = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            new AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            Math.max(minimum, rate * channelCount * 2 / 20), AudioTrack.MODE_STREAM,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            stop();
            throw new IllegalStateException("AUDIO_INITIALIZATION_FAILED");
        }
        sampleRate = rate;
        channels = channelCount;
        track.play();
        events.add(EventCode.AUDIO_STARTED);
    }

    public synchronized int write(ByteBuffer pcm, int bytes) {
        if (track == null || bytes < 0 || bytes > pcm.remaining() || bytes % (channels * 2) != 0) return 0;
        int written = track.write(pcm, bytes, AudioTrack.WRITE_NON_BLOCKING);
        if (written < 0) { events.add(EventCode.AUDIO_ERROR, written); return 0; }
        return written; // Caller owns/reuses remaining PCM; never queue it indefinitely.
    }

    public synchronized int sampleRate() { return sampleRate; }
    public synchronized int channels() { return channels; }
    public synchronized int underruns() { return track == null ? 0 : track.getUnderrunCount(); }

    public synchronized void stop() {
        if (track != null) {
            try { track.pause(); track.flush(); } finally { track.release(); track = null; }
            events.add(EventCode.AUDIO_STOPPED);
        }
        sampleRate = 0;
        channels = 0;
    }
}
