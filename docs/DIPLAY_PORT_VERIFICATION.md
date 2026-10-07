# DiPlay v0.2 preview verification ledger

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
| Auth/gate/DiPlay | PASS24+30+444 fixtures; fake provider test sources only; tunnel loopback is not radio/phone proof |
| Diagnostic privacy | PASS ReportSanitizerTest; diagnostic code/workflow unchanged |
| Both backend routes | PASS16 unit tests; v0.1/v0.2 fixed13, diagnostic fixed5, privacy/auth deny |
| Normal APK inspect | PASSAPI27/version0.2-alpha/permissions/actualARMv7/DiPlaypresence/no credentials/signature |
| Android8.1 emulator | PASS initial87e9901: normal startup/authgate/BCcrypto/native link/originalSurface/reset/stop; final source rerun pending |
| Hosted CI | PASS initial PR37604670362 / push37604605337; Diagnostic37604670078; final source rerun pending |
| Production relay | READY dpl_3eDB7nymbC7ug9LoeeENwf82JLJ1, staged both GET405/invalidPOST400 then promoted; both public routes retested PASS |
| New release download | PENDING publish/hash/readback |

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
reset/recovery/stop PASS. Initial exactsource87e9901; final hardened source rerun needed.

Relay backend source is unchanged from87e9901 (16 tests). CLI upload was stopped
when unexpected build artifacts were selected; .vercelignore added to allow backend
only. Replacement deployment contains9 files/two functions, no SDK/APK/signing files.
No GitHub issue comment created by smoke checks (invalid payload only).
Post-promotion error-log query last15min returned no entries; logging/monitoring/drains
not otherwise audited. Real v0.2 device-to-GitHub positive upload still user verification.
Production secret untouched/server-only. No APK contains that GitHub credential.

Independent upstream wireless source review identified raw auth/WiFi traces, producer
wakeup, interface/binding and service/socket cleanup risks; fixed in this port.
Final new-port independent review unavailable due reviewer usage limit.
Main-agent implementation review and regression tests run; not a full independent audit.
