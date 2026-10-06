package io.ts7.carplay;

import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.LockSupport;

/** Generated developer pattern; immutable compressed sample reused across all loops. */
public final class PatternSource {
    private static final int MAX_SAMPLE_BYTES = 1024 * 1024;
    private static final int MAX_ACCESS_UNITS = 600;
    private final SurfaceRenderer renderer;
    private final VideoProfile profile;
    private final byte[] sample = new byte[MAX_SAMPLE_BYTES];
    private final int[] starts = new int[MAX_ACCESS_UNITS + 1];
    private int count;
    private volatile boolean active;
    private volatile boolean keyframeWanted;
    private Thread thread;

    public PatternSource(AssetManager assets, SurfaceRenderer renderer, VideoProfile profile) throws IOException {
        this.renderer = renderer;
        this.profile = profile;
        int length = 0;
        try (InputStream stream = assets.open("ts7-pattern.h264")) {
            int read;
            while (length < sample.length && (read = stream.read(sample, length, sample.length - length)) != -1) length += read;
            if (stream.read() != -1) throw new IOException("PATTERN_TOO_LARGE");
        }
        int position = AnnexB.start(sample, 0, length);
        while (position >= 0) {
            int nal = AnnexB.header(sample, position);
            if ((sample[nal] & 31) == 9) {
                if (count == MAX_ACCESS_UNITS) throw new IOException("PATTERN_TOO_MANY_FRAMES");
                starts[count++] = position;
            }
            position = AnnexB.start(sample, nal + 1, length);
        }
        if (count == 0 || starts[0] != 0) throw new IOException("PATTERN_INVALID");
        starts[count] = length;
    }

    public void start() {
        active = true;
        thread = new Thread(this::feed, "ts7-pattern");
        thread.start();
    }

    public void requestKeyframe() { keyframeWanted = true; }

    public void stop() {
        active = false;
        if (thread != null) thread.interrupt();
    }

    private void feed() {
        long periodNs = 1000000000L / profile.fps;
        long startNs = System.nanoTime();
        long dueNs = startNs;
        int frame = 0;
        while (active) {
            long nowNs = System.nanoTime();
            if (dueNs > nowNs) { LockSupport.parkNanos(Math.min(dueNs - nowNs, 20000000)); continue; }
            if (keyframeWanted || nowNs - dueNs > 250000000) {
                keyframeWanted = false;
                frame = 0;
                dueNs = nowNs;
            }
            renderer.offer(sample, starts[frame], starts[frame + 1] - starts[frame], (dueNs - startNs) / 1000);
            frame = (frame + 1) % count;
            dueNs += periodNs;
        }
    }
}
