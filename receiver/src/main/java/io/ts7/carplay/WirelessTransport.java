// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay;

import android.net.Network;
import android.net.wifi.WifiConfiguration;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.DatagramSocket;

/** Network readiness is transport evidence only, not authentication or a CarPlay session. */
public interface WirelessTransport {
    enum Mode { LOCAL_ONLY_HOTSPOT, SAME_LAN }
    enum State { STOPPED, STARTING, WAITING_NETWORK, HOTSPOT_READY_UNBOUND, BOUND, FAILED }
    enum Failure { NONE, PERMISSION_DENIED, LOCATION_SETTING_REQUIRED, HOTSPOT_UNAVAILABLE,
        INVALID_NETWORK, NETWORK_LOST, SOCKET_FAILED }
    interface Listener {
        void changed(State state, Failure failure);
        // Session-only credentials; never put this object in diagnostics or logs.
        void hotspotConfiguration(WifiConfiguration configuration);
    }
    void start(Mode mode, Listener listener);
    void stop();
    State state();
    Network boundNetwork();
    boolean selectNetwork(Network network, InetAddress localAddress);
    Socket connect(InetAddress peer, int port) throws IOException;
    ServerSocket listen(int port) throws IOException;
    DatagramSocket datagram() throws IOException;
    String diagnostics(); // fixed enum values only
}
