// TS7 DiPlay port: modified 2026-10-07; GPL3; body/identity/credential trace removed.
package com.shilapi.xcertplay.iap2.trace
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
enum class Iap2TraceDirection(val label: String) { TX("TX"), RX("RX") }
/** IDs and byte counts only. Never format parameters, identities, secrets or exception messages. */
object Iap2FrameFormatter {
    fun format(direction: Iap2TraceDirection, context: String, frame: Iap2Frame): String =
        "IAP2 " + direction.label + " id=" + frame.messageId + " bytes=" + frame.payload.size
    fun formatFailure(direction: Iap2TraceDirection, context: String, messageId: Int?, failure: Throwable): String =
        "IAP2 " + direction.label + " FAILED id=" + (messageId ?: 0)
}
