package io.ts7diag.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable result returned by one probe. */
public final class ProbeResult {
    public enum Status {
        PASS,
        FAILED,
        TIMEOUT,
        UNAVAILABLE
    }

    private final String key;
    private final Status status;
    private final long durationMs;
    private final Map<String, Object> data;
    private final String error;

    private ProbeResult(
            String key,
            Status status,
            long durationMs,
            Map<String, Object> data,
            String error) {
        this.key = key;
        this.status = status;
        this.durationMs = durationMs;
        this.data = data == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.error = error;
    }

    public static ProbeResult pass(String key, long durationMs, Map<String, Object> data) {
        return new ProbeResult(key, Status.PASS, durationMs, data, null);
    }

    public static ProbeResult failed(
            String key,
            Status status,
            long durationMs,
            Map<String, Object> data,
            String error) {
        return new ProbeResult(key, status, durationMs, data, error);
    }

    public String getKey() {
        return key;
    }

    public Status getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getError() {
        return error;
    }
}
