// SPDX-License-Identifier: GPL-3.0-only
// Test certificate generation follows pinned DiPlay LocalMfiAuthenticationClientTest (45f07ef8).
package io.ts7.carplay.core

import io.ts7.carplay.auth.AuthenticationProvider
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers

private var checks = 0
private fun expectAuth(ok: Boolean) { checks++; check(ok) { "EXPERIMENTAL_AUTH_TEST_FAILED" } }

private fun generatedIdentity(): Pair<ByteArray, ByteArray> {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"))
    val pair = generator.generateKeyPair()
    val algorithm = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256)
    val name = X500Name("CN=TS7 generated test only")
    val tbs = V3TBSCertificateGenerator().apply {
        setSerialNumber(ASN1Integer(BigInteger.ONE))
        setSignature(algorithm); setIssuer(name); setSubject(name)
        setStartDate(Time(Date(0))); setEndDate(Time(Date(4102444800000L)))
        setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pair.public.encoded))
    }.generateTBSCertificate()
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(pair.private); signer.update(tbs.encoded)
    return pair.private.encoded to DERSequence(arrayOf<ASN1Encodable>(
        tbs, algorithm, DERBitString(signer.sign()),
    )).encoded
}

private fun verifyProvider(key: ByteArray, certificate: ByteArray) {
    val provider = ExperimentalDiPlayAuthenticationProvider()
    expectAuth(!provider.isAvailable)
    provider.initialize(key, certificate)
    expectAuth(key.all { it == 0.toByte() } && certificate.all { it == 0.toByte() })
    expectAuth(provider.isAvailable && provider.initializationStatus() == "EXPERIMENTAL_IDENTITY_READY")
    expectAuth(!provider.info.isAuthorized && provider.info.canUseForConnection())
    val gate = ProtocolGate()
    gate.begin(provider)
    expectAuth(gate.state() == ProtocolGate.State.BOOTSTRAP) // Availability is not authenticated/session proof.
    val authenticator = ProviderAuthenticator(provider, 3)
    val cert = authenticator.readCertificate(65525)
    val publicKey = CertificateFactory.getInstance("X.509").generateCertificates(cert.inputStream()).single().publicKey
    cert.fill(0)
    repeat(8) { index ->
        val digest = MessageDigest.getInstance("SHA-256").digest("generated-challenge-$index".toByteArray())
        val raw = authenticator.signChallenge(digest)
        expectAuth(raw.size == 64)
        val der = DERSequence(arrayOf(
            ASN1Integer(BigInteger(1, raw.copyOfRange(0, 32))),
            ASN1Integer(BigInteger(1, raw.copyOfRange(32, 64))),
        )).encoded
        val verifier = Signature.getInstance("NONEwithECDSA")
        verifier.initVerify(publicKey); verifier.update(digest)
        expectAuth(verifier.verify(der))
        digest[0] = (digest[0].toInt() xor 1).toByte()
        verifier.initVerify(publicKey); verifier.update(digest)
        expectAuth(!verifier.verify(der))
        raw.fill(0); digest.fill(0)
    }
    for (length in listOf(1, 20, 31, 33, 64, 128)) {
        val request = AuthenticationProvider.AuthRequest(AuthenticationProvider.AuthRequest.Operation.SIGN_CHALLENGE,
            ByteArray(length), System.nanoTime() + 1_000_000_000L)
        val result = provider.authenticate(request)
        expectAuth(result.status == AuthenticationProvider.AuthResult.Status.FAILED)
        expectAuth(result.copyProtocolBytes().isEmpty())
        result.close(); request.close()
    }
    val expired = AuthenticationProvider.AuthRequest(AuthenticationProvider.AuthRequest.Operation.SIGN_CHALLENGE,
        ByteArray(32), System.nanoTime() - 1)
    val cancelled = provider.authenticate(expired)
    expectAuth(cancelled.status == AuthenticationProvider.AuthResult.Status.CANCELLED)
    cancelled.close(); expired.close()
    provider.close()
    expectAuth(!provider.isAvailable)
}

fun main(arguments: Array<String>) {
    if (arguments.size == 2 && arguments[0] == "--generate-api27-fixture") {
        val output = File(arguments[1]).canonicalFile
        val build = File("build").canonicalFile
        require(output.toPath().startsWith(build.toPath()) && output != build)
        require(output.mkdirs() || output.isDirectory)
        val fixture = generatedIdentity()
        try {
            File(output, "identity.pk8").writeBytes(fixture.first)
            File(output, "certificate.p7b").writeBytes(fixture.second)
        } finally { fixture.first.fill(0); fixture.second.fill(0) }
        println("Fresh generated API27 test identity only; NOT a CarPlay runtime identity or release input")
        return
    }
    val identity = generatedIdentity()
    verifyProvider(identity.first, identity.second)
    val good = generatedIdentity()
    val different = generatedIdentity()
    val mismatch = ExperimentalDiPlayAuthenticationProvider()
    mismatch.initialize(different.first, good.second)
    expectAuth(!mismatch.isAvailable && mismatch.initializationStatus() == "EXPERIMENTAL_IDENTITY_INVALID")
    mismatch.close(); good.first.fill(0); different.second.fill(0)
    val malformed = ExperimentalDiPlayAuthenticationProvider()
    malformed.initialize(ByteArray(16 * 1024 + 1), byteArrayOf(1))
    expectAuth(!malformed.isAvailable)
    malformed.close()
    val held = CountDownLatch(1)
    val release = CountDownLatch(1)
    val slow = ExperimentalDiPlayAuthenticationProvider()
    val owner = Thread { synchronized(slow) { held.countDown(); release.await() } }.apply { isDaemon = true; start() }
    try {
        expectAuth(held.await(2, TimeUnit.SECONDS))
        val started = System.nanoTime()
        expectAuth(!slow.isAvailable)
        slow.close(); slow.close()
        expectAuth(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(250))
        expectAuth(slow.initializationStatus() == "EXPERIMENTAL_IDENTITY_CLOSED")
    } finally { release.countDown(); owner.join(1000) }
    val late = generatedIdentity()
    slow.initialize(late.first, late.second)
    expectAuth(!slow.isAvailable && late.first.all { it == 0.toByte() } && late.second.all { it == 0.toByte() })
    if (arguments.size == 1) {
        // Explicit ignored build directory only. No bytes, subjects, identifiers or fingerprints are logged.
        val directory = File(arguments[0])
        verifyProvider(File(directory, "identity.pk8").readBytes(), File(directory, "certificate.p7b").readBytes())
        println("Selected upstream runtime identity cryptography PASS; NOT phone acceptance or Apple certification")
    }
    println("Experimental authentication PASS: $checks checks; real ECDSA, key match, bounds, expiry, no false authorization/session")
}
