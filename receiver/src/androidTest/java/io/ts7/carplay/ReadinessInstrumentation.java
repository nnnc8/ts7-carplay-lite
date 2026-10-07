package io.ts7.carplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import static io.ts7.carplay.PlatformReadiness.Code.*;

/** CI-only actual API27 probes. Never substitute an emulator result for physical ARM/radio proof. */
public final class ReadinessInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity activity = null;
        try {
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity app = activity;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ("NOT_INITIALIZED".equals(app.coreStatusForTest()) && System.nanoTime() < deadline) Thread.sleep(50);
            require("DIPLAY_CORE_READY_AUTH_BLOCKED".equals(app.coreStatusForTest()));
            require(!app.coreAuthForTest() && app.rendererForTest() == null && "IDLE".equals(app.modeForTest()));

            // Fresh installation has no location permission: denial must not stop Surface/audio/socket checks.
            run(app);
            PlatformReadiness denied = app.readinessForTest();
            require(denied.get(PlatformReadiness.Probe.localOnlyHotspot).errorCode == PERMISSION_MISSING);
            verify(denied);
            require(!app.readinessBusyForTest());

            // Actual optional API26 hotspot call on API27, not reflection or a mock service.
            try (ParcelFileDescriptor ignored = getUiAutomation().executeShellCommand(
                    "pm grant io.ts7.carplay android.permission.ACCESS_FINE_LOCATION")) { }
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && System.nanoTime() < deadline) Thread.sleep(50);
            require(app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            run(app);
            PlatformReadiness actual = app.readinessForTest();
            verify(actual);
            require(actual.get(PlatformReadiness.Probe.localOnlyHotspot).errorCode != NOT_RUN);
            require(!app.coreAuthForTest() && !app.sessionForTest().authenticated());
            require(app.rendererForTest() == null && "IDLE".equals(app.modeForTest()));
            JSONObject report = new JSONObject(app.reportForTest());
            require("0.2.1-platform".equals(report.getString("appVersion")));
            require("BLOCKED_BY_AUTHENTICATION_REQUIREMENT".equals(report.getString("authentication")));
            require(report.getJSONObject("platformReadiness").length() == 12);
            Bundle evidence = new Bundle();
            evidence.putString("platformReport", report.toString());
            sendStatus(1, evidence); // Strict public JSON only; never log raw platform exceptions or identities.

            runOnMainSync(app::showReadinessForTest);
            Bundle viewport = new Bundle();
            viewport.putString("progress", "PLATFORM_READINESS_READY");
            sendStatus(2, viewport);
            Thread.sleep(3000); // Screenshot collector; the full fixed report is separate evidence.
            result.putString("result", "PASS: Android 8.1 readiness 12 probes, permission isolation, actual binds/Surface/AudioTrack, ARMv7 NOT_TESTED on x86, auth BLOCKED, no renderer/session start");
            runOnMainSync(app::finish);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL: API27_READINESS_TEST_FAILED");
            if (activity != null) { MainActivity app = activity; runOnMainSync(app::finish); }
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void run(MainActivity app) throws Exception {
        boolean[] started = {false};
        runOnMainSync(() -> started[0] = app.startReadinessForTest());
        require(started[0]);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(65);
        while (app.readinessRunningForTest() && System.nanoTime() < deadline) Thread.sleep(50);
        require(!app.readinessRunningForTest());
    }

    private static void verify(PlatformReadiness report) throws Exception {
        JSONObject json = new JSONObject(report.json());
        require(json.length() == 12);
        for (PlatformReadiness.Probe probe : PlatformReadiness.Probe.values()) {
            PlatformReadiness.Result value = report.get(probe);
            require(value.errorCode != NOT_RUN && value.errorCode != TEST_CANCELLED);
            require(value.durationMs >= 0 && value.durationMs <= 60000 && value.status == value.errorCode.status);
            require(json.getJSONObject(probe.name()).length() == 3);
        }
        require(report.get(PlatformReadiness.Probe.jni).errorCode == ABI_NOT_ARMV7);
        for (PlatformReadiness.Probe probe : new PlatformReadiness.Probe[]{
                PlatformReadiness.Probe.coreInitialization, PlatformReadiness.Probe.multicast,
                PlatformReadiness.Probe.mdns, PlatformReadiness.Probe.tcpBind, PlatformReadiness.Probe.udpBind,
                PlatformReadiness.Probe.surface, PlatformReadiness.Probe.audioTrack})
            require(report.get(probe).errorCode == NONE);
        require(report.get(PlatformReadiness.Probe.networkBinding).errorCode == NONE
            || report.get(PlatformReadiness.Probe.networkBinding).errorCode == NETWORK_UNAVAILABLE);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("API27_READINESS_TEST_FAILED");
    }
}
