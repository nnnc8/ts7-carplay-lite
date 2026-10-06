# Project Status

Last updated: 2026-10-06

## Current phase

**Phase 0 — Hardware / codec diagnostics**

Status: **WAITING_FOR_V0.2_DEVICE_RESULT**

The v0.1 device run established a reproducible black-screen failure: the APK installed and launched, but no diagnostic text appeared. v0.2 moves UI creation before all probes and is now ready for the real-device reliability check.

Repository preparation, public GitHub publication, v0.1 tag/Release and the v0.2 implementation are complete. The remaining blocker is the missing real-device v0.2 diagnostic result.

## GitHub publication

- Repository: [nnnc8/ts7-carplay-lite](https://github.com/nnnc8/ts7-carplay-lite)
- Visibility: PUBLIC
- Default branch: `main`
- Initial commit: `43a3be62b4c092acf04a557fc483b6d441d7dcb9`
- First Actions run: [37448438159](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37448438159) — SUCCESS, including APK artifact upload.
- Release/tag: [`diagnostic-v0.1.0`](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/diagnostic-v0.1.0)
- Release APK: [`TS7-Diagnostic-v0.1.apk`](https://github.com/nnnc8/ts7-carplay-lite/releases/download/diagnostic-v0.1.0/TS7-Diagnostic-v0.1.apk)
- v0.2 branch CI: [37479300420](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37479300420) — SUCCESS, including APK signature and credential scan.
- v0.2 APK SHA-256: `87dc5f08a1d0d5562720b834d460329a4cbc008f10072778d8e7f81540497a34`
- v0.2 Release/tag: pending PR merge and release completion.

## Diagnostic upload relay

- Production endpoint: `https://ts7-carplay-lite-relay.vercel.app/api/diagnostics`
- Deployment: READY on Vercel; GET/invalid-JSON contract checks return safe 405/400 responses.
- GitHub credential: **NOT CONFIGURED**. The production Vercel environment still needs `GITHUB_TOKEN`; no token is in source, APK, CI artifact or release asset.
- Required token scope: fine-grained PAT restricted to this repository with Issues **Read and write** only.

Created issue seeds: #1 Wi-Fi/Bluetooth, #2 performance telemetry, #3 wireless feasibility, #4 H.264 decoder, #5 first diagnostic report, #6 60-minute stability, #7 30-minute stability, #8 display resolution, #9 Surface prototype, #10 USB topology, #11 wired transport.

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
- [x] v0.1 had no INTERNET permission or telemetry; v0.2 adds INTERNET only for explicit upload.
- [x] APK signed with a local test certificate for sideloading.
- [x] Source retained.
- [x] Reproducible local build script added.
- [x] CI workflow added for future agent handoff.
- [x] README, agent handoff contract, roadmap, backlog and release guidance completed.
- [x] Local ARMv7 APK build and JAR signature verification completed on 2026-10-06 with LLVM/LLD.
- [x] Public GitHub repository created, `main` pushed without force, labels and 11 issue seeds created.
- [x] GitHub Actions build and APK artifact upload verified on the initial push.
- [x] `diagnostic-v0.1.0` tag and `TS7 Diagnostic v0.1` Release created with the APK asset.
- [x] Replaced blocking NativeActivity startup with an Android framework Activity that renders UI before probes.
- [x] Added isolated background probes with per-probe status and timeout handling.
- [x] Removed MediaCodec decoder initialization from basic startup enumeration.
- [x] Added local/public report separation and client-side privacy sanitizer.
- [x] Added fixed Issue #5 HTTPS relay source, server-side validation/sanitization, rate limiting, Markdown escaping and duplicate suppression.
- [x] Added backend tests and JVM ReportSanitizer tests.
- [x] GitHub Actions built `TS7-Diagnostic-v0.2.apk` with the public relay URL and no embedded credential.
- [x] Vercel relay deployed; production GitHub credential remains intentionally unconfigured.

## Evidence labels

- **OBSERVED:** device context showed `Quad-SL8141E`, Android 8.1.0, MCU `Ts7.4.6-100-10-A3A39D-250805` and system build `V12.1.1_20250827.144232_THEME1`.
- **TARGET:** 2 GB RAM and 32 GB storage are the project target, not yet a diagnostic measurement.
- **UNKNOWN:** actual ABI, display resolution/DPI, H.264 decoder behavior, USB topology and Wi-Fi chipset/behavior until a real report is returned.
- **OBSERVED:** v0.1 installed and launched on the real TS7 but displayed a black content area with Android navigation controls still working.
- **UNKNOWN:** whether the v0.1 stall came from synchronous report generation, vendor API behavior or MediaCodec initialization; v0.2 separates these paths for testing.

## Waiting on user / device

- [ ] Install `downloads/TS7-Diagnostic-v0.2.apk` on the real TS7.
- [ ] Confirm the v0.2 UI appears within approximately two seconds.
- [ ] Run the basic diagnostic and confirm a complete report despite any individual probe failure.
- [ ] Use the explicit upload confirmation only after reviewing the sanitized report.
- [ ] Run with the same iPhone/USB cable used for wired CarPlay.
- [ ] Run with Wi‑Fi/Bluetooth in the normal wireless CarPlay state if wireless is the target.
- [ ] Return the full report or screenshots.

## Next

- Analyze MediaCodec results.
- Identify the hardware AVC decoder, if available.
- Identify the physical display resolution and DPI.
- Inspect the USB device path and topology.
- Inspect the Wi-Fi environment and Bluetooth capabilities.
- Determine the first CarPlay Lite architecture from measured evidence.

## Blocked

CarPlay Receiver implementation remains gated on the v0.2 real-device diagnostic result.

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
| Diagnostic v0.2 | `downloads/TS7-Diagnostic-v0.2.apk` / release asset | Built and CI-verified; relay credential and real-device validation pending |
| CarPlay Lite v0.1 | — | Not started; gated on diagnostic result |
