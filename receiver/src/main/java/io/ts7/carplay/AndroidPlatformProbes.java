package io.ts7.carplay;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.SurfaceHolder;
import io.ts7.carplay.auth.UnavailableAuthenticationProvider;
import io.ts7.carplay.core.DiPlayReceiverCore;
import io.ts7.carplay.core.PlatformNativeSmoke;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static io.ts7.carplay.PlatformReadiness.Code.*;

/** Actual API calls, temporary local resources only; no session, packets, phone or auth input. */
public final class AndroidPlatformProbes implements PlatformReadinessRunner.Probes {
    private final Context context;
    private final SurfaceHolder surface;
    // Survives Activity recreation: do not abandon a late reservation or allow repeated cleanup owners.
    private static volatile HandlerThread hotspotCallbacks;

    AndroidPlatformProbes(Context context, SurfaceHolder surface) {
        this.context = context.getApplicationContext();
        this.surface = surface;
    }

    @Override public boolean hasPendingResources() {
        HandlerThread thread = hotspotCallbacks;
        return thread != null && thread.isAlive();
    }

    @Override public PlatformReadiness.Code run(PlatformReadiness.Probe probe,
            PlatformReadinessRunner.Cancellation cancel) throws Exception {
        cancel.check();
        try {
            switch (probe) {
                case coreInitialization: return core();
                case jni: return nativeLoad();
                case bluetoothApi: return bluetooth();
                case rfcomm: return rfcomm();
                case localOnlyHotspot: return hotspot(cancel);
                case multicast: return multicast();
                case mdns: return mdns();
                case tcpBind: return tcp();
                case udpBind: return udp();
                case networkBinding: return network();
                case surface: return surface();
                case audioTrack: return audio();
                default: return PROBE_FAILED;
            }
        } catch (SecurityException error) { return PERMISSION_MISSING; }
        catch (ReleaseFailure error) { return RESOURCE_RELEASE_FAILED; }
        catch (NoSuchMethodError | NoClassDefFoundError error) { return API_UNAVAILABLE; }
        catch (UnsatisfiedLinkError error) { return probe == PlatformReadiness.Probe.jni ? JNI_LOAD_FAILED : PROBE_FAILED; }
        catch (IOException | RuntimeException error) { return failure(probe); }
    }

    private PlatformReadiness.Code core() {
        // Default provider cannot read credentials or start radios; initialize stops at WaitingForMfi.
        DiPlayReceiverCore core = new DiPlayReceiverCore(context, new UnavailableAuthenticationProvider());
        try {
            core.initialize();
            return "DIPLAY_CORE_READY_AUTH_BLOCKED".equals(core.initializationStatus()) && !core.hasLawfulAuthentication()
                ? NONE : CORE_INIT_FAILED;
        } finally { core.close(); }
    }

    private PlatformReadiness.Code nativeLoad() {
        boolean arm = false;
        for (String abi : Build.SUPPORTED_ABIS) if ("armeabi-v7a".equals(abi)) arm = true;
        // A successful x86 library load is not ARMv7 evidence.
        if (!arm || Process.is64Bit()) return ABI_NOT_ARMV7;
        return PlatformNativeSmoke.loadWithoutRadioAccess() ? NONE : JNI_LOAD_FAILED;
    }

    private PlatformReadiness.Code bluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return HARDWARE_UNAVAILABLE;
        adapter.getState(); // API access, not adapter identity, discovery or pairing.
        return NONE;
    }

    private PlatformReadiness.Code rfcomm() throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return HARDWARE_UNAVAILABLE;
        if (!adapter.isEnabled()) return RADIO_DISABLED;
        // Generic, non-iAP service; never accept/connect/pair or enable Bluetooth.
        BluetoothServerSocket socket = adapter.listenUsingInsecureRfcommWithServiceRecord(
            "TS7 Platform Probe", UUID.fromString("5e922b87-3b18-4fa3-9144-d3257c77667e"));
        if (socket == null) return RFCOMM_CREATE_FAILED;
        try { return NONE; }
        finally { try { socket.close(); } catch (IOException error) { throw new ReleaseFailure(); } }
    }

    private PlatformReadiness.Code hotspot(PlatformReadinessRunner.Cancellation cancel) throws InterruptedException {
        if (Build.VERSION.SDK_INT < 26) return API_UNAVAILABLE;
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return PERMISSION_MISSING;
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) return SERVICE_UNAVAILABLE;
        HandlerThread callbacks = new HandlerThread("ts7-platform-hotspot-cleanup");
        hotspotCallbacks = callbacks;
        callbacks.start();
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<PlatformReadiness.Code> code = new AtomicReference<>(HOTSPOT_START_FAILED);
        WifiManager.LocalOnlyHotspotCallback callback = new WifiManager.LocalOnlyHotspotCallback() {
            private void finish(PlatformReadiness.Code value) {
                if (completed.compareAndSet(false, true)) {
                    code.set(value);
                    done.countDown();
                    callbacks.quitSafely();
                }
            }
            @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                PlatformReadiness.Code result = NONE;
                try {
                    // Never read getWifiConfiguration(): it contains SSID/password.
                    if (reservation == null) result = HOTSPOT_START_FAILED;
                    else reservation.close(); // Also release late reservations after cancel/timeout.
                } catch (Throwable error) { result = RESOURCE_RELEASE_FAILED; }
                finally { finish(result); }
            }
            @Override public void onFailed(int reason) {
                PlatformReadiness.Code result = HOTSPOT_START_FAILED;
                if (reason == ERROR_NO_CHANNEL) result = HOTSPOT_UNSUPPORTED;
                else if (reason == ERROR_INCOMPATIBLE_MODE) result = HOTSPOT_INCOMPATIBLE;
                else if (reason == ERROR_TETHERING_DISALLOWED) result = HOTSPOT_DISALLOWED;
                finish(result);
            }
            @Override public void onStopped() { finish(HOTSPOT_START_FAILED); }
        };
        try {
            cancel.check();
            wifi.startLocalOnlyHotspot(callback, new Handler(callbacks.getLooper()));
        } catch (Throwable error) {
            callbacks.quitSafely();
            if (error instanceof SecurityException) return PERMISSION_MISSING;
            if (error instanceof InterruptedException) throw (InterruptedException) error;
            if (error instanceof LinkageError) return API_UNAVAILABLE;
            return HOTSPOT_START_FAILED;
        }
        // If a vendor never sends a callback, keep this one cleanup owner alive; block further runs.
        // Quitting it on timeout could drop a late reservation and leave the hotspot active.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (done.getCount() != 0) {
            cancel.check();
            if (System.nanoTime() >= deadline) return PROBE_TIMEOUT;
            done.await(50, TimeUnit.MILLISECONDS);
        }
        cancel.check();
        return code.get();
    }

    private PlatformReadiness.Code multicast() {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) return SERVICE_UNAVAILABLE;
        WifiManager.MulticastLock lock = wifi.createMulticastLock("ts7-platform-probe");
        if (lock == null) return MULTICAST_FAILED;
        lock.setReferenceCounted(false);
        try {
            lock.acquire();
            return lock.isHeld() ? NONE : MULTICAST_FAILED;
        } finally {
            try {
                if (lock.isHeld()) lock.release();
                if (lock.isHeld()) throw new ReleaseFailure();
            } catch (RuntimeException error) { throw new ReleaseFailure(); }
        }
    }

    private PlatformReadiness.Code mdns() throws IOException {
        MulticastSocket socket = new MulticastSocket(null);
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(5353));
            return socket.isBound() ? NONE : MDNS_BIND_FAILED;
        } finally { socket.close(); }
    }

    private PlatformReadiness.Code tcp() throws IOException {
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            return socket.isBound() ? NONE : TCP_BIND_FAILED;
        } finally { try { socket.close(); } catch (IOException error) { throw new ReleaseFailure(); } }
    }

    private PlatformReadiness.Code udp() throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        try {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.isBound() ? NONE : UDP_BIND_FAILED;
        } finally { socket.close(); }
    }

    private PlatformReadiness.Code network() throws IOException {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return SERVICE_UNAVAILABLE;
        Network network = manager.getActiveNetwork();
        if (network == null) return NETWORK_UNAVAILABLE;
        try (Socket tcp = new Socket(); DatagramSocket udp = new DatagramSocket(null)) {
            tcp.getReuseAddress(); // Allocate the TCP descriptor without connecting or sending anything.
            network.bindSocket(tcp);
            network.bindSocket(udp);
            return NONE;
        }
    }

    private PlatformReadiness.Code surface() {
        if (surface == null || surface.getSurface() == null) return SURFACE_UNAVAILABLE;
        // Borrow the Activity's actual Surface; never release the renderer's UI-owned Surface.
        return surface.getSurface().isValid() ? NONE : SURFACE_INVALID;
    }

    private PlatformReadiness.Code audio() {
        int buffer = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (buffer <= 0 || buffer > 262144) return AUDIO_CREATE_FAILED;
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, 48000,
            AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, buffer, AudioTrack.MODE_STREAM);
        try { return track.getState() == AudioTrack.STATE_INITIALIZED ? NONE : AUDIO_CREATE_FAILED; }
        finally {
            try { track.release(); } catch (RuntimeException error) { throw new ReleaseFailure(); }
        }
    }

    private static PlatformReadiness.Code failure(PlatformReadiness.Probe probe) {
        switch (probe) {
            case coreInitialization: return CORE_INIT_FAILED;
            case jni: return JNI_LOAD_FAILED;
            case rfcomm: return RFCOMM_CREATE_FAILED;
            case localOnlyHotspot: return HOTSPOT_START_FAILED;
            case multicast: return MULTICAST_FAILED;
            case mdns: return MDNS_BIND_FAILED;
            case tcpBind: return TCP_BIND_FAILED;
            case udpBind: return UDP_BIND_FAILED;
            case networkBinding: return NETWORK_BIND_FAILED;
            case surface: return SURFACE_INVALID;
            case audioTrack: return AUDIO_CREATE_FAILED;
            default: return PROBE_FAILED;
        }
    }

    private static final class ReleaseFailure extends RuntimeException {}
}
