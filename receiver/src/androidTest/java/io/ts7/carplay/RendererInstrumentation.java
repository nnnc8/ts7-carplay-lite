package io.ts7.carplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;
import io.ts7.carplay.core.Api27RuntimeSmoke;

/** Separate CI-only APK: actual Android 8.1 MediaCodec -> Surface smoke test. */
public final class RendererInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity activity = null;
        try {
            Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(launch);
            MainActivity app = activity;
            long startupDeadline = System.currentTimeMillis() + 10000;
            while ("NOT_INITIALIZED".equals(app.coreStatusForTest()) && System.currentTimeMillis() < startupDeadline) Thread.sleep(100);
            require("DIPLAY_CORE_READY_AUTH_BLOCKED".equals(app.coreStatusForTest()), "Actual DiPlay controller failed to initialize");
            require(!app.coreAuthForTest(), "Default provider must remain unavailable");
            Api27RuntimeSmoke.run();
            verifyAudioFocus();
            require(app.rendererForTest() == null && "IDLE".equals(app.modeForTest()), "Idle startup must not initialize decoder");
            runOnMainSync(() -> { app.enableDeveloperTest(); app.startPattern(); });
            long deadline = System.currentTimeMillis() + 20000;
            SurfaceRenderer renderer = app.rendererForTest();
            require(renderer != null, "Renderer was not created");
            while (renderer.renderedFrames() < 90 && System.currentTimeMillis() < deadline) Thread.sleep(100);
            require(renderer.renderedFrames() >= 90, "No sustained Surface frames within 20 seconds");
            require(renderer.width() == 1280 && renderer.height() == 720, "Video resolution mismatch");
            require(renderer.queueDepth() <= VideoQueue.CAPACITY, "Queue exceeds bound");
            require(!app.sessionForTest().authenticated() && app.sessionForTest().state() != SessionMachine.State.STREAMING,
                "Developer pattern must not claim CarPlay session");
            Bundle frameStatus = new Bundle();
            frameStatus.putString("progress", "SURFACE_FRAMES_READY");
            sendStatus(1, frameStatus);
            Thread.sleep(3000); // Give the separate adb screenshot collector a stable rendered viewport.
            renderer.streamReset();
            long previous = renderer.renderedFrames();
            deadline = System.currentTimeMillis() + 15000;
            while (renderer.renderedFrames() < previous + 30 && System.currentTimeMillis() < deadline) Thread.sleep(100);
            require(renderer.renderedFrames() >= previous + 30 && renderer.restartCount() >= 1, "Stream-reset recovery did not render again");
            result.putString("result", "PASS: Android 8.1 DiPlay startup AUTH_BLOCKED, BC crypto, JNI load (no radio access), native audio focus interruption/regain/permanent-loss/late-callback, silent LPCM44.1/48k mono/stereo, idle startup, 1280x720 H.264 -> MediaCodec -> Surface, 90+ frames, stream-reset recovery, bounded queue; decoder=" + renderer.decoderName());
            runOnMainSync(app::finish);
            deadline = System.currentTimeMillis() + 5000;
            while (!renderer.stopped() && System.currentTimeMillis() < deadline) Thread.sleep(100);
            require(renderer.stopped(), "Decoder worker did not stop");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            if (activity != null) { MainActivity app = activity; runOnMainSync(app::finish); }
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyAudioFocus() throws Exception {
        AudioManager manager = (AudioManager) getTargetContext().getSystemService(Context.AUDIO_SERVICE);
        require(manager != null, "Audio service unavailable");
        EventRing events = new EventRing();
        PcmAudioOutput pcm = new PcmAudioOutput(events);
        FocusedPcmAudioOutput focused = new FocusedPcmAudioOutput(getTargetContext(), pcm, events, new Handler(Looper.getMainLooper()));
        AudioFocusRequest transientFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setOnAudioFocusChangeListener(change -> {}).build();
        AudioFocusRequest permanentFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(change -> {}).build();
        try {
            for (int rate : new int[] {44100, 48000}) for (int channels : new int[] {1, 2}) {
                require(focused.start(rate, channels) && pcm.sampleRate() == rate && pcm.channels() == channels,
                    "Focused PCM format failed");
                require(focused.write(ByteBuffer.allocateDirect(3840), 3840) > 0, "Silent nonblocking PCM write failed");
            }
            AudioManager.OnAudioFocusChangeListener previous = focused.listenerForTest();
            require(manager.requestAudioFocus(transientFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                "Transient native focus request denied");
            await(() -> pcm.sampleRate() == 0, "Native transient focus did not pause PCM");
            ByteBuffer muted = ByteBuffer.allocateDirect(3840);
            require(focused.write(muted, 3840) == 3840 && muted.position() == 3840 && pcm.sampleRate() == 0,
                "Muted live samples must be discarded, not played or queued");
            require(focused.write(ByteBuffer.allocate(3), 3) == 0, "Unaligned PCM must be rejected even when muted");
            manager.abandonAudioFocusRequest(transientFocus);
            await(() -> pcm.sampleRate() == 48000 && pcm.channels() == 2, "Native focus regain did not resume PCM");
            require(manager.requestAudioFocus(permanentFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                "Permanent native focus request denied");
            await(() -> pcm.sampleRate() == 0, "Permanent focus loss did not stop PCM");
            runOnMainSync(() -> previous.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN));
            require(pcm.sampleRate() == 0, "Late gain after permanent loss restarted PCM");
            manager.abandonAudioFocusRequest(permanentFocus);
            require(focused.start(44100, 1), "Explicit next audio start failed");
            AudioManager.OnAudioFocusChangeListener replacement = focused.listenerForTest();
            runOnMainSync(() -> previous.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS));
            require(pcm.sampleRate() == 44100 && pcm.channels() == 1, "Old loss affected a replacement audio request");
            focused.stop(); focused.stop();
            runOnMainSync(() -> replacement.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN));
            require(pcm.sampleRate() == 0 && pcm.channels() == 0, "Stop must revoke pending gain");
            require(!focused.start(16000, 1) && focused.write(ByteBuffer.allocate(8), 8) == 0,
                "Unsupported format or stopped audio accepted PCM");
        } finally {
            focused.stop();
            manager.abandonAudioFocusRequest(transientFocus);
            manager.abandonAudioFocusRequest(permanentFocus);
        }
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 3000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
        require(condition.getAsBoolean(), message);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
