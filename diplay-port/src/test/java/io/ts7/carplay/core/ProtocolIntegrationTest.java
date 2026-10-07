package io.ts7.carplay.core;

import io.ts7.carplay.auth.FakeAuthenticationProvider;
import io.ts7.carplay.auth.UnavailableAuthenticationProvider;

/** Synthetic protocol-state fixtures only. Not evidence of a real phone or legal provider. */
public final class ProtocolIntegrationTest {
    private static int assertions;
    private static final byte[] IDR = {0, 0, 0, 1, 0x65, 0x11};
    private static final byte[] SPS = {0, 0, 0, 1, 0x67, 0x11};
    private static void check(boolean ok) { assertions++; if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        ProtocolGate gate = new ProtocolGate();
        long blocked = gate.begin(new UnavailableAuthenticationProvider());
        check(!gate.bootstrapConfirmed(blocked)); // fake bootstrap cannot bypass provider
        check(!gate.authenticationSucceeded(blocked));
        check(!gate.renderedFrame(blocked));
        check(gate.failure() == ProtocolGate.Failure.AUTH_UNAVAILABLE);
        long token = gate.begin(new FakeAuthenticationProvider());
        check(!gate.authenticationSucceeded(token)); // no actual bootstrap/network
        check(!gate.carplayNetworkReady(token)); // generic Wi-Fi has no event/proof
        check(gate.bootstrapConfirmed(token));
        check(gate.carplayNetworkReady(token));
        check(!gate.sessionEstablished(token)); // bootstrap + Wi-Fi without auth rejected
        check(gate.authenticationSucceeded(token));
        check(!gate.renderedFrame(token)); // auth success without video is not streaming
        check(gate.sessionEstablished(token));
        check(!gate.videoAllowed(token, IDR, 0, IDR.length)); // sink readiness handshake
        check(gate.videoSinkReady(token));
        check(gate.videoAllowed(token, SPS, 0, SPS.length));
        gate.videoAccepted(token, SPS, 0, SPS.length);
        check(!gate.renderedFrame(token)); // config is not real video
        check(!gate.videoAllowed(token, new byte[] {0, 0, 1}, 0, 3));
        check(!gate.videoAllowed(token, IDR, -1, 6));
        check(!gate.videoAllowed(token, new byte[] {0, 0, 1, 0x65}, 0, 4));
        check(!gate.videoAllowed(token, new byte[] {0, 0, 1, (byte)0xe5, 1}, 0, 5));
        check(gate.videoAllowed(token, IDR, 0, IDR.length)); // malformed input did not crash
        gate.videoAccepted(token, IDR, 0, IDR.length);
        check(gate.renderedFrame(token));
        check(gate.state() == ProtocolGate.State.STREAMING);
        check(gate.fail(token, ProtocolGate.Failure.NETWORK_LOST));
        check(!gate.renderedFrame(token));
        long retry = gate.begin(new FakeAuthenticationProvider());
        check(!gate.bootstrapConfirmed(token)); // stale callback after reconnect ignored
        check(!gate.videoAllowed(token, IDR, 0, IDR.length));
        check(gate.bootstrapConfirmed(retry));
        gate.stop();
        // Developer pattern has no access to any protocol event; a renderer callback alone fails.
        check(!gate.renderedFrame(retry));
        check(gate.state() == ProtocolGate.State.IDLE);
        System.out.println("Protocol integration PASS: " + assertions + " fail-closed fixture checks");
    }
}
