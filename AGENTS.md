# AGENTS.md — TS7 CarPlay Lite Agent Handoff Contract

## Current instruction — full Fork first (2026-10-08)

Primary receiver development is now https://github.com/nnnc8/ts7-diplay . This
repository is preserved for diagnostics, physical evidence, regression checks and
history. Do not continue a competing Lite receiver or merge PR18. Read
docs/FULL_FORK_HANDOFF.md before treating the older Phase1 instructions below as
current work. Preserve stable main, every historical release and fixed5/fixed13.

The later explicit Full-Fork request supersedes the experimental-identity
exception below for all new work: no extraction or reuse of unauthorized runtime
credentials. Do not access the old ignored identity directories. Public Fork,
CI, APK, source and diagnostics must be identity-free; only legally authorized
external provisioning may use upstream interfaces. Missing provider is
AUTH_BLOCKED. Historical acceptance claims are not real iPhone proof.

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
- Do not reverse engineer or redistribute proprietary TLink/ZLink receiver binaries. On 2026-10-08 the user explicitly selected DiPlay's experimental local authentication and the two runtime inputs from its official v0.2.7 APK, superseding the previous blanket identity prohibition. This is not Apple certification or a legal conclusion. Keep inputs out of Git/source/CI/logs; only an explicitly opted-in standalone APK may contain them.
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

Continue toward v1.0 using existing DiPlay code and necessary TS7 adaptations. Experimental local authentication is implemented separately from authorized MFi; never label it Apple-certified. Missing inputs fail closed. Key consistency, synthetic video, observed radios, platform PASS and codec initialization are not iPhone-session proof. Keep #4 open until its measured stability gate is met.

Preserve Diagnostic v0.2 workflow/APK and fixed Issue #5 relay. Alpha uploads go to fixed #13 only after explicit confirmation, with codes/counters and no identifiers, arbitrary exception text or credentials. No client destination control.

Primary strategy: TS7-specific DiPlay Android8.1 port on feature/diplay-ts7-port. Preserve feature/lawful-carplay-core checkpoint; xcertplay is reference/fallback only. Read the DiPlay base/module/API27/verification docs.

Inspect APK API27/ARMv7, permissions, GitHub/signing-secret scans, GPL notices, runtime provenance and signature. Source pin c8884adcc75bfda3c134db63877bd6c6f83beb74; no upstream UI/icons/vendor receiver binaries. Only two explicitly selected runtime assets may enter standalone builds. FINE_LOCATION is explained discovery/LOHS opt-in, never GPS/upload. Keep nine renderer-lock files unchanged. Separate host, emulator, physical platform and real phone evidence.

The two real TS7 platform uploads are already captured; do not request repeated probes or routine car trips. Complete host/CI work first, then consolidate indispensable phone acceptance. PR18 remains OPEN/unmerged; no merge without explicit authorization. Read docs/EXPERIMENTAL_AUTHENTICATION.md for current build/evidence requirements.
