package com.shilapi.xcertplay.mfi

import com.shilapi.xcertplay.transport.I2cTransport
import com.shilapi.xcertplay.transport.I2cTransportException

enum class MfiCertificateType {
    MFI,
    BAA,
}

class BaaCertificatePair(leaf: ByteArray, intermediate: ByteArray) {
    val leaf: ByteArray = leaf.copyOf()
    val intermediate: ByteArray = intermediate.copyOf()

    init {
        require(this.leaf.isNotEmpty()) { "BAA leaf certificate must not be empty" }
        require(this.intermediate.isNotEmpty()) { "BAA intermediate certificate must not be empty" }
    }
}

/** Common certificate/signing contract implemented by local coprocessors and remote services. */
interface MfiAuthenticator {
    val certificateType: MfiCertificateType
        get() = MfiCertificateType.MFI

    fun protocolMajor(): Int

    fun readCertificate(
        maximumOutputLength: Int = MfiAuthenticationClient.DEFAULT_MAXIMUM_CERTIFICATE_OUTPUT_LENGTH,
    ): ByteArray

    fun signChallenge(challenge: ByteArray): ByteArray

    fun baaCertificates(): BaaCertificatePair =
        throw MfiInvalidDataException("Authenticator does not provide BAA certificates")
}

/** TS7 port: register client intentionally excluded; use an authorized external provider. */
object MfiAuthenticationClient {
    const val DEFAULT_MAXIMUM_CERTIFICATE_OUTPUT_LENGTH = 65525
}
sealed class MfiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class MfiInvalidDataException(message: String, cause: Throwable? = null) : MfiException(message, cause)
class MfiAuthenticationFailedException(val errorCode: Int?, cause: Throwable? = null) :
    MfiException("AUTH_FAILED", cause)
