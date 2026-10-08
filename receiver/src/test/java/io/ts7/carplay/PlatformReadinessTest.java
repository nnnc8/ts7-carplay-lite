package io.ts7.carplay;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static io.ts7.carplay.PlatformReadiness.Code.*;

public final class PlatformReadinessTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        PlatformReadiness empty = new PlatformReadiness();
        check(PlatformReadiness.Probe.values().length == 12, "12 independent probes");
        for (PlatformReadiness.Probe probe : PlatformReadiness.Probe.values()) {
            check(empty.get(probe).errorCode == NOT_RUN, "initial NOT_RUN");
            check(empty.json().contains("\"" + probe.name() + "\":"), "all fixed keys");
        }
        for (PlatformReadiness.Code code : PlatformReadiness.Code.values()) {
            PlatformReadiness.Result result = new PlatformReadiness.Result(code, Long.MAX_VALUE);
            check(result.status == code.status && result.durationMs == 60000, "status-code mapping and cap");
            check(new PlatformReadiness.Result(code, -100).durationMs == 0, "nonnegative duration");
        }
        PlatformReadiness changed = empty.with(PlatformReadiness.Probe.surface, new PlatformReadiness.Result(NONE, 8));
        check(empty.get(PlatformReadiness.Probe.surface).errorCode == NOT_RUN, "immutable snapshot");
        check(changed.json().contains("\"surface\":{\"status\":\"PASS\",\"durationMs\":8,\"errorCode\":\"NONE\"}"), "exact public shape");
        check(changed.display().contains("Authentication  NOT TESTED"), "platform probes do not make an authentication claim");

        AtomicBoolean networkAvailable = new AtomicBoolean(true);
        java.util.List<PlatformReadiness.Probe> order = new java.util.ArrayList<>();
        CountDownLatch orderDone = new CountDownLatch(1);
        PlatformReadinessRunner ordered = new PlatformReadinessRunner((probe, token) -> {
            order.add(probe);
            if (probe == PlatformReadiness.Probe.localOnlyHotspot) networkAvailable.set(false);
            if (probe == PlatformReadiness.Probe.networkBinding && !networkAvailable.get()) return NETWORK_UNAVAILABLE;
            return NONE;
        }, 100);
        check(ordered.start(value -> {}, orderDone::countDown), "ordered probes start");
        check(orderDone.await(2, TimeUnit.SECONDS), "ordered probes finish");
        check(order.size() == 12 && new java.util.HashSet<>(order).size() == 12, "all probes execute exactly once");
        check(order.get(11) == PlatformReadiness.Probe.localOnlyHotspot, "disruptive hotspot is last");
        check(ordered.snapshot().get(PlatformReadiness.Probe.networkBinding).errorCode == NONE,
            "network binding is measured before hotspot interruption, not claimed concurrent");

        AtomicInteger executed = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        PlatformReadinessRunner runner = new PlatformReadinessRunner((probe, cancel) -> {
            executed.incrementAndGet();
            if (probe == PlatformReadiness.Probe.coreInitialization) throw new SecurityException("PRIVATE_SENTINEL");
            if (probe == PlatformReadiness.Probe.jni) throw new UnsatisfiedLinkError("PRIVATE_SENTINEL");
            return NONE;
        }, 100);
        check(runner.start(value -> {}, done::countDown), "start");
        check(done.await(2, TimeUnit.SECONDS), "bounded completion");
        check(executed.get() == 12, "auth/permission/error does not short circuit");
        check(runner.snapshot().get(PlatformReadiness.Probe.coreInitialization).errorCode == PERMISSION_MISSING, "permission is fixed");
        check(!runner.snapshot().json().contains("PRIVATE_SENTINEL"), "exception text is discarded");
        check(runner.snapshot().get(PlatformReadiness.Probe.audioTrack).errorCode == NONE, "last probe executes");

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch timeoutDone = new CountDownLatch(1);
        PlatformReadinessRunner timeout = new PlatformReadinessRunner((probe, cancel) -> {
            if (probe == PlatformReadiness.Probe.coreInitialization) {
                entered.countDown();
                boolean released = false;
                while (!released) try { released = release.await(20, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException ignored) { /* Deliberately simulate a vendor ignoring interruption. */ }
            }
            return NONE;
        }, 40);
        check(timeout.start(value -> {}, timeoutDone::countDown), "timeout run starts");
        check(entered.await(1, TimeUnit.SECONDS), "hung probe entered");
        check(timeoutDone.await(2, TimeUnit.SECONDS), "later probes complete despite hang");
        check(timeout.snapshot().get(PlatformReadiness.Probe.coreInitialization).errorCode == PROBE_TIMEOUT, "fixed timeout");
        check(timeout.snapshot().get(PlatformReadiness.Probe.audioTrack).errorCode == NONE, "independent last probe");
        check(timeout.isBusy() && !timeout.start(value -> {}, () -> {}), "no accumulating repeat workers");
        PlatformReadinessRunner recreated = new PlatformReadinessRunner((probe, token) -> NONE, 40);
        check(!recreated.start(value -> {}, () -> {}), "Activity recreation cannot bypass pending worker");
        release.countDown();
        waitIdle(timeout);
        check(timeout.snapshot().get(PlatformReadiness.Probe.coreInitialization).errorCode == PROBE_TIMEOUT, "late success cannot replace timeout");

        CountDownLatch cancelEntered = new CountDownLatch(1);
        CountDownLatch cancelDone = new CountDownLatch(1);
        AtomicInteger cancelCalls = new AtomicInteger();
        PlatformReadinessRunner cancelled = new PlatformReadinessRunner((probe, token) -> {
            cancelCalls.incrementAndGet();
            cancelEntered.countDown();
            while (true) { token.check(); Thread.sleep(5); }
        }, 100);
        check(cancelled.start(value -> {}, cancelDone::countDown), "cancel run starts");
        check(cancelEntered.await(1, TimeUnit.SECONDS), "cancel probe entered");
        cancelled.cancel();
        check(cancelDone.await(2, TimeUnit.SECONDS), "cancel completes");
        check(cancelCalls.get() == 1, "cancel prevents later acquisition");
        for (PlatformReadiness.Probe probe : PlatformReadiness.Probe.values())
            check(cancelled.snapshot().get(probe).errorCode == TEST_CANCELLED, "cancel result fixed");
        waitIdle(cancelled);

        AtomicBoolean pending = new AtomicBoolean(true);
        PlatformReadinessRunner pendingCleanup = new PlatformReadinessRunner(new PlatformReadinessRunner.Probes() {
            public PlatformReadiness.Code run(PlatformReadiness.Probe probe, PlatformReadinessRunner.Cancellation cancel) { return NONE; }
            public boolean hasPendingResources() { return pending.get(); }
        });
        check(!pendingCleanup.start(value -> {}, () -> {}), "pending async cleanup blocks rerun");
        pending.set(false);
        CountDownLatch pendingDone = new CountDownLatch(1);
        check(pendingCleanup.start(value -> {}, pendingDone::countDown), "released cleanup permits run");
        check(pendingDone.await(2, TimeUnit.SECONDS), "released run completes");
        System.out.println("Platform readiness host PASS: " + checks + " assertions; privacy, isolation, timeout, cancel, late cleanup");
    }

    private static void waitIdle(PlatformReadinessRunner runner) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (runner.isBusy() && System.nanoTime() < deadline) Thread.sleep(5);
        check(!runner.isBusy(), "worker cleanup finishes");
    }
    private static void check(boolean passed, String label) {
        checks++;
        if (!passed) throw new AssertionError(label);
    }
}
