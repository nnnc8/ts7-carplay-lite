# Changelog

## Unreleased

### Changed

- Expanded the handoff documentation for AI agents, evidence labels, issue tracking and release downloads.
- Added the complete Phase 0–7 roadmap and GitHub issue seed list.
- Improved build-toolchain diagnostics for hosts that do not expose `ld.lld` on `PATH`.
- Local build and JAR signature verification completed on 2026-10-06 after installing LLVM/LLD.
- Published the private `nnnc8/ts7-carplay-lite` repository on `main` with 12 labels and 11 issue seeds.
- Verified the first GitHub Actions run builds and uploads the diagnostic APK artifact.

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
