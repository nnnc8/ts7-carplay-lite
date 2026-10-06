package io.ts7diag.tool;

import android.os.Environment;

import android.content.Context;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StorageProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "storage";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            File dataDirectory = Environment.getDataDirectory();
            if (dataDirectory == null) {
                return ProbeResult.failed(getKey(), ProbeResult.Status.UNAVAILABLE, elapsed(started), data, "DataDirectoryUnavailable");
            }
            data.put("dataTotalMb", megabytes(dataDirectory.getTotalSpace()));
            data.put("dataAvailableMb", megabytes(dataDirectory.getUsableSpace()));
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
