package io.ts7.carplay;

import com.shilapi.xcertplay.airplay.AirPlayCrypto;
import com.shilapi.xcertplay.airplay.AirPlayIdentity;
import io.ts7.carplay.auth.AuthenticationHardwareInventory;
import io.ts7.carplay.auth.UnavailableAuthenticationProvider;
import java.io.IOException;
import java.net.InetAddress;

/** Instrumented APK only: runtime linkage/permission/ownership checks, not real CarPlay evidence. */
final class CoreRuntimeChecks {
    static void run(android.content.Context context) throws Exception {
        AuthenticationHardwareInventory inventory = AuthenticationInventoryProbe.probe(context);
        require(inventory.authenticationHardware == AuthenticationHardwareInventory.Presence.UNKNOWN);
        Api27WirelessTransport transport = new Api27WirelessTransport(context);
        transport.start(WirelessTransport.Mode.SAME_LAN, new WirelessTransport.Listener() {
            public void changed(WirelessTransport.State state, WirelessTransport.Failure failure) {}
            public void hotspotConfiguration(android.net.wifi.WifiConfiguration configuration) {}
        });
        require(transport.state() == WirelessTransport.State.WAITING_NETWORK);
        require(transport.boundNetwork() == null); // generic/default Wi-Fi never supplies session proof
        require(!transport.selectNetwork(null, InetAddress.getLoopbackAddress()));
        boolean blocked = false;
        try { transport.listen(0); } catch (IOException expected) { blocked = true; }
        require(blocked);
        transport.stop();
        require(transport.state() == WirelessTransport.State.STOPPED);
        require(new LawfulReceiverCore(context, new UnavailableAuthenticationProvider()).hasLawfulAuthentication() == false);
        require(AirPlayIdentity.Companion.generate().getPublicKey().length == 32);
        byte[] key = new byte[32], nonce = AirPlayCrypto.INSTANCE.nonce64(0), message = {1, 2, 3};
        byte[] sealed = AirPlayCrypto.INSTANCE.chachaSeal(key, nonce, message, new byte[0]);
        require(java.util.Arrays.equals(message, AirPlayCrypto.INSTANCE.chachaOpen(key, nonce, sealed, new byte[0])));
    }
    private static void require(boolean ok) { if (!ok) throw new AssertionError("CORE_API27_RUNTIME"); }
}
