package io.ts7.carplay;

public final class RetryBudget {
    private static final long[] DELAYS_MS = {1000, 2000, 5000};
    private int attempts;
    private boolean inFlight;

    public boolean attemptStarted() {
        if (inFlight) return false;
        inFlight = true;
        return true;
    }

    public boolean attemptEnded() {
        if (!inFlight) return false;
        inFlight = false;
        return true;
    }

    public boolean inFlight() { return inFlight; }

    public long peekDelayMs() {
        return attempts < DELAYS_MS.length ? DELAYS_MS[attempts] : -1;
    }

    public long nextDelayMs() {
        return attempts < DELAYS_MS.length ? DELAYS_MS[attempts++] : -1;
    }

    public void reset() { attempts = 0; inFlight = false; }
}
