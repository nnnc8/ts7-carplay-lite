# Architecture decisions

## ADR-001 — Diagnose before receiver implementation

Status: Accepted

Reason: TLink/ZLink freezing/disconnects can originate in transport, radio/USB drivers, decoder behavior, memory pressure or application logic. Rebuilding a UI without identifying the bottleneck is low-value.

Decision: build a minimal native diagnostic utility first and gate receiver architecture on real TS7 evidence.

## ADR-002 — Android 8.1 / low-memory is the primary target

Status: Accepted

Decision: optimize for API 27-era behavior and a 2 GB head unit. Modern Android convenience APIs are secondary.

## ADR-003 — Wired first

Status: Accepted

Decision: establish a stable wired baseline before wireless. This separates video/decode/application issues from Wi‑Fi/Bluetooth instability.

## ADR-004 — Direct hardware decode path is preferred

Status: Provisional; requires diagnostic confirmation

Preferred pipeline:

`CarPlay transport -> H.264 -> MediaCodec -> Surface`

Avoid frame conversion to Bitmap and CPU copies unless evidence shows the platform requires a fallback.

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
