# TS7 CarPlay Lite

**v1.0 development — experimental authentication; real iPhone session not yet verified.**

主線：**TS7-specific DiPlay Android8.1 port**，Phase1 IN_PROGRESS。
已接回固定 DiPlay Legacy 的現成本機認證與無線連線，保留 TS7 已驗證的顯示路徑。
使用者於 2026-10-08 明確選用上游實驗性認證，不必先購買額外認證硬體。
資料可用不代表 iPhone 已信任、CarPlay 已連線或 Apple 官方認證；PR #18 不合併。

## Downloads / install

新版：[直接下載 v1.0 Development standalone APK](https://github.com/nnnc8/ts7-carplay-lite/releases/download/carplay-v1.0.0-dev-standalone/TS7-CarPlay-Lite-DiPlay-v1.0.0-dev-standalone.apk)（含明確選用的實驗性認證）。
[預發佈／對應GPL原始碼／檢查碼與完整驗證](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v1.0.0-dev-standalone)。
[來源、最短操作、建置及驗證範圍](docs/EXPERIMENTAL_AUTHENTICATION.md)。
保留：[v0.2.1 平台檢查 APK](https://github.com/nnnc8/ts7-carplay-lite/releases/download/carplay-v0.2.1-platform-preview/TS7-CarPlay-Lite-DiPlay-v0.2.1-platform.apk)。
[TS7 Platform Readiness 操作與驗證範圍](docs/PLATFORM_READINESS.md)／
[v0.2.1 prerelease](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.2.1-platform-preview)。
保留 v0.2 DiPlay port：
[v0.2 verification](docs/DIPLAY_PORT_VERIFICATION.md)／
[TS7 CarPlay Lite v0.2 Alpha — DiPlay Port Preview](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.2.0-diplay-preview)，
對應 GPL source、patches、
完整 notices/build scripts 隨同 release 提供；該歷史版本不含認證資料。

standalone 啟動應顯示「TS7 CarPlay Lite · v1.0 Development」及
「experimental identity ready · DiPlay ready」。等待畫面中央沒有影片是正常。
Settings → Connect iPhone 引導必要的 discovery 權限及已配對 iPhone 選擇。
不含 identity 的 source/CI 安裝包仍顯示 authentication blocked，不會假成功。
不要求重跑已上傳的平台檢查或反覆上車；實際 iPhone 接受與媒體仍須獨立證據。

保留：
[v0.1-alpha APK](https://github.com/nnnc8/ts7-carplay-lite/releases/download/carplay-v0.1.0-alpha-preview/TS7-CarPlay-Lite-v0.1-alpha.apk)
／[Diagnostic v0.2 APK](https://github.com/nnnc8/ts7-carplay-lite/releases/download/diagnostic-v0.2.0/TS7-Diagnostic-v0.2.apk)。
均測試簽章、sideload用，非production signing。不同簽章可能需先移除舊receiver
（本機設定會清除）；Diagnostic App獨立，不會被receiver取代。

## Evidence — real TS7, not emulator

[使用者上傳 #13](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6030102288)：
OMX.sprd.h264.decoder、1280×720、約29.85fps、8380 rendered frames、
7drops、queue0、0restarts、RAM455MB。約4m41s為frames/fps估算，
不是連續計時或5/15分鐘驗收；7drops不是WiFi封包遺失率。
這是 **TEST_PATTERN / synthetic H264**，不是CarPlay/auth成功。

Android8.1/API27/ARMv7/2GB/1280×720/160DPI由
[實機Diagnostic report](https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-6018940228)驗證。
sprd/SPRD、sp7731e_1h10為VERIFIED字串；About SL8141E為OBSERVED，
精確silicon UNKNOWN。兩次實機平台報告已覆蓋12項各自PASS，
不是同一輪12項全PASS或熱點/client Wi-Fi共存證明。AP channel/peer流量/SELinux、
真正iPhone/video/audio/touch/reconnect仍未驗證，Issue4/15保持OPEN。
[平台結果1](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6050562144)／
[平台結果2](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6050623491)。

## Port / architecture

選用 [DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android)
固定 c8884adcc75bfda3c134db63877bd6c6f83beb74，GPL3核心。
CASKA當前source存取未證實、exactSHA UNKNOWN，未混入。
原feature/lawful-carplay-core checkpoint801d99e保留；xcertplay只作reference/fallback。
[Base audit](docs/DIPLAY_BASE_SELECTION.md) /
[module map](docs/DIPLAY_MODULE_MAP.md) /
[API27 audit](docs/DIPLAY_API27_PORT.md)。

Actual DiPlay Controller → Bluetooth RFCOMM/iAP2 → LOHS/Bonjour →
verified encrypted AirPlay → MediaSink adapter → 原本TS7 bounded queue →
MediaCodec → Surface。無Compose/WebView/Bitmap/Canvas/RGB pixel copy。
九個renderer/audio/touch/asset檔保持byte-identical；預設720p30，25/20保留。
缺少有效provider時不啟動radio/listener，只初始化到WaitingForMfi。
standalone 使用上游 P-256 本機認證；真實 AA05/SAP/pair-verify/encrypted RECORD 與顯示畫格才可報成功。

移除BYD/CASKA品牌UI/HUD/CAN/導航/ADB、AGPL UI/site、vendor services/assets、
wired USB/NCM接收器、MFi/I2C掃描及raw capture/log。
無TLink/ZLink/Carlinkit接收器binary或fake authentication；只有已選用的兩個實驗性認證asset。
資料不因GPL原始碼而重新授權，公開散布與未來iOS接受仍未確認。
[Source provenance/patches](third_party/diplay-base/SOURCE_MANIFEST.json) /
[licenses](THIRD_PARTY_NOTICES.md)。

目前接H264 main screen、LPCM44.1/48k mono/stereo和normalized single touch；
fixture測試不代表實機iPhone/audio/touch。Siri/麥克風延後，無錄音權限。
API27 discovery/LOHS location權限只在Settings明確選擇並說明後請求；
不收集GPS/location，不上傳名稱/MAC/IP/SSID/BSSID/cert等資料。
Android系統可能要求Location開關，不能將其當GPS功能。

## Diagnostics / optional renderer check

Settings → Developer test mode → Start H264 pattern，顯示TEST_PATTERN，不是CarPlay。
保留既有實機證據；這是可選工程工具，不要求重複5/15分鐘測試。勿開車操作。
[Full checklist](docs/REAL_DEVICE_ALPHA_TEST.md)。
Copy離線可用；Upload只有明確確認後傳固定[#13](https://github.com/nnnc8/ts7-carplay-lite/issues/13)，
500event ring/latest200、固定碼/數值、無識別資料或任意exception文字。
Diagnostic v0.2仍傳固定[#5](https://github.com/nnnc8/ts7-carplay-lite/issues/5)。
GitHub token只有server-side，無analytics或background upload。

## Build / source distribution

JDK17、Python3、zip、Node20；SDK API27/build-tools35.0.0/NDK25.2.9519653。
Pinned官方Kotlin2.2.10/BC1.79/JmDNS3.6.3/SLF4J2.0.7經digest check。
Native radio JNI由source編ARMv7及x86_64，不是預編vendor blob。

```sh
./receiver/test.sh
./diagnostic-app/test.sh
npm --prefix backend test
TS7_CARPLAY_UPLOAD_URL=https://ts7-carplay-lite-relay.vercel.app/api/carplay-diagnostics ./receiver/build.sh
./diplay-port/test.sh
python3 diplay-port/tools/verify_source.py
```

Output: dist/TS7-CarPlay-Lite-DiPlay-v1.0.0-dev.apk（不含identity）。
standalone 的明確選用建置方式見認證文件；runtime資料不進Git/原始碼包/CI。
[Receiver build](receiver/README.md) / [port build](diplay-port/README.md)。
CI獨立驗證normal API27啟動、authblocked、crypto/JNI、原Surface90+frames/reset/stop，
並驗證12項readiness實際API、權限隔離、生成測試identity的P-256簽章及Android→backend公開JSON契約，
並保留Diagnostic CI。Emulator不是SPRD/TS7硬體驗證。
[Project status](PROJECT_STATUS.md)、[verification ledger](docs/DIPLAY_PORT_VERIFICATION.md)、
[roadmap](ROADMAP.md)、[agent handoff](AGENTS.md)。
Issues10/11保持DEFERRED；functional phase須真實phone-confirmed session，實驗性認證不稱Apple-certified。
