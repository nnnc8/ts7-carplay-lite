# Architecture decisions

## ADR-001 — Diagnose before receiver implementation

Status: Accepted

Reason: TLink/ZLink freezing/disconnects can originate in transport, radio/USB drivers, decoder behavior, memory pressure or application logic. Rebuilding a UI without identifying the bottleneck is low-value.

Decision: build a minimal native diagnostic utility first and gate receiver architecture on real TS7 evidence.

## ADR-002 — Android 8.1 / low-memory is the primary target

Status: Accepted

Decision: optimize for API 27-era behavior and a 2 GB head unit. Modern Android convenience APIs are secondary.

## ADR-003 — Wired first

Status: Superseded by ADR-009

Historical decision: establish wired baseline first. User explicitly changed Phase 1 to wireless-only after diagnostic evidence; USB work is now deferred.

## ADR-004 — Direct hardware decode path is preferred

Status: Accepted pipeline; sustained TS7 decoding still unverified

Preferred pipeline:

`CarPlay transport -> H.264 -> MediaCodec -> Surface`

No decoded-frame Bitmap/Canvas/CPU conversion in the normal path. Captured v0.2 vendor codec enumeration/instantiate PASS supports a SPRD-preferred prototype, not proof of sustained playback.

## ADR-005 — No proprietary TLink/ZLink repackaging

Status: Accepted

Decision: do not treat APK slimming/repacking of proprietary TLink/ZLink as the main solution. Build an independent implementation using legally reusable components and documented interfaces.

## ADR-006 — UI before diagnostics

Status: Accepted for Diagnostic v0.2

Reason: v0.1 performed report generation, file I/O, clipboard work and MediaCodec initialization before creating its NativeActivity UI. The real TS7 launched the APK but showed a black content area.

Decision: use a programmatic Java `Activity` and render the status screen immediately. Each probe runs away from the main thread, has a bounded wait, and returns an explicit `PASS`, `FAILED`, `TIMEOUT` or `UNAVAILABLE` result without stopping sibling probes.

## ADR-007 — Safe codec enumeration before decoder initialization

Status: Accepted for Diagnostic v0.2

Decision: basic startup diagnostics may enumerate `video/avc` decoder names and capabilities through `MediaCodecList`, but must not call `MediaCodec.createDecoderByType`. Decoder initialization is an explicit advanced test with a five-second watchdog and must run only after the basic report is available.

## ADR-008 — Server-side public report relay

Status: Accepted for Diagnostic v0.2

Decision: the APK creates a `PublicReport` through an allowlist sanitizer and sends it only after an explicit user confirmation. A Vercel Node.js function validates and sanitizes again, enforces a 32 KB request limit, rate limits and duplicate suppression, escapes Markdown, and writes only a comment to the fixed `nnnc8/ts7-carplay-lite` Issue #5. GitHub credentials exist only in the deployment environment.

## ADR-009 — Wireless-only Phase 1

Status: Accepted, explicit user direction

Phase 0 basic report gate complete. Prioritize wireless session, video, decoder, recovery, audio, touch, then Siri. Keep #10/#11 deferred and available as debugging fallback. Use measured 1280×720/160 DPI/API 27/ARMv7/2 GB, not prior 1024×600 assumptions.

## ADR-010 — Lawful-authentication boundary / technical preview

Status: Historical unavailable-provider decision; source strategy superseded by ADR-014 and user-selected experimental authentication by ADR-015.

Research pinned five upstream projects. Current DiPlay/xcertplay require API 28; legacy fork is API-compatible but documented extracted credentials are not acceptable. No inspected project is a lawful ready-to-ship API 27 receiver for this TS7 with an available auth provider. Choose original Java shell/renderer, not a hand-written full protocol. Future hardware-auth core requires license review, legal provider and API 27 port. Shipping ReceiverCore.Unavailable always refuses connections.

Only release a technical preview after actual H.264 Surface playback is proven and clearly label NOT YET A FUNCTIONAL CARPLAY RECEIVER. No authenticated/STREAMING claim from synthetic input or observed radios.

## ADR-011 — Bounded compressed-video pipeline

Status: Accepted, target runtime validation pending

4 preallocated 256 KiB input slots, 250 ms age bound. Validate SPS/PPS dimensions and IDR; discard dependent stale/overflow frames, request keyframe and resume at IDR. Direct MediaCodec → Surface, record actual decoder and rendered callback fps/latency. 1280×720 @ 30/25/20 fps. Prefer SPRD, permit vendor/system fallback, never label generic emulator output as SPRD verification.

## ADR-012 — Explicit alpha diagnostics, fixed destination

Status: Accepted

500 fixed-code events, newest 200 export, primitive metrics only. No names/MAC/IP/SSID/accounts/location/keys or arbitrary exception text. Explicit copy/upload confirmation. Separate HTTPS /api/carplay-diagnostics with hard-coded Issue #13; /api/diagnostics remains #5. Strict server allowlist, 32 KiB, finite network timeouts and bounded best-effort process-local rate/dedup caches. Client never controls GitHub destination or sees credential.

## ADR-013 — Safe bounded recovery and incomplete media features

Status: Accepted, real-device unverified

One codec worker; finite 1/2/5 s retries, 3 s frame stall watchdog and vendor-call hang detection. Never spawn replacement while prior vendor worker is stuck. Surface teardown/lifecycle must cancel and prevent new rendering, with bounded wait instead of indefinite UI freeze. Unresponsive vendor calls cannot be force-killed safely; contain/report limitation.

AudioTrack PCM sink and normalized single-finger touch are integration boundaries, not verified CarPlay delivery. No microphone permission; Siri postponed. Long-session/reconnect milestones require lawful real-device evidence.

## ADR-014 — TS7-specific DiPlay Android8.1 primary port

Status: Accepted by explicit user direction,2026-10-07.

Preserve old lawful-core branch/checkpoint; main strategy now selected GPL DiPlay Legacy revision c8884adcc75bfda3c134db63877bd6c6f83beb74. CASKA live source inaccessible/currentSHA UNKNOWN. Retain actual Controller wireless/iAP2/AirPlay/network/media code; remove AGPL UI/site, restricted assets, vendor HUD/CAN/ADB/navigation, wired receiver and offline auth loaders. GPL corresponding source and source-built API27 ARMv7 radio JNI required.

Small MediaSink bridge to the nine frozen TS7 renderer/audio/touch/asset files, not a new decoder. LOHS API26 callback and explicit AP/peer binding; permission explained/no GPS. Unavailable external provider stops before radios/listeners. Authenticated media requires real AA05/SAP/pair-verify/encrypted RECORD and accepted/rendered VCL; fixture states or generated test video never qualify. Core compile/runtime/real phone are separate evidence gates. See DiPlay base/module/API27 audit/verification documents.

## ADR-015 — Reuse selected DiPlay experimental local authentication

Status: Accepted by explicit user direction,2026-10-08; supersedes prior blanket prohibition on these two runtime inputs.

Reuse the pinned GPL LocalMfiAuthenticationClient, protocol-major3/P-256/NONEwithECDSA on an already-digested32-byte challenge. Do not invent a second handshake or require new hardware solely because the old provider was deliberately unavailable. Load bounded inputs directly on a startup worker, validate key/certificate consistency, and keep fixed errors/deadlines/no raw logs.

Experimental availability has an exact positive metadata allowlist separate from isAuthorized(). Phone-confirmed AA05/SAP/pair-verify/encrypted RECORD and real rendered frames still gate session/STREAMING. The user-selected official matching v0.2.7 APK provides only two runtime files; they stay outside Git/source/CI, and require an explicit standalone-build opt-in. Source/CI remains identity-free; API27 CI uses fresh generated test identity, never the selected runtime data. Runtime files are not GPL-relicensed, not Apple-certified, and redistribution/future iOS acceptance unresolved.

Keep existing TS7 renderer/media/recovery and privacy boundaries; guide Connect's permission/paired-phone prerequisites. Existing physical platform reports suffice for that gate, not the phone session. Avoid repeat car trips; complete autonomous checks first. See docs/EXPERIMENTAL_AUTHENTICATION.md.

## ADR-016 — Retry only between attempts; native audio focus

Status: Implemented for1.0.0-dev.1; actual phone acceptance pending.

Captured standalone failure6051350325 shows no session/decoder, first failure23ms after Connect and3 retries exhausted13s later. Retire the startup probe's idle controller before explicit Connect. A single in-flight negotiation owns its120s startup deadline;1/2/5s backoff applies only after failure, never repeatedly cancels that negotiation. Stop/success/replacement invalidates pending timers. Preserve finite intent and old-teardown quarantine; don't overlap a stuck controller. Recovery revokes old/failed-partial radio proof and accepts fresh bootstrap/Wi-Fi proof before authentication/rendering. Fixed status/failure codes, never raw messages or credentials. This repairs source defects consistent with the report, not proof that every device blocker is solved.

Use Android's API26 AudioFocusRequest rather than a media library. Wrap the unchanged PCM sink: transient loss pauses/discards live samples, gain restarts cached format, permanent loss/stop invalidates callbacks. No microphone permission, PCM backlog, decoded-frame copy or frozen renderer change. Silent API27 native focus tests are not audible real-iPhone/TS7 proof.
