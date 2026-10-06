# Project Status

Last updated: 2026-10-07

## Current phase

**Phase 1 — Wireless CarPlay minimal receiver**

Status: **IMPLEMENTATION_IN_PROGRESS**

Phase 0 basic report gate is COMPLETE. [Captured real-device v0.2 report](https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-6018940228) includes AVC instantiate PASS, explicitly without decoding input. Issue #4 remains OPEN for sustained operation.

v0.1-alpha is **TECHNICAL PREVIEW / NOT YET A FUNCTIONAL CARPLAY RECEIVER**. Original shell/renderer and wireless integration boundaries are implemented; lawful protocol/authentication provider is unavailable: **BLOCKED_BY_AUTHENTICATION_REQUIREMENT**. No simulated iPhone session, extracted identity or proprietary receiver.

## Evidence

| Item | Captured fact | Status |
| --- | --- | --- |
| OS / ABI | Android 8.1.0 / API 27 / armeabi-v7a, armeabi, armv7l | VERIFIED report |
| Platform properties | sprd / SPRD; board/hardware sp7731e_1h10; 4 cores | VERIFIED strings; exact silicon UNKNOWN |
| RAM | total 2048 MB, available 496 MB, lowMemory=false, threshold 144 MB | VERIFIED snapshot |
| Display | 1280×720 / 160 DPI / 60.0024 Hz / landscape | VERIFIED Android real-display report |
| Storage | data total 28157 MB / available 26158 MB | VERIFIED snapshot, not nominal chip capacity |
| Graphics | OpenGL ES 3.2 | VERIFIED reported version |
| Wi-Fi | 2437 MHz / 65 Mbps / −41 dBm, enabled/connected | VERIFIED snapshot, load behavior UNKNOWN |
| Bluetooth | adapter present/enabled; BLE feature | VERIFIED inventory, bootstrap UNKNOWN |
| AVC | OMX.sprd.h264.decoder; advanced instantiate PASS (46 ms probe) | VERIFIED enumeration/initialization only |
| Advertised AVC | width 64–1920; height 64–1088; bitrate 1–50M; fps 0–960 | OBSERVED capability range, not sustain guarantee |
| USB | deviceCount=0 | VERIFIED that instant; no inference about MFi hardware |
| Earlier About screen | Quad-SL8141E | OBSERVED label, not exact silicon proof |

UNKNOWN: full wireless session compatibility, legal authentication mechanism, sustained H.264, real audio/touch, reconnect and RAM trend under load.

## Implemented this round

- API 27 Java/framework shell; visible UI before probes, explicit developer mode, settings/status/diagnostics; no Compose/WebView.
- Separate Bluetooth/Wi-Fi observations and evidence-gated CarPlay states; shipping core refuses authentication.
- SPS/PPS validation, IDR resync, 4 × 256 KiB compressed slots, 250 ms age bound; MediaCodec → Surface, no decoded pixel copies.
- 1280×720 @ 30/25/20 fps; SPRD-preferred decoder/fallback and actual rendered-frame telemetry.
- Locally generated H.264 asset; never claims real CarPlay STREAMING.
- Fixed 500-event ring/newest 200 exports; frame/drop/latency/queue/RAM/radio/restart/reconnect/audio metrics. No identifiers, credentials or arbitrary exception text.
- Finite recovery 1/2/5 s, frame watchdog and blocked-vendor-call containment without infinite workers.
- Normalized touch boundary and minimal AudioTrack PCM sink. Real media audio/touch unverified; Siri/microphone deferred.
- Fixed alpha route /api/carplay-diagnostics → #13; preserved /api/diagnostics → #5. Explicit upload only, token server-side.
- Reproducible inspected alpha build, core/privacy tests, separate API 27 Surface instrumentation CI; diagnostic workflow/APK unchanged.
- Pinned upstream research/licenses and lawful authentication boundary; no upstream protocol/asset imported.

## Verification ledger

| Check | Result / scope |
| --- | --- |
| Local Java core | PASS: config/IDR/bounds/malformed input/states/retries/touch |
| Both backend route tests | PASS: fixed destinations/schema/privacy/limits/auth/duplicates |
| Local alpha APK | PASS: API 27, Java-only ARMv7-compatible, signed, generated asset inspected |
| Actual API 27 MediaCodec Surface | Pending emulator CI; no TS7 alpha result yet |
| PR/main CI | Pending branch publication |
| Real TS7 alpha rendering | NOT YET |
| Real wireless CarPlay | BLOCKED: lawful authentication/core |
| Real audio/touch/Siri | NOT YET / Siri deferred |
| 30/60-minute session gates | NOT YET |

JVM tests mean IMPLEMENTED / LOCAL TESTED, not hardware VERIFIED. Emulator evidence proves generic Surface output, not SPRD decode.

## Publication / deployment

- [Repository](https://github.com/nnnc8/ts7-carplay-lite): PUBLIC, default main.
- Branch: feature/wireless-carplay-alpha from 44b688a. Independent receiver/runtime review completed; identified callback/retry/fallback/touch/lifecycle issues repaired, no residual blocking findings.
- [Alpha test issue #13](https://github.com/nnnc8/ts7-carplay-lite/issues/13).
- [PR #14](https://github.com/nnnc8/ts7-carplay-lite/pull/14); [lawful wireless core #15](https://github.com/nnnc8/ts7-carplay-lite/issues/15), [state validation #16](https://github.com/nnnc8/ts7-carplay-lite/issues/16), [recovery #17](https://github.com/nnnc8/ts7-carplay-lite/issues/17).
- Diagnostic endpoint: https://ts7-carplay-lite-relay.vercel.app/api/diagnostics. User upload succeeded; token configured server-side only.
- Alpha endpoint: https://ts7-carplay-lite-relay.vercel.app/api/carplay-diagnostics; Vercel production deployment dpl_CQPYaboA5wGxM1Zj8KdUKU7qTyDX READY. Both routes return safe GET 405 and invalid-schema POST 400; alpha successful GitHub-write behavior tested locally, real alpha user upload pending.
- Preserved [diagnostic-v0.2.0](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/diagnostic-v0.2.0), APK SHA-256 87dc5f08a1d0d5562720b834d460329a4cbc008f10072778d8e7f81540497a34.
- Historical diagnostic main CI [37479902327](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37479902327) SUCCESS after PR #12.

## Next

1. Prove API 27 Surface playback before publishing the technical preview.
2. TS7: synthetic 5/15-minute runs for all profiles; copy/upload decoder/fps/drops/RAM/recovery evidence to #13.
3. Keep #4 open until sustained real-device decoding is captured.
4. Obtain an authorized authentication provider; review/port compatible upstream core. No vendor identity extraction or verification bypass.
5. Integrate real audio/touch, Siri and actual reconnect; pass 30/60-minute gates only on real evidence.

User only cares about wireless. #10/#11 deferred, not deleted; wired diagnostics are a narrow debugging fallback, not Phase 1 scope.
