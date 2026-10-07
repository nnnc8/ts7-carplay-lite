package io.ts7.carplay;

/** Only fixed enums and bounded durations can enter the public readiness report. */
public final class PlatformReadiness {
    public static final int MAX_DURATION_MS = 60000;

    public enum Probe {
        coreInitialization("DiPlay core"), jni("ARMv7 JNI"), bluetoothApi("Bluetooth API"),
        rfcomm("RFCOMM"), localOnlyHotspot("LocalOnlyHotspot"), multicast("Multicast"),
        mdns("mDNS bind"), tcpBind("TCP bind"), udpBind("UDP bind"),
        networkBinding("Network binding"), surface("Surface"), audioTrack("AudioTrack");
        public final String label;
        Probe(String label) { this.label = label; }
    }

    public enum Status { PASS, FAIL, PERMISSION_DENIED, UNAVAILABLE, NOT_TESTED }

    public enum Code {
        NONE(Status.PASS), PERMISSION_MISSING(Status.PERMISSION_DENIED),
        API_UNAVAILABLE(Status.UNAVAILABLE), SERVICE_UNAVAILABLE(Status.UNAVAILABLE),
        HARDWARE_UNAVAILABLE(Status.UNAVAILABLE), RADIO_DISABLED(Status.UNAVAILABLE),
        NETWORK_UNAVAILABLE(Status.UNAVAILABLE), HOTSPOT_UNSUPPORTED(Status.UNAVAILABLE),
        HOTSPOT_INCOMPATIBLE(Status.UNAVAILABLE), HOTSPOT_DISALLOWED(Status.UNAVAILABLE),
        SURFACE_UNAVAILABLE(Status.UNAVAILABLE),
        PROBE_FAILED(Status.FAIL), PROBE_TIMEOUT(Status.FAIL), CORE_INIT_FAILED(Status.FAIL),
        JNI_LOAD_FAILED(Status.FAIL), RFCOMM_CREATE_FAILED(Status.FAIL),
        HOTSPOT_START_FAILED(Status.FAIL), MULTICAST_FAILED(Status.FAIL),
        MDNS_BIND_FAILED(Status.FAIL), TCP_BIND_FAILED(Status.FAIL), UDP_BIND_FAILED(Status.FAIL),
        NETWORK_BIND_FAILED(Status.FAIL), SURFACE_INVALID(Status.FAIL),
        AUDIO_CREATE_FAILED(Status.FAIL), RESOURCE_RELEASE_FAILED(Status.FAIL),
        NOT_RUN(Status.NOT_TESTED), TEST_CANCELLED(Status.NOT_TESTED),
        ABI_NOT_ARMV7(Status.NOT_TESTED), PREVIOUS_PROBE_RUNNING(Status.NOT_TESTED);
        public final Status status;
        Code(Status status) { this.status = status; }
    }

    public static final class Result {
        public final Status status;
        public final int durationMs;
        public final Code errorCode;
        public Result(Code code, long durationMs) {
            if (code == null) throw new IllegalArgumentException("Fixed code required");
            this.status = code.status;
            this.durationMs = (int) Math.max(0, Math.min(MAX_DURATION_MS, durationMs));
            this.errorCode = code;
        }
    }

    private final Result[] results;

    public PlatformReadiness() {
        results = new Result[Probe.values().length];
        for (int i = 0; i < results.length; i++) results[i] = new Result(Code.NOT_RUN, 0);
    }

    private PlatformReadiness(Result[] results) { this.results = results.clone(); }

    public Result get(Probe probe) { return results[probe.ordinal()]; }

    public PlatformReadiness with(Probe probe, Result result) {
        if (probe == null || result == null) throw new IllegalArgumentException("Fixed result required");
        Result[] updated = results.clone();
        updated[probe.ordinal()] = result;
        return new PlatformReadiness(updated);
    }

    public String json() {
        StringBuilder json = new StringBuilder(1600).append('{');
        for (Probe probe : Probe.values()) {
            if (probe.ordinal() != 0) json.append(',');
            Result result = get(probe);
            json.append('"').append(probe.name()).append("\":{\"status\":\"")
                .append(result.status.name()).append("\",\"durationMs\":").append(result.durationMs)
                .append(",\"errorCode\":\"").append(result.errorCode.name()).append("\"}");
        }
        return json.append('}').toString();
    }

    public String display() {
        StringBuilder text = new StringBuilder("TS7 Platform Readiness\n\n");
        for (Probe probe : Probe.values()) {
            Result result = get(probe);
            text.append(probe.label).append("  ").append(result.status.name()).append('\n')
                .append("  ").append(result.durationMs).append(" ms · ")
                .append(result.errorCode.name()).append('\n');
        }
        return text.append("\nAuthentication  BLOCKED (expected)\n")
            .append("Local capabilities only; not a CarPlay session or end-to-end wireless proof.").toString();
    }
}
