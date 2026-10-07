package io.ts7.carplay;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** A hung vendor call cannot prevent later probes. Late results never replace timeout/cancel. */
public final class PlatformReadinessRunner {
    public interface Probes {
        PlatformReadiness.Code run(PlatformReadiness.Probe probe, Cancellation cancel) throws Exception;
        default boolean hasPendingResources() { return false; }
    }

    public static final class Cancellation {
        private volatile boolean cancelled;
        public boolean isCancelled() { return cancelled; }
        public void check() throws InterruptedException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        }
        private void cancel() { cancelled = true; }
    }

    private final Probes probes;
    private final int timeoutMs;
    private static final Object RUN_LOCK = new Object();
    private static final AtomicInteger RUNS = new AtomicInteger();
    private static final AtomicInteger WORKERS = new AtomicInteger();
    private volatile boolean running;
    private volatile boolean cancelled;
    private volatile PlatformReadiness report = new PlatformReadiness();
    private volatile Cancellation active;
    private volatile Thread coordinator;

    public PlatformReadinessRunner(Probes probes) { this(probes, 5000); }
    PlatformReadinessRunner(Probes probes, int timeoutMs) {
        if (probes == null || timeoutMs < 1 || timeoutMs > 5000) throw new IllegalArgumentException("Probe budget");
        this.probes = probes;
        this.timeoutMs = timeoutMs;
    }

    public boolean start(Consumer<PlatformReadiness> progress, Runnable done) {
        // A vendor call may ignore interruption. Do not accumulate another run while it still owns resources.
        synchronized (RUN_LOCK) {
            if (isBusy()) return false;
            RUNS.incrementAndGet();
            running = true;
            cancelled = false;
            report = new PlatformReadiness();
            coordinator = new Thread(() -> execute(progress, done), "ts7-platform-coordinator");
            coordinator.setDaemon(true);
            coordinator.start();
            return true;
        }
    }

    public PlatformReadiness snapshot() { return report; }
    public boolean isRunning() { return running; }
    public boolean isBusy() {
        return PlatformResourceGuard.isQuarantined() || RUNS.get() != 0 || WORKERS.get() != 0
            || probes.hasPendingResources();
    }

    public void cancel() {
        cancelled = true;
        Cancellation current = active;
        if (current != null) current.cancel();
        Thread thread = coordinator;
        if (thread != null) thread.interrupt();
    }

    private void execute(Consumer<PlatformReadiness> progress, Runnable done) {
        try {
            for (PlatformReadiness.Probe probe : PlatformReadiness.Probe.values()) {
                PlatformReadiness.Result result;
                if (cancelled) result = new PlatformReadiness.Result(PlatformReadiness.Code.TEST_CANCELLED, 0);
                else result = executeOne(probe);
                report = report.with(probe, result);
                progress.accept(report);
            }
        } finally {
            active = null;
            running = false;
            RUNS.decrementAndGet();
            done.run();
        }
    }

    private PlatformReadiness.Result executeOne(PlatformReadiness.Probe probe) {
        Cancellation token = new Cancellation();
        active = token;
        final PlatformReadiness.Code[] result = {PlatformReadiness.Code.PROBE_FAILED};
        long started = System.nanoTime();
        WORKERS.incrementAndGet();
        Thread worker = new Thread(() -> {
            try {
                token.check();
                PlatformReadiness.Code code = probes.run(probe, token);
                result[0] = code == null ? PlatformReadiness.Code.PROBE_FAILED : code;
            } catch (SecurityException error) {
                result[0] = PlatformReadiness.Code.PERMISSION_MISSING;
            } catch (InterruptedException error) {
                result[0] = PlatformReadiness.Code.TEST_CANCELLED;
            } catch (Throwable error) {
                // Never inspect or retain messages, paths, peer data, exception causes or stack traces.
                result[0] = PlatformReadiness.Code.PROBE_FAILED;
            } finally { WORKERS.decrementAndGet(); }
        }, "ts7-platform-" + probe.name());
        worker.setDaemon(true);
        worker.start();
        long deadline = started + timeoutMs * 1000000L;
        try {
            while (worker.isAlive() && !cancelled) {
                long left = deadline - System.nanoTime();
                if (left <= 0) break;
                worker.join(Math.max(1, Math.min(50, left / 1000000L)));
            }
        } catch (InterruptedException error) { cancelled = true; }
        long duration = (System.nanoTime() - started) / 1000000L;
        PlatformReadiness.Code code = result[0];
        if (cancelled) code = PlatformReadiness.Code.TEST_CANCELLED;
        else if (worker.isAlive()) code = PlatformReadiness.Code.PROBE_TIMEOUT;
        token.cancel();
        if (worker.isAlive()) worker.interrupt();
        active = null;
        return new PlatformReadiness.Result(code, duration);
    }
}
