# TS7 explicit diagnostic relays

These Vercel Node.js Serverless Functions accept only strict sanitized reports and create comments on fixed public destinations:

- POST /api/diagnostics: Diagnostic v0.2 → nnnc8/ts7-carplay-lite #5 (preserved).
- POST /api/carplay-diagnostics: CarPlay Lite v0.1-alpha → same repository #13.

Production base URL: https://ts7-carplay-lite-relay.vercel.app.

The Android APK never receives a GitHub credential. `GITHUB_TOKEN` exists only as a Vercel environment variable.

## Required deployment secret

Use a fine-grained GitHub PAT restricted to the `nnnc8/ts7-carplay-lite` repository with only Issues **Read and write** permission. Do not grant administration, deletion, workflow, package or organization permissions. Add the token to the Vercel project as the sensitive production variable `GITHUB_TOKEN`; never paste it into source code, chat or an APK build variable.

Only rate-window/max tuning is configurable; owner/repository/issue are hard-coded per server route. Previous destination environment variables have no effect. Neither route accepts client destination fields.

## Local tests

```sh
npm test
```

Tests cover both destinations, 32 KiB, sensitive/unknown fields, Markdown escaping, safe GitHub failure, rate limits and duplicates. Alpha accepts only fixed-code/numeric metrics and newest 200 events, never arbitrary error text, IP/MAC/SSID/accounts/credentials. The current no-auth binary cannot submit a CARPLAY/STREAMING claim.

## Runtime contract

POST with Content-Type: application/json and the route's exact schema. HTTPS, server sanitization, finite GitHub timeout, no-store safe JSON responses. Alpha comments prominently label TECHNICAL PREVIEW / NOT YET A FUNCTIONAL CARPLAY RECEIVER; TEST_PATTERN is synthetic, not iPhone video.

Rate limit defaults to 10 requests per 15 minutes per SHA-256 hashed client address (never written to GitHub/logs). Buckets are capped at 4096. Duplicate window is 24 hours, capped at 2048 entries with expired/oldest eviction. Both are **best-effort process-local warm-instance protections**, not durable/global guarantees across Vercel instances or cold starts; use a deployment edge limiter for production abuse control. No public diagnostic payload logging.

The APK sends only on explicit user confirmation; offline copy works without the relay. GitHub credential remains only in deployment environment, never Android build configuration. Tests use fake placeholder tokens, not production credentials.
