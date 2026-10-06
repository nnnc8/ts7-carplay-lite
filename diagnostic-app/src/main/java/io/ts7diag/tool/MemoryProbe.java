package io.ts7diag.tool;

import android.app.ActivityManager;
import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MemoryProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "memory";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("logicalCores", Runtime.getRuntime().availableProcessors());
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return ProbeResult.failed(getKey(), ProbeResult.Status.UNAVAILABLE, elapsed(started), data, "ActivityManagerUnavailable");
            }
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            data.put("totalRamMb", megabytes(info.totalMem));
            data.put("availableRamMb", megabytes(info.availMem));
            data.put("lowMemory", info.lowMemory);
            data.put("thresholdMb", megabytes(info.threshold));
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (Throwable error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        }
    }

    private static long megabytes(long bytes) {
        return bytes < 0L ? -1L : bytes / (1024L * 1024L);
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
