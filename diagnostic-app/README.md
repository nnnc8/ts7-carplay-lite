# TS7 Diagnostic v0.2

This is the Android framework implementation for the TS7 / SL8141E / Android 8.1 target.

## Reliability changes from v0.1

- A normal `Activity` creates the UI before any hardware probe runs.
- Each probe runs away from the main thread and reports `PASS`, `FAILED`, `TIMEOUT` or `UNAVAILABLE`.
- A probe exception does not stop the remaining probes.
- Basic MediaCodec work only enumerates AVC decoders and capabilities.
- The risky `MediaCodec.createDecoderByType("video/avc")` call is behind an explicit button and a five-second watchdog.

## Privacy and upload

- `LocalReport` is retained only on the device's app-specific Downloads directory.
- `PublicReport` is produced by `ReportSanitizer` before it is copied or uploaded.
- The app never collects IMEI, IMSI, SIM data, phone numbers, accounts, location, SSID, BSSID, MAC addresses, Android ID, serial numbers, IP addresses or user files.
- `INTERNET` is present only for the user-triggered HTTPS upload button. There is no background telemetry or analytics.
- `GITHUB_TOKEN` is never part of the Android project. The endpoint is generated from the public `TS7_DIAGNOSTIC_UPLOAD_URL` build variable.

## Build

From the repository root, with an Android SDK installed:

```sh
TS7_DIAGNOSTIC_UPLOAD_URL=https://relay.example/api/diagnostics ./diagnostic-app/build.sh
```

The script uses the newest installed Android platform and build-tools, targets API 27 behavior, and produces:

```text
dist/TS7-Diagnostic-v0.2.apk
```

If the relay URL is omitted, the APK still builds and all offline diagnostics, copy and save behavior remain available; upload reports that the relay is not configured.
