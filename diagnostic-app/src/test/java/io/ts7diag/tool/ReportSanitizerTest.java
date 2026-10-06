package io.ts7diag.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free JVM test so privacy behavior can run without an Android device. */
public final class ReportSanitizerTest {
    public static void main(String[] args) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("model", "TS7");
        raw.put("ssid", "private-wifi");
        raw.put("bssid", "00:11:22:33:44:55");
        raw.put("macAddress", "00:11:22:33:44:55");
        raw.put("imei", "123456789012345");
        raw.put("imsi", "001010123456789");
        raw.put("serial", "private-serial");
        raw.put("androidId", "private-android-id");
        raw.put("email", "person@example.com");
        raw.put("location", "25.0,121.0");
        raw.put("ipAddress", "192.0.2.1");
        raw.put("fingerprint", "private/fingerprint");
        raw.put("safeList", new ArrayList<>(Arrays.asList("USB", "AVC")));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("path", "/dev/bus/usb/001/002");
        nested.put("vendorId", 1234);
        raw.put("usb", nested);

        Map<String, Object> safe = ReportSanitizer.sanitize(raw);
        require("TS7".equals(safe.get("model")), "safe model retained");
        require(safe.containsKey("safeList"), "safe list retained");
        require(!safe.containsKey("ssid"), "SSID removed");
        require(!safe.containsKey("bssid"), "BSSID removed");
        require(!safe.containsKey("macAddress"), "MAC removed");
        require(!safe.containsKey("imei"), "IMEI removed");
        require(!safe.containsKey("imsi"), "IMSI removed");
        require(!safe.containsKey("serial"), "serial removed");
        require(!safe.containsKey("androidId"), "Android ID removed");
        require(!safe.containsKey("email"), "email removed");
        require(!safe.containsKey("location"), "location removed");
        require(!safe.containsKey("ipAddress"), "IP removed");
        require(!safe.containsKey("fingerprint"), "fingerprint removed");

        @SuppressWarnings("unchecked")
        Map<String, Object> safeUsb = (Map<String, Object>) safe.get("usb");
        require(safeUsb != null && safeUsb.containsKey("vendorId"), "USB VID retained");
        require(!safeUsb.containsKey("path"), "USB path removed");

        String encoded = JsonEncoder.encode(safe);
        require(encoded.indexOf("private-wifi") < 0, "private value absent from JSON");
        System.out.println("ReportSanitizerTest PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
