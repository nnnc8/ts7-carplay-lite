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
