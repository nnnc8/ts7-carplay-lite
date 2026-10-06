package io.ts7.carplay;

public final class RetryBudget {
    private static final long[] DELAYS_MS = {1000, 2000, 5000};
    private int attempts;

    public long peekDelayMs() {
        return attempts < DELAYS_MS.length ? DELAYS_MS[attempts] : -1;
    }

    public long nextDelayMs() {
        return attempts < DELAYS_MS.length ? DELAYS_MS[attempts++] : -1;
    }

    public void reset() { attempts = 0; }
}
