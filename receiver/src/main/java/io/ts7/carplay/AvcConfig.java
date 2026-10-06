package io.ts7.carplay;

/** Bounded SPS/PPS cache. New SPS invalidates PPS; configuration precedes any decoder creation. */
public final class AvcConfig {
    private static final int MAX_PARAMETER_BYTES = 1024;
    private byte[] sps;
    private byte[] pps;
    private int spsId;
    private int width;
    private int height;
    private int revision;
    private Snapshot ready;

    public static final class Snapshot {
        public final byte[] sps;
        public final byte[] pps;
        public final int width;
        public final int height;
        public final int revision;
        private Snapshot(byte[] sps, byte[] pps, int width, int height, int revision) {
            this.sps = sps;
            this.pps = pps;
            this.width = width;
            this.height = height;
            this.revision = revision;
        }
    }

    public synchronized boolean accept(byte[] data, int offset, int length, EventRing events) {
        if (length > VideoQueue.MAX_PACKET_BYTES || !AnnexB.valid(data, offset, length)) return false;
        int end = offset + length;
        int position = AnnexB.start(data, offset, end);
        while (position >= 0) {
            int nal = AnnexB.header(data, position);
            int next = AnnexB.start(data, nal + 1, end);
            int nalEnd = next < 0 ? end : next;
            int type = data[nal] & 31;
            if ((type == 7 || type == 8) && nalEnd - nal > MAX_PARAMETER_BYTES) return false;
            try {
                if (type == 7 && !sameNal(sps, data, nal, nalEnd)) {
                    Bits bits = new Bits(data, nal + 1, nalEnd);
                    parseSps(bits);
                    sps = normalized(data, nal, nalEnd);
                    pps = null;
                    ready = null;
                    revision++;
                    events.add(EventCode.VIDEO_SPS_RECEIVED);
                } else if (type == 8 && !sameNal(pps, data, nal, nalEnd)) {
                    Bits bits = new Bits(data, nal + 1, nalEnd);
                    int ppsId = bits.ue();
                    int linkedSps = bits.ue();
                    if (ppsId > 255 || sps == null || linkedSps != spsId) return false;
                    // Mandatory PPS fields, bounded slice groups; no partial/header-only PPS.
                    bits.read(1);
                    bits.read(1);
                    if (bits.ue() != 0) return false;
                    if (bits.ue() > 31 || bits.ue() > 31) return false;
                    bits.read(1);
                    bits.read(2);
                    bits.se(); bits.se(); bits.se();
                    bits.read(1); bits.read(1); bits.read(1);
                    pps = normalized(data, nal, nalEnd);
                    revision++;
                    ready = new Snapshot(sps, pps, width, height, revision);
                    events.add(EventCode.VIDEO_PPS_RECEIVED);
                }
            } catch (IllegalArgumentException error) {
                reset();
                return false;
            }
            position = next;
        }
        return true;
    }

    public synchronized Snapshot snapshot() { return ready; }
    public synchronized int revision() { return revision; }

    public synchronized void reset() {
        sps = null;
        pps = null;
        ready = null;
        revision++;
    }

    private void parseSps(Bits bits) {
        int profile = bits.read(8);
        bits.read(8); // constraint flags
        bits.read(8); // level
        int id = bits.ue();
        if (id > 31) throw new IllegalArgumentException("SPS_ID");
        int chroma = 1;
        if (profile == 100 || profile == 110 || profile == 122 || profile == 244
                || profile == 44 || profile == 83 || profile == 86 || profile == 118
                || profile == 128 || profile == 138 || profile == 139 || profile == 134) {
            chroma = bits.ue();
            if (chroma > 3) throw new IllegalArgumentException("SPS_CHROMA");
            if (chroma == 3 && bits.read(1) != 0) chroma = 0;
            if (bits.ue() > 0 || bits.ue() > 0) throw new IllegalArgumentException("SPS_BIT_DEPTH");
            bits.read(1);
            if (bits.read(1) != 0) {
                for (int i = 0; i < (chroma == 3 ? 12 : 8); i++) {
                    if (bits.read(1) != 0) skipScalingList(bits, i < 6 ? 16 : 64);
                }
            }
        } else if (profile != 66 && profile != 77 && profile != 88) {
            throw new IllegalArgumentException("SPS_PROFILE");
        }
        if (bits.ue() > 12) throw new IllegalArgumentException("SPS_FRAME_NUM");
        int order = bits.ue();
        if (order == 0) {
            if (bits.ue() > 12) throw new IllegalArgumentException("SPS_POC");
        } else if (order == 1) {
            bits.read(1); bits.se(); bits.se();
            int cycles = bits.ue();
            if (cycles > 255) throw new IllegalArgumentException("SPS_CYCLES");
            for (int i = 0; i < cycles; i++) bits.se();
        } else if (order != 2) throw new IllegalArgumentException("SPS_ORDER");
        if (bits.ue() > 16) throw new IllegalArgumentException("SPS_REFERENCES");
        bits.read(1);
        int widthMbs = bits.ue() + 1;
        int heightMbs = bits.ue() + 1;
        int progressive = bits.read(1);
        if (widthMbs > 80 || heightMbs > 45 || progressive != 1) {
            throw new IllegalArgumentException("SPS_RESOLUTION");
        }
        bits.read(1);
        int cropX = chroma == 1 || chroma == 2 ? 2 : 1;
        int cropY = chroma == 1 ? 2 : 1;
        int parsedWidth = widthMbs * 16;
        int parsedHeight = heightMbs * 16;
        if (bits.read(1) != 0) {
            parsedWidth -= (bits.ue() + bits.ue()) * cropX;
            parsedHeight -= (bits.ue() + bits.ue()) * cropY;
        }
        if (parsedWidth < 64 || parsedHeight < 64 || parsedWidth > 1280 || parsedHeight > 720) {
            throw new IllegalArgumentException("SPS_CROP");
        }
        spsId = id;
        width = parsedWidth;
        height = parsedHeight;
    }

    private static void skipScalingList(Bits bits, int size) {
        int last = 8;
        int next = 8;
        for (int i = 0; i < size; i++) {
            if (next != 0) next = (last + bits.se() + 256) & 255;
            if (next != 0) last = next;
        }
    }

    private static boolean sameNal(byte[] cached, byte[] source, int start, int end) {
        if (cached == null || cached.length != end - start + 4) return false;
        for (int i = start; i < end; i++) if (cached[i - start + 4] != source[i]) return false;
        return true;
    }

    private static byte[] normalized(byte[] data, int start, int end) {
        byte[] result = new byte[end - start + 4];
        result[3] = 1;
        System.arraycopy(data, start, result, 4, end - start);
        return result;
    }

    private static final class Bits {
        private final byte[] rbsp;
        private int position;
        Bits(byte[] data, int start, int end) {
            rbsp = new byte[end - start];
            int count = 0;
            int zeros = 0;
            for (int i = start; i < end; i++) {
                int value = data[i] & 255;
                if (zeros >= 2 && value == 3) { zeros = 0; continue; }
                rbsp[count++] = data[i];
                zeros = value == 0 ? zeros + 1 : 0;
            }
            limit = count * 8;
        }
        private final int limit;
        int read(int count) {
            if (count < 0 || count > 31 || position > limit - count) throw new IllegalArgumentException("SPS_TRUNCATED");
            int value = 0;
            for (int i = 0; i < count; i++) {
                value = (value << 1) | ((rbsp[position / 8] >> (7 - position % 8)) & 1);
                position++;
            }
            return value;
        }
        int ue() {
            int zeros = 0;
            while (read(1) == 0) if (++zeros > 20) throw new IllegalArgumentException("SPS_GOLOMB");
            return (1 << zeros) - 1 + read(zeros);
        }
        int se() { int value = ue(); return (value & 1) == 0 ? -value / 2 : (value + 1) / 2; }
    }
}
