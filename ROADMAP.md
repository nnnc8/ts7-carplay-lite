# Roadmap

Wireless-only priority supersedes the earlier wired-first plan. Diagnostic v0.2 and USB research remain available but do not gate this Phase 1.

## Phase 0 — Diagnostics — COMPLETE

Captured v0.2 report confirms Android 8.1/API 27/ARMv7/2 GB/1280×720/radios/AVC enumeration and instantiate PASS. Basic gate complete; sustained decoding remains #4, not implicitly passed.

## Phase 1 — Wireless CarPlay minimal receiver — CURRENT

Primary strategy: TS7-specific DiPlay Android8.1 fork (Legacy c8884ad). Existing experimental local authentication explicitly selected 2026-10-08 and implemented; source builds identity-free, opted-in standalone includes runtime inputs. This is not Apple certification or phone acceptance.

v0.2.1 gate has two real TS7 uploads covering12 individualPASS results. Continue toward v1.0 using existing upstream code, without repeated car trips. PR18 OPEN/unmerged; readiness/crypto PASS is not a session.

Lightweight Java/framework shell, lawful-core integration boundary, separate Bluetooth/Wi-Fi/CarPlay states, H.264 → MediaCodec → Surface, telemetry, touch boundary, minimal PCM output and finite recovery.

1280×720 @ 30 fps with 25/20 stability profiles. Prefer OMX.sprd.h264.decoder but record actual fallback. No Compose/WebView or decoded-frame copies.

Current build: **1.0.0-dev experimental standalone**, real iPhone acceptance not yet verified. Generated-H.264 technical preview and identity-free developer build remain separate.

Functional exit: actual wireless bootstrap/phone-confirmed session and first real CarPlay frame on TS7, bounded queue, explicit failures and captured evidence. Partial audio/touch must be labeled. Only the user-selected experimental identity exception is allowed, not proprietary receiver blobs.

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
