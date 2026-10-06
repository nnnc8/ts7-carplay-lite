package io.ts7.carplay;

import java.nio.ByteBuffer;

/** Copies compressed access units once into four reusable slots; no decoded frames here. */
public final class VideoQueue {
    public static final int CAPACITY = 4;
    public static final int MAX_PACKET_BYTES = 256 * 1024;
    public static final long MAX_AGE_MS = 250;
    private final byte[][] slots = new byte[CAPACITY][MAX_PACKET_BYTES];
    private final int[] lengths = new int[CAPACITY];
    private final long[] timestampsUs = new long[CAPACITY];
    private final long[] arrivalsMs = new long[CAPACITY];
    private int head;
    private int count;
    private boolean needsIdr = true;
    private long droppedPackets;
    private long droppedFrames;

    public static final class Packet {
        public int size;
        public long timestampUs;
        public long arrivalMs;
    }

    public synchronized boolean offer(byte[] data, int offset, int length,
            long timestampUs, long nowMs) {
        if (length > MAX_PACKET_BYTES || !AnnexB.valid(data, offset, length)) {
            droppedPackets++;
            return false;
        }
        boolean video = AnnexB.hasType(data, offset, length, 1) || AnnexB.hasType(data, offset, length, 5);
        if (!video) return true; // Parameter sets are cached separately before this queue.
        boolean idr = AnnexB.hasType(data, offset, length, 5);
        if (count == CAPACITY || (count > 0 && nowMs - arrivalsMs[head] > MAX_AGE_MS)) resync();
        if (needsIdr && !idr) {
            droppedPackets++;
            droppedFrames++;
            return false;
        }
        if (idr) needsIdr = false;
        int slot = (head + count) % CAPACITY;
        System.arraycopy(data, offset, slots[slot], 0, length);
        lengths[slot] = length;
        timestampsUs[slot] = timestampUs;
        arrivalsMs[slot] = nowMs;
        count++;
        notifyAll();
        return true;
    }

    public synchronized boolean poll(ByteBuffer target, Packet packet, long nowMs) {
        if (count == 0) return false;
        if (nowMs - arrivalsMs[head] > MAX_AGE_MS) {
            resync();
            return false;
        }
        if (target.remaining() < lengths[head]) throw new IllegalArgumentException("INPUT_BUFFER_SMALL");
        packet.size = lengths[head];
        packet.timestampUs = timestampsUs[head];
        packet.arrivalMs = arrivalsMs[head];
        target.put(slots[head], 0, packet.size);
        head = (head + 1) % CAPACITY;
        count--;
        return true;
    }

    public synchronized void resync() {
        droppedPackets += count;
        droppedFrames += count;
        count = 0;
        head = 0;
        needsIdr = true;
    }

    public synchronized int depth() { return count; }
    public synchronized boolean needsIdr() { return needsIdr; }
    public synchronized long droppedPackets() { return droppedPackets; }
    public synchronized long droppedFrames() { return droppedFrames; }
}
