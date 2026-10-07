package io.ts7.carplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;

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
            Thread.sleep(1000);
            require(app.rendererForTest() == null && "IDLE".equals(app.modeForTest()), "Idle startup must not initialize decoder");
            CoreRuntimeChecks.run(getTargetContext());
            Bundle coreStatus = new Bundle();
            coreStatus.putString("progress", "CORE_API27_RUNTIME_PASS");
            sendStatus(1, coreStatus);
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
            result.putString("result", "PASS: Android 8.1 idle startup, 1280x720 H.264 -> MediaCodec -> Surface, 90+ frames, stream-reset recovery, bounded queue; decoder=" + renderer.decoderName());
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

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
