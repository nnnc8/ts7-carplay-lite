# Diagnostic report guide

Run the diagnostic on the actual TS7 under the state that normally causes CarPlay trouble.

For wired diagnosis:

1. Connect the same iPhone and USB cable normally used for CarPlay.
2. Open `TS7 Diagnostic v0.2`.
3. Confirm that the UI appears before the probe rows complete.
4. Wait for the basic report to finish.
5. Use `Save local report` for the local detailed copy and `Copy public report` for a sanitized copy.
6. If the car unit has network access, review the upload confirmation before optionally sending the sanitized report to Issue #5.

For wireless diagnosis, also keep Wi‑Fi and Bluetooth in the same state used for CarPlay.

## Important fields

- Android / SDK / ABI
- board / hardware properties
- total and available RAM
- real resolution and DPI
- OpenGL ES
- USB devices (VID/PID/class)
- H.264 / AVC decoder names
- H.264 capability ranges
- decoder instantiate result, only after the basic report is saved or uploaded
- Wi‑Fi link speed / RSSI / frequency

## Privacy check before upload

Do not publish IMEI, serial numbers, account names, SSID/BSSID, MAC addresses, Android ID, IP address or other unnecessary identifiers. The v0.2 app creates a public allowlisted report and the relay sanitizes it again, but the report should still be reviewed before public upload.
