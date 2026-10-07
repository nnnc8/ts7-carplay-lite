// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay;

import android.content.Context;
import io.ts7.carplay.auth.AuthenticationProvider;
import io.ts7.carplay.core.ProtocolGate;
import io.ts7.carplay.core.XcertplayAdapter;

/** Compiled protocol adapter plus Android transport ownership; release has no provisioned deployment. */
public final class LawfulReceiverCore implements ReceiverCore {
    public interface Deployment {
        // Future reviewed configuration supplies legitimate identification, paired-device selection,
        // explicit Wi-Fi Network/address, Bonjour, and calls the real adapter, never success events.
        void start(VideoProfile profile, XcertplayAdapter adapter, WirelessTransport transport,
            Api27BluetoothBootstrap bluetooth);
    }
    private final AuthenticationProvider authentication;
    private final WirelessTransport transport;
    private final Api27BluetoothBootstrap bluetooth;
    private final XcertplayAdapter adapter;
    private final Deployment deployment;
    private Listener listener;
    private long activeEpoch = -1;
    private VideoProfile profile;
    public LawfulReceiverCore(Context context, AuthenticationProvider provider) {
        this(context, provider, null);
    }
    public LawfulReceiverCore(Context context, AuthenticationProvider provider, Deployment deployment) {
        authentication = provider;
        this.deployment = deployment;
        transport = new Api27WirelessTransport(context);
        bluetooth = new Api27BluetoothBootstrap(context);
        adapter = new XcertplayAdapter(provider, new XcertplayAdapter.Sink() {
            public void bootstrapConfirmed(long epoch) {
                Listener active = listenerFor(epoch); if (active != null) active.bluetoothBootstrapConfirmed();
            }
            public void carplayNetworkReady(long epoch) {
                Listener active = listenerFor(epoch); if (active != null) active.wifiSessionLinkConfirmed();
            }
            public void authenticationSucceeded(long epoch) {} // AA05 is not AirPlay session proof.
            public void sessionEstablished(long epoch) {
                Listener active = listenerFor(epoch); if (active != null) active.authenticatedSessionStarted();
            }
            public boolean video(long epoch, byte[] bytes, long timestampUs) {
                Listener active = listenerFor(epoch);
                return active != null && active.videoAccessUnit(bytes, 0, bytes.length, timestampUs);
            }
            public void failed(long epoch, ProtocolGate.Failure reason) {
                Listener active = listenerFor(epoch);
                if (active != null) active.disconnected(map(reason));
            }
        });
    }
    @Override public boolean hasLawfulAuthentication() {
        return authentication.isAvailable() && authentication.getInfo().isAuthorized() && deployment != null;
    }
    @Override public void connect(VideoProfile requested, Listener callback) {
        disconnect();
        if (!hasLawfulAuthentication() || !adapter.begin())
            throw new IllegalStateException("BLOCKED_BY_AUTHENTICATION_REQUIREMENT");
        synchronized (this) { listener = callback; activeEpoch = adapter.epoch(); }
        profile = requested;
        deployment.start(requested, adapter, transport, bluetooth);
    }
    @Override public void disconnect() {
        synchronized (this) { listener = null; activeEpoch = -1; }
        adapter.stop();
        bluetooth.stop();
        transport.stop();
    }
    @Override public void videoSinkReady() { adapter.videoSinkReady(); }
    @Override public boolean renderedFrameConfirmed() { return adapter.renderedFrame(); }
    @Override public boolean reconnect() {
        Listener previous = listener;
        if (previous == null || !hasLawfulAuthentication()) return false;
        connect(profile, previous);
        return true;
    }
    @Override public boolean requestKeyframe() { return adapter.requestKeyframe(); }
    @Override public boolean touch(int action, float x, float y) { return adapter.touch(action, x, y); }
    private synchronized Listener listenerFor(long epoch) {
        return epoch == activeEpoch ? listener : null;
    }
    private static SessionMachine.Reason map(ProtocolGate.Failure reason) {
        switch (reason) {
            case AUTH_FAILED: return SessionMachine.Reason.AUTH_FAILED;
            case NETWORK_LOST: return SessionMachine.Reason.NETWORK_LOST;
            case VIDEO_STALL: return SessionMachine.Reason.VIDEO_STALL;
            case DECODER_FAILED: return SessionMachine.Reason.DECODER_FAILED;
            case INPUT_REJECTED: return SessionMachine.Reason.INPUT_REJECTED;
            default: return SessionMachine.Reason.SESSION_CLOSED;
        }
    }
}
