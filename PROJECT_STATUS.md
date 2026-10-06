# Project Status

Last updated: 2026-10-06

## Current phase

**Phase 0 — Hardware / codec diagnostics**

Status: **WAITING_FOR_DEVICE_RESULT**

The immediate blocker is not source code. We need one real diagnostic run on the target TS7 before choosing the CarPlay video/transport architecture.

Repository preparation is complete locally. GitHub publication, Issues, tag and Release remain pending until the first safe push.

## Target device

| Item | Known value |
|---|---|
| Product family | TS7 head unit |
| SoC / platform shown by device | Quad-SL8141E |
| Android | 8.1.0 (API 27 expected) |
| RAM | 2 GB |
| Storage | 32 GB |
| MCU | Ts7.4.6-100-10-A3A39D-250805 |
| System build | V12.1.1_20250827.144232_THEME1 |
| Display resolution | Unknown — diagnostic required |
| CPU ABI | Unknown — expected 32-bit ARM, diagnostic required |
| H.264 decoder | Unknown — diagnostic required |
| USB CarPlay VID/PID | Unknown — diagnostic required |
| Wi‑Fi chipset / behavior | Unknown — diagnostic required |

## Completed

- [x] Establish target: TS7 / SL8141E / Android 8.1 / 2+32 GB.
- [x] Build `TS7 Diagnostic v0.1` as a minimal NativeActivity APK.
- [x] Diagnostic collects device/build, RAM/storage, display, graphics/features, Wi‑Fi/Bluetooth, USB and H.264 MediaCodec data.
- [x] No INTERNET permission or telemetry.
- [x] APK signed with a local test certificate for sideloading.
- [x] Source retained.
- [x] Reproducible local build script added.
- [x] CI workflow added for future agent handoff.
- [x] README, agent handoff contract, roadmap, backlog and release guidance completed.
- [x] Local ARMv7 APK build and JAR signature verification completed on 2026-10-06 with LLVM/LLD.

## Evidence labels

- **OBSERVED:** device context showed `Quad-SL8141E`, Android 8.1.0, MCU `Ts7.4.6-100-10-A3A39D-250805` and system build `V12.1.1_20250827.144232_THEME1`.
- **TARGET:** 2 GB RAM and 32 GB storage are the project target, not yet a diagnostic measurement.
- **UNKNOWN:** actual ABI, display resolution/DPI, H.264 decoder behavior, USB topology and Wi-Fi chipset/behavior until a real report is returned.

## Waiting on user / device

- [ ] Install `downloads/TS7-Diagnostic-v0.1.apk` on the real TS7.
- [ ] Run with the same iPhone/USB cable used for wired CarPlay.
- [ ] Run with Wi‑Fi/Bluetooth in the normal wireless CarPlay state if wireless is the target.
- [ ] Return the full report or screenshots.

## Blocked

CarPlay Receiver implementation must not be optimized blindly until the diagnostic result is available.

## Data required from the first report

Highest priority:

1. Real resolution / density DPI.
2. CPU ABI and board / hardware properties.
3. Total and available RAM.
4. OpenGL ES.
5. Connected USB VID/PID/class.
6. H.264 / AVC decoder names and capability ranges.
7. Whether H.264 decoder instantiation succeeds.
8. Wi‑Fi link speed, RSSI and frequency when relevant.

## Decision gate after report

If a working hardware AVC decoder is present, prototype CarPlay video as:

`transport -> H.264 elementary stream -> MediaCodec -> Surface`

Avoid CPU frame conversion and avoid Bitmap copies.

If only software AVC decoding is available or hardware decode is unstable, lower requested resolution/fps and benchmark before building the receiver around the platform.

## Version inventory

| Version | Artifact | Status |
|---|---|---|
| Diagnostic v0.1 | `downloads/TS7-Diagnostic-v0.1.apk` | Built, not yet validated on target hardware |
| CarPlay Lite v0.1 | — | Not started; gated on diagnostic result |
