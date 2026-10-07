// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import com.shilapi.xcertplay.airplay.AirPlayCrypto
import com.shilapi.xcertplay.network.LegacyHotspotRadio

/** Invoked only by separate instrumentation. No radio, credentials or phone interaction. */
object Api27RuntimeSmoke {
    @JvmStatic fun run() {
        val pair = AirPlayCrypto.ed25519Generate()
        val message = byteArrayOf(1, 2, 3)
        check(AirPlayCrypto.ed25519Verify(pair.publicKey, message,
            AirPlayCrypto.ed25519Sign(pair.privateKey, message)))
        pair.privateKey.fill(0)
        val key = java.security.SecureRandom().let { ByteArray(32).also(it::nextBytes) }
        val nonce = AirPlayCrypto.nonce64(0)
        check(AirPlayCrypto.chachaOpen(key, nonce, AirPlayCrypto.chachaSeal(key, nonce, message))
            .contentEquals(message))
        key.fill(0)
        check(LegacyHotspotRadio.verifyNativeLoadWithoutRadioAccess())
        check(org.slf4j.LoggerFactory.getLogger("TS7-native-smoke") is org.slf4j.helpers.NOPLogger)
    }
}
