package io.ts7.carplay.auth;

/** TEST SOURCE ONLY. Not a hardware/provider claim and never part of an Android release. */
public final class FakeAuthenticationProvider implements AuthenticationProvider {
    public boolean isAvailable() { return true; }
    public ProviderInfo getInfo() {
        return new ProviderInfo(ProviderInfo.Type.HARDWARE_MFI, "FakeAuthenticationProvider",
            ProviderInfo.Source.MFI_AND_VENDOR_AGREEMENT, ProviderInfo.IdentifierType.I2C_BUS,
            ProviderInfo.Provisioning.AUTHORIZED);
    }
    public AuthResult authenticate(AuthRequest request) {
        return AuthResult.success(new byte[] {1, 2, 3}); // Generated, non-secret parser fixture.
    }
    public void close() {}
}
