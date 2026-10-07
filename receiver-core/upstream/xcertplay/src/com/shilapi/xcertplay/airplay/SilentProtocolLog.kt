package com.shilapi.xcertplay.airplay

/** Upstream payload/error logging is never forwarded to Android or diagnostics. */
internal object SilentProtocolLog {
    fun i(vararg ignored: Any?) {}
    fun w(vararg ignored: Any?) {}
    fun e(vararg ignored: Any?) {}
}
