# Alpha verification / provenance — 2026-10-07

Authorized fallback deliverable: **TECHNICAL PREVIEW / NOT YET A FUNCTIONAL
CARPLAY RECEIVER**, blocked by missing lawful authentication/core. Phase 1
remains IMPLEMENTATION_IN_PROGRESS.

## Verified code / runtime

- Receiver code: 07a12a20f01c796eacdd3ef43a4534ab19c6c6ca.
- [PR #14](https://github.com/nnnc8/ts7-carplay-lite/pull/14).
- [Alpha PR CI](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37495146916) PASS.
- [Preserved diagnostic CI](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37495147056) PASS.
- Actual Android8.1/API27 MediaCodec → Surface: 90+ rendered frames at 1280×720,
  bounded queue, stream-reset recovery and worker shutdown PASS.
- Emulator decoder: OMX.google.h264.decoder, not SPRD/hardware verification.
- INSTRUMENTATION_CODE -1. Screenshot visually inspected: color bars/time counter,
  Technical preview/Test pattern labels; roughly 30fps and q=0 in captured image.
- 2047 core checks, Diagnostic privacy tests and 15 mocked relay tests PASS.
- Inspected min/target27/launcher/exact four permissions, Java-only ARMv7 compatibility,
  normal APK excludes CI instrumentation/debuggable flag, generated asset matches,
  no credential/proprietary/native/pixel-copy references; test signature verified.
- Source/DEX secret checks / GitGuardian PASS. No upstream protocol, Apple identity
  or authentication material, TLink/ZLink binary imported.
- Independent receiver/runtime and relay/security review completed; no residual
  blocking findings after fallback/Surface/touch/callback/retry fixes.

Only normal TS7-CarPlay-Lite-v0.1-alpha.apk is intended for device installation,
never the debuggable CI instrumentation APK.

Earlier failed runs remain visible: deprecated SDK tools, absent ripgrep in smoke
wrapper, and result-code output suppressed by Android's special stream key. Fixed
package selection, portable checks and structured result; the success above is a
passing runtime run, not those failures.

## Deployment / privacy

Production https://ts7-carplay-lite-relay.vercel.app, deployment
dpl_CQPYaboA5wGxM1Zj8KdUKU7qTyDX READY. Both diagnostic (#5) and alpha (#13) routes
verified live GET405 / invalid-schema POST400. Successful GitHub destinations tested
with mocked responses; no fabricated device report posted as a canary.

Diagnostic v0.2 real user upload succeeded. Alpha real user upload pending.
Token remains only in Vercel, not read back or embedded. Bounded process-local
rate/dedup are best-effort, not distributed guarantees.

## Preserved / unverified

Diagnostic workflow/sources/downloads unchanged from 44b688a. v0.2 SHA-256:
87dc5f08a1d0d5562720b834d460329a4cbc008f10072778d8e7f81540497a34.
#5 basic gate closed; #4 sustained vendor decode OPEN; #8 display evidence captured;
#10/#11 OPEN/deferred. #13/#15/#16/#17 track device/core/state/recovery.

Real TS7 alpha rendering NOT YET; real wireless session BLOCKED; real media audio/
touch/reconnect NOT YET; Siri/microphone deferred; 30/60-min gates not passed.
Already hung native vendor calls cannot be force-cancelled safely; finite stop
acknowledgement/watchdog contains/reports the fault and refuses another live worker.

Release uses the normal inspected APK from successful main CI, with checksum and
exact tag/commit/run evidence in release notes. CI test keys are ephemeral per run;
signed bytes are not reproducible across runs. Use the fixed published APK for the
device round; differently signed later builds may require uninstall/reinstall,
losing only local app settings. No production-signing claim.

## Published artifact

- Final PR CI [37496018107](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37496018107) PASS.
- Main alpha/Surface [37496485921](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37496485921) PASS.
- Main diagnostic [37496485898](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37496485898) PASS.
- PR #14 MERGED, source f4a7020c95c602935ffa20b5dfcf85b5f0454aac.
- [Preview prerelease/tag](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.1.0-alpha-preview) points to that source; functional carplay-v0.1.0-alpha tag intentionally NOT created.
- Normal APK: 176624 bytes, SHA-256 5a2461ce16324b793425aa3fa287840c64f55a9980f95385044839bd252b26da.
- Test certificate SHA-256: 99a7f72aa1e82e64e76a3c6a3e35c3dad0dc990dd5c1602b95026f6936f03167.

GitHub digest read back; published APK/checksum downloaded again and checksum OK.
Only normal APK/checksum attached. Main emulator structured PASS/code -1 checked,
OMX.google.h264.decoder. Not a real TS7 or actual CarPlay test.
