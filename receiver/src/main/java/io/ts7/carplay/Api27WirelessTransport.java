// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/** API-27 public APIs only. No default-network assumption, hidden AP reflection or location reads. */
public final class Api27WirelessTransport implements WirelessTransport {
    private final Context context;
    private final ConnectivityManager connectivity;
    private final WifiManager wifi;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Closeable> sockets = new ArrayList<>();
    private Listener listener;
    private Mode mode;
    private State state = State.STOPPED;
    private Failure failure = Failure.NONE;
    private Network network;
    private InetAddress local;
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private long generation;
    private boolean watching;
    private final ConnectivityManager.NetworkCallback watcher = new ConnectivityManager.NetworkCallback() {
        @Override public void onLost(Network lost) {
            synchronized (Api27WirelessTransport.this) {
                if (lost.equals(network)) fail(Failure.NETWORK_LOST);
            }
        }
        @Override public void onLinkPropertiesChanged(Network changed, LinkProperties properties) {
            synchronized (Api27WirelessTransport.this) {
                if (changed.equals(network) && !contains(properties, local)) fail(Failure.NETWORK_LOST);
            }
        }
    };
    public Api27WirelessTransport(Context context) {
        this.context = context.getApplicationContext();
        connectivity = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        wifi = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }
    @Override public synchronized void start(Mode requested, Listener callback) {
        stop();
        if (requested == null || callback == null) throw new IllegalArgumentException("INVALID_TRANSPORT");
        mode = requested;
        listener = callback;
        final long token = generation;
        change(State.STARTING, Failure.NONE);
        if (connectivity == null) { fail(Failure.INVALID_NETWORK); return; }
        try {
            // Tracks loss only. Does not select the default network or treat availability as proof.
            connectivity.registerNetworkCallback(new android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), watcher);
            watching = true;
            if (mode == Mode.SAME_LAN) { change(State.WAITING_NETWORK, Failure.NONE); return; }
            if (wifi == null) { fail(Failure.HOTSPOT_UNAVAILABLE); return; }
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) { fail(Failure.PERMISSION_DENIED); return; }
            wifi.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation created) {
                    synchronized (Api27WirelessTransport.this) {
                        if (token != generation || state == State.STOPPED || state == State.FAILED) {
                            created.close(); return;
                        }
                        reservation = created;
                        // API 27 reservation has no Network. Address/network binding remains explicit.
                        change(State.HOTSPOT_READY_UNBOUND, Failure.NONE);
                        Listener active = listener;
                        if (active != null) active.hotspotConfiguration(created.getWifiConfiguration());
                    }
                }
                @Override public void onStopped() {
                    synchronized (Api27WirelessTransport.this) {
                        if (token == generation) fail(Failure.NETWORK_LOST);
                    }
                }
                @Override public void onFailed(int reason) {
                    synchronized (Api27WirelessTransport.this) {
                        if (token == generation) fail(Failure.HOTSPOT_UNAVAILABLE);
                    }
                }
            }, main);
        } catch (SecurityException denied) { fail(Failure.PERMISSION_DENIED); }
        catch (IllegalStateException unavailable) { fail(Failure.HOTSPOT_UNAVAILABLE); }
    }
    @Override public synchronized boolean selectNetwork(Network selected, InetAddress address) {
        if (state != State.WAITING_NETWORK && state != State.HOTSPOT_READY_UNBOUND) return false;
        if (selected == null || address == null || address.isAnyLocalAddress()
                || address.isLoopbackAddress() || address.isMulticastAddress()) return false;
        NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(selected);
        if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || !contains(connectivity.getLinkProperties(selected), address)) return false;
        network = selected;
        local = address;
        change(State.BOUND, Failure.NONE);
        return true;
    }
    private static boolean contains(LinkProperties properties, InetAddress address) {
        if (properties == null || address == null) return false;
        for (LinkAddress value : properties.getLinkAddresses())
            if (address.equals(value.getAddress())) return true;
        return false;
    }
    private void requireBound() throws IOException {
        if (state != State.BOUND || network == null || local == null || sockets.size() >= 32)
            throw new IOException("NETWORK_NOT_BOUND");
    }
    @Override public Socket connect(InetAddress peer, int port) throws IOException {
        final Socket socket = new Socket();
        synchronized (this) {
            requireBound();
            try {
                network.bindSocket(socket);
                socket.bind(new InetSocketAddress(local, 0));
                sockets.add(socket);
            } catch (IOException error) { socket.close(); throw new IOException("SOCKET_FAILED"); }
        }
        try {
            socket.connect(new InetSocketAddress(peer, port), 5000);
            socket.setSoTimeout(10000);
            return socket;
        } catch (IOException | IllegalArgumentException error) {
            socket.close();
            synchronized (this) { sockets.remove(socket); }
            throw new IOException("SOCKET_FAILED");
        }
    }
    @Override public synchronized ServerSocket listen(int port) throws IOException {
        requireBound();
        ServerSocket socket = new ServerSocket();
        try {
            // Public API has no Network.bindSocket(ServerSocket); exact local bind scopes inbound.
            socket.bind(new InetSocketAddress(local, port), 1);
            socket.setSoTimeout(15000);
            sockets.add(socket);
            return socket;
        } catch (IOException | IllegalArgumentException error) {
            socket.close(); throw new IOException("SOCKET_FAILED");
        }
    }
    @Override public synchronized DatagramSocket datagram() throws IOException {
        requireBound();
        DatagramSocket socket = new DatagramSocket(null);
        try {
            network.bindSocket(socket);
            socket.bind(new InetSocketAddress(local, 0));
            socket.setSoTimeout(10000);
            sockets.add(socket);
            return socket;
        } catch (IOException error) { socket.close(); throw new IOException("SOCKET_FAILED"); }
    }
    @Override public synchronized void stop() {
        generation++;
        closeResources();
        state = State.STOPPED;
        failure = Failure.NONE;
        listener = null;
    }
    private void closeResources() {
        for (Closeable socket : sockets) try { socket.close(); } catch (IOException ignored) {}
        sockets.clear();
        network = null;
        local = null;
        if (reservation != null) { reservation.close(); reservation = null; }
        if (watching) {
            watching = false;
            try { connectivity.unregisterNetworkCallback(watcher); } catch (RuntimeException ignored) {}
        }
    }
    private void fail(Failure reason) {
        generation++; // rejects late LOHS callbacks; ownership is still closed even on startup failure
        closeResources();
        change(State.FAILED, reason);
    }
    private void change(State next, Failure reason) {
        state = next; failure = reason;
        Listener active = listener;
        if (active != null) active.changed(next, reason);
    }
    @Override public synchronized State state() { return state; }
    @Override public synchronized Network boundNetwork() { return network; }
    @Override public synchronized String diagnostics() { return state.name() + "/" + failure.name(); }
}
