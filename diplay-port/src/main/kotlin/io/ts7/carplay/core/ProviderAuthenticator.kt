// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import com.shilapi.xcertplay.mfi.MfiAuthenticator
import com.shilapi.xcertplay.mfi.MfiInvalidDataException
import io.ts7.carplay.auth.AuthenticationProvider
import java.util.Arrays

/** No identity is supplied here. A provisioned future provider must implement both operations. */
class ProviderAuthenticator(
    private val provider: AuthenticationProvider,
    private val major: Int,
) : MfiAuthenticator {
    init { require(major in 1..255) { "AUTH_PROTOCOL_REQUIRED" } }
    override fun protocolMajor(): Int = major
    override fun readCertificate(maximumOutputLength: Int): ByteArray =
        operation(AuthenticationProvider.AuthRequest.Operation.READ_ACCESSORY_CERTIFICATE,
            ByteArray(0), maximumOutputLength)
    override fun signChallenge(challenge: ByteArray): ByteArray =
        operation(AuthenticationProvider.AuthRequest.Operation.SIGN_CHALLENGE, challenge, 65525)
    private fun operation(kind: AuthenticationProvider.AuthRequest.Operation,
        challenge: ByteArray, maximum: Int): ByteArray {
        if (!provider.isAvailable || !provider.info.canUseForConnection())
            throw MfiInvalidDataException("AUTH_UNAVAILABLE")
        require(maximum in 1..65525) { "AUTH_BOUND" }
        val request = AuthenticationProvider.AuthRequest(kind, challenge, System.nanoTime() + 3_000_000_000L)
        try {
            val result = provider.authenticate(request)
            try {
                if (request.isExpired || result.status != AuthenticationProvider.AuthResult.Status.SUCCESS)
                    throw MfiInvalidDataException("AUTH_FAILED")
                val bytes = result.copyProtocolBytes()
                if (bytes.size !in 1..maximum) {
                    Arrays.fill(bytes, 0.toByte())
                    throw MfiInvalidDataException("AUTH_BOUND")
                }
                return bytes // Protocol only; never a diagnostic field.
            } finally { result.close() }
        } finally { request.close() }
    }
}
