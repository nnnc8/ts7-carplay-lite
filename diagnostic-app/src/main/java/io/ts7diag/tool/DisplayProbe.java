package io.ts7diag.tool;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DisplayProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "display";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (manager == null || manager.getDefaultDisplay() == null) {
                return ProbeResult.failed(getKey(), ProbeResult.Status.UNAVAILABLE, elapsed(started), data, "WindowManagerUnavailable");
            }
            Display display = manager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            data.put("realWidth", metrics.widthPixels);
            data.put("realHeight", metrics.heightPixels);
            data.put("densityDpi", metrics.densityDpi);
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                data.put("refreshRateHz", display.getRefreshRate());
            }
            int orientation = context.getResources().getConfiguration().orientation;
            data.put("orientation", orientationName(orientation));
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (Throwable error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        }
    }

    private static String orientationName(int orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return "landscape";
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return "portrait";
        }
        return "unknown";
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
