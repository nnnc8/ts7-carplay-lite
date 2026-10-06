# Changelog

## CarPlay Lite v0.1-alpha technical preview — 2026-10-07

- Advanced to wireless-only Phase 1 after captured Diagnostic v0.2 report; wired/USB issues remain deferred.
- Added original API 27 Java shell and evidence-gated session states; lawful authentication boundary fails closed.
- Added bounded SPS/PPS/IDR-aware H.264 input, SPRD-preferred MediaCodec → Surface and 1280×720 @ 30/25/20 fps.
- Added developer-only generated H.264 pattern, never called CarPlay.
- Added rendered-frame/queue/drop/latency/RAM/radio telemetry, fixed event ring and finite recovery/watchdog.
- Added normalized touch boundary and minimal PCM AudioTrack sink; real media audio/touch unverified, Siri deferred.
- Added fixed Issue #13 alpha relay/privacy tests; preserved Diagnostic v0.2 and Issue #5.
- Added reproducible inspected APK build and separate API 27 Surface instrumentation CI.
- Pinned upstream/API/ABI/license/auth research; no core, proprietary binary or Apple identity imported.
- Wireless CarPlay BLOCKED; real TS7 alpha rendering/reconnect/long runs NOT YET VERIFIED. **TECHNICAL PREVIEW / NOT YET A FUNCTIONAL CARPLAY RECEIVER**.

### Validation / publication

- Merged PR #14; preview tag carplay-v0.1.0-alpha-preview at f4a7020.
- PR/main alpha/diagnostic CI PASS; API27 emulator Surface output, stream-reset recovery and stop PASS (OMX.google.h264.decoder, not SPRD evidence).
- Published normal APK, SHA-256 5a2461ce16324b793425aa3fa287840c64f55a9980f95385044839bd252b26da.
- Independent runtime/backend security reviews cleared; no embedded credential, proprietary receiver or Apple authentication identity.
- Repaired production relay 404 regression caused by GitHub deployments using the repository root: Vercel Root Directory is now `backend`, both serverless routes restored and live GET405/invalid POST400 checked. Published APK unchanged; alpha real-user upload remains pending.

## Unreleased

### Changed

- Expanded the handoff documentation for AI agents, evidence labels, issue tracking and release downloads.
- Added the complete Phase 0–7 roadmap and GitHub issue seed list.
- Improved build-toolchain diagnostics for hosts that do not expose `ld.lld` on `PATH`.
- Local build and JAR signature verification completed on 2026-10-06 after installing LLVM/LLD.
- Published the `nnnc8/ts7-carplay-lite` repository on `main` with 12 labels and 11 issue seeds.
- Changed the canonical GitHub repository visibility from private to public.
- Verified the first GitHub Actions run builds and uploads the diagnostic APK artifact.
- Created tag `diagnostic-v0.1.0` and the `TS7 Diagnostic v0.1` GitHub Release with the APK asset.
- Clarified the GitHub repository as the single source of truth and documented the stable-main branch workflow and next diagnostic gate.
- Replaced the v0.1 blocking NativeActivity startup path with a Java Android Activity that renders UI before diagnostics.
- Added isolated background probes, per-probe statuses/timeouts and safe MediaCodec enumeration without startup decoder initialization.
- Added local/public report separation, privacy tests and explicit offline-safe copy/save/upload controls.
- Added a server-side Vercel relay with fixed Issue #5 destination, strict schema, 32 KB limit, rate limiting, Markdown escaping and duplicate suppression.

## 0.2 — 2026-10-06

### Changed

- Replaced the blocking NativeActivity startup path with a programmatic Java Android Activity.
- Rendered the diagnostic UI before background probes and isolated probe failures/timeouts.
- Changed basic MediaCodec work to safe AVC enumeration; decoder initialization is now explicit and watchdog-protected.
- Added LocalReport/PublicReport separation, privacy sanitizer tests, offline copy/save fallback and user-confirmed upload.
- Added the Vercel relay source and tests for schema validation, size limits, sensitive fields, Markdown escaping, GitHub failures and duplicates.
- Built and CI-verified `TS7-Diagnostic-v0.2.apk`; SHA-256: `87dc5f08a1d0d5562720b834d460329a4cbc008f10072778d8e7f81540497a34`.

### Remaining validation

- Real TS7 v0.2 UI/probe execution is pending.
- Vercel production `GITHUB_TOKEN` is intentionally pending; the token must be added as a deployment secret with only Issues read/write permission.

## 0.1 — 2026-10-06

### Added

- Initial TS7 Diagnostic native APK.
- Local device/build information collection.
- RAM and storage status.
- Physical display resolution and density reporting.
- OpenGL ES and system feature reporting.
- Wi‑Fi and Bluetooth state reporting.
- USB device VID/PID/class enumeration.
- H.264 / AVC MediaCodec decoder enumeration and decoder instantiate test.
- Clipboard output and attempt to save `Download/TS7-Diagnostic.txt`.
- No INTERNET permission and no telemetry.

### Validation status

- APK structure and signing completed.
- ARMv7 native library produced successfully.
- Real TS7 execution is still pending.
