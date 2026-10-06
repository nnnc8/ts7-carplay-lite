# AGENTS.md — TS7 CarPlay Lite Agent Handoff Contract

This repository is intended to be worked on by multiple AI/coding agents. Preserve continuity.

## Read first

Before changing code, read in this order:

1. `README.md`
2. `PROJECT_STATUS.md`
3. `ROADMAP.md`
4. `AGENTS.md`
5. `docs/ARCHITECTURE_DECISIONS.md`
6. the GitHub Issues for the task and the current highest-priority issue
7. the README in the component you are modifying

## Hard constraints

- Target is **TS7 / SL8141E / Android 8.1 / 2 GB RAM**. Do not optimize for modern flagship Android devices at the expense of this target.
- Do not assume the marketing Android version. Use actual diagnostic evidence.
- Never assume unverified hardware behavior.
- Every hardware statement must be marked `VERIFIED`, `OBSERVED`, `HYPOTHESIS`, or `UNKNOWN`.
- `HYPOTHESIS` is not a verified fact. `TARGET` values from a product description are not device measurements.
- Keep runtime memory allocations low.
- Prefer direct `MediaCodec -> Surface` rendering for video.
- Do not introduce WebView or a heavy UI framework without a measured reason.
- Wired stability comes before wireless features.
- Do not silently add telemetry, analytics, account access, or INTERNET permission.
- Do not commit user-private diagnostic data. Redact serials, SSIDs, BSSIDs, IMEI, account names, precise location and similar data.
- Do not reverse engineer or redistribute proprietary TLink/ZLink binaries or proprietary Apple authentication material.
- When reusing open-source code, record repository URL, exact revision and license in `THIRD_PARTY_NOTICES.md` before merging.

## Required handoff behavior

Every meaningful change must also update one of:

- `PROJECT_STATUS.md` for current state / blockers / validated facts.
- `CHANGELOG.md` for user-visible changes.
- `docs/ARCHITECTURE_DECISIONS.md` for architecture choices.

For work tracked in GitHub, also update the corresponding GitHub Issue. Keep the issue, status board and changelog consistent enough for the next agent to resume without guessing.

Do not mark hardware behavior as validated unless it was observed on the real TS7 or backed by a captured diagnostic result.

## Branch / PR convention

`main` means a stable state that is safe for the next agent to use. Do not put unverified experiments directly on `main`.

Use these branch prefixes:

- `agent/<short-task>`
- `diag/<short-task>`
- `carplay/<short-task>`
- `feature/<topic>` for new functionality
- `fix/<short-task>`
- `research/<topic>` for investigations

PR descriptions should state:

- what changed
- why it changed
- whether it was compiled
- whether it was tested on emulator/device
- what remains unverified

Large features and experiments should go through a Pull Request before merging to `main`.

## Definition of done

For code changes:

- Builds from a clean checkout.
- No new unexplained permissions.
- No accidental architecture/API-level increase.
- Update relevant docs/status.
- If an APK changes, bump the component version and add a changelog entry.

## Current highest-priority task

Do not start implementing the full CarPlay receiver yet. First validate **TS7 Diagnostic v0.2** on the real TS7, collect the sanitized report, and use it to choose the decoder and transport path. See `PROJECT_STATUS.md`.
