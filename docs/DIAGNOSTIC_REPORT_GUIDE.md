# Diagnostic report guide

Run the diagnostic on the actual TS7 under the state that normally causes CarPlay trouble.

For wired diagnosis:

1. Connect the same iPhone and USB cable normally used for CarPlay.
2. Open TS7 Diagnostic.
3. Wait for the report to finish.
4. Save/copy `TS7-Diagnostic.txt`.

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
- decoder instantiate result
- Wi‑Fi link speed / RSSI / frequency

## Privacy check before upload

Do not publish IMEI, serial numbers, account names, SSID/BSSID or other unnecessary identifiers. The diagnostic app is designed not to request network/account access, but the report should still be reviewed before posting publicly.
