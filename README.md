# TS7 CarPlay Lite

**Latest diagnostic APK:** [GitHub Releases](https://github.com/nnnc8/ts7-carplay-lite/releases/latest) · [repository copy](downloads/TS7-Diagnostic-v0.1.apk)

針對低階 Android 車機 **TS7 / SL8141E / Android 8.1 / 2 GB RAM / 32 GB storage** 的極簡 CarPlay Receiver 研究與實作專案。

這個 private GitHub repository 是本專案的 **single source of truth**；後續 agents 應以此處的程式碼、文件、Issues、Actions 與 Releases 為準。

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
- Android 8.1 / 32-bit ARM 專用 NativeActivity 診斷工具
- CPU / ABI、RAM、儲存空間、解析度 / DPI、OpenGL ES、Wi‑Fi、Bluetooth、USB 與 H.264 / AVC MediaCodec 探測
- 不要求 INTERNET 權限，不上傳資料
- 可重現的命令列 build script 與 GitHub Actions workflow

尚待完成：

1. 在目標 TS7 安裝並執行診斷 APK。
2. 在有線 CarPlay 與必要時無線 CarPlay 的平常狀態下保存完整報告。
3. 依 decoder、USB、Wi‑Fi 與 RAM 結果決定 CarPlay Lite v0.1 pipeline。

完整進度見 [`PROJECT_STATUS.md`](PROJECT_STATUS.md)，階段見 [`ROADMAP.md`](ROADMAP.md)，待辦與 GitHub Issue 種子見 [`docs/BACKLOG.md`](docs/BACKLOG.md)。

## 4. Current Diagnostic APK

- [Latest diagnostic APK — GitHub Releases](https://github.com/nnnc8/ts7-carplay-lite/releases/latest)
- [Repository copy — `TS7-Diagnostic-v0.1.apk`](downloads/TS7-Diagnostic-v0.1.apk)

這是 sideload 測試 APK，使用本地測試憑證簽署；目前尚未在目標 TS7 實機驗證。

## 5. How to install APK

1. 從 Releases 下載 `TS7-Diagnostic-v0.1.apk`。
2. 將 APK 複製到 TS7，允許這一次的未知來源安裝，或在已開啟 USB debugging 時執行：

   ```sh
   adb install -r TS7-Diagnostic-v0.1.apk
   ```

3. 開啟 `TS7 Diagnostic`。若要檢查 USB CarPlay，請先接上平常使用的 iPhone 與 USB 線；若要檢查無線狀態，維持平常的 Wi‑Fi / Bluetooth 狀態。

## 6. How to submit diagnostic results

1. 等待報告完整顯示；工具會複製到剪貼簿並嘗試儲存為 `Download/TS7-Diagnostic.txt`。
2. 提交前移除 IMEI、序號、SSID、BSSID、帳號名稱與其他私人識別資訊。
3. 使用 GitHub 的 [diagnostic result issue template](https://github.com/nnnc8/ts7-carplay-lite/issues/new?template=diagnostic-result.yml)，或把完整的 sanitized report 貼回開發對話。
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
docs/                        Target, architecture, report guide, backlog
downloads/                   Current sideload APK
reports/                     Local-only sanitized report staging area
.github/workflows/            Build and artifact workflow
.github/ISSUE_TEMPLATE/       Bug and diagnostic report forms
```

## 10. For AI / coding agents

開始工作前依序讀：`README.md`、`PROJECT_STATUS.md`、`ROADMAP.md`、`AGENTS.md`、`docs/ARCHITECTURE_DECISIONS.md`，再讀 GitHub Issues。硬體結論必須標記 `VERIFIED`、`OBSERVED`、`HYPOTHESIS` 或 `UNKNOWN`；不得把 hypothesis 寫成 verified fact。重要變更要同步更新狀態、changelog 與對應 Issue。

## 11. Build instructions

需求：`python3`、支援 Android ARM target 的 `clang`、`ld.lld`、`zip`、`keytool`、`jarsigner`。

在 repository root 執行：

```sh
./diagnostic/scripts/build.sh
```

輸出為 `dist/TS7-Diagnostic-v0.1.apk`。本地測試憑證會產生在 ignored `build/` 內；不要把正式 release signing key 放進 repository。macOS 若 `ld.lld` 不在 PATH，可安裝 LLVM/LLD 後重試；Ubuntu CI workflow 會自行安裝 `clang` 與 `lld`。

## 12. Known limitations

- 尚未在真實 TS7 上執行診斷或驗證 CarPlay session。
- Display resolution、CPU ABI、實際 RAM、H.264 decoder、USB topology、Wi‑Fi chipset/behavior 仍有 UNKNOWN 項目。
- 目前沒有 CarPlay protocol、audio、microphone、touch 或 wireless receiver implementation。
- APK 使用測試憑證，不是 production signing。
- `WRITE_EXTERNAL_STORAGE`、Bluetooth、Wi‑Fi 等舊 Android 權限只用於診斷；工具不含 INTERNET permission。

## 13. Releases

- [GitHub Releases](https://github.com/nnnc8/ts7-carplay-lite/releases/latest)
- [`diagnostic-v0.1.0` — TS7 Diagnostic v0.1](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/diagnostic-v0.1.0)
- [Direct APK download](https://github.com/nnnc8/ts7-carplay-lite/releases/download/diagnostic-v0.1.0/TS7-Diagnostic-v0.1.apk)

## 安全與授權

`TS7 Diagnostic` 不會上傳裝置資訊，也不含帳號或 analytics。提交報告前仍須自行檢查私人資料。整個 repository 尚未授予統一開源 license；`diagnostic/native/jni.h` 保留 OpenJDK 原始版權與授權聲明。未來整合 DiPlay 或其他 CarPlay receiver code 前，必須先記錄 upstream URL、exact revision、license 與 attribution obligations。
