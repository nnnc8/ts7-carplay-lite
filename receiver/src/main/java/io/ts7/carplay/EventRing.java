package io.ts7.carplay;

public final class EventRing {
    public static final int CAPACITY = 500;
    private final long[] elapsed = new long[CAPACITY];
    private final EventCode[] codes = new EventCode[CAPACITY];
    private final long[] values = new long[CAPACITY];
    private final long startedNs = System.nanoTime();
    private int next;
    private int count;

    public synchronized void add(EventCode code) { add(code, 0); }

    public synchronized void add(EventCode code, long value) {
        elapsed[next] = (System.nanoTime() - startedNs) / 1000000;
        codes[next] = code;
        values[next] = value;
        next = (next + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    public synchronized int size() { return count; }

    public synchronized String json(int limit) {
        int length = Math.min(count, Math.max(0, limit));
        int start = (next - length + CAPACITY) % CAPACITY;
        StringBuilder result = new StringBuilder(length * 64).append('[');
        for (int i = 0; i < length; i++) {
            int slot = (start + i) % CAPACITY;
            if (i != 0) result.append(',');
            result.append("{\"elapsedMs\":").append(elapsed[slot])
                .append(",\"code\":\"").append(codes[slot].name())
                .append("\",\"value\":").append(values[slot]).append('}');
        }
        return result.append(']').toString();
    }
}
