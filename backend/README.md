# TS7 explicit diagnostic relays

These Vercel Node.js Serverless Functions accept only strict sanitized reports and create comments on fixed public destinations:

- POST /api/diagnostics: Diagnostic v0.2 → nnnc8/ts7-carplay-lite #5 (preserved).
- POST /api/carplay-diagnostics: CarPlay Lite v0.1-alpha / DiPlay v0.2-alpha → same repository #13.

Production base URL: https://ts7-carplay-lite-relay.vercel.app.

The Android APK never receives a GitHub credential. `GITHUB_TOKEN` exists only as a Vercel environment variable.

## Required deployment secret

Use a fine-grained GitHub PAT restricted to the `nnnc8/ts7-carplay-lite` repository with only Issues **Read and write** permission. Do not grant administration, deletion, workflow, package or organization permissions. Add the token to the Vercel project as the sensitive production variable `GITHUB_TOKEN`; never paste it into source code, chat or an APK build variable.

Only rate-window/max tuning is configurable; owner/repository/issue are hard-coded per server route. Previous destination environment variables have no effect. Neither route accepts client destination fields.

## Production deployment

The existing Vercel project is `ncnc8/ts7-carplay-lite-relay`. Its **Root Directory
must be `backend`**, including for GitHub-triggered deployments. Keep the existing
production `GITHUB_TOKEN` secret; do not copy it into local files or Android builds.

Run Vercel CLI linking/deployment from the **repository root**, not from `backend`,
because Vercel applies the configured root directory itself:

Root .vercelignore allowlists backend only (and excludes env/node_modules); SDK,
compiler jars, APKs and local test signing keys must never be sent in a CLI deployment.
This follows the official [.vercelignore allowlist](https://vercel.com/docs/deployments/vercel-ignore).

```sh
vercel link --scope ncnc8 --project ts7-carplay-lite-relay --yes
vercel deploy --prod --yes --scope ncnc8
```

After any deployment or GitHub push, verify **both** public API routes: GET must
return JSON 405 and an empty-object JSON POST must return JSON 400. These harmless
checks must not create an issue comment. A 404/static page is not a passing check.
Successful alpha device-to-GitHub upload remains a separate real-user test.

During preview publication, the project was incorrectly configured with the
repository root. A later GitHub deployment replaced the working manual relay with
a static deployment, producing 404 for both routes. Setting Root Directory to
`backend` repairs both manual and future GitHub deployments; APK assets are unchanged.

## Local tests

```sh
npm test
```

Tests cover both destinations, 32 KiB, sensitive/unknown fields, Markdown escaping, safe GitHub failure, rate limits and duplicates. Alpha accepts only fixed-code/numeric metrics and newest 200 events, never arbitrary error text, IP/MAC/SSID/accounts/credentials. The current no-auth binary cannot submit a CARPLAY/STREAMING claim.

## Runtime contract

POST with Content-Type: application/json and the route's exact schema. HTTPS, server sanitization, finite GitHub timeout, no-store safe JSON responses. Alpha comments prominently label TECHNICAL PREVIEW / NOT YET A FUNCTIONAL CARPLAY RECEIVER; TEST_PATTERN is synthetic, not iPhone video.

Rate limit defaults to 10 requests per 15 minutes per SHA-256 hashed client address (never written to GitHub/logs). Buckets are capped at 4096. Duplicate window is 24 hours, capped at 2048 entries with expired/oldest eviction. Both are **best-effort process-local warm-instance protections**, not durable/global guarantees across Vercel instances or cold starts; use a deployment edge limiter for production abuse control. No public diagnostic payload logging.

The APK sends only on explicit user confirmation; offline copy works without the relay. GitHub credential remains only in deployment environment, never Android build configuration. Tests use fake placeholder tokens, not production credentials.
