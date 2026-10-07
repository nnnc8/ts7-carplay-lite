# Checkpoint before DiPlay pivot — 2026-10-07

User direction changed to a TS7-specific DiPlay Android 8.1 port. The
`feature/lawful-carplay-core` branch preserves unfinished xcertplay research/code.
It is not a release candidate and must not be merged as a complete receiver.

Reusable work: lawful AuthenticationProvider contracts, passive hardware inventory,
API27 Bluetooth/Wi-Fi transport ownership, explicit protocol evidence/epoch gates,
host fixtures, renderer provenance lock, authentication procurement/source research.
No legitimate provider is available; real TS7 authentication hardware is UNKNOWN.

Historical execution evidence before the latest edits: selected Kotlin subset
compiled against API27, normal DEX-only preview packaging/signature/scan passed,
24 provider +30 gate +1520 parser fixtures passed, existing renderer2047 checks,
Diagnostic sanitizer and backend16 checks passed. These are not a validation of
every latest checkpoint edit. Latest adapter/tunnel changes and instrumented APK
remain unverified; source port hashes/patch manifest need reconciliation if resumed.
No API27 emulator/core-session or real iPhone result is claimed.

Known unfinished items: lifecycle cancellation/race review, endpoint/Bonjour deployment,
LOHS session-interface binding, decoded audio, supplier provisioning. Experimental
core build hooks are retained only in this old checkpoint, not made the new strategy.
No external credential, firmware, proprietary APK or private identity is committed.
Published alpha and Diagnostic downloads remain unchanged.

New main line: `feature/diplay-ts7-port`, based on stable main. Copy only needed
provider/transport/research pieces; use a pinned DiPlay-derived source base with
minimal TS7 UI and the preserved MediaCodec-to-Surface renderer.
