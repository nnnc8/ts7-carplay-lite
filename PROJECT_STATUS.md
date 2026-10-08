# Project Status

Updated: 2026-10-08.

## Current phase

Phase 1 — Wireless CarPlay minimal receiver / IMPLEMENTATION_IN_PROGRESS.
Primary strategy: **TS7-specific DiPlay Android 8.1 port**.
Protocol: DiPlay-derived port IN_PROGRESS.
Authentication: **EXPERIMENTAL_LOCAL IMPLEMENTED**, user-selected 2026-10-08.
Version: **1.0.0-dev.1 repair in progress**, NOT Apple-certified; real iPhone session not yet verified.
Continue toward v1.0 with existing DiPlay code and necessary TS7 adaptations.
Missing runtime inputs remain BLOCKED; PR18 unmerged/main preserved.
xcertplay remains fallback/auth-hardware architecture reference, not primary work.

Published [v1.0 development standalone prerelease](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v1.0.0-dev-standalone)
at exact runtime/source0b5dd532bab09a76ba52a9ece6355dbbc984f58a. Download/read-back APK and source ZIP SHA256 match release assets;
APK76e0d62d1df3e6fb1b79b4f2299aa3864e72388c4c0b584b1b7e78c53949104f.
Exact push37719221003 / PR37719225761 / Diagnostic37719225683 allPASS, genuineAPI27 normal/crypto/readiness/Surface and generated-identity signing execution;
host selected-runtime93 checks and independent repaired-delta recheck PASS. Not real iPhone proof/finalv1 acceptance.
Current fixed13/fixed5 production relay READY/promoted dpl_67eEPkck3ZZjKX4jdFHQAxgjuwEN;9backend-only files,
both public routes GET405/invalidPOST400/new-version missing-readiness rejection, no positive test comment, secret untouched.
Detailed compiled-artifact evidence is in the release notes; subsequent documentation-only commits do not change that release's APK/source tag.

## Preserved real TS7 evidence

- [Actual standalone connection failure](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6051350325): v1.0.0-dev identity available, no decoder/frame/session, initial failure23ms after Connect,3 retries exhausted13s later. Source confirms idle startup controller teardown rejects the immediate first attempt, and prior1/2/5s timers could cancel an accepted slow hotspot/RFCOMM/handshake; timing is consistent but the old generic report cannot identify every runtime failure. v1.0.0-dev.1 repairs these control-flow defects and adds fixed stage/failure codes. Exact-build/device evidence pending; no repeat platform tests requested.

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

72 source/notice files with original blobs/ported hashes in third_party/diplay-base.
GPL protocol/local-auth code and source-built JNI; no upstream UI/icons/proprietary receiver.
Only two explicitly selected runtime inputs from the matching official v0.2.7 APK, outside Git/source/CI.
Runtime credentials are not GPL-relicensed or Apple-certified; public distribution rights unresolved.
Authorized-provider templates remain separate. See docs/EXPERIMENTAL_AUTHENTICATION.md.

## Implemented

- Recovery waits for actual attempt completion before1/2/5s backoff; each negotiation has120s deadline. Startup probe controller retires before Connect; old-controller teardown remains quarantined. Fresh recovery requires Bluetooth/Wi-Fi proof again. Fixed failure/stage events expose no peer/credential/error text.
- Native API26 audio focus wraps the unchanged PCM sink; temporary loss drops live audio without queueing, gain restarts the same format, permanent loss/stop revoke stale callbacks. No new permission/dependency or frozen renderer change. Candidate API27 focus instrumentation pending.

- Reused pinned P-256/protocol-major3 authenticator; bounded direct assets, key/certificate self-check, fixed failures, startup worker and no secret logging/storage.
- Experimental availability separate from authorized MFi/real session proof; Connect guides permission and paired-phone selection.
- Identity-free source/CI versus explicitly opted-in standalone; freshly generated CI crypto fixture, never real runtime data in CI.
- Hotspot readiness runs last, preventing its client Wi-Fi interruption from contaminating earlier Network checks; no coexistence claim.
- Strict1.0.0-dev report states BLOCKED / EXPERIMENTAL_IDENTITY_AVAILABLE / PHONE_CONFIRMED_SESSION. Old report versions/fixed5 remain unchanged; client reports are not server-verified phone trust.
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
Current local delta: API27/ARMv7/x86_64 compile PASS, identity-free/standalone APK signature/ABI/input inspection PASS,
72 source hash +9 renderer-lock PASS. Selected runtime cryptography and nonblocking-close regression covered;
readiness126, receiver2047, backend29 PASS. Current hosted/commit results require separate evidence.
Historical v0.2 preview checks: receiver2047, authentication24, gate30, DiPlay478 fixtures PASS;
Diagnostic sanitizer PASS; backend16 tests PASS.
v0.2.1 local delta: readiness121 + immediate/late/admission cleanup24 assertions, existing receiver2047/auth24/gate30/DiPlay478,
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
- Historical v0.2-compatible backend READY/promoted dpl_3eDB7nymbC7ug9LoeeENwf82JLJ1;
  both public routes GET405/invalidPOST400 PASS. No smoke comments/private uploads.

## Next blocker / next device check

[Real report1](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6050562144): 11PASS/hotspot permission denied.
[Real report2](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6050623491): 11PASS including hotspot reservation+close; Network UNAVAILABLE after client Wi-Fi loss.
Across both: all12 individual probes have physicalPASS, not same-run12PASS/coexistence proof.
Do not request repeats. Finish host/API27/packaging/review first, then consolidate indispensable phone acceptance.
Crypto/emulator cannot prove real iPhone trust/video/audio/touch/recovery or30/60-minute gates.
Siri/microphone remain deferred as previously documented.
