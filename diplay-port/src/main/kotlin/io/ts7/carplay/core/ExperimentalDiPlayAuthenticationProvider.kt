// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import io.ts7.carplay.auth.AuthenticationProvider
import java.util.concurrent.atomic.AtomicBoolean

/** Reuses pinned upstream signing code. Availability is not phone trust or Apple certification. */
class ExperimentalDiPlayAuthenticationProvider : AuthenticationProvider {
    @Volatile private var client: LocalMfiAuthenticationClient? = null
    private val closed = AtomicBoolean()
    @Volatile private var status = "EXPERIMENTAL_IDENTITY_MISSING"
    private val provenance = AuthenticationProvider.ProviderInfo(
        AuthenticationProvider.ProviderInfo.Type.EXPERIMENTAL_LOCAL,
        "ExperimentalDiPlayAuthenticationProvider",
        AuthenticationProvider.ProviderInfo.Source.PUBLIC_DIPLAY_RELEASE,
        AuthenticationProvider.ProviderInfo.IdentifierType.BUNDLED_ASSET,
        AuthenticationProvider.ProviderInfo.Provisioning.EXPERIMENTAL_USER_SELECTED,
    )

    fun initializationStatus(): String = if (closed.get()) "EXPERIMENTAL_IDENTITY_CLOSED" else status

    /** Called on the startup worker; buffers are never written to diagnostics or app storage. */
    @Synchronized fun initialize(key: ByteArray, certificate: ByteArray) {
        try {
            if (closed.get() || client != null) return
            client = LocalMfiAuthenticationClient.load(key, certificate)
            status = "EXPERIMENTAL_IDENTITY_READY"
        } catch (_: Exception) {
            status = "EXPERIMENTAL_IDENTITY_INVALID"
        } catch (_: LinkageError) {
            status = "EXPERIMENTAL_IDENTITY_INVALID"
        } finally {
            key.fill(0)
            certificate.fill(0)
        }
    }

    // UI status must not wait for the initialization/signing monitor or provider crypto.
    override fun isAvailable(): Boolean = !closed.get() && client != null
    override fun getInfo(): AuthenticationProvider.ProviderInfo = provenance

    @Synchronized override fun authenticate(request: AuthenticationProvider.AuthRequest?): AuthenticationProvider.AuthResult {
        if (request == null || request.isExpired) return AuthenticationProvider.AuthResult.cancelled()
        val signer = if (!closed.get()) client else null
        if (signer == null) return AuthenticationProvider.AuthResult.unavailable()
        return try {
            val challenge = request.copyChallenge()
            val bytes = try {
                when (request.operation) {
                    AuthenticationProvider.AuthRequest.Operation.READ_ACCESSORY_CERTIFICATE -> signer.readCertificate(65525)
                    AuthenticationProvider.AuthRequest.Operation.SIGN_CHALLENGE -> signer.signChallenge(challenge)
                }
            } finally { challenge.fill(0) }
            try {
                if (closed.get() || request.isExpired) AuthenticationProvider.AuthResult.cancelled()
                else AuthenticationProvider.AuthResult.success(bytes)
            } finally { bytes.fill(0) }
        } catch (_: Exception) { AuthenticationProvider.AuthResult.failed() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Revoke immediately. One background cleanup owner serializes with in-flight crypto;
        // Activity destruction never waits or destroys an actively used key.
        Thread({ synchronized(this) {
            client?.close()
            client = null
        } }, "ts7-auth-close").apply { isDaemon = true; start() }
    }
}
