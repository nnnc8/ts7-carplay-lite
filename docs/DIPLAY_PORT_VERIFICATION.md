# DiPlay verification ledger

## v1.0.0-dev.1 connection repair — 2026-10-08

Published [connection repair prerelease](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v1.0.0-dev.1-standalone), exact runtime/source354cf813864943bb6ad5ad3392715f62d6fdd3f3. APK8,140,108bytes/SHA256f366c157002a7d70046467ca72bb5b73d33073ea2f5c9ac99bc486f104c55d05; corresponding source ZIP569,011bytes/SHA256a13e855da930f45c8be1b8d7233becac7f7f077101968e0d9f9df342ccc31fd8. Public downloads/checksums verified. Signing certificate equals original public v1.0.0-dev APK; in-place upgrade compatible. Source archive excludes APK/download/report/build/runtime/signing files; nine renderer locks unchanged.

Captured [failed actual phone attempt6051350325](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6051350325): experimental assets loaded, no session/decoder/frame, immediate23ms failure and3 retries exhausted13s later. Source fixes retire idle startup controller before Connect, let each accepted negotiation finish/fail within120s, backoff1/2/5 only between failures, revoke failed partial proof, and retain fixed stage/failure diagnostics. Old generic report cannot prove every device-level cause. No repeat platform probes requested.

Exact [push37722236538](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37722236538) / [PR37722239621](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37722239621) / [Diagnostic37722239566](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37722239566) allPASS. ActualAPI27 normal/readiness/Surface90+/reset/stop/generated-identity8signatures/mutation rejection and Android→server offline JSON PASS. New silent AudioManager/AudioTrack44.1/48k mono/stereo, native transient-loss/regain/permanent-loss, stop/replacement/late-callback regressions PASS. Generic OMX.google codec, not SPRD/audio audibility/phone proof. Local receiver2066/readiness126+cleanup24/auth24/gate30/DiPlay496/runtime93/backend30/Diagnostic sanitizer/source72/frozen9 PASS. Independent bounded connection/recovery static review no P1/P2; audio and whole upstream not covered by that review.

Fixed13/fixed5 backend-only source354cf81,9files staged/tested/promoted dpl_5963XPZvpbTvg87UDqXqac32qyHY. Canonical routes GET405/invalidPOST400/new dev.1 missing-readiness rejection; no fake successful GitHub comment, no credentials read/changed. Last15min error query empty, broader monitoring/drains not audited. PR18 OPEN/unmerged; stable main4e98000 unchanged. Actual TS7/iPhone full session, audible media/touch/recovery/30/60min NOTYET; microphone/Siri/production signing still incomplete. Not finalv1.0 acceptance.

## v1.0 development standalone — 2026-10-08

Published [experimental standalone prerelease](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v1.0.0-dev-standalone), exact source/runtime0b5dd532bab09a76ba52a9ece6355dbbc984f58a. APK8,123,724bytes/SHA25676e0d62d1df3e6fb1b79b4f2299aa3864e72388c4c0b584b1b7e78c53949104f; corresponding source ZIP SHA256ada4dcba1334de015d9d8703b3bb89b811aaea9afd0579ac5eb7be12c959ff39. Actual public downloads match. Source72/frozen renderer9, API27/ARMv7, license/signature/explicit-input inspection PASS.

Exact [push37719221003](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37719221003)/[PR37719225761](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37719225761)/[Diagnostic37719225683](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37719225683) allPASS, not cancelled/skipped. GenuineAPI27 x86_64 normal idle startup, BCcrypto/JNI, actual12readiness APIs/permission isolation,1280x720 Surface90+/reset/stop and Android→server offline JSON checks. Generated-identity asset load/8P-256 signatures/mutation rejection/experimental-not-authorized/no-false-phone/session/frame PASS. Real selected runtime key/certificate93 host checks separately; runtime inputs NOT uploaded to CI. Renderer screenshot visually checked, generic OMX.google codec, not SPRD/phone proof.

Local receiver2047/readiness126+cleanup24/auth24/gate30/DiPlay478/experimental93/backend29/Diagnostic sanitizer PASS. Scoped independent review found main-thread monitor blocking and inconsistent server session reports; both repaired/regression-tested and independently rechecked, no remaining blockers in delta. Not a full upstream audit/certification.

Fixed13/fixed5 relay dpl_67eEPkck3ZZjKX4jdFHQAxgjuwEN, reviewed source0b5dd53,9backend-only files, staged/tested then promoted; both public routes405/invalid400, new dev version recognized via missing-readiness rejection. Secret unchanged, no fake positive report/comment. Last15min error query empty, monitoring/drains not audited.

Two real TS7 platform reports cover12 individualPASS across runs; no repeat requested, not same-run12PASS/coexistence/phone traffic. User selected upstream experimental runtime on2026-10-08; missing assets still block, available remains not MFi-authorized/Apple-certified. Runtime data not GPL-relicensed, redistribution/futureiOS unresolved. Actual iPhone AA05/SAP/pair-verify/RECORD/firstframe/audio/touch/recovery/30/60min remain NOTYET; Siri/microphone/wired deferred. PR18 OPEN/unmerged, main unchanged. See [source/build/evidence contract](EXPERIMENTAL_AUTHENTICATION.md).

## Historical v0.2 preview

2026-10-07. Release must state NOT YET A FUNCTIONAL CARPLAY RECEIVER.
Exact source is the release tag; source archive includes GPL/license/provenance/build scripts.
Compile != runtime != iPhone != authentication != video.

| Gate | Captured result |
| --- | --- |
| Primary base selection | Legacy public fixed c8884adcc75bfda3c134db63877bd6c6f83beb74 |
| CASKA | current access unproven/latestSHA UNKNOWN; not imported |
| API27 compile | PASS: actual API27 android.jar, Kotlin2.2.10 JVM8 and javac release8 |
| ARMv7 | PASS: NDK25.2.9519653 APP_PLATFORM android27,armeabi-v7a source build; APK ELF32 ARM |
| x86_64 | PASS source build, for emulator smoke only |
| Source/license/no blobs | PASS71 fixed file digests/originalGitblobs/GPL/dependency notices |
| Renderer preserved | PASS9 baseline hashes, including asset and PCM/touch |
| Local receiver | PASS2047 fixtures |
| Auth/gate/DiPlay | PASS24+30+478 fixtures; fake provider test sources only; tunnel loopback is not radio/phone proof |
| Diagnostic privacy | PASS ReportSanitizerTest; diagnostic code/workflow unchanged |
| Both backend routes | PASS16 unit tests; v0.1/v0.2 fixed13, diagnostic fixed5, privacy/auth deny |
| Normal APK inspect | PASSAPI27/version0.2-alpha/permissions/actualARMv7/DiPlaypresence/no credentials/signature |
| Android8.1 emulator | PASS hardened77ef5cb: normal startup/authgate/BCcrypto/native link/originalSurface/reset/stop |
| Hosted CI | PASS hardened [PR37605750814](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37605750814) / [push37605744850](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37605744850), Diagnostic37605750770 |
| Production relay | READY dpl_3eDB7nymbC7ug9LoeeENwf82JLJ1, staged both GET405/invalidPOST400 then promoted; both public routes retested PASS |
| New release download | Normal CI APK, tag/source archive/SHA256SUMS at [preview release](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.2.0-diplay-preview); exact final source CI/hash/download proof recorded in release notes |

## Runtime smoke scope

CI launches normal APK before instrumentation, waits for actual DiPlay Controller
initialization/WaitingForMfi and UI DiPlay ready/authblocked, records screenshot/XML.
Separate instrumented APK verifies idle decoder not constructed, generated Ed25519
sign/verify and ChaCha20-Poly1305 runtime, native library load/empty-name EINVAL
(no socket/ioctl/radio access), NOP logger, then720p90+Surface frames/reset/stop.
No simulated phone/auth provider shipped. Generic x86_64 emulator decoder is not SPRD.
Source-built ARMv7 library has not been executed on real TS7 in this round.

## Physical evidence / unavailable claims

Existing user report: Issue13#issuecomment-6030102288,SPRD720p~29.85fps8380
TEST_PATTERN frames/7drops/0restarts. Approx4m41 estimated, not5/15minute acceptance.
No new TS7/iPhone run. BT bootstrap/WiFi CarPlay transport/auth/video/audio/touch:
NOT YET VERIFIED. Siri/microphone and wired receiver deferred.
No stolen credentials or proprietary receiver downloaded/copied. Provider unavailable.

## Review

Initial CI normal activity startStatusOK/677ms; screenshot shows DiPlayv0.2Preview
and Waiting for iPhone/authentication blocked/DiPlay ready. Instrumentation:
BC crypto/native empty-name load PASS; OMX.google.h264.decoder720p90+frames,
reset/recovery/stop PASS. Initial exactsource87e9901 and hardened77ef5cb rerun PASS.
Final lifecycle/authentication fixes add plaintext-boundary, writer-lock, stale-callback
and early-audio regressions. Release gates require CI passing the exact final source
before the normal APK is published; release notes record those final runs. Do not infer ARM/phone
runtime from x86_64. Local BT MAC may be masked/unavailable and fails explicitly;
authorized authentication alone does not prove TS7 Bluetooth/hotspot compatibility.

Relay backend source is unchanged from87e9901 (16 tests). CLI upload was stopped
when unexpected build artifacts were selected; .vercelignore added to allow backend
only. Replacement deployment contains9 files/two functions, no SDK/APK/signing files.
No GitHub issue comment created by smoke checks (invalid payload only).
Post-promotion error-log query last15min returned no entries; logging/monitoring/drains
not otherwise audited. Real v0.2 device-to-GitHub positive upload still user verification.
Production secret untouched/server-only. No APK contains that GitHub credential.

Independent upstream wireless source review identified raw auth/WiFi traces, producer
wakeup, interface/binding and service/socket cleanup risks; fixed in this port.
Final new-port independent review identified five authorized-path issues: recovery/write
lock inversion, plaintext RECORD provenance, stale rejection, lost reconnect intent and
one-shot early audio readiness. Corrected in source with bounded regression fixtures;
follow-up review also caught SETUP-before-RECORD eligibility and late UI delivery.
Both corrected and independently rechecked: no remaining P1/P2 blockers in the
bounded code delta. Generated host PairVerify + AEAD SETUP/RECORD and delayed-clear
UI delivery fixtures PASS; SAP completion is explicitly host-test-only. Not an authentication
certification or full upstream security audit.
