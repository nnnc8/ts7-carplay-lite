// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.UUID;

/** User-selected paired device; RFCOMM connected is not iAP2/bootstrap/authentication proof. */
public final class Api27BluetoothBootstrap {
    public enum Failure { PERMISSION_DENIED, BLUETOOTH_OFF, NOT_PAIRED, TIMEOUT, CONNECT_FAILED }
    public interface Listener {
        void connected(BluetoothSocket socket); // transfers socket ownership to protocol worker
        void failed(Failure reason);
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothSocket pending;
    private long generation;
    public Api27BluetoothBootstrap(Context context) { this.context = context.getApplicationContext(); }
    public synchronized void connect(BluetoothDevice selected, Listener listener) {
        stop();
        if (listener == null) throw new IllegalArgumentException("MISSING_LISTENER");
        if (Build.VERSION.SDK_INT >= 31 && (context.checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED)) {
            listener.failed(Failure.PERMISSION_DENIED); return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        try {
            if (adapter == null || !adapter.isEnabled()) { listener.failed(Failure.BLUETOOTH_OFF); return; }
            if (selected == null || selected.getBondState() != BluetoothDevice.BOND_BONDED) {
                listener.failed(Failure.NOT_PAIRED); return;
            }
            adapter.cancelDiscovery(); // no scans, location or MAC collection
            final BluetoothSocket socket = selected.createRfcommSocketToServiceRecord(
                UUID.fromString("00000000-deca-fade-deca-deafdecacafe"));
            pending = socket;
            final long token = generation;
            main.postDelayed(() -> {
                synchronized (Api27BluetoothBootstrap.this) {
                    if (token != generation || pending != socket) return;
                    stop();
                    listener.failed(Failure.TIMEOUT);
                }
            }, 15000);
            new Thread(() -> {
                try {
                    socket.connect();
                    synchronized (Api27BluetoothBootstrap.this) {
                        if (token != generation || pending != socket) { close(socket); return; }
                        pending = null;
                        listener.connected(socket);
                    }
                } catch (IOException error) {
                    synchronized (Api27BluetoothBootstrap.this) {
                        close(socket);
                        if (token != generation || pending != socket) return;
                        pending = null;
                        listener.failed(Failure.CONNECT_FAILED);
                    }
                }
            }, "ts7-rfcomm-connect").start();
        } catch (SecurityException denied) { listener.failed(Failure.PERMISSION_DENIED); }
        catch (IOException failed) { listener.failed(Failure.CONNECT_FAILED); }
    }
    public synchronized void stop() {
        generation++;
        if (pending != null) { close(pending); pending = null; }
        main.removeCallbacksAndMessages(null);
    }
    private static void close(BluetoothSocket socket) {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
