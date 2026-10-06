# TS7 Diagnostic

A minimal Android NativeActivity diagnostic utility for the TS7 target.

Package: `io.ts7diag.tool`

Current version: `0.1`

## What it reads

- Android / build / CPU ABI properties
- RAM and storage status
- real display resolution and DPI
- OpenGL ES version and selected hardware features
- Wi‑Fi enabled state, link speed/RSSI/frequency (no SSID/BSSID requested)
- Bluetooth enabled state
- connected USB device VID/PID/class
- H.264/AVC MediaCodec decoder names and capability information
- decoder instantiation test

## What it does not do

- no INTERNET permission
- no network upload
- no account access
- no analytics / telemetry

## Build

From the repository root:

```sh
./diagnostic/scripts/build.sh
```

Output:

```text
dist/TS7-Diagnostic-v0.1.apk
```

The build intentionally uses a NativeActivity and a small ARMv7 shared library. A local debug/test keystore is generated if no keystore is supplied. Do not use that test key for a production release.
