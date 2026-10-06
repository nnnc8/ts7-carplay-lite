# TS7 CarPlay Lite

**Latest diagnostic APK:** [GitHub Releases](https://github.com/nnnc8/ts7-carplay-lite/releases/latest) · [repository copy](downloads/TS7-Diagnostic-v0.2.apk)

針對低階 Android 車機 **TS7 / SL8141E / Android 8.1 / 2 GB RAM / 32 GB storage** 的極簡 CarPlay Receiver 研究與實作專案。

這個公開 GitHub repository 是本專案的 **single source of truth**；後續 agents 應以此處的程式碼、文件、Issues、Actions 與 Releases 為準。

目前還不是完整 CarPlay Receiver。現在是 **Phase 0 — Diagnostics**：先取得真實 TS7 的硬體、MediaCodec、USB、Wi‑Fi、記憶體與顯示能力，再決定 receiver 架構。

## 1. Project Goal

建立一個只針對低階 TS7 平台、低記憶體配置、可長時間穩定運作的 CarPlay Receiver。專案優先解決可量測的 transport、decoder、Surface、audio 與 recovery 問題，不把「APK 大小」當成 TLink / ZLink 斷線或卡頓的預設原因。

## 2. Target Hardware

| Item | Value | Evidence status |
| --- | --- | --- |
| Platform | TS7 head unit | OBSERVED from device context |
| SoC family | SL8141E / UIS8141E; device showed `Quad-SL8141E` | OBSERVED |
| Android | 8.1.0 / API 27 | OBSERVED from device context; diagnostic confirmation pending |
| RAM | 2 GB target | TARGET, diagnostic confirmation pending |
| Storage | 32 GB target | TARGET, diagnostic confirmation pending |
| MCU | `Ts7.4.6-100-10-A3A39D-250805` | OBSERVED |
| System build | `V12.1.1_20250827.144232_THEME1` | OBSERVED |

詳細紀錄：[`docs/DEVICE_TARGET.md`](docs/DEVICE_TARGET.md)。未經實機報告確認的項目不得當成 VERIFIED hardware fact。

## 3. Current Status

**Phase 0 — Hardware / codec diagnostics**

已完成：

- `TS7 Diagnostic v0.1` APK
- `TS7 Diagnostic v0.2` reliability redesign with a normal Android `Activity`
- Android 8.1 / 32-bit ARM 專用 Android framework 診斷工具
- Background-isolated CPU / ABI、RAM、儲存空間、解析度 / DPI、OpenGL ES、Wi‑Fi、Bluetooth、USB 與 H.264 / AVC MediaCodec 探測
- Sanitized public report copy/save and explicit HTTPS upload relay for Issue #5
- Backend schema/rate-limit/duplicate tests and a reproducible Android build script with GitHub Actions

尚待完成：

1. 在目標 TS7 安裝並執行 v0.2 診斷 APK，確認啟動後先出現 UI。
2. 在有線 CarPlay 與必要時無線 CarPlay 的平常狀態下保存完整報告。
3. 由使用者確認後，將 sanitized public report 上傳到 Issue #5。
4. 依 decoder、USB、Wi‑Fi 與 RAM 結果決定 CarPlay Lite v0.1 pipeline。

完整進度見 [`PROJECT_STATUS.md`](PROJECT_STATUS.md)，階段見 [`ROADMAP.md`](ROADMAP.md)，待辦與 GitHub Issue 種子見 [`docs/BACKLOG.md`](docs/BACKLOG.md)。

## 4. Current Diagnostic APK

- [Latest diagnostic APK — GitHub Releases](https://github.com/nnnc8/ts7-carplay-lite/releases/latest)
- [Repository copy — `TS7-Diagnostic-v0.2.apk`](downloads/TS7-Diagnostic-v0.2.apk)

這是 sideload 測試 APK，使用本地測試憑證簽署；v0.2 的 UI、probe timeout 與實機上傳流程仍待目標 TS7 驗證。

## 5. How to install APK

1. 從 Releases 下載 `TS7-Diagnostic-v0.2.apk`。
2. 將 APK 複製到 TS7，允許這一次的未知來源安裝，或在已開啟 USB debugging 時執行：

   ```sh
   adb install -r TS7-Diagnostic-v0.1.apk
   ```

3. 開啟 `TS7 Diagnostic`。啟動後應先看到 UI，再逐項看到 probe 狀態。若要檢查 USB CarPlay，請先接上平常使用的 iPhone 與 USB 線；若要檢查無線狀態，維持平常的 Wi‑Fi / Bluetooth 狀態。

## 6. How to submit diagnostic results

1. 等待基本診斷完成；按 `Save local report` 保存較完整的本機報告，或按 `Copy public report` 複製已去識別化內容。
2. 車機有網路時，只有在使用者按下 `Upload report to GitHub` 並確認 Dialog 後，才會透過 HTTPS relay 寫入 [Issue #5](https://github.com/nnnc8/ts7-carplay-lite/issues/5)。
3. 若沒有網路，報告仍可本機保存或複製；不會背景自動上傳。
4. 同時註明 APK 版本、測試時的有線 / 無線狀態，以及是否發生 freeze、black screen 或 disconnect。

## 7. Architecture direction

先定位完整路徑，不預設問題是 APK 大小：

```text
iPhone
  → USB / Wi‑Fi transport
  → CarPlay protocol
  → H.264 stream
  → MediaCodec
  → Surface
  → Display
```

未來 CarPlay Lite 的正常影像路徑目標是：

```text
MediaCodec → Surface
```

避免：

```text
MediaCodec → Bitmap → CPU conversion → UI
```

初期限制：有線優先、原生面板解析度（預期先驗證 `1024×600`）、H.264、30 fps；必要時提供 25 fps 與 20 fps stability mode。完整決策見 [`docs/ARCHITECTURE_DECISIONS.md`](docs/ARCHITECTURE_DECISIONS.md)。

## 8. Roadmap

1. Phase 0 — Diagnostics
2. Phase 1 — Wired CarPlay minimal prototype
3. Phase 2 — H.264 decode optimization and performance telemetry
4. Phase 3 — Audio / microphone / Siri
5. Phase 4 — Touch input
6. Phase 5 — Long-term stability
7. Phase 6 — Wireless CarPlay
8. Phase 7 — TS7 production optimization

第一個真正 prototype 以 Android 8.1 / API 27、wired first、no animations、no WebView、minimal allocations、Surface rendering、硬體 H.264（若實機證實可用）為原則。

## 9. Repository structure

```text
diagnostic/                  Native diagnostic source and build tools
diagnostic-app/              Android Activity v0.2 app and privacy tests
backend/                     HTTPS server-side GitHub relay and tests
docs/                        Target, architecture, report guide, backlog
downloads/                   Current sideload APK
reports/                     Local-only sanitized report staging area
.github/workflows/            Build and artifact workflow
.github/ISSUE_TEMPLATE/       Bug and diagnostic report forms
```

## 10. For AI / coding agents

開始工作前依序讀：`README.md`、`PROJECT_STATUS.md`、`ROADMAP.md`、`AGENTS.md`、`docs/ARCHITECTURE_DECISIONS.md`，再讀 GitHub Issues。硬體結論必須標記 `VERIFIED`、`OBSERVED`、`HYPOTHESIS` 或 `UNKNOWN`；不得把 hypothesis 寫成 verified fact。重要變更要同步更新狀態、changelog 與對應 Issue。

## 11. Build instructions

v0.2 需求：Android SDK（platform、build-tools）、JDK 8+、`javac`、`python3`、`zip`、`keytool`。若要執行 relay tests，另需 Node.js 18+。

在 repository root 執行：

```sh
./diagnostic-app/test.sh
TS7_DIAGNOSTIC_UPLOAD_URL=https://<deployment-domain>/api/diagnostics ./diagnostic-app/build.sh
npm --prefix backend test
```

輸出為 `dist/TS7-Diagnostic-v0.2.apk`。本地測試憑證會產生在 ignored `build/` 內；不要把正式 release signing key 或 GitHub credential 放進 repository。v0.1 的 NativeActivity build script 保留在 `diagnostic/scripts/build.sh` 作為歷史重現用途。

## 12. Known limitations

- v0.1 在真實 TS7 上可安裝、可啟動，但已觀察到啟動後黑屏；v0.2 尚未在真實 TS7 驗證。
- 尚未驗證 v0.2 的實機 UI、probe timeout、H.264 advanced test 或 HTTPS upload。
- Display resolution、CPU ABI、實際 RAM、H.264 decoder、USB topology、Wi‑Fi chipset/behavior 仍有 UNKNOWN 項目。
- 目前沒有 CarPlay protocol、audio、microphone、touch 或 wireless receiver implementation。
- APK 使用測試憑證，不是 production signing。
- v0.2 的 `INTERNET` permission 只用於使用者主動觸發的 sanitized report upload；沒有 background telemetry 或 analytics。

## 13. Releases

- [GitHub Releases](https://github.com/nnnc8/ts7-carplay-lite/releases/latest)
- [`diagnostic-v0.2.0` — TS7 Diagnostic v0.2](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/diagnostic-v0.2.0)
- [`diagnostic-v0.1.0` — TS7 Diagnostic v0.1](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/diagnostic-v0.1.0)
- [Direct v0.2 APK download](https://github.com/nnnc8/ts7-carplay-lite/releases/download/diagnostic-v0.2.0/TS7-Diagnostic-v0.2.apk)

## 14. Diagnostic upload relay

The v0.2 app uses the public endpoint [`https://ts7-carplay-lite-relay.vercel.app/api/diagnostics`](https://ts7-carplay-lite-relay.vercel.app/api/diagnostics) only after the user confirms an upload. The Vercel function validates and sanitizes the report again, then writes a comment only to [Issue #5](https://github.com/nnnc8/ts7-carplay-lite/issues/5).

The relay is deployed, but its production `GITHUB_TOKEN` is intentionally not stored in this repository or APK. Before expecting a successful GitHub upload, configure Vercel `GITHUB_TOKEN` with a fine-grained PAT limited to this repository and Issues **Read and write** permission. See [`backend/README.md`](backend/README.md).

## 安全與授權

`TS7 Diagnostic v0.2` 只會在使用者主動確認後，上傳經過本機與 server-side sanitizer 的硬體診斷資料；不含 GitHub token、帳號或 analytics，也沒有 background telemetry。整個 repository 尚未授予統一開源 license；`diagnostic/native/jni.h` 保留 OpenJDK 原始版權與授權聲明。未來整合 DiPlay 或其他 CarPlay receiver code 前，必須先記錄 upstream URL、exact revision、license 與 attribution obligations。
