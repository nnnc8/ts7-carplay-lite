// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.core

import com.shilapi.xcertplay.network.LegacyHotspotRadio

/** Empty-interface JNI call validates linkage only: no radio ioctl, identifiers or credentials. */
object PlatformNativeSmoke {
    @JvmStatic fun loadWithoutRadioAccess(): Boolean = LegacyHotspotRadio.verifyNativeLoadWithoutRadioAccess()
}
