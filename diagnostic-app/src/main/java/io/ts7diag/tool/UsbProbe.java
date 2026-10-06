package io.ts7diag.tool;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UsbProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "usb";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        try {
            UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (manager == null) {
                return ProbeResult.failed(getKey(), ProbeResult.Status.UNAVAILABLE, elapsed(started), data, "UsbManagerUnavailable");
            }
            HashMap<String, UsbDevice> devices = manager.getDeviceList();
            List<Map<String, Object>> items = new ArrayList<>();
            if (devices != null) {
                for (UsbDevice device : devices.values()) {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("vendorId", device.getVendorId());
                    item.put("productId", device.getProductId());
                    item.put("deviceClass", device.getDeviceClass());
                    item.put("deviceSubclass", device.getDeviceSubclass());
                    item.put("deviceProtocol", device.getDeviceProtocol());
                    items.add(item);
                }
            }
            data.put("deviceCount", items.size());
            data.put("devices", items);
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (Throwable error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        }
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
