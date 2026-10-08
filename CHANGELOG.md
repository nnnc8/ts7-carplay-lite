# Changelog

## v1.0.0-dev.1 connection repair — 2026-10-08

- Fixed initial idle-controller teardown race and retry timers cancelling a running wireless negotiation. Backoff1/2/5s only between failures, bounded120s per attempt, no infinite retry.
- Fresh Bluetooth/Wi-Fi proof retained during recovery; no reuse of the lost session's radio proof. Fixed stage/failure codes identify where connection stopped without private data/arbitrary exception text.
- Native Android8 audio-focus interruption/regain around unchanged LPCM sink; muted live PCM discarded, stale gain/loss revoked on stop/replacement.
- versionCode5; older reports/releases/Diagnostic preserved. Actual iPhone acceptance pending. Not final v1.0 or Apple certification.
- Published carplay-v1.0.0-dev.1-standalone at354cf81; exact push/PR/Diagnostic CI allPASS, actualAPI27 native focus+Surface+generated crypto PASS. Public APK/source downloads verified; same signer as prior standalone permits in-place upgrade. Real phone acceptance still pending.

## v1.0.0-dev experimental standalone — 2026-10-08

- Reused pinned DiPlay P-256 authentication and two explicitly user-selected runtime inputs from official v0.2.7. No proprietary receiver binary/UI/icons.
- Experimental availability separate from MFi authorization/phone session proof; identity-free source/CI and opted-in standalone; missing/invalid inputs fail closed.
- Bounded startup-worker loading, real signature/key match, expiry/failure tests and no credential logging/storage.
- Connect guides permission/paired-phone selection; nine TS7 renderer files unchanged; hotspot readiness last to avoid earlier Network-check interference.
- Strict1.0.0-dev report contract, generated-identity API27 crypto test; old clients/Diagnostic v0.2/fixed5 unchanged.
- Runtime identity is NOT Apple-certified or GPL-relicensed; public distribution/future iOS acceptance unresolved. Actual phone/video/audio/touch/recovery not yet verified; not final v1.0 acceptance.

## TS7 Platform Readiness Preview v0.2.1 — 2026-10-07

- Added Settings → Developer → Test platform readiness: 12 independent actual core/JNI/Bluetooth/RFCOMM/hotspot/multicast/mDNS/TCP/UDP/Network/Surface/AudioTrack checks.
- Added immutable fixed status/duration/error-code results, per-probe watchdog/cancel, late hotspot reservation cleanup and process-wide repeat-run/failed-cleanup quarantine guards.
- No iPhone/session/authentication/credential access; silent AudioTrack, local binds only, no app-level packet exchange. Hotspot interruption warning is explicit.
- Extended sanitized fixed Issue13 relay for 0.2.1-platform with strict 12-probe nested schema; old clients and Diagnostic v0.2/fixed5 preserved.
- Added API27 actual readiness instrumentation and Android-to-backend public-JSON verification, alongside existing H.264 Surface smoke; nine renderer/audio/touch/asset files unchanged.
- ARMv7 native runtime and TS7 radios/vendor services remain unverified until real-device report; x86 reports JNI NOT_TESTED/ABI_NOT_ARMV7.
- Prerelease carplay-v0.2.1-platform-preview; APK TS7-CarPlay-Lite-DiPlay-v0.2.1-platform.apk. PR18 remains OPEN/unmerged; further CarPlay and authentication hardware work paused.

## CarPlay Lite DiPlay v0.2-alpha port preview — 2026-10-07

- Pivoted primary development to TS7-specific DiPlay Android8.1/Legacy GPL core, exact c8884ad; preserved old lawful-core branch801d99e.
- Imported selected pinned source/notices, API27 patches and source-built ARMv7 read-only radio JNI; no upstream UI/assets/APK/credentials.
- Connected actual DiPlay wireless lifecycle and compressed-H264/LPCM/touch seams to preserved TS7 Surface pipeline; existing nine renderer files unchanged.
- Added bounded parsers/discovery/tunnel queues, explicit AP/peer binding, private trace removal and fail-closed external authentication.
- Minimal DiPlayready/waiting/authblocked UI, explained optional discovery/LOHS permission; no GPS/telemetry/microphone or fake session.
- Preserved Diagnostic v0.2/fixed5 and extended fixed13 relay for v0.2-alpha without accepting simulated auth/streaming.
- Added API27/ARMv7/provenance/licenses/privacy/crypto/JNI/startup and preserved Surface CI checks.
- **NOT YET A FUNCTIONAL CARPLAY RECEIVER**. Real iPhone/TS7 wireless/auth/audio/touch unverified; Siri/wired deferred.

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
