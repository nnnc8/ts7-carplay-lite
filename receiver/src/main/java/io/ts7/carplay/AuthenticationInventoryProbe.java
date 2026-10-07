// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import io.ts7.carplay.auth.AuthenticationHardwareInventory;
import io.ts7.carplay.auth.AuthenticationHardwareInventory.Presence;
import java.util.Map;

/** Explicit inventory: UsbManager descriptors + stat only. No open, IOCTL, register/data read. */
public final class AuthenticationInventoryProbe {
    private AuthenticationInventoryProbe() {}

    public static AuthenticationHardwareInventory probe(Context context) {
        Presence usb = Presence.UNKNOWN;
        int usbCount = 0;
        try {
            UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (manager != null) {
                Map<String, UsbDevice> devices = manager.getDeviceList();
                usbCount = Math.min(256, devices.size());
                // Inspect non-secret class/VID/PID only; no built-in identity matcher or identifiers saved.
                for (UsbDevice device : devices.values()) {
                    device.getDeviceClass();
                    device.getVendorId();
                    device.getProductId();
                }
                usb = usbCount == 0 ? Presence.NOT_PRESENT : Presence.PRESENT;
            }
        } catch (SecurityException denied) { usb = Presence.PERMISSION_DENIED; }
        catch (RuntimeException unavailable) { usb = Presence.UNKNOWN; }

        int nodes = 0;
        boolean denied = false;
        boolean unknown = false;
        for (int bus = 0; bus < 16; bus++) {
            try { Os.stat("/dev/i2c-" + bus); nodes++; }
            catch (ErrnoException error) {
                if (error.errno == OsConstants.EACCES || error.errno == OsConstants.EPERM) denied = true;
                else if (error.errno != OsConstants.ENOENT && error.errno != OsConstants.ENOTDIR) unknown = true;
            } catch (SecurityException error) { denied = true; }
        }
        Presence i2c = nodes > 0 ? Presence.PRESENT : denied ? Presence.PERMISSION_DENIED
            : unknown ? Presence.UNKNOWN : Presence.NOT_PRESENT;
        boolean usbHost = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        return new AuthenticationHardwareInventory(usb, usbCount, i2c, nodes, usbHost);
    }
}
