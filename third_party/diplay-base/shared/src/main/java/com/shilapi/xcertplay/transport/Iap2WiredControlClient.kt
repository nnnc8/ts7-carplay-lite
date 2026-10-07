// TS7 DiPlay port: modified 2026-10-07; GPL-3.0 core. See SOURCE_MANIFEST.json / PATCHES.md.
package com.shilapi.xcertplay.transport
import com.shilapi.xcertplay.iap2.message.Iap2ControlMessages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
/** Only the shared subscription declaration required by wireless; no wired transport remains. */
object Iap2WiredControlClient {
    fun subscriptions(): List<Iap2Frame> = Iap2ControlMessages.subscriptions()
}
