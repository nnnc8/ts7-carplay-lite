package io.ts7.carplay;

/** Failed cleanup survives Activity recreation. Only process termination resets this quarantine. */
final class PlatformResourceGuard {
    private static volatile boolean quarantined;
    // One bounded run can fail at most ten releases. Keep handles owned, never inspect/serialize them.
    private static final Object[] retained = new Object[12];
    private PlatformResourceGuard() {}

    static boolean isQuarantined() { return quarantined; }

    static synchronized void quarantine(Object handle) {
        quarantined = true;
        if (handle == null) return;
        for (int i = 0; i < retained.length; i++) {
            if (retained[i] == handle) return;
            if (retained[i] == null) { retained[i] = handle; return; }
        }
    }
}
