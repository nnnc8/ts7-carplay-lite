package io.ts7diag.tool;

import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeviceProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "device";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("androidRelease", Build.VERSION.RELEASE);
            data.put("sdkInt", Build.VERSION.SDK_INT);
            data.put("model", Build.MODEL);
            data.put("manufacturer", Build.MANUFACTURER);
            data.put("brand", Build.BRAND);
            data.put("device", Build.DEVICE);
            data.put("product", Build.PRODUCT);
            data.put("hardware", Build.HARDWARE);
            data.put("board", Build.BOARD);
            data.put("buildDisplay", Build.DISPLAY);
            data.put("cpuAbi", Build.CPU_ABI);
            data.put("cpuAbi2", Build.CPU_ABI2);
            data.put("osArch", System.getProperty("os.arch", "unknown"));
            data.put("buildFingerprint", Build.FINGERPRINT);

            List<String> supportedAbis = new ArrayList<>();
            if (Build.SUPPORTED_ABIS != null) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (abi != null) {
                        supportedAbis.add(abi);
                    }
                }
            }
            data.put("supportedAbis", supportedAbis);
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (Throwable error) {
            return ProbeResult.failed(
                    getKey(),
                    ProbeResult.Status.FAILED,
                    elapsed(started),
                    data,
                    error.getClass().getSimpleName());
        }
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
