package io.ts7.carplay;

/** Integration contract only. A legal core must provide real protocol evidence, not simulated states. */
public interface ReceiverCore {
    interface Listener {
        void bluetoothBootstrapConfirmed();
        void wifiSessionLinkConfirmed();
        void authenticatedSessionStarted();
        // Source owns bytes; receiver copies compressed input once before returning.
        // Wait for videoSinkReady before sending initial SPS/PPS/IDR. False means not accepted.
        boolean videoAccessUnit(byte[] bytes, int offset, int length, long timestampUs);
        void streamReset();
        void disconnected(SessionMachine.Reason reason);
    }
    boolean hasLawfulAuthentication();
    void connect(VideoProfile profile, Listener listener);
    void disconnect();
    // Called after the renderer is ready to accept input, including after fresh reauthentication.
    void videoSinkReady();
    boolean reconnect();
    boolean requestKeyframe();
    boolean touch(int action, float normalizedX, float normalizedY);

    final class Unavailable implements ReceiverCore {
        public boolean hasLawfulAuthentication() { return false; }
        public void connect(VideoProfile profile, Listener listener) {
            throw new IllegalStateException("BLOCKED_BY_AUTHENTICATION_REQUIREMENT");
        }
        public void disconnect() {}
        public void videoSinkReady() {}
        public boolean reconnect() { return false; }
        public boolean requestKeyframe() { return false; }
        public boolean touch(int action, float x, float y) { return false; }
    }
}
