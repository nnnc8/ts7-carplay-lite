# TS7 CarPlay Lite

**TECHNICAL PREVIEW — NOT YET A FUNCTIONAL CARPLAY RECEIVER.**

專為 TS7、Android 8.1／API 27、32-bit ARM、2 GB RAM 開發的極簡無線 CarPlay receiver。Phase 0 實機報告已收到，現在是 **Phase 1 — Wireless CarPlay minimal receiver / IMPLEMENTATION_IN_PROGRESS**。

目前可做：啟動輕量 UI、觀察 Wi-Fi／Bluetooth、播放自行產生的 H.264 圖樣、量測 MediaCodec → Surface、複製或手動上傳去識別化診斷。

目前不能做：真正連線 iPhone 或播放 CarPlay。合法認證元件尚未取得：`BLOCKED_BY_AUTHENTICATION_REQUIREMENT`。圖樣不是 CarPlay；Wi-Fi 連上或 Bluetooth 開啟也不是 CarPlay session。

## Downloads

- 技術預覽 APK：[下載 `TS7-CarPlay-Lite-v0.1-alpha.apk`](https://github.com/nnnc8/ts7-carplay-lite/releases/download/carplay-v0.1.0-alpha-preview/TS7-CarPlay-Lite-v0.1-alpha.apk) · [Release／checksum／驗證紀錄](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.1.0-alpha-preview)。**還不能連線 iPhone／使用 CarPlay。**
- 保留的診斷工具：[`TS7-Diagnostic-v0.2.apk`](https://github.com/nnnc8/ts7-carplay-lite/releases/download/diagnostic-v0.2.0/TS7-Diagnostic-v0.2.apk)，另有 [repository copy](downloads/TS7-Diagnostic-v0.2.apk)。不會被 receiver APK 覆蓋。

兩者使用測試簽章，僅供 sideload，非 production signing。
Alpha APK 來自通過的 [main CI](https://github.com/nnnc8/ts7-carplay-lite/actions/runs/37496485921)，176624 bytes，SHA-256：`5a2461ce16324b793425aa3fa287840c64f55a9980f95385044839bd252b26da`。API 27 模擬器實際畫面／重置恢復／停止 PASS，不等於 TS7 實機驗證。

## Target evidence

來源：[實機 v0.2 report / Issue #5](https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-6018940228)。數值是該次 snapshot，不代表持續負載結果。

| Item | Captured evidence | Status |
| --- | --- | --- |
| OS / ABI | Android 8.1.0 / API 27 / `armeabi-v7a`, `armv7l` | VERIFIED report |
| Platform | manufacturer `sprd`, board/hardware `sp7731e_1h10` | VERIFIED strings; exact silicon identity UNKNOWN |
| RAM | 2048 MB total; 496 MB available; lowMemory=false | VERIFIED snapshot |
| Display | 1280×720, 160 DPI, approximately 60 Hz | VERIFIED Android real-display report; panel internals UNKNOWN |
| Graphics | OpenGL ES 3.2 | VERIFIED reported version |
| Wi-Fi | connected, 2437 MHz, 65 Mbps, −41 dBm | VERIFIED snapshot; sustained bandwidth UNKNOWN |
| Bluetooth | adapter present/enabled | VERIFIED; iPhone bootstrap UNKNOWN |
| AVC | `OMX.sprd.h264.decoder` enumerated; instantiate PASS | VERIFIED initialization only; sustained decode UNKNOWN |

詳見 [device evidence](docs/DEVICE_TARGET.md)、[status](PROJECT_STATUS.md)、[research](docs/CARPLAY_IMPLEMENTATION_RESEARCH.md)。不把 advertised codec range、chipset 名稱、5 GHz 或 APK 大小當成卡頓原因。

## Real-device alpha test

第一輪只測 decoder／Surface，不測 iPhone 連線：

1. 安裝 `TS7-CarPlay-Lite-v0.1-alpha.apk`。開啟後確認立即看到標題、Settings、Diagnostics；啟動不初始化 decoder。
2. Settings → Developer test mode 設為 ON，再選 Start developer H.264 pattern。
3. 預設 1280×720 @ 30 fps，先跑 5 分鐘再跑 15 分鐘。若不穩，分別重測 Balanced 25 fps、Stability 20 fps。切換 profile 會停止播放，須手動重啟。
4. Diagnostics → Copy diagnostics。記錄 decoderName、measuredFps、queue、drops、RAM、restart，以及 freeze／black screen。按 Upload diagnostics 並確認才公開上傳到固定 [Issue #13](https://github.com/nnnc8/ts7-carplay-lite/issues/13)；離線仍可複製。
5. Stop playback、回到桌面再重開，確認沒有殘留黑屏／decoder 卡死。保留每輪報告；不要在駕駛中操作。

完整 [alpha checklist](docs/REAL_DEVICE_ALPHA_TEST.md) 包含 App opens、iPhone detected、Bluetooth、Wi-Fi、CarPlay session、first video frame、touch、audio、5／15／30 分鐘、freeze、disconnect、RAM trend、reconnect。真正 CarPlay 項目現在一律 **BLOCKED / NOT YET VERIFIED**，不能用圖樣結果代替。

Diagnostic v0.2 仍上傳到 [Issue #5](https://github.com/nnnc8/ts7-carplay-lite/issues/5)，與 receiver #13 分開。無背景上傳／analytics／帳號存取。

## Architecture and priorities

使用者只關心 **wireless CarPlay**。Phase 1 不做 USB wired receiver；[Issue #10](https://github.com/nnnc8/ts7-carplay-lite/issues/10)／[#11](https://github.com/nnnc8/ts7-carplay-lite/issues/11) 保留為 deferred fallback。

```text
future lawful core: Bluetooth bootstrap → Wi-Fi → authenticated CarPlay session
                                                      │
developer-only synthetic H.264 ────────────────────────┤
                                                      ▼
                    bounded encoded queue → MediaCodec → Surface → display
```

影像不經 Bitmap／Canvas／CPU 色彩轉換。Queue 固定 4 slots × 256 KiB，超過 250 ms 或溢位時丟掉過期依賴幀、等待 IDR；另外驗證 SPS／PPS。偏好 `OMX.sprd.h264.decoder`，記錄實際 decoder。1280×720 @ 30／25／20 fps，不預設 1024×600。

無 vendored CarPlay core、MFi identity、TLink／ZLink code。評估五個固定 revision／license 的開源專案，先完成原創 Java shell／renderer；不繞過認證、不複製 vendor keys。[Research](docs/CARPLAY_IMPLEMENTATION_RESEARCH.md)、[architecture](docs/WIRELESS_CARPLAY_ARCHITECTURE.md)、[decisions](docs/ARCHITECTURE_DECISIONS.md)、[notices](THIRD_PARTY_NOTICES.md)。

AudioTrack PCM sink 和 normalized touch boundary 已有實作，真實音訊／touch delivery 未驗證。Siri／microphone 延後，無錄音權限。Recovery 有有限重試／watchdog；TS7 持續解碼和無線 reconnect 尚待驗證。

## Roadmap

0. Diagnostics — COMPLETE (basic report/initialization, not sustained decode)
1. Wireless minimal receiver — CURRENT
2. SPRD H.264 / Surface optimization
3. Audio / microphone / Siri / touch
4. Reconnect / recovery
5. 30-minute stability
6. 60-minute stability
7. TS7 production optimization

[ROADMAP.md](ROADMAP.md)、[BACKLOG.md](docs/BACKLOG.md)。

## Repository / build

```text
receiver/                 Java shell, Surface renderer, generated test input
diagnostic-app/           Preserved Diagnostic v0.2 Activity and privacy tests
diagnostic/               Legacy v0.1 sources
backend/                  Fixed HTTPS relays (#5 and #13), schema tests
docs/                     Evidence, research, architecture, device checklist
downloads/                Preserved diagnostic APKs
.github/workflows/        Independent diagnostic and receiver workflows
```

Use JDK 17, SDK platform 27 / build-tools 35.0.0, Python 3, zip; Node.js 20 for relay tests. Java DEX only: no native library/ABI filter excludes ARMv7.

```sh
./receiver/test.sh
./diagnostic-app/test.sh
npm --prefix backend test
TS7_CARPLAY_UPLOAD_URL=https://ts7-carplay-lite-relay.vercel.app/api/carplay-diagnostics ./receiver/build.sh
```

Output: `dist/TS7-CarPlay-Lite-v0.1-alpha.apk`. [Build guide](receiver/README.md) describes signing and separate API 27 instrumentation. CI builds PR/main, inspects API/permissions/DEX/asset provenance, exercises actual MediaCodec output to Surface. Emulator success is not SPRD/TS7 validation.

Preserved diagnostic: `TS7_DIAGNOSTIC_UPLOAD_URL=https://ts7-carplay-lite-relay.vercel.app/api/diagnostics ./diagnostic-app/build.sh`.

Public repository/Issues/Actions/Releases are the source of truth. Agents read [AGENTS.md](AGENTS.md) and separate implementation from physical-device evidence.
