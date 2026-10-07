# Project Status

Updated: 2026-10-08.

## Current phase

Phase 1 — Wireless CarPlay minimal receiver / IMPLEMENTATION_IN_PROGRESS.
Primary strategy: **TS7-specific DiPlay Android 8.1 port**.
Protocol: DiPlay-derived port IN_PROGRESS.
Authentication: **BLOCKED_BY_AUTHENTICATION_REQUIREMENT**.
Preview: **NOT YET A FUNCTIONAL CARPLAY RECEIVER**.
Scoped deliverable: **v0.2.1 TS7 Platform Readiness Preview**; no new CarPlay features,
no merge of PR18 and no authentication hardware work pending physical TS7 evidence.
xcertplay remains fallback/auth-hardware architecture reference, not primary work.

## Preserved real TS7 evidence

- Android8.1/API27/ARMv7/2GB, display1280×720/160DPI.
- sprd/SPRD + sp7731e_1h10 board strings VERIFIED; About SL8141E OBSERVED,
  precise silicon UNKNOWN.
- [User alpha report](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6030102288):
  OMX.sprd.h264.decoder, target30/measured29.8507fps,8380 rendered test-pattern frames,
  7 drops,queue0,193ms latency,0 restarts/reconnects,availableRAM455MB.
- Approximately4m41s is 8380/fps estimate, not an uploaded continuous-duration field.
  Seven drops are not measured network packet loss. TEST_PATTERN is not CarPlay.
- Renderer VERIFIED on real TS7 for this captured run;5/15-minute acceptance NOT YET.
  Issue4 stays OPEN. Prior [diagnostic report](https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-6018940228)
  is initialization/inventory evidence, not a session/load test.

## DiPlay pivot

Old branch feature/lawful-carplay-core preserved/pushed at
801d99e9775a50ee32d8486f7fe604250c047047 (unfinished checkpoint, not a release).
New branch feature/diplay-ts7-port starts from stable main4e98000.
Primary evaluated: programmerguohuajing/DiPlay-Legacy-Android.
Secondary evaluated: mrisX/DiPlay-Android8.1-CASKA; live404/access unproven,
latest exact revision UNKNOWN, no source imported.
Chosen Legacy revision c8884adcc75bfda3c134db63877bd6c6f83beb74, GPL3 core.
Reason: reproducible public source, actual wireless/iAP2/AirPlay/audio/touch lifecycle
and legacy Android work. Current minSdk/README is not TS7 compatibility proof.

71 exact-source/notice files with original blobs/ported hashes in third_party/diplay-base.
GPL source and source-built read-only JNI only. No upstream UI/site/assets/APK/firmware.
No extracted MFi identity, private keys, certificate bundle or proprietary receiver.
Legal provider is separate from source license; external interface READY, unavailable default.

## Implemented

- User-triggered Developer platform readiness: 12 independent real local API probes,
  fixed typed status/duration/error codes, bounded workers/cancel/late-reservation cleanup,
  process-wide repeat-run/failed-cleanup quarantine guard, no phone/session/authentication/credential reads.
- Strict `0.2.1-platform` report extension on fixed Issue13; old alpha reports and fixed5 unchanged.
  [Exact operations / privacy / emulator limits](docs/PLATFORM_READINESS.md).

- Actual DiPlay Controller wireless lifecycle + Bonjour/iAP2/AirPlay/media core.
- API27 LOHS callback/WifiConfiguration, explicit AP/peer sockets and selected paired
  Bluetooth phone. Permission optional/explained; no GPS/location collection/upload.
- Bounded parser/media/discovery/tunnel buffers, epoch/cancel/teardown guards,
  private trace removal, RAM-only bounded pairing store.
- H264 main720p → adapter → original bounded MediaCodec→Surface;9 locked renderer/
  audio/touch/asset files byte-identical. Default30fps/25/20 retained.
- LPCM44.1/48k1/2channels → unchanged AudioTrack sink; normalized single-touch
  via upstream HID. Both fixture-tested, real iPhone audio/touch NOT YET.
- Minimal framework UI, waiting/authblocked status; no Compose/WebView/decoded pixel copy.
- Existing Diagnostic v0.2, both fixed relays(#5/#13), bounded metrics/privacy kept.
  Relay v0.2-alpha allowlist is backward-compatible; no client destinations/auth success claims.

## Verification

See [DiPlay verification ledger](docs/DIPLAY_PORT_VERIFICATION.md) for exact results.
Local: API27 source compile PASS; native ARMv7/x86_64 build PASS; APK/signature/ABI/
credentials/license inspection PASS;71 source hash +9 renderer-lock PASS.
Host: receiver2047, authentication24, gate30, DiPlay478 fixtures PASS;
Diagnostic sanitizer PASS; backend16 tests PASS.
v0.2.1 local delta: readiness121 + immediate/late cleanup21 assertions, existing receiver2047/auth24/gate30/DiPlay478,
Diagnostic sanitizer, backend27 PASS; source/license71 + frozen renderer9 PASS; normal and
separate instrumented API27 APK builds/inspections PASS. Hosted readiness CI and release
acceptance are recorded against the final exact commit in the v0.2.1 release notes; do not
reuse v0.2 or local compile results as hosted or physical runtime proof.
Initial87e9901 Android8.1 normal startup/authblocked/crypto/JNI/Surface PASS,
PR CI37604670362/push37604605337 PASS, Diagnostic37604670078 PASS.
Hardened77ef5cb PR37605750814/push37605744850 and Diagnostic37605750770 PASS.
Final lifecycle/authentication repair source must also pass CI before publication;
exact source/run/APK/download SHA256 proof is recorded in the release notes.
Do not reuse v0.1 emulator/CI results as v0.2 proof.

Independent upstream network/privacy review completed; identified log/binding/queue/
producer-wakeup/cleanup issues repaired. Final fresh-context port review identified
five lock/provenance/stale-callback/reconnect/audio-readiness issues; fixes and regression
fixtures added. Follow-up review found SETUP ordering and late UI notification issues;
both repaired and independently rechecked, no remaining P1/P2 in the bounded delta. This
does not constitute an authentication certification or complete upstream security audit.

## Releases / deployment preserved

- Diagnostic v0.2 tag/APK unchanged, SHA87dc5f08a1d0d5562720b834d460329a4cbc008f10072778d8e7f81540497a34.
- v0.1 technical-preview release unchanged, SHA5a2461ce16324b793425aa3fa287840c64f55a9980f95385044839bd252b26da.
- Preview prerelease: carplay-v0.2.0-diplay-preview, exact title
  TS7 CarPlay Lite v0.2 Alpha — DiPlay Port Preview. Only normal CI APK/source/checksum,
  no instrumentation published; [release record](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.2.0-diplay-preview).
- Production relay: https://ts7-carplay-lite-relay.vercel.app;RootDirectory backend.
  Preserve secret server-side. Version2 support deployment/live checks recorded in ledger.
- Issue15 stays OPEN;Issues10/11 DEFERRED;Issue4 stays OPEN. Main not overwritten by experiment.
- [PR18](https://github.com/nnnc8/ts7-carplay-lite/pull/18) OPEN/unmerged;
  high-priority [port issue19](https://github.com/nnnc8/ts7-carplay-lite/issues/19).
- Relay v0.2-compatible backend READY/promoted dpl_3eDB7nymbC7ug9LoeeENwf82JLJ1;
  both public routes GET405/invalidPOST400 PASS. No smoke comments/private uploads.

## Next blocker / next device check

Authorized AuthenticationProvider + realTS7 Bluetooth/LOHS/multicast/channel/SELinux
compatibility and actual iPhone bootstrap remain unverified.
Install the platform release APK; expected v0.2.1PlatformPreview +Waiting for iPhone +
authentication blocked +DiPlay ready. Empty central Surface is expected in waiting mode.
Do not claim connected/session/streaming. Copy diagnostics or explicitly upload to13.
No driving-time testing. Further5/15-minute renderer tests remain optional separate evidence.
Next required check: Settings → Developer → Test platform readiness, then Copy/explicit Upload
to fixed13. No iPhone needed. Restore Internet after temporary hotspot check; see platform guide.
Real TS7 readiness is still UNKNOWN; emulator radios and x86 JNI cannot fulfill that evidence gate.
