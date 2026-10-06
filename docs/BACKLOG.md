# Backlog

This file mirrors the issues that should exist in GitHub. Once the repository is writable, create one GitHub Issue per item and keep issue state synchronized here only when useful for agent handoff.

## GitHub issue seed list

Create or maintain one GitHub Issue for each title below. Keep the labels in sync with the issue purpose.

| Priority | Issue title | Labels |
| --- | --- | --- |
| P0 | Collect first TS7 diagnostic report | `high-priority`, `diagnostic`, `blocked` |
| P0 | Verify H.264 MediaCodec hardware decoder | `high-priority`, `codec`, `hardware` |
| P0 | Determine physical display resolution | `high-priority`, `diagnostic`, `hardware` |
| P0 | Inspect TS7 USB topology | `diagnostic`, `usb`, `hardware` |
| P0 | Inspect Wi-Fi and Bluetooth capabilities | `diagnostic`, `wifi`, `hardware` |
| P1 | Choose wired CarPlay transport implementation | `carplay`, `research`, `feature` |
| P1 | Build minimal H.264 Surface rendering prototype | `carplay`, `codec`, `feature` |
| P1 | Implement performance telemetry | `performance`, `feature` |
| P1 | 30-minute stability test | `performance`, `research` |
| P1 | 60-minute stability test | `performance`, `research` |
| P2 | Wireless CarPlay feasibility investigation | `carplay`, `wifi`, `research` |

The first issue, **Collect first TS7 diagnostic report**, is the current blocker and must carry `high-priority`, `diagnostic`, and `blocked`.

## P0 — Validate Diagnostic v0.1 on real TS7

Acceptance criteria:

- APK installs and launches on Android 8.1 target.
- Full report renders without crash.
- Report can be copied or saved.
- Record any permission/install quirks.

## P0 — Analyze first real-device report

Acceptance criteria:

- Confirm CPU ABI and board strings.
- Confirm actual display resolution / DPI.
- Identify H.264 hardware decoder(s) and usable capability range.
- Identify USB device path used with iPhone connected.
- Record free RAM under normal head-unit load.
- Update `PROJECT_STATUS.md` and `docs/ARCHITECTURE_DECISIONS.md`.

## P1 — Wired CarPlay session proof of concept

Acceptance criteria:

- Establish a wired CarPlay session on the TS7 target.
- Render a basic CarPlay video stream.
- No wireless implementation in this issue.
- Instrument connection state and failure reason.

## P1 — Direct MediaCodec to Surface video pipeline

Acceptance criteria:

- H.264 frames are decoded with the selected MediaCodec implementation.
- Output goes directly to Surface.
- No Bitmap conversion in the normal path.
- Resolution/fps constrained to what the target can sustain.

## P1 — Stability benchmark and freeze classification

Acceptance criteria:

- 30–60 minute wired run.
- Detect transport disconnect vs decoder stall vs UI stall.
- Log frame timing / queue state without excessive allocation.
- Define 30/25/20 fps stability profiles if needed.

## P2 — Wireless CarPlay prototype

Acceptance criteria:

- Start only after wired baseline is stable.
- Characterize Bluetooth bootstrap and Wi‑Fi data path.
- Record latency / disconnect pattern.
- Implement reconnect without requiring full app restart where feasible.
