// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.auth;

/** The only provider selected by the shipping preview. Never synthesizes authentication. */
public final class UnavailableAuthenticationProvider implements AuthenticationProvider {
    private final ProviderInfo info = new ProviderInfo(ProviderInfo.Type.UNAVAILABLE,
        "UnavailableAuthenticationProvider", ProviderInfo.Source.UNAVAILABLE,
        ProviderInfo.IdentifierType.NONE, ProviderInfo.Provisioning.UNAVAILABLE);
    public boolean isAvailable() { return false; }
    public ProviderInfo getInfo() { return info; }
    public AuthResult authenticate(AuthRequest request) {
        return request == null || request.isExpired() ? AuthResult.cancelled() : AuthResult.unavailable();
    }
    public void close() {}
}
