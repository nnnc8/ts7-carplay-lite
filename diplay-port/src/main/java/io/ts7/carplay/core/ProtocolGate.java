// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core;

import io.ts7.carplay.auth.AuthenticationProvider;

/** Evidence, not radio observations. One monotonically increasing epoch owns every callback. */
public final class ProtocolGate {
    public enum State { IDLE, BOOTSTRAP, NETWORK_READY, AUTHENTICATED, SESSION, STREAMING, FAILED }
    public enum Failure { NONE, AUTH_UNAVAILABLE, AUTH_FAILED, NETWORK_LOST, SESSION_CLOSED,
        VIDEO_STALL, DECODER_FAILED, INPUT_REJECTED }
    private long epoch;
    private State state = State.IDLE;
    private Failure failure = Failure.NONE;
    private boolean authorized, bootstrap, network, authentication, session, sink, receivedVideo;

    public synchronized long begin(AuthenticationProvider provider) {
        stop();
        authorized = provider != null && provider.isAvailable() && provider.getInfo().isAuthorized();
        state = authorized ? State.BOOTSTRAP : State.FAILED;
        failure = authorized ? Failure.NONE : Failure.AUTH_UNAVAILABLE;
        return epoch;
    }
    public synchronized void stop() {
        epoch++;
        authorized = bootstrap = network = authentication = session = sink = receivedVideo = false;
        state = State.IDLE;
        failure = Failure.NONE;
    }
    public synchronized boolean current(long token) {
        return token == epoch && authorized && state != State.IDLE && state != State.FAILED;
    }
    public synchronized boolean bootstrapConfirmed(long token) {
        if (!current(token) || bootstrap) return false;
        bootstrap = true;
        return true;
    }
    /** Only an explicitly bound session network/endpoint supplies this event, never default Wi-Fi. */
    public synchronized boolean carplayNetworkReady(long token) {
        if (!current(token) || !bootstrap || network) return false;
        network = true;
        state = State.NETWORK_READY;
        return true;
    }
    /** Called after phone-confirmed iAP2 AA05, not after a certificate read or signing return. */
    public synchronized boolean authenticationSucceeded(long token) {
        if (!current(token) || !network || authentication) return false;
        authentication = true;
        state = State.AUTHENTICATED;
        return true;
    }
    /** The adapter also requires MFi-SAP, verified pairing and encrypted RECORD. */
    public synchronized boolean sessionEstablished(long token) {
        if (!current(token) || !authentication || session) return false;
        session = true;
        state = State.SESSION;
        return true;
    }
    public synchronized boolean videoSinkReady(long token) {
        if (!current(token) || !session) return false;
        sink = true;
        return true;
    }
    public synchronized boolean videoAllowed(long token, byte[] bytes, int offset, int length) {
        return current(token) && session && sink && scan(bytes, offset, length) != 0;
    }
    /** A real VCL access unit must have been accepted by the unchanged renderer queue. */
    public synchronized void videoAccepted(long token, byte[] bytes, int offset, int length) {
        if (videoAllowed(token, bytes, offset, length) && (scan(bytes, offset, length) & 2) != 0)
            receivedVideo = true;
    }
    public interface VideoConsumer { boolean offer(); }
    /** Holds the proof lock through queue acceptance so a fast output callback cannot race it. */
    public synchronized boolean deliverVideo(long token, byte[] bytes, int offset, int length,
            VideoConsumer consumer) {
        if (!videoAllowed(token, bytes, offset, length) || consumer == null || !consumer.offer()) return false;
        videoAccepted(token, bytes, offset, length);
        return true;
    }
    public synchronized boolean renderedFrame(long token) {
        if (!current(token) || !session || !receivedVideo || !sink) return false;
        state = State.STREAMING;
        return true;
    }
    public synchronized boolean fail(long token, Failure reason) {
        if (!current(token) || reason == null || reason == Failure.NONE) return false;
        state = State.FAILED;
        failure = reason;
        authorized = authentication = session = sink = receivedVideo = false;
        return true;
    }
    public synchronized State state() { return state; }
    public synchronized Failure failure() { return failure; }
    public synchronized long epoch() { return epoch; }

    /** Bounded complete Annex-B framing validation; SPS/PPS are config, not received video proof. */
    private static int scan(byte[] bytes, int offset, int length) {
        if (bytes == null || offset < 0 || length < 5 || length > 256 * 1024
                || offset > bytes.length - length) return 0;
        int end = offset + length, cursor = offset, count = 0, flags = 1;
        while (cursor < end) {
            int prefix = prefix(bytes, cursor, end);
            if (prefix == 0 || ++count > 128) return 0;
            int start = cursor + prefix;
            if (start >= end || (bytes[start] & 128) != 0) return 0;
            int type = bytes[start] & 31;
            if (type < 1 || type > 23) return 0;
            cursor = start + 1;
            while (cursor < end && prefix(bytes, cursor, end) == 0) cursor++;
            if (cursor - start < 2) return 0;
            if (type == 1 || type == 5) flags |= 2;
        }
        return flags;
    }
    private static int prefix(byte[] bytes, int at, int end) {
        if (end - at < 3 || bytes[at] != 0 || bytes[at + 1] != 0) return 0;
        if (bytes[at + 2] == 1) return 3;
        return end - at >= 4 && bytes[at + 2] == 0 && bytes[at + 3] == 1 ? 4 : 0;
    }
}
