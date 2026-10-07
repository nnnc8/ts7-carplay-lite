package io.ts7.carplay;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static io.ts7.carplay.PlatformReadiness.Code.*;

/** Each scenario gets its own process; production has deliberately no quarantine-reset method. */
public final class PlatformCleanupTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        boolean late = "late".equals(args[0]);
        Object handle = new Object();
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        PlatformReadinessRunner runner = new PlatformReadinessRunner((probe, token) -> {
            calls.incrementAndGet();
            if (probe == PlatformReadiness.Probe.coreInitialization) {
                if (late) {
                    boolean released = false;
                    while (!released) try { released = release.await(10, TimeUnit.MILLISECONDS); }
                        catch (InterruptedException ignored) { }
                }
                try { throw new IllegalStateException("PRIVATE_RELEASE_SENTINEL"); }
                catch (Throwable ignored) { PlatformResourceGuard.quarantine(handle); }
                failed.countDown();
                return RESOURCE_RELEASE_FAILED;
            }
            return NONE;
        }, 40);
        check(runner.start(value -> {}, done::countDown), "start");
        check(done.await(2, TimeUnit.SECONDS), "later probes complete");
        check(calls.get() == 12, "cleanup failure cannot stop remaining probes");
        check(runner.snapshot().get(PlatformReadiness.Probe.audioTrack).errorCode == NONE, "last probe executes");
        if (late) {
            check(runner.snapshot().get(PlatformReadiness.Probe.coreInitialization).errorCode == PROBE_TIMEOUT, "timeout fixed before late cleanup");
            release.countDown();
        } else check(runner.snapshot().get(PlatformReadiness.Probe.coreInitialization).errorCode == RESOURCE_RELEASE_FAILED, "immediate failure fixed");
        check(failed.await(2, TimeUnit.SECONDS), "close-throws quarantine");
        check(PlatformResourceGuard.isQuarantined(), "quarantine remains after workers end");
        check(runner.isBusy() && !runner.start(value -> {}, () -> {}), "rerun blocked");
        PlatformReadinessRunner recreated = new PlatformReadinessRunner((probe, token) -> NONE, 40);
        check(recreated.isBusy() && !recreated.start(value -> {}, () -> {}), "new Activity cannot bypass failed cleanup");
        check(!runner.snapshot().json().contains("PRIVATE_RELEASE_SENTINEL"), "release error stays private");
        if (late) check(runner.snapshot().get(PlatformReadiness.Probe.coreInitialization).errorCode == PROBE_TIMEOUT, "late failure cannot replace frozen timeout");
        System.out.println("Platform cleanup " + args[0] + " PASS: " + checks + " assertions; process quarantine, remaining probes and privacy");
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
