// TS7 DiPlay port: modified 2026-10-07; GPL-3.0 core. See SOURCE_MANIFEST.json / PATCHES.md.
package com.shilapi.xcertplay.airplay

import java.util.UUID

/** Long-term Ed25519 identity of the CarPlay accessory, persisted by the caller. */
data class AirPlayIdentity(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
    val pairingId: String,
) {
    val publicKeyHex: String get() = publicKey.toHexString()

    companion object {
        fun generate(): AirPlayIdentity {
            val keyPair = AirPlayCrypto.ed25519Generate()
            return AirPlayIdentity(keyPair.privateKey, keyPair.publicKey, UUID.randomUUID().toString())
        }
    }
}
