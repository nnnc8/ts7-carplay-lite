package io.ts7diag.tool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Thread-safe report accumulator with separate local and public snapshots. */
public final class DiagnosticReport {
    private final Map<String, Object> root = new LinkedHashMap<>();

    public DiagnosticReport(String appVersion) {
        root.put("schemaVersion", 1);
        root.put("appVersion", appVersion);
        root.put("timestamp", utcNow());
        root.put("probes", new LinkedHashMap<String, Object>());
    }

    public synchronized void addResult(ProbeResult result) {
        if ("mediaCodecAdvanced".equals(result.getKey())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mediaCodec = root.containsKey("mediaCodec")
                    ? (Map<String, Object>) root.get("mediaCodec")
                    : new LinkedHashMap<String, Object>();
            mediaCodec.put("advancedTest", copyValue(result.getData()));
            root.put("mediaCodec", mediaCodec);
        } else {
            root.put(result.getKey(), copyValue(result.getData()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> statuses = (Map<String, Object>) root.get("probes");
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", result.getStatus().name());
        status.put("durationMs", result.getDurationMs());
        if (result.getError() != null) {
            status.put("error", result.getError());
        }
        statuses.put(result.getKey(), status);
    }

    public synchronized Map<String, Object> localSnapshot() {
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) copyValue(root);
        return copy;
    }

    public synchronized Map<String, Object> publicSnapshot() {
        return ReportSanitizer.sanitize(localSnapshot());
    }

    public synchronized String localText() {
        return JsonEncoder.encodePretty(localSnapshot());
    }

    public synchronized String publicText() {
        return JsonEncoder.encodePretty(publicSnapshot());
    }

    public synchronized String publicJson() {
        return JsonEncoder.encode(publicSnapshot());
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() instanceof String) {
                    copy.put((String) entry.getKey(), copyValue(entry.getValue()));
                }
            }
            return copy;
        }
        if (value instanceof List) {
            List<?> source = (List<?>) value;
            List<Object> copy = new ArrayList<>();
            for (Object item : source) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }
}
