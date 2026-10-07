# Roadmap

Wireless-only priority supersedes the earlier wired-first plan. Diagnostic v0.2 and USB research remain available but do not gate this Phase 1.

## Phase 0 — Diagnostics — COMPLETE

Captured v0.2 report confirms Android 8.1/API 27/ARMv7/2 GB/1280×720/radios/AVC enumeration and instantiate PASS. Basic gate complete; sustained decoding remains #4, not implicitly passed.

## Phase 1 — Wireless CarPlay minimal receiver — CURRENT

Primary strategy: TS7-specific DiPlay Android8.1 fork (Legacy c8884ad), not xcertplay-primary. GPL protocol port IN_PROGRESS; external authentication provider BLOCKED. Deliver v0.2 DiPlay Port Preview and prove API27 startup first; actual iPhone bootstrap is a later real-device gate.

Current scoped gate: v0.2.1 TS7 Platform Readiness Preview, 12 independent local platform
probes and sanitized fixed13 upload. Further CarPlay features/authentication hardware work
paused pending physical TS7 evidence. PR18 remains OPEN/unmerged; readiness PASS is not a session.

Lightweight Java/framework shell, lawful-core integration boundary, separate Bluetooth/Wi-Fi/CarPlay states, H.264 → MediaCodec → Surface, telemetry, touch boundary, minimal PCM output and finite recovery.

1280×720 @ 30 fps with 25/20 stability profiles. Prefer OMX.sprd.h264.decoder but record actual fallback. No Compose/WebView or decoded-frame copies.

Current deliverable: generated-H.264 **TECHNICAL PREVIEW / NOT YET A FUNCTIONAL CARPLAY RECEIVER**. Authentication is BLOCKED_BY_AUTHENTICATION_REQUIREMENT. Preview requires an actual working Surface path, not compilation alone.

Functional exit: lawful wireless bootstrap/authenticated session and first real CarPlay frame on TS7, bounded queue, explicit failure classifications and captured evidence. Partial audio/touch must be labeled. No extracted identities/proprietary blobs.

## Phase 2 — SPRD H.264 / Surface optimization

Measure sustained vendor decode, fps/latency/drops/queue/RAM/Surface lifecycle on TS7. Lower fps before adding complexity. No Bitmap/Canvas conversion, shader stack or speculative chipset patch.

Exit: repeatable measured profile with bounded latency; record actual decoder/fallback.

## Phase 3 — Audio / microphone / Siri / touch

Integrate real core PCM/AudioTrack, focus, rates/channels/underruns; prove normalized single-touch delivery. Add microphone/Siri only with a real lawful protocol path, permission decision and device evidence.

Exit: real media audio/touch, then verified Siri without degrading video. Incomplete audio is not called complete.

## Phase 4 — Reconnect / recovery

Prove transport loss vs codec stall vs Surface loss; finite 1/2/5 s budgets, IDR resync, authenticated renegotiation. Adapter/network observation never synthesizes recovery.

Exit: controlled real interruptions recover or stop with explicit reason; no infinite retry or multiplied blocked workers.

## Phase 5 — 30-minute stability

Real wireless video/audio/touch, RAM trend, latency/drops and classified failures. Synthetic playback cannot pass this session gate.

## Phase 6 — 60-minute stability

Repeat realistic load/network/reconnect for 60 minutes. Keep sanitized evidence; emulator success alone does not pass.

## Phase 7 — TS7 production optimization

Measured profiles, minimal settings/lifecycle/startup, production signing/key handling, reproducible artifacts, upstream license obligations and extended real-device evidence.

## Deferred wired work

#10 USB topology / #11 wired transport remain open/deferred. Narrow debugging fallback only, not a Phase 1 USB receiver.
