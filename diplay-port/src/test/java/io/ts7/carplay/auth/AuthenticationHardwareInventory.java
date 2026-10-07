// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.auth;

/** Non-secret metadata only. A generic USB device/I2C bus is not evidence of an MFi chip. */
public final class AuthenticationHardwareInventory {
    public enum Presence { PRESENT, NOT_PRESENT, PERMISSION_DENIED, UNKNOWN }
    public final Presence authenticationHardware;
    public final Presence usbInventory;
    public final Presence i2cNodes;
    public final int usbDeviceCount;
    public final int i2cNodeCount;
    public final boolean usbHostDeclared;

    public AuthenticationHardwareInventory(Presence usbInventory, int usbCount,
            Presence i2cNodes, int i2cCount, boolean usbHostDeclared) {
        if (usbInventory == null || i2cNodes == null || usbCount < 0 || usbCount > 256
                || i2cCount < 0 || i2cCount > 16)
            throw new IllegalArgumentException("INVALID_INVENTORY");
        this.authenticationHardware = Presence.UNKNOWN;
        this.usbInventory = usbInventory;
        this.i2cNodes = i2cNodes;
        this.usbDeviceCount = usbCount;
        this.i2cNodeCount = i2cCount;
        this.usbHostDeclared = usbHostDeclared;
    }

    @Override public String toString() {
        return "Authentication hardware: " + authenticationHardware.name()
            + "\nUSB inventory: " + usbInventory.name() + " / devices=" + usbDeviceCount
            + "\nUSB host declared: " + usbHostDeclared
            + "\nI2C metadata (nodes 0-15 only): " + i2cNodes.name() + " / nodes=" + i2cNodeCount
            + "\nA USB interface or I2C node does not prove an authorized authentication device.";
    }
}
