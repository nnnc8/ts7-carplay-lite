package com.shilapi.xcertplay.transport

/** TS7 source port: shared timeout without importing wired USB implementation. */
sealed class ProtocolTransportException(message: String) : java.io.IOException(message) {
    class TimedOut(message: String) : ProtocolTransportException(message)
}
