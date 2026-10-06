package io.ts7diag.tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NetworkProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "network";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiNetwork = connectivity == null
                    ? null
                    : connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            data.put("wifiConnected", wifiNetwork != null && wifiNetwork.isConnected());

            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) {
                return ProbeResult.failed(getKey(), ProbeResult.Status.UNAVAILABLE, elapsed(started), data, "WifiManagerUnavailable");
            }
            data.put("wifiEnabled", wifi.isWifiEnabled());
            WifiInfo info = wifi.getConnectionInfo();
            if (info != null) {
                data.put("linkSpeedMbps", info.getLinkSpeed());
                data.put("rssiDbm", info.getRssi());
                data.put("frequencyMHz", info.getFrequency());
            }
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (SecurityException error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.UNAVAILABLE, elapsed(started), data, "PermissionUnavailable");
        } catch (Throwable error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        }
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
