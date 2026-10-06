package io.ts7.carplay;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

public final class ReceiverTest {
    private static int checks;
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        byte[] sample = Files.readAllBytes(Paths.get(args[0]));
        EventRing events = new EventRing();
        AvcConfig config = new AvcConfig();
        check(config.accept(sample, 0, sample.length, events), "Real generated SPS/PPS parse");
        AvcConfig.Snapshot snapshot = config.snapshot();
        check(snapshot != null && snapshot.width == 1280 && snapshot.height == 720, "SPS dimensions match target");
        int firstEnd = findSecondAud(sample);
        check(firstEnd > 0 && AnnexB.hasType(sample, 0, firstEnd, 5), "Pattern begins with IDR");
        config.reset();
        check(config.snapshot() == null, "Stream reset invalidates all config");
        check(config.accept(snapshot.sps, 0, snapshot.sps.length, events) && config.snapshot() == null, "SPS alone cannot start decoder");
        check(config.accept(snapshot.pps, 0, snapshot.pps.length, events) && config.snapshot() != null, "Matching PPS completes config");
        byte[] truncated = {0, 0, 0, 1, 0x67, 0};
        check(!config.accept(truncated, 0, truncated.length, events), "Truncated SPS is rejected");
        check(config.snapshot() == null, "Corrupt SPS cannot retain ready state");
        check(config.accept(sample, 0, firstEnd, events) && config.snapshot() != null, "IDR + complete headers recover config");

        byte[] idr = {0, 0, 0, 1, 0x65, 1, 2};
        byte[] inter = {0, 0, 1, 0x41, 1, 2};
        VideoQueue queue = new VideoQueue();
        check(!queue.offer(inter, 0, inter.length, 1, 1000), "Startup waits for IDR");
        check(queue.offer(idr, 0, idr.length, 2, 1000), "IDR opens queue");
        for (int i = 1; i < VideoQueue.CAPACITY; i++) check(queue.offer(inter, 0, inter.length, 2 + i, 1000), "Fill bounded queue");
        check(!queue.offer(inter, 0, inter.length, 6, 1000), "Overflow rejects dependent stale delta frame");
        check(queue.depth() == 0 && queue.needsIdr(), "Overflow resynchronizes at IDR boundary");
        check(queue.droppedFrames() >= 5, "Dropped frames counted");
        check(queue.offer(idr, 0, idr.length, 7, 1000), "New IDR restores queue");
        VideoQueue.Packet packet = new VideoQueue.Packet();
        ByteBuffer buffer = ByteBuffer.allocate(VideoQueue.MAX_PACKET_BYTES);
        check(queue.poll(buffer, packet, 1001) && packet.timestampUs == 7, "Compressed packet reaches reusable ByteBuffer");
        check(Arrays.equals(Arrays.copyOf(buffer.array(), packet.size), idr), "Packet content preserved");
        queue.offer(idr, 0, idr.length, 8, 1000);
        buffer.clear();
        check(!queue.poll(buffer, packet, 1300) && queue.needsIdr(), "Stale packet discarded, not decoded late");
        byte[] oversized = new byte[VideoQueue.MAX_PACKET_BYTES + 1];
        check(!queue.offer(oversized, 0, oversized.length, 0, 1300), "Oversized input rejected");

        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            byte[] corrupt = new byte[5 + random.nextInt(100)];
            random.nextBytes(corrupt);
            queue.offer(corrupt, 0, corrupt.length, i, i);
            config.accept(corrupt, 0, corrupt.length, events);
            check(queue.depth() <= VideoQueue.CAPACITY, "Fuzzed input cannot grow queue");
        }
        check(!AnnexB.valid(null, 0, 5), "Null packet rejected");
        check(!AnnexB.valid(inter, -1, inter.length), "Negative range rejected");
        check(!AnnexB.valid(inter, 0, inter.length + 1), "Invalid range rejected");

        SessionMachine session = new SessionMachine(events);
        session.observeBluetooth(true, false, true);
        session.observeWifi(true);
        check(session.state() == SessionMachine.State.IDLE && !session.authenticated(), "Observed radios are not CarPlay proof");
        session.begin(false);
        check(session.state() == SessionMachine.State.ERROR && session.lastReason() == SessionMachine.Reason.BLOCKED_BY_AUTHENTICATION_REQUIREMENT, "Missing provider stops at boundary");
        boolean refused = false;
        try { session.firstCarPlayFrame(); } catch (IllegalStateException expected) { refused = true; }
        check(refused, "Test/local frame cannot claim authenticated streaming");
        session.begin(true);
        session.bootstrapConfirmed();
        session.sessionWifiConfirmed();
        refused = false;
        try { session.firstCarPlayFrame(); } catch (IllegalStateException expected) { refused = true; }
        check(refused, "Negotiation alone is not authentication");
        session.authenticationConfirmed();
        session.firstCarPlayFrame();
        check(session.state() == SessionMachine.State.STREAMING, "Only confirmed session plus real frame can stream");
        session.recovering(SessionMachine.Reason.NETWORK_LOSS);
        check(session.state() == SessionMachine.State.RECOVERING, "Network loss enters recovery");
        check(!session.authenticated() && session.uptimeMs() == 0, "Lost session cannot accept video as authenticated");
        session.authenticationConfirmed();
        check(session.state() == SessionMachine.State.RECOVERING, "Fresh authentication alone is not recovered video");
        session.recovering(SessionMachine.Reason.SESSION_LOST);
        check(!session.authenticated(), "Loss before first recovered frame invalidates fresh authentication");
        session.authenticationConfirmed();
        session.firstCarPlayFrame();
        check(session.state() == SessionMachine.State.STREAMING, "Fresh real frame ends authenticated recovery");
        session.observeBluetooth(true, false, true);
        session.observeWifi(true);
        check(session.bluetooth() == SessionMachine.BluetoothState.BOOTSTRAP_CONFIRMED
            && session.wifi() == SessionMachine.WifiState.SESSION_LINK_CONFIRMED, "Radio refresh does not overwrite provider proof");
        session.observeWifi(false);
        check(session.authenticated() && session.state() == SessionMachine.State.STREAMING
            && session.wifi() == SessionMachine.WifiState.SESSION_LINK_CONFIRMED, "Client Wi-Fi observation cannot tear down a provider hotspot session");
        session.recovering(SessionMachine.Reason.SESSION_LOST);
        session.exhausted();
        check(!session.authenticated() && session.state() == SessionMachine.State.ERROR, "Exhaustion stops session");
        check(!new ReceiverCore.Unavailable().hasLawfulAuthentication(), "Shipped core never grants authentication");

        RetryBudget retry = new RetryBudget();
        check(retry.peekDelayMs() == 1000 && retry.peekDelayMs() == 1000, "Cancelled timer does not consume retry");
        check(retry.nextDelayMs() == 1000 && retry.nextDelayMs() == 2000 && retry.nextDelayMs() == 5000 && retry.nextDelayMs() == -1, "Recovery waits 1/2/5 seconds then stops");
        float[] mapped = new float[2];
        check(TouchMapper.map(640, 360, 1280, 720, 1280, 720, mapped) && mapped[0] == 0.5f && mapped[1] == 0.5f, "Center touch normalized");
        check(!TouchMapper.map(10, 50, 1280, 600, 1280, 720, mapped), "Letterbox touch excluded");
        check(TouchMapper.map(1280, 720, 1280, 720, 1280, 720, mapped) && mapped[0] == 1f && mapped[1] == 1f, "Edge touch clamped by viewport");
        for (int i = 0; i < 10000; i++) events.add(EventCode.FIRST_FRAME, i);
        check(events.size() == 500, "Event ring stays bounded");
        check(events.json(200).length() < 32000 && events.json(200).contains("9999"), "Export bounded recent events");
        check(VideoProfile.DEFAULT.fps == 30 && VideoProfile.BALANCED.fps == 25 && VideoProfile.STABILITY.fps == 20, "Profiles remain 720p low-fps");
        System.out.println("Receiver tests PASS: " + checks + " checks (SPS/PPS, malformed input, IDR resync, bounded queue/ring, truthful states, retries, touch)");
    }

    private static int findSecondAud(byte[] sample) {
        int position = AnnexB.start(sample, 0, sample.length);
        int count = 0;
        while (position >= 0) {
            int header = AnnexB.header(sample, position);
            if ((sample[header] & 31) == 9 && ++count == 2) return position;
            position = AnnexB.start(sample, header + 1, sample.length);
        }
        return -1;
    }
}
