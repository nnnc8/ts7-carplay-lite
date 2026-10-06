# CarPlay Lite v0.1-alpha real-device testing

This first build is a **TECHNICAL PREVIEW — NOT YET A FUNCTIONAL CARPLAY RECEIVER**.
Its lawful authentication provider is unavailable. The developer pattern is not iPhone video.
Phase 1 is wireless-only. Diagnostic v0.2 and its Issue #5 endpoint stay available.

## Before testing

Park the car. Install `TS7-CarPlay-Lite-v0.1-alpha.apk` alongside TS7 Diagnostic.
Close other projection apps before a later real CarPlay session test.
Open Settings, enable Developer test mode explicitly, and choose Start H.264 pattern.
This local pattern needs no iPhone, account, hotspot or network permission prompt.

## First round

- [ ] App opens with its status UI before codec initialization.
- [ ] Start the developer pattern; record first frame and actual decoder name.
- [ ] Resolution is 1280×720; profile Default is 30 fps.
- [ ] Run 5 minutes, then 15 minutes; copy diagnostics after each.
- [ ] Record measured fps, dropped frames/packets, queue depth and decoder latency.
- [ ] Record available RAM, low-memory state and whether RAM trends downward.
- [ ] Note freeze, black screen, unexpected stop, decoder restart and recovery outcome.
- [ ] Try Balanced (25 fps), then Stability (20 fps), with playback stopped first.
- [ ] Stop, background/foreground, and restart; no idle decoder or runaway worker remains.
- [ ] Turn Wi-Fi off/on; only the Wi-Fi stage changes during the offline pattern.
- [ ] Open system Bluetooth settings; adapter/link observations never claim an iPhone session.
- [ ] Copy diagnostics offline; upload only after reviewing and confirming.
- [ ] Uploaded alpha report goes to this new debugging issue, never Phase 0 Issue #5.

## Later gates (not validated by the test pattern)

- [ ] Authorized authentication hardware/provider integrated and documented.
- [ ] iPhone detected; Bluetooth bootstrap and Wi-Fi stage observed separately.
- [ ] Real authenticated CarPlay session and first iPhone video frame.
- [ ] Actual CarPlay touch down/move/up and media audio.
- [ ] Network loss, keyframe recovery and bounded reconnect (1, 2, 5 seconds).
- [ ] 30 minutes continuous use; classify freeze/disconnect and memory trend.
- [ ] 60 minutes continuous use (later milestone).
- [ ] Siri/microphone (later milestone).

Observed target and initialization-only PASS:
[Issue #5 advanced report](https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-6018940228).
It proves codec creation only, not decoding or sustained operation. Leave Issue #4 open.

Include APK version, mode (`TEST_PATTERN` or real `CARPLAY`), profile, elapsed time,
steps and sanitized counters. Do not include device identifiers, accounts, SSID/BSSID,
MAC/IP addresses, location, media content or credentials.
