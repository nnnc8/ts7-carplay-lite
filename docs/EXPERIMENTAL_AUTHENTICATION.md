# v1.0 development: reuse existing DiPlay authentication

The user explicitly approved this route on2026-10-08: reuse existing components and complete with the simplest necessary TS7 changes. The previous unavailable-provider/hardware prerequisite is superseded for this experimental route. PR18 remains OPEN/unmerged; this is not final v1.0 acceptance or Apple certification.

## Provenance and limits

- GPL source: [DiPlay Legacy](https://github.com/programmerguohuajing/DiPlay-Legacy-Android), tagv0.2.7/commitc8884adcc75bfda3c134db63877bd6c6f83beb74.
- LocalMfiAuthenticationClient original Git blob ebb4a08cb7d4f4be8392307666ba345f7eff2e92; generated-test recipe45f07ef858f9e53bef9bc6d15a6fb0561b4acb42. Full source/patch/license manifest retained.
- Selected runtime: [official v0.2.7 release](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/releases/tag/v0.2.7), APK SHA256391a68216498cdc221745aa8bb2ee06aecb48680bfd982d37bbeb390d8050927; downloaded digest matches. Only assets/offline-mfi/identity.pk8 and certificate.p7b are inputs, no receiver binary/UI/icons imported.
- Upstream [README](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/blob/main/README.md), [security](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/blob/main/SECURITY.md) and [notices](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/blob/main/docs/THIRD_PARTY_NOTICES.md) describe shared experimental firmware-derived identity. Runtime credentials are not GPL-relicensed, not Apple-certified, and suitability for public redistribution/future iOS acceptance is unresolved. An APK recipient can extract bundled inputs; they are not a confidential production secret.
- GitHub tokens, Android signing keys and user identifiers are separate and never bundled/exported.

## Implementation

Reuse upstream protocol-major3 P-256 signing:32-byte already-digested challenge, NONEwithECDSA,64-byte raw r/s. Do not hash twice. Startup worker reads only two fixed assets, each1..16384bytes, checks matching key/certificate and P-256, then wipes input arrays. No storage scanning, helper, hardware bus scan, raw exception/identity logs or private app-file copy.

Exact experimental metadata may attempt a connection but isAuthorized() remains false. Actual AA05, SAP, pair-verify, encrypted RECORD and real displayed frame still gate phone/session/STREAMING. Missing/invalid identity remains blocked. Managed key destruction is best-effort, not guaranteed zeroization.

Connect guides optional explained discovery permission and paired iPhone selection. Bluetooth pairing/enable is manual Android behavior. Selection stays in memory and never enters diagnostics/disk. No microphone/GPS/account permission, WebView/Compose/pixel copies or automatic telemetry. Nine original TS7 renderer/audio/touch/asset files remain unchanged.

## Build variants

Availability is a nonblocking volatile/atomic read. close revokes immediately and schedules exactly one background cleanup owner, serialized with in-flight crypto; UI never waits on signing. Close/deadline races cancel returned results. If a crypto provider stalls, cleanup cannot forcibly terminate it or promise immediate key zeroization.

Default source/CI build is identity-free:

```sh
./receiver/build.sh
```

Explicit standalone build, with two runtime inputs already obtained outside Git:

```sh
TS7_ENABLE_EXPERIMENTAL_AUTH=1 \
TS7_DIPLAY_AUTH_ASSETS_DIR=/absolute/private/runtime/offline-mfi \
TS7_CARPLAY_UPLOAD_URL=https://ts7-carplay-lite-relay.vercel.app/api/carplay-diagnostics \
./receiver/build.sh
```

Outputs: TS7-CarPlay-Lite-DiPlay-v1.0.0-dev.1.apk (identity-free) or -standalone.apk (selected runtime). Both API27/ARMv7, versionCode5, test-signed. Original v1.0.0-dev and this local build reuse the same ignored signing key; verify the released artifacts' certificate equality before claiming in-place upgrade. Other local/CI builds may differ and require reinstall, clearing receiver preferences. Diagnostic v0.2 is separate and unaffected.

Build rejects symlinks/missing/oversize inputs, copies only the exact two filenames, and inspection verifies packaged bytes against selected inputs. Source archives and hosted CI never include the real runtime files. A fresh generated self-signed test identity builds a clearly labeled -auth-fixture-instrumented.apk for API27 crypto tests only; do not distribute that as a usable receiver.

## Current evidence and acceptance

Host: actual selected runtime key/certificate match and eight fresh challenge signatures verify; wrong/expired/oversize/mismatch/closed cases fail, experimental stays not-authorized. This does not show iPhone trust. API27 source/native build and signed APK inspection pass. Hosted API27 generated-identity execution is recorded against exact commit, separately from host/TS7 results.

Two existing real TS7 platform uploads cover all12 individual probes across runs, not same-run12PASS, simultaneous station/AP, actual radio channel/peer traffic or a CarPlay session. Hotspot now runs last to avoid contaminating earlier Network checks. Existing physical renderer report is synthetic H264, not CarPlay.

Required functional acceptance remains actual iPhone bootstrap/phone confirmation/first frame, real audio/touch/recovery and measured stability. Siri/microphone were already deferred and remain explicitly incomplete. Do not claim the30/60-minute milestones, official certification, or final v1.0 merely because a build/fixture succeeds. Complete autonomous checks first; no repeated platform testing or routine car trips.

Public report1.0.0-dev uses only BLOCKED_BY_AUTHENTICATION_REQUIREMENT, EXPERIMENTAL_IDENTITY_AVAILABLE or PHONE_CONFIRMED_SESSION plus existing fixed counters/events. Server validates consistency but cannot independently certify phone acceptance. Explicit upload only to fixed Issue13; old clients/Diagnostic fixed5 preserved. No fake positive smoke report is posted.
