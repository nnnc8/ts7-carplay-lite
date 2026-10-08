package io.ts7.carplay;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import java.nio.ByteBuffer;

/** API26 focus around the unchanged PCM sink. Live muted samples are dropped, never replayed. */
final class FocusedPcmAudioOutput {
    private final AudioManager manager;
    private final PcmAudioOutput output;
    private final EventRing events;
    private final Handler handler;
    private AudioFocusRequest request;
    private AudioManager.OnAudioFocusChangeListener listener;
    private long epoch;
    private int rate;
    private int channels;
    private boolean granted;

    FocusedPcmAudioOutput(Context context, PcmAudioOutput output, EventRing events, Handler handler) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.output = output;
        this.events = events;
        this.handler = handler;
    }

    synchronized boolean start(int sampleRate, int channelCount) {
        stop();
        if (manager == null || (sampleRate != 44100 && sampleRate != 48000)
                || (channelCount != 1 && channelCount != 2)) return false;
        rate = sampleRate;
        channels = channelCount;
        long current = epoch;
        listener = change -> changed(current, change);
        request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAcceptsDelayedFocusGain(true).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(listener, handler).build();
        try {
            int result = manager.requestAudioFocus(request);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                output.start(rate, channels);
                granted = true;
                return true;
            }
            if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) return true;
        } catch (RuntimeException error) { events.add(EventCode.AUDIO_ERROR); }
        stop();
        return false;
    }

    private synchronized void changed(long current, int change) {
        if (current != epoch || request == null) return;
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (granted) return;
            try { output.start(rate, channels); granted = true; }
            catch (RuntimeException error) { events.add(EventCode.AUDIO_ERROR); stop(); }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            granted = false;
            stopTrack();
            if (change == AudioManager.AUDIOFOCUS_LOSS) abandon();
        }
    }

    synchronized int write(ByteBuffer pcm, int bytes) {
        if (pcm == null || channels == 0 || bytes < 0 || bytes > pcm.remaining()
                || bytes % (channels * 2) != 0) return 0;
        if (granted) return output.write(pcm, bytes);
        // ponytail: pause live audio by discarding PCM; no catch-up queue after an interruption.
        pcm.position(pcm.position() + bytes);
        return bytes; // Consumed, not played; the underlying sink reports rate/channels zero while muted.
    }

    synchronized void stop() {
        granted = false;
        abandon();
        rate = 0;
        channels = 0;
        stopTrack();
    }

    private void abandon() {
        epoch++; // Late gain/loss from an old request must not affect a replacement or stopped session.
        AudioFocusRequest previous = request;
        request = null;
        listener = null;
        if (previous != null) {
            try { manager.abandonAudioFocusRequest(previous); }
            catch (RuntimeException error) { events.add(EventCode.AUDIO_ERROR); }
        }
    }

    private void stopTrack() {
        try { output.stop(); }
        catch (RuntimeException error) { events.add(EventCode.AUDIO_ERROR); }
    }

    // Package-visible only for the separate CI instrumentation's late-callback regression.
    synchronized AudioManager.OnAudioFocusChangeListener listenerForTest() { return listener; }
}
