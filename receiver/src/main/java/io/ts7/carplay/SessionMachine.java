package io.ts7.carplay;

/** Radio observations are separate from proof supplied by an authenticated protocol core. */
public final class SessionMachine {
    public enum State { IDLE, BT_DISCOVERY, BT_CONNECTED, WIFI_CONNECTING, WIFI_CONNECTED,
        CARPLAY_NEGOTIATING, STREAMING, RECOVERING, ERROR }
    public enum BluetoothState { OFF, IDLE, DISCOVERING, LINK_OBSERVED, BOOTSTRAP_CONFIRMED }
    public enum WifiState { DISCONNECTED, NETWORK_OBSERVED, SESSION_LINK_CONFIRMED }
    public enum Reason { NONE, BLOCKED_BY_AUTHENTICATION_REQUIREMENT, NETWORK_LOSS, SESSION_LOST,
        DECODER_ERROR, VIDEO_STALL, RECOVERY_EXHAUSTED, SURFACE_LOST, USER_STOP, INPUT_REJECTED }
    private final EventRing events;
    private State state = State.IDLE;
    private BluetoothState bluetooth = BluetoothState.IDLE;
    private WifiState wifi = WifiState.DISCONNECTED;
    private Reason lastReason = Reason.NONE;
    private boolean authenticated;
    private boolean observedNetwork;
    private long sessionStartedNs;

    public SessionMachine(EventRing events) { this.events = events; events.add(EventCode.STATE_IDLE); }
    public synchronized State state() { return state; }
    public synchronized BluetoothState bluetooth() { return bluetooth; }
    public synchronized WifiState wifi() { return wifi; }
    public synchronized Reason lastReason() { return lastReason; }
    public synchronized boolean authenticated() { return authenticated; }
    public synchronized long uptimeMs() {
        return sessionStartedNs == 0 ? 0 : (System.nanoTime() - sessionStartedNs) / 1000000;
    }

    public synchronized void observeBluetooth(boolean enabled, boolean discovering, boolean link) {
        BluetoothState next = !enabled ? BluetoothState.OFF : discovering ? BluetoothState.DISCOVERING
            : link ? BluetoothState.LINK_OBSERVED : BluetoothState.IDLE;
        if (enabled && bluetooth == BluetoothState.BOOTSTRAP_CONFIRMED) return;
        if (next != bluetooth) {
            if (next == BluetoothState.LINK_OBSERVED) events.add(EventCode.BT_CONNECT);
            if (bluetooth == BluetoothState.LINK_OBSERVED && next != BluetoothState.LINK_OBSERVED) events.add(EventCode.BT_DISCONNECT);
            bluetooth = next;
        }
    }

    public synchronized boolean observeWifi(boolean connected) {
        boolean lost = !connected && observedNetwork;
        if (connected != observedNetwork) events.add(connected ? EventCode.WIFI_CONNECT : EventCode.NETWORK_LOSS);
        observedNetwork = connected;
        if (wifi != WifiState.SESSION_LINK_CONFIRMED)
            wifi = connected ? WifiState.NETWORK_OBSERVED : WifiState.DISCONNECTED;
        return lost; // Observation only; client Wi-Fi loss is not proof of a hotspot/session loss.
    }

    public synchronized void begin(boolean authenticationProviderAvailable) {
        authenticated = false;
        sessionStartedNs = 0;
        clearRadioProof();
        if (!authenticationProviderAvailable) {
            lastReason = Reason.BLOCKED_BY_AUTHENTICATION_REQUIREMENT;
            events.add(EventCode.AUTHENTICATION_BLOCKED);
            transition(State.ERROR);
            return;
        }
        lastReason = Reason.NONE;
        transition(State.BT_DISCOVERY);
    }

    public synchronized void bootstrapConfirmed() {
        if (state == State.RECOVERING && !authenticated) {
            bluetooth = BluetoothState.BOOTSTRAP_CONFIRMED;
            return;
        }
        require(State.BT_DISCOVERY);
        bluetooth = BluetoothState.BOOTSTRAP_CONFIRMED;
        transition(State.BT_CONNECTED);
        transition(State.WIFI_CONNECTING);
    }

    public synchronized void sessionWifiConfirmed() {
        if (state == State.RECOVERING && !authenticated && bluetooth == BluetoothState.BOOTSTRAP_CONFIRMED) {
            wifi = WifiState.SESSION_LINK_CONFIRMED;
            return;
        }
        require(State.WIFI_CONNECTING);
        wifi = WifiState.SESSION_LINK_CONFIRMED;
        transition(State.WIFI_CONNECTED);
        transition(State.CARPLAY_NEGOTIATING);
    }

    public synchronized void authenticationConfirmed() {
        if (state != State.CARPLAY_NEGOTIATING && state != State.RECOVERING)
            throw new IllegalStateException("INVALID_TRANSITION");
        authenticated = true;
        wifi = WifiState.SESSION_LINK_CONFIRMED;
        sessionStartedNs = System.nanoTime();
        events.add(EventCode.CARPLAY_SESSION_START);
    }

    public synchronized void firstCarPlayFrame() {
        if (state != State.CARPLAY_NEGOTIATING && state != State.RECOVERING)
            throw new IllegalStateException("INVALID_TRANSITION");
        if (!authenticated) throw new IllegalStateException("AUTH_REQUIRED");
        if (state == State.RECOVERING) events.add(EventCode.RECOVERY_SUCCESS);
        transition(State.STREAMING);
    }

    public synchronized void recovering(Reason reason) {
        if (state == State.IDLE || state == State.ERROR) return;
        authenticated = false;
        sessionStartedNs = 0;
        clearRadioProof();
        lastReason = reason;
        events.add(EventCode.SESSION_LOST);
        transition(State.RECOVERING);
    }

    public synchronized void stop(Reason reason) {
        authenticated = false;
        sessionStartedNs = 0;
        clearRadioProof();
        lastReason = reason;
        transition(State.IDLE);
    }

    public synchronized void exhausted() {
        authenticated = false;
        sessionStartedNs = 0;
        clearRadioProof();
        lastReason = Reason.RECOVERY_EXHAUSTED;
        events.add(EventCode.RECOVERY_EXHAUSTED);
        transition(State.ERROR);
    }

    private void require(State expected) {
        if (state != expected) throw new IllegalStateException("INVALID_TRANSITION");
    }

    private void clearRadioProof() {
        if (bluetooth == BluetoothState.BOOTSTRAP_CONFIRMED) bluetooth = BluetoothState.IDLE;
        wifi = observedNetwork ? WifiState.NETWORK_OBSERVED : WifiState.DISCONNECTED;
    }

    private void transition(State next) {
        if (next == state) return;
        state = next;
        events.add(EventCode.valueOf("STATE_" + next.name()));
    }
}
