package io.ts7diag.tool;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphicsProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "graphics";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ConfigurationInfo info = manager.getDeviceConfigurationInfo();
                if (info != null) {
                    data.put("openGlEsVersion", info.getGlEsVersion());
                }
            }
            PackageManager packageManager = context.getPackageManager();
            Map<String, Object> features = new LinkedHashMap<>();
            features.put("wifi", hasFeature(packageManager, "android.hardware.wifi"));
            features.put("bluetooth", hasFeature(packageManager, "android.hardware.bluetooth"));
            features.put("bluetoothLe", hasFeature(packageManager, "android.hardware.bluetooth_le"));
            features.put("usbHost", hasFeature(packageManager, "android.hardware.usb.host"));
            features.put("touchscreen", hasFeature(packageManager, "android.hardware.touchscreen"));
            data.put("features", features);
            data.put("renderer", "not probed during basic diagnostics");
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (Throwable error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        }
    }

    private static boolean hasFeature(PackageManager manager, String feature) {
        return manager != null && manager.hasSystemFeature(feature);
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
