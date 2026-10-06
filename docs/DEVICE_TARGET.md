# Target device and evidence

Primary: [captured TS7 Diagnostic v0.2 report](https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-6018940228).

| Field | Value | Status |
| --- | --- | --- |
| Family | TS7 head unit | OBSERVED context |
| OS/API | 8.1.0 / 27 | VERIFIED report |
| ABI/arch | armeabi-v7a, armeabi / armv7l | VERIFIED |
| Manufacturer/brand | sprd / SPRD | VERIFIED properties |
| Model | sp7731e_1h10_native | VERIFIED property |
| Board/hardware | sp7731e_1h10 | VERIFIED strings, not exact silicon identification |
| Cores/RAM | 4 logical / 2048 MB | VERIFIED |
| RAM snapshot | 496 MB available, threshold 144 MB, lowMemory=false | VERIFIED snapshot |
| Display | 1280×720 / 160 DPI / 60.0024 Hz / landscape | VERIFIED Android real-display report |
| Data partition | total 28157 MB / available 26158 MB | VERIFIED snapshot |
| Graphics | OpenGL ES 3.2 | VERIFIED reported version |
| Wi-Fi | enabled/connected, 2437 MHz / 65 Mbps / −41 dBm | VERIFIED snapshot, not throughput |
| Bluetooth/BLE | adapter enabled, features present | VERIFIED inventory, not iPhone proof |
| AVC | OMX.sprd.h264.decoder; instantiate PASS, no input decoded | VERIFIED initialization only |
| USB devices | 0 | VERIFIED that instant; not absence of MFi hardware |

## Earlier About-screen evidence

OBSERVED: Quad-SL8141E label, Android 8.1.0, 2+32 GB, MCU Ts7.4.6-100-10-A3A39D-250805, build V12.1.1_20250827.144232_THEME1.

Do not guess a silicon identity to reconcile marketing label and diagnostic board strings. Target measured API/ABI/RAM/display.

## Unknown / next

- Sustained 720p 30/25/20 fps vendor decode, latency/drops/RAM trend, Surface lifecycle.
- Physical panel internals; Android real-display size is the usable target, not teardown proof.
- Radio/chipset/bootstrap under actual wireless load; no 5 GHz requirement inferred from 2.4 GHz snapshot.
- Lawful authentication provider and full wireless session compatibility.
- Real audio/touch/Siri/reconnect.
- USB topology with relevant connection (deferred).

Keep [#4](https://github.com/nnnc8/ts7-carplay-lite/issues/4) open until sustained real-device decoding. Prefer SPRD; log actual selection/fallback.
