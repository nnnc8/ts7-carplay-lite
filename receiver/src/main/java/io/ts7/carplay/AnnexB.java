package io.ts7.carplay;

/** Complete Annex-B access units only; the future transport must defragment first. */
public final class AnnexB {
    private AnnexB() {}

    public static int start(byte[] data, int from, int end) {
        for (int i = from; i + 3 < end; i++) {
            if (data[i] == 0 && data[i + 1] == 0
                    && (data[i + 2] == 1 || (data[i + 2] == 0 && data[i + 3] == 1))) return i;
        }
        return -1;
    }

    public static int header(byte[] data, int start) {
        return start + (data[start + 2] == 1 ? 3 : 4);
    }

    public static boolean hasType(byte[] data, int offset, int length, int type) {
        if (offset < 0 || length < 4 || offset > data.length - length) return false;
        int end = offset + length;
        int position = start(data, offset, end);
        while (position >= 0) {
            int nal = header(data, position);
            if (nal < end && (data[nal] & 31) == type) return true;
            position = start(data, nal + 1, end);
        }
        return false;
    }

    public static boolean valid(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 5 || offset > data.length - length) return false;
        int end = offset + length;
        int position = start(data, offset, end);
        if (position != offset) return false;
        while (position >= 0) {
            int nal = header(data, position);
            if (nal >= end || (data[nal] & 128) != 0) return false;
            int type = data[nal] & 31;
            if (type == 0 || type >= 24) return false;
            int next = start(data, nal + 1, end);
            if ((next < 0 ? end : next) <= nal + 1) return false;
            position = next;
        }
        return true;
    }
}
