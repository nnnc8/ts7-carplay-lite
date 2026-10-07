// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.auth;

/** Future authorized I2C/USB/UART contract only. Inventory is not provisioning. No bus access. */
public final class HardwareMfiAuthenticationProvider implements AuthenticationProvider {
    private final ProviderInfo info = new ProviderInfo(ProviderInfo.Type.HARDWARE_MFI,
        "HardwareMfiAuthenticationProvider", ProviderInfo.Source.UNAVAILABLE,
        ProviderInfo.IdentifierType.NONE, ProviderInfo.Provisioning.UNVERIFIED);
    public boolean isAvailable() { return false; }
    public ProviderInfo getInfo() { return info; }
    public AuthResult authenticate(AuthRequest request) { return AuthResult.unavailable(); }
    public void close() {}
}
