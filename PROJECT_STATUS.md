# Project Status

Last updated: 2026-10-07

## Current phase

**Phase 1 — Wireless CarPlay minimal receiver**

Status: **IMPLEMENTATION_IN_PROGRESS**

Subphase: **Phase 1.5 — Lawful protocol/core integration**. Work branch:
`feature/lawful-carplay-core`, based on latest main 4e98000. Renderer is frozen;
this round prioritizes xcertplay API27 feasibility/port, protocol/transport adapters
and an external lawful authentication-provider boundary, not synthetic-video tuning.

Renderer: **REAL TS7 SHORT-RUN VERIFIED**. Authentication: **BLOCKED**, integration
work continues; no lawful provider is configured and real iPhone video remains blocked.

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
| AVC | OMX.sprd.h264.decoder; advanced instantiate PASS (46 ms probe); now 8380 real-device rendered frames at 1280×720 / approximately 29.85 fps | VERIFIED initialization and short-run Surface output; not CarPlay |
| Advertised AVC | width 64–1920; height 64–1088; bitrate 1–50M; fps 0–960 | OBSERVED capability range, not sustain guarantee |
| USB | deviceCount=0 | VERIFIED that instant; no inference about MFi hardware |
| Earlier About screen | Quad-SL8141E | OBSERVED label, not exact silicon proof |

UNKNOWN: full wireless session compatibility, legal authentication mechanism, 15/30/60-minute sustained H.264, real audio/touch, reconnect and RAM trend under load.

## Real TS7 short-run renderer evidence — 2026-10-07

[Explicit user upload #13 / 6030102288](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6030102288):
TEST_PATTERN, CarPlay IDLE, authentication blocked; decoder OMX.sprd.h264.decoder,
1280×720, target30fps, measured29.850746268656717fps, renderedFrames8380,
droppedVideoFrames7, droppedPackets7, videoQueueDepth0, decoderRestartCount0,
availableRam455MB, lowMemory=false, lastPlaybackReason=NONE; reported pipeline latency193ms.

VERIFIED: real TS7 H.264 → OMX.sprd.h264.decoder → MediaCodec → Surface short-run
output. At the reported fps, 8380 frames correspond to about281seconds / 4m41s;
this duration is a frame-based estimate, not an uploaded continuous wall-clock timer.
The renderer counter belongs to the current renderer instance and resets on a new
playback; the event log also records an earlier stop/start. FPS is a recent window,
not a whole-run average. OBSERVED:7/8380 rendered-frame-scale drops, approximately0.084%,
not a protocol-loss probability. 15/30/60-minute behavior UNKNOWN. This is not a
full CarPlay/audio/touch/reconnect verification. #4 and #17 remain OPEN.

The upload also proves the real alpha #13 relay write succeeded; prior "pending"
statements below describe the earlier publication-time verification only.

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
| Local Java core | PASS: 2047 checks for config/IDR/bounds/malformed input/states/retries/touch |
| Both backend route tests | PASS: fixed destinations/schema/privacy/limits/auth/duplicates |
| Local alpha APK | PASS: API 27, Java-only ARMv7-compatible, signed, generated asset inspected |
| Actual API 27 MediaCodec Surface | PASS emulator: 90+ frames, 720p, stream-reset recovery and stop; OMX.google.h264.decoder, NOT SPRD/TS7 |
| PR CI | PASS [37495146916](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37495146916); preserved diagnostic [37495147056](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37495147056) PASS |
| main CI | PASS [alpha + Surface 37496485921](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37496485921) / [diagnostic 37496485898](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37496485898), release source f4a7020 |
| Real TS7 alpha rendering | VERIFIED short-run:1280×720 / OMX.sprd.h264.decoder / approximately29.85fps /8380frames; about4m41s frame-equivalent |
| Real wireless CarPlay | BLOCKED: lawful authentication/core |
| Real audio/touch/Siri | NOT YET / Siri deferred |
| 30/60-minute session gates | NOT YET |

JVM tests mean IMPLEMENTED / LOCAL TESTED, not hardware VERIFIED. API 27 screenshot visually inspected: synthetic color bars/counter, TECHNICAL PREVIEW / TEST PATTERN labels, approximately 30 fps and bounded queue. Emulator proves generic Surface output, not SPRD decode. Independent runtime/security reviews found no residual blocking regressions after fixes. See docs/ALPHA_VERIFICATION.md.

## Publication / deployment

- [Repository](https://github.com/nnnc8/ts7-carplay-lite): PUBLIC, default main.
- Branch: feature/wireless-carplay-alpha from 44b688a. Independent receiver/runtime review completed; identified callback/retry/fallback/touch/lifecycle issues repaired, no residual blocking findings.
- [Alpha test issue #13](https://github.com/nnnc8/ts7-carplay-lite/issues/13).
- [PR #14](https://github.com/nnnc8/ts7-carplay-lite/pull/14); [lawful wireless core #15](https://github.com/nnnc8/ts7-carplay-lite/issues/15), [state validation #16](https://github.com/nnnc8/ts7-carplay-lite/issues/16), [recovery #17](https://github.com/nnnc8/ts7-carplay-lite/issues/17).
- PR #14 MERGED; merge/source f4a7020c95c602935ffa20b5dfcf85b5f0454aac. Final PR CI [37496018107](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37496018107) PASS.
- Published prerelease/tag [carplay-v0.1.0-alpha-preview](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.1.0-alpha-preview), explicitly TECHNICAL PREVIEW / NOT YET A FUNCTIONAL CARPLAY RECEIVER.
- [Normal alpha APK](https://github.com/nnnc8/ts7-carplay-lite/releases/download/carplay-v0.1.0-alpha-preview/TS7-CarPlay-Lite-v0.1-alpha.apk), 176624 bytes, SHA-256 5a2461ce16324b793425aa3fa287840c64f55a9980f95385044839bd252b26da. Published download checksum/permission/asset/signature inspection PASS; no instrumentation.
- Diagnostic endpoint: https://ts7-carplay-lite-relay.vercel.app/api/diagnostics. User upload succeeded; token configured server-side only.
- Alpha endpoint: https://ts7-carplay-lite-relay.vercel.app/api/carplay-diagnostics. Final checks caught both routes returning 404 after GitHub auto-deployment used the wrong project root. Vercel Root Directory corrected/read back as `backend`; redeployed from repository root, dpl_D7rRSmDUZfzAPtJ1mPSCavJ3HGWC READY with both API functions. Both routes again pass live JSON GET 405 and invalid-schema POST 400. Future GitHub deployments use the same root; check both routes after every push. Alpha successful GitHub-write behavior tested with mocked responses; real alpha user upload pending. Published APK unchanged.
- Preserved [diagnostic-v0.2.0](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/diagnostic-v0.2.0), APK SHA-256 87dc5f08a1d0d5562720b834d460329a4cbc008f10072778d8e7f81540497a34.
- Historical diagnostic main CI [37479902327](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37479902327) SUCCESS after PR #12.

## Next

1. This round delivered the proven API 27 Surface technical preview; functional Phase 1 remains incomplete/auth-blocked.
2. TS7: synthetic 5/15-minute runs for all profiles; copy/upload decoder/fps/drops/RAM/recovery evidence to #13.
3. Keep #4 open until sustained real-device decoding is captured.
4. Obtain an authorized authentication provider; review/port compatible upstream core. No vendor identity extraction or verification bypass.
5. Integrate real audio/touch, Siri and actual reconnect; pass 30/60-minute gates only on real evidence.

User only cares about wireless. #10/#11 deferred, not deleted; wired diagnostics are a narrow debugging fallback, not Phase 1 scope.
