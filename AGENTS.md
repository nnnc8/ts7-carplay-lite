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

- Target is **TS7 / Android 8.1 / API 27 / ARMv7 / 2 GB RAM**. About-screen SL8141E and diagnostic sp7731e_1h10 strings are distinct evidence, not exact silicon proof.
- Do not assume the marketing Android version. Use actual diagnostic evidence.
- Never assume unverified hardware behavior.
- Every hardware statement must be marked `VERIFIED`, `OBSERVED`, `HYPOTHESIS`, or `UNKNOWN`.
- `HYPOTHESIS` is not a verified fact. `TARGET` values from a product description are not device measurements.
- Keep runtime memory allocations low.
- Prefer direct `MediaCodec -> Surface` rendering for video.
- Do not introduce WebView or a heavy UI framework without a measured reason.
- User only cares about wireless CarPlay. Do not spend Phase 1 effort on wired USB unless required as a debugging fallback. Keep #10 and #11 deferred, not deleted.
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

Phase 0 basic report is complete. Current: **Phase 1 — Wireless CarPlay minimal receiver / IMPLEMENTATION_IN_PROGRESS**. Read research, wireless architecture and the captured report linked in PROJECT_STATUS.

Priority: wireless session → video stability → decoder stability → recovery → audio → touch → Siri.

Lawful authentication/core is blocked. Ship only a clearly labeled technical preview of the working renderer until legal integration exists. Never equate synthetic video, paired/enabled adapters, network connection, codec enumeration or instantiate PASS with CarPlay or sustained TS7 decode. Keep #4 open.

Preserve Diagnostic v0.2 workflow/APK and fixed Issue #5 relay. Alpha uploads go to fixed #13 only after explicit confirmation, with codes/counters and no identifiers, arbitrary exception text or credentials. No client destination control.

Primary strategy: TS7-specific DiPlay Android8.1 port on feature/diplay-ts7-port. Preserve feature/lawful-carplay-core checkpoint; xcertplay is reference/fallback only. Read the DiPlay base/module/API27/verification docs.

Inspect actual APK API27, source-built armeabi-v7a JNI, permissions, secrets/proprietary scans, GPL/source notices, generated asset provenance and signature. Source pin c8884adcc75bfda3c134db63877bd6c6f83beb74; no upstream UI/assets/APK or authentication identity. FINE_LOCATION is explicit explained discovery/LOHS opt-in, never GPS/data upload. Keep nine renderer-lock files unchanged. Separate emulator evidence from TS7 evidence in release/handoff.
