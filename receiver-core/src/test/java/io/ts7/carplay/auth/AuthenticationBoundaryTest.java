package io.ts7.carplay.auth;

import io.ts7.carplay.auth.AuthenticationProvider.AuthRequest;
import io.ts7.carplay.auth.AuthenticationProvider.AuthResult;

public final class AuthenticationBoundaryTest {
    private static int checks;
    public static void main(String[] ignored) {
        AuthenticationProvider[] providers = {new UnavailableAuthenticationProvider(),
            new HardwareMfiAuthenticationProvider(), new RemoteAuthorizedAuthenticationProvider()};
        byte[] challenge = {21, 22, 23};
        AuthRequest request = new AuthRequest(AuthRequest.Operation.SIGN_CHALLENGE,
            challenge, System.nanoTime() + 1000000000L);
        challenge[0] = 0;
        check(request.copyChallenge()[0] == 21, "request owns bounded bytes");
        for (AuthenticationProvider provider : providers) {
            check(!provider.isAvailable(), "shipping providers unavailable");
            check(!provider.getInfo().isAuthorized(), "no inferred authorization");
            AuthResult result = provider.authenticate(request);
            check(result.status != AuthResult.Status.SUCCESS, "fail closed");
            check(result.copyProtocolBytes().length == 0, "no embedded credential/response");
            check(!result.toString().contains("21"), "result redacts bytes");
            result.close(); provider.close();
        }
        request.close();
        check(request.isExpired(), "closed request cancelled");
        boolean rejected = false;
        try { request.copyChallenge(); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "closed request inaccessible");
        for (AuthenticationHardwareInventory.Presence presence : AuthenticationHardwareInventory.Presence.values()) {
            AuthenticationHardwareInventory inventory = new AuthenticationHardwareInventory(presence, 0,
                presence, 0, true);
            check(inventory.authenticationHardware == AuthenticationHardwareInventory.Presence.UNKNOWN,
                "USB0 / I2C metadata is not MFi absence or authentication proof");
        }
        FakeAuthenticationProvider fake = new FakeAuthenticationProvider();
        check(fake.isAvailable() && fake.getInfo().isAuthorized(), "test-only fixture can model future provider");
        AuthResult result = fake.authenticate(new AuthRequest(AuthRequest.Operation.SIGN_CHALLENGE,
            new byte[] {1}, System.nanoTime() + 1000000000L));
        check(result.status == AuthResult.Status.SUCCESS, "test source works");
        result.close(); fake.close();
        System.out.println("Authentication boundary PASS: " + checks + " checks; shipping providers fail closed, metadata is not MFi proof");
    }
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
