# Backlog / handoff

Phase 0 basic gate complete; Phase 1 wireless current. GitHub issue state is authoritative; this file records priorities, not automatic acceptance.

| Priority | Tracking | Remaining acceptance |
| --- | --- | --- |
| COMPLETE basic gate | #5 diagnostic | Captured report/instantiate; upload preserved |
| Evidence captured | #8 display | 1280×720 / 160 DPI / ~60 Hz; panel internals not claimed |
| P0 OPEN | #4 decoder | Sustained real TS7 30/25/20 fps, not enumeration/instantiate |
| P0 | Wireless alpha | Lawful API 27/ARMv7 core/auth; no vendor keys/proprietary blobs |
| P0 | #9 Surface | API 27 actual output plus TS7 5/15-min, bounded queue and stop/restart |
| P0 | #13 device alpha test | UI/pattern/decoder/fps/drops/RAM/copy/explicit upload/failures |
| P1 | Wireless state machine | Real bootstrap/Wi-Fi/auth/frame proof, guarded callbacks, no fake STREAMING |
| P1 | Recovery | Real network/decoder loss, finite 1/2/5s, IDR resync, no multiplied stuck workers |
| P1 | #1 / #3 radios/research | Baseline captured; usable lawful session still unproven |
| P1 | #2 telemetry | Codes/counters only; no identifiers/automatic uploads |
| P2 | Audio/touch/Siri | Real PCM/touch first; permission-gated Siri later |
| P2 | #7 30-min / #6 60-min | Real wireless session, RAM trend/classified failures |
| DEFERRED | #10 USB / #11 wired | Keep open; debugging fallback only |

## Publication gate

Technical preview: working H.264 Surface renderer, documented architecture, passing build/CI/privacy/APK checks and explicit **NOT YET A FUNCTIONAL CARPLAY RECEIVER** authentication blocker.

Functional CarPlay tag requires a lawful real wireless session and first real video frame. Never close #4/long-session gates from advertised ranges, instantiate or emulator output; keep incomplete audio/touch/reconnect visible.
