package io.ts7.carplay;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All codec calls run on one worker. No decoded pixels ever enter Java memory. */
public final class SurfaceRenderer {
    public interface Listener {
        void firstFrame();
        void keyframeNeeded();
        void failed(SessionMachine.Reason reason);
    }
    private final Surface surface;
    private final VideoProfile profile;
    private final EventRing events;
    private final Handler callbacks;
    private final Listener listener;
    private final VideoQueue queue = new VideoQueue();
    private final AvcConfig config = new AvcConfig();
    private final RetryBudget retries = new RetryBudget();
    private final Set<String> rejectedCodecs = new HashSet<>();
    private final MediaCodec.BufferInfo output = new MediaCodec.BufferInfo();
    private final VideoQueue.Packet packet = new VideoQueue.Packet();
    private final long[] submittedPts = new long[64];
    private final long[] submittedAt = new long[64];
    private int submittedNext;
    private Thread worker;
    private volatile boolean active;
    private volatile boolean recoveryRequested;
    private volatile MediaCodec codec;
    private volatile String decoderName = "NOT_STARTED";
    private volatile int videoWidth;
    private volatile int videoHeight;
    private volatile int restartCount;
    private volatile long lastFrameMs;
    private volatile long startedMs;
    private volatile long lastLoopMs;
    private volatile long retryAtMs;
    private long lastKeyframeRequestMs;
    private long stableSinceMs;
    private boolean hasConfigured;
    private int configuredRevision = -1;
    private int pendingInput = -1;
    private long renderedFrames;
    private long fpsWindowStartMs;
    private long fpsWindowFrames;
    private double measuredFps;
    private double latencyMs;

    public SurfaceRenderer(Surface surface, VideoProfile profile, EventRing events,
            Handler callbacks, Listener listener) {
        this.surface = surface;
        this.profile = profile;
        this.events = events;
        this.callbacks = callbacks;
        this.listener = listener;
    }

    public synchronized void start() {
        if (worker != null) throw new IllegalStateException("WORKER_ALREADY_STARTED");
        active = true;
        startedMs = SystemClock.elapsedRealtime();
        lastLoopMs = startedMs;
        fpsWindowStartMs = startedMs;
        worker = new Thread(this::decodeLoop, "ts7-video");
        worker.start();
    }

    public boolean offer(byte[] data, int offset, int length, long timestampUs) {
        if (!active) return false;
        int revision = config.revision();
        if (!config.accept(data, offset, length, events)) {
            events.add(EventCode.INPUT_REJECTED);
            queue.resync();
            return false;
        }
        if (config.revision() != revision) queue.resync();
        return queue.offer(data, offset, length, timestampUs, SystemClock.elapsedRealtime());
    }

    public void streamReset() {
        config.reset();
        queue.resync();
        recoveryRequested = true;
        events.add(EventCode.STREAM_RESET);
    }

    public void stop() {
        active = false;
        Thread current = worker;
        if (current != null) current.interrupt();
    }

    public boolean stopped() { return worker == null || !worker.isAlive(); }
    public boolean awaitStopped(long timeoutMs) {
        Thread current = worker;
        if (current == null) return true;
        try { current.join(Math.max(1, Math.min(250, timeoutMs))); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        return !current.isAlive();
    }
    public String decoderName() { return decoderName; }
    public int width() { return videoWidth; }
    public int height() { return videoHeight; }
    public int restartCount() { return restartCount; }
    public int queueDepth() { return queue.depth(); }
    public long droppedPackets() { return queue.droppedPackets(); }
    public long droppedFrames() { return queue.droppedFrames(); }
    public synchronized long renderedFrames() { return renderedFrames; }
    public synchronized double latencyMs() { return latencyMs; }
    public synchronized double measuredFps() {
        if (SystemClock.elapsedRealtime() - lastFrameMs > 1500) return 0;
        return measuredFps;
    }

    /** Main-thread watchdog only signals the worker; never flush/release codec on the UI thread. */
    public void watchdog() {
        long now = SystemClock.elapsedRealtime();
        if (active && now - lastLoopMs > 5000) {
            events.add(EventCode.CODEC_CALL_TIMEOUT);
            fail(SessionMachine.Reason.DECODER_ERROR);
            return; // Keep the one stuck worker referenced; do not multiply blocked vendor calls.
        }
        long reference = lastFrameMs == 0 ? startedMs : lastFrameMs;
        long threshold = lastFrameMs == 0 ? 5000 : 3000;
        if (active && retryAtMs == 0 && now - reference > threshold && !recoveryRequested) {
            events.add(EventCode.VIDEO_STALL);
            recoveryRequested = true;
        }
    }

    private void decodeLoop() {
        try {
            while (active) {
                long now = SystemClock.elapsedRealtime();
                lastLoopMs = now;
                if (!surface.isValid()) { fail(SessionMachine.Reason.SURFACE_LOST); break; }
                if (recoveryRequested) {
                    recoveryRequested = false;
                    if (!recover(now)) break;
                }
                if (retryAtMs != 0 && now < retryAtMs) { pause(); continue; }
                if (retryAtMs != 0) {
                    retryAtMs = 0;
                    startedMs = now;
                    lastFrameMs = 0;
                    listener.keyframeNeeded();
                }
                AvcConfig.Snapshot current = config.snapshot();
                if (codec != null && (current == null || current.revision != configuredRevision)) releaseCodec();
                if (codec == null && current != null && queue.depth() > 0) configure(current);
                if (codec == null) { requestIdrIfNeeded(now); pause(); continue; }
                feedInput(now);
                drainOutput();
                requestIdrIfNeeded(now);
                if (stableSinceMs != 0 && now - stableSinceMs > 10000 && now - lastFrameMs < 1500) retries.reset();
                pause();
            }
        } catch (RuntimeException error) {
            // A cleanup/configuration failure is contained, not an Activity crash.
            events.add(EventCode.DECODER_ERROR, codecErrorCode(error));
            fail(SessionMachine.Reason.DECODER_ERROR);
        } finally {
            active = false;
            try { releaseCodec(); } catch (RuntimeException ignored) { events.add(EventCode.DECODER_ERROR); }
            queue.resync();
        }
    }

    private void feedInput(long now) {
        try {
            if (pendingInput < 0) pendingInput = codec.dequeueInputBuffer(0);
            if (pendingInput < 0 || queue.depth() == 0) return;
            ByteBuffer input = codec.getInputBuffer(pendingInput);
            if (input == null) throw new IllegalStateException("INPUT_BUFFER_UNAVAILABLE");
            input.clear();
            if (!queue.poll(input, packet, now)) return;
            synchronized (this) {
                submittedPts[submittedNext] = packet.timestampUs;
                submittedAt[submittedNext] = packet.arrivalMs;
                submittedNext = (submittedNext + 1) % submittedPts.length;
            }
            codec.queueInputBuffer(pendingInput, 0, packet.size, packet.timestampUs, 0);
            pendingInput = -1;
        } catch (RuntimeException error) {
            events.add(EventCode.DECODER_ERROR, codecErrorCode(error));
            recoveryRequested = true;
        }
    }

    private void drainOutput() {
        try {
            int index;
            // Bound each drain turn so stop/recovery/input cannot starve.
            for (int drained = 0; active && drained < 8; drained++) {
                index = codec.dequeueOutputBuffer(output, 0);
                if (index >= 0) {
                    boolean render = active && surface.isValid()
                        && (output.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        && !((output.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 && output.size == 0);
                    codec.releaseOutputBuffer(index, render);
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    videoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                } else break;
            }
        } catch (RuntimeException error) {
            events.add(EventCode.DECODER_ERROR, codecErrorCode(error));
            recoveryRequested = true;
        }
    }

    private void configure(AvcConfig.Snapshot current) {
        for (String candidate : decoderNames()) {
            if (!active) return;
            if (rejectedCodecs.contains(candidate)) continue;
            try {
                codec = MediaCodec.createByCodecName(candidate);
                if (!active) { releaseCodec(); return; }
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", current.width, current.height);
                format.setByteBuffer("csd-0", ByteBuffer.wrap(current.sps));
                format.setByteBuffer("csd-1", ByteBuffer.wrap(current.pps));
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, VideoQueue.MAX_PACKET_BYTES);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, profile.fps);
                codec.configure(format, surface, null, 0);
                if (!active) { releaseCodec(); return; }
                codec.setOnFrameRenderedListener(this::frameRendered, callbacks);
                codec.start();
                decoderName = codec.getName();
                videoWidth = current.width;
                videoHeight = current.height;
                configuredRevision = current.revision;
                pendingInput = -1;
                if (hasConfigured) restartCount++;
                hasConfigured = true;
                events.add(EventCode.DECODER_CONFIGURED);
                return;
            } catch (Exception error) {
                events.add(EventCode.DECODER_ERROR, codecErrorCode(error));
                rejectedCodecs.add(candidate);
                // Configuration/start failure, not just create failure, advances to next decoder.
                releaseCodec();
            }
        }
        recoveryRequested = true;
    }

    private static List<String> decoderNames() {
        MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        List<String> vendors = new ArrayList<>();
        List<String> systems = new ArrayList<>();
        boolean sprd = false;
        for (MediaCodecInfo info : codecs) {
            if (info.isEncoder()) continue;
            boolean avc = false;
            for (String type : info.getSupportedTypes()) if ("video/avc".equalsIgnoreCase(type)) avc = true;
            if (!avc) continue;
            String name = info.getName();
            if ("OMX.sprd.h264.decoder".equals(name)) {
                sprd = true;
            } else if (!name.startsWith("OMX.google.") && !name.startsWith("c2.android.")) vendors.add(name);
            else systems.add(name);
        }
        List<String> ordered = new ArrayList<>();
        if (sprd) ordered.add("OMX.sprd.h264.decoder");
        ordered.addAll(vendors);
        ordered.addAll(systems);
        return ordered;
    }

    private synchronized void frameRendered(MediaCodec source, long ptsUs, long nanoTime) {
        if (!active || source != codec) return;
        long now = SystemClock.elapsedRealtime();
        boolean first = renderedFrames == 0;
        if (lastFrameMs == 0) {
            stableSinceMs = now;
            if (!first) events.add(EventCode.RECOVERY_SUCCESS);
        }
        lastFrameMs = now;
        renderedFrames++;
        fpsWindowFrames++;
        long windowMs = now - fpsWindowStartMs;
        if (windowMs >= 1000) {
            measuredFps = fpsWindowFrames * 1000.0 / windowMs;
            fpsWindowFrames = 0;
            fpsWindowStartMs = now;
        }
        for (int i = 0; i < submittedPts.length; i++) {
            if (submittedPts[i] == ptsUs && submittedAt[i] != 0) {
                latencyMs = Math.max(0, now - submittedAt[i]);
                submittedAt[i] = 0;
                break;
            }
        }
        if (first) { events.add(EventCode.FIRST_FRAME); listener.firstFrame(); }
    }

    private boolean recover(long now) {
        releaseCodec();
        queue.resync();
        stableSinceMs = 0;
        long delay = retries.nextDelayMs();
        if (delay < 0) { fail(SessionMachine.Reason.RECOVERY_EXHAUSTED); return false; }
        events.add(EventCode.RECOVERY_START, delay);
        retryAtMs = now + delay;
        listener.keyframeNeeded();
        return true;
    }

    private void requestIdrIfNeeded(long now) {
        if (queue.needsIdr() && now - lastKeyframeRequestMs >= 1000) {
            lastKeyframeRequestMs = now;
            events.add(EventCode.KEYFRAME_REQUEST);
            listener.keyframeNeeded();
        }
    }

    private void releaseCodec() {
        MediaCodec previous = codec;
        codec = null;
        configuredRevision = -1;
        pendingInput = -1;
        if (previous != null) {
            try { previous.stop(); } catch (IllegalStateException ignored) { /* May not have started. */ }
            finally { previous.release(); }
        }
    }

    private void fail(SessionMachine.Reason reason) {
        active = false;
        listener.failed(reason);
    }

    private static long codecErrorCode(Throwable error) {
        return error instanceof MediaCodec.CodecException ? ((MediaCodec.CodecException) error).getErrorCode() : 0;
    }

    private void pause() {
        try { Thread.sleep(5); } catch (InterruptedException ignored) { /* active is checked next. */ }
    }
}
