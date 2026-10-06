# TS7 Diagnostic relay

This Vercel Node.js Serverless Function accepts only a sanitized v0.2 report and creates a comment on the fixed public destination:

`nnnc8/ts7-carplay-lite` issue `#5`

The Android APK never receives a GitHub credential. `GITHUB_TOKEN` exists only as a Vercel environment variable.

## Required deployment secret

Use a fine-grained GitHub PAT restricted to the `nnnc8/ts7-carplay-lite` repository with only Issues **Read and write** permission. Do not grant administration, deletion, workflow, package or organization permissions. Add the token to the Vercel project as the sensitive production variable `GITHUB_TOKEN`; never paste it into source code, chat or an APK build variable.

The remaining variables are non-secret and are documented in `.env.example`. The handler still uses the fixed owner, repository and issue defaults and never accepts those values from the Android client.

## Local tests

```sh
npm test
```

The tests cover accepted payloads, 32 KB limits, missing/unknown sensitive fields, Markdown escaping, GitHub API failures and duplicate suppression.

## Runtime contract

`POST /api/diagnostics` with `Content-Type: application/json` and the v0.2 report JSON. Production requests must arrive through HTTPS. The relay applies a strict schema, server-side sanitization, rate limiting, a 32 KB request limit, and a 24-hour SHA-256 duplicate window.
