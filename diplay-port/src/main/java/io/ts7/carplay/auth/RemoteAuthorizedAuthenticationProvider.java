// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.auth;

/** No endpoint or credential. Requires explicit Apple/vendor-authorized service provisioning. */
public final class RemoteAuthorizedAuthenticationProvider implements AuthenticationProvider {
    private final ProviderInfo info = new ProviderInfo(ProviderInfo.Type.REMOTE_AUTHORIZED,
        "RemoteAuthorizedAuthenticationProvider", ProviderInfo.Source.UNAVAILABLE,
        ProviderInfo.IdentifierType.SERVICE, ProviderInfo.Provisioning.UNVERIFIED);
    public boolean isAvailable() { return false; }
    public ProviderInfo getInfo() { return info; }
    public AuthResult authenticate(AuthRequest request) { return AuthResult.unavailable(); }
    public void close() {}
}
