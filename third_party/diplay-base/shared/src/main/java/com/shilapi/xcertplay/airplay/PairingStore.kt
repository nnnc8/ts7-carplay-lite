// TS7 DiPlay port: modified 2026-10-07; GPL-3.0 core. See SOURCE_MANIFEST.json / PATCHES.md.
package com.shilapi.xcertplay.airplay

/** Store of paired controllers keyed by their long-term Ed25519 public key. */
class PairingStore(private val onSave: ((String, ByteArray) -> Unit)? = null) {
    private val entries = HashMap<String, ByteArray>()

    @Synchronized fun save(identifier: String, longTermPublicKey: ByteArray) {
        require(identifier.length in 1..128 && longTermPublicKey.size == 32) { "PAIRING_INPUT_BOUND" }
        require(entries.containsKey(identifier) || entries.size < 8) { "PAIRING_STORE_BOUND" }
        val copy = longTermPublicKey.copyOf()
        entries.put(identifier, copy)?.fill(0)
        onSave?.invoke(identifier, copy.copyOf())
    }

    @Synchronized fun get(identifier: String): ByteArray? = entries[identifier]?.copyOf()

    @Synchronized fun clear() { entries.values.forEach { it.fill(0) }; entries.clear() }
}
