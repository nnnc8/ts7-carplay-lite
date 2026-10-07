// TS7 DiPlay port: modified 2026-10-07; GPL-3.0 core. See SOURCE_MANIFEST.json / PATCHES.md.
package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.transport.Iap2IdentificationConfig

enum class CarPlayTransport { WIRELESS }
/** TS7-only wireless configuration: no helper address, credential loader, CAN or GPS. */
class CarPlayRuntimeConfig(
    val identification: Iap2IdentificationConfig,
    val label: String = "TS7 CarPlay Lite",
    val hostName: String = "ts7-carplay-lite",
    val wirelessBluetoothDeviceAddress: String? = null,
) {
    val transport = CarPlayTransport.WIRELESS
    init {
        require(!identification.locationInformationEnabled && !identification.vehicleStatusEnabled)
        require(wirelessBluetoothDeviceAddress == null ||
            Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$").matches(wirelessBluetoothDeviceAddress))
    }
}
