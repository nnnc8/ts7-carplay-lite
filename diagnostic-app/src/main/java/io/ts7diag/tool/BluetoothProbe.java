package io.ts7diag.tool;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BluetoothProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "bluetooth";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                data.put("hasAdapter", false);
                data.put("enabled", false);
                return ProbeResult.failed(getKey(), ProbeResult.Status.UNAVAILABLE, elapsed(started), data, "BluetoothAdapterUnavailable");
            }
            data.put("hasAdapter", true);
            data.put("enabled", adapter.isEnabled());
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (Throwable error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        }
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
