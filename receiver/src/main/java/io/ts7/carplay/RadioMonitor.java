package io.ts7.carplay;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/** Observes system radio state only. Pairing/discovery remains in Android's consent UI. */
public final class RadioMonitor extends BroadcastReceiver {
    public interface Listener { void networkLost(); }
    private final Context context;
    private final SessionMachine machine;
    private final Listener listener;
    private boolean registered;
    private boolean observedLink;
    public volatile boolean connected;
    public volatile int rssiDbm = -127;
    public volatile int linkSpeedMbps;
    public volatile int frequencyMHz;

    public RadioMonitor(Context context, SessionMachine machine, Listener listener) {
        this.context = context.getApplicationContext();
        this.machine = machine;
        this.listener = listener;
    }

    public void start() {
        if (registered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        context.registerReceiver(this, filter);
        registered = true;
        refresh();
    }

    public void stop() {
        if (registered) { context.unregisterReceiver(this); registered = false; }
    }

    @Override public void onReceive(Context ignored, Intent intent) {
        // Never inspect the BluetoothDevice extra or any peer identifier.
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) observedLink = true;
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) observedLink = false;
        refresh();
    }

    public void refresh() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            boolean enabled = adapter != null && adapter.isEnabled();
            boolean profileLink = enabled && (adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                    == BluetoothProfile.STATE_CONNECTED || adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                    == BluetoothProfile.STATE_CONNECTED);
            machine.observeBluetooth(enabled, enabled && adapter.isDiscovering(), observedLink || profileLink);
        } catch (RuntimeException ignored) { machine.observeBluetooth(false, false, false); }
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            connected = info != null && info.isConnected();
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo metrics = wifi.getConnectionInfo();
            rssiDbm = connected && metrics != null ? metrics.getRssi() : -127;
            linkSpeedMbps = connected && metrics != null ? Math.max(0, metrics.getLinkSpeed()) : 0;
            frequencyMHz = connected && metrics != null ? Math.max(0, metrics.getFrequency()) : 0;
        } catch (RuntimeException ignored) { connected = false; rssiDbm = -127; linkSpeedMbps = 0; frequencyMHz = 0; }
        if (machine.observeWifi(connected)) listener.networkLost();
    }
}
