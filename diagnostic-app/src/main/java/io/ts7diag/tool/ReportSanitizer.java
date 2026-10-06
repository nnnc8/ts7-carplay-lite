package io.ts7diag.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Removes identifiers and paths before a report can leave the device. */
public final class ReportSanitizer {
    private static final int MAX_STRING_LENGTH = 512;
    private static final int MAX_DEPTH = 8;

    private ReportSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> raw) {
        Object safe = sanitizeValue(raw, null, 0);
        if (safe instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) safe;
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Object sanitizeValue(Object value, String key, int depth) {
        if (value == null || depth > MAX_DEPTH || isSensitiveKey(key)) {
            return null;
        }
        if (value instanceof Map) {
            Map<?, ?> input = (Map<?, ?>) value;
            Map<String, Object> output = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : input.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    continue;
                }
                String childKey = (String) entry.getKey();
                Object child = sanitizeValue(entry.getValue(), childKey, depth + 1);
                if (child != null) {
                    output.put(childKey, child);
                }
            }
            return output;
        }
        if (value instanceof List) {
            List<?> input = (List<?>) value;
            List<Object> output = new ArrayList<>();
            for (Object item : input) {
                Object child = sanitizeValue(item, key, depth + 1);
                if (child != null) {
                    output.add(child);
                }
            }
            return output;
        }
        if (value instanceof String) {
            String text = (String) value;
            if (text.length() > MAX_STRING_LENGTH) {
                return text.substring(0, MAX_STRING_LENGTH);
            }
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.US).replace("_", "").replace("-", "");
        return normalized.equals("imei")
                || normalized.equals("imsi")
                || normalized.equals("sim")
                || normalized.equals("phonenumber")
                || normalized.equals("phone")
                || normalized.contains("account")
                || normalized.contains("email")
                || normalized.contains("contact")
                || normalized.contains("location")
                || normalized.equals("gps")
                || normalized.equals("ssid")
                || normalized.equals("bssid")
                || normalized.equals("mac")
                || normalized.contains("macaddress")
                || normalized.contains("androidid")
                || normalized.equals("serial")
                || normalized.equals("ip")
                || normalized.contains("ipaddress")
                || normalized.contains("password")
                || normalized.contains("fingerprint")
                || normalized.contains("userfile")
                || normalized.contains("devicepath")
                || normalized.equals("path");
    }
}
