# TS7 Platform Readiness Preview v0.2.1

**NOT YET A FUNCTIONAL CARPLAY RECEIVER.** PR #18 stays OPEN/unmerged.
Historical v0.2.1 instructions retained below. The2026-10-08 user-approved experimental route now continues toward v1.0; two physical uploads already cover12 individualPASS results. Do not request a repeat run. See [current authentication/build evidence](EXPERIMENTAL_AUTHENTICATION.md).
This is a user-triggered platform test, not a wireless session or an authentication attempt.

## Download / operate on TS7

[Normal APK](https://github.com/nnnc8/ts7-carplay-lite/releases/download/carplay-v0.2.1-platform-preview/TS7-CarPlay-Lite-DiPlay-v0.2.1-platform.apk)
and [prerelease / corresponding GPL source / checksums](https://github.com/nnnc8/ts7-carplay-lite/releases/tag/carplay-v0.2.1-platform-preview).
VersionName `0.2.1-platform`, versionCode `3`, min/target API27.
Test signing only; different CI builds may require uninstall/reinstall, losing receiver preferences.
Diagnostic v0.2 is a different app and is not replaced.

1. Park safely; no driving-time testing. Install the normal APK, without an iPhone attached.
2. Expect `TS7 CarPlay Lite · v0.2.1 Platform Preview` and `authentication blocked · DiPlay ready`.
   Empty central Surface in idle mode is expected, not a failed CarPlay video stream.
3. For the full hotspot check, optionally grant Settings → Wireless discovery permission.
   Android8.1 may also require the system Location switch. The app never reads GPS/location.
   Without permission, that probe is PERMISSION_DENIED and the other checks still run.
   Enable Bluetooth yourself if RFCOMM should be tested; the app does not enable it automatically.
4. Settings → Developer → Test platform readiness → read the warning → Run tests.
   Active H.264 pattern playback stops. Hotspot start/close may briefly interrupt Wi-Fi;
   no phone connects, no app-level packets are sent/received, no CarPlay service is advertised.
5. Read all 12 status/duration/error-code rows; `Authentication BLOCKED (expected)` remains.
   A normal run is usually short, with up to 5 seconds per probe (roughly 60 seconds worst-case).
   Close/back/background cancels unfinished probes, not completed results.
6. Copy report offline, or restore Internet and choose Upload to Issue #13 → explicitly confirm.
   The latest results can also be viewed under Developer → View platform readiness.
   Results are process-local, not saved automatically: copy/upload before leaving or force-stopping.
7. Share the resulting [Issue #13 comment](https://github.com/nnnc8/ts7-carplay-lite/issues/13).
   Do not upload screenshots/system logs containing names, SSIDs, addresses or credentials.

If a vendor call ignores cancellation, never returns a hotspot callback, or any resource release
throws, subsequent probes
still run, but another full run is blocked while the old resources may be outstanding.
This guard survives Activity recreation. Failed cleanup quarantines the process and retains a
bounded number of owned handles without inspecting/serializing them; no in-app reset bypass.
Even a late close failure after a timeout keeps the quarantine, while the captured timeout result
remains unchanged. Save results first, then Android Settings → Apps →
TS7 CarPlay Lite → Force stop before retrying. Reopening the Activity alone is not a reset.
Java interruption is not a promise to forcibly terminate a stuck vendor/native call.

## Implemented probes and limits

| Public key | Actual operation | What it does not prove |
| --- | --- | --- |
| coreInitialization | Construct/initialize the actual DiPlay core with unavailable provider, then close | No credential read, authentication or phone bootstrap |
| jni | On 32-bit ARMv7, source-built JNI load plus empty-interface native linkage check | x86 is NOT_TESTED/ABI_NOT_ARMV7; no radio ioctl or chipset probe |
| bluetoothApi | Get Bluetooth adapter and access its state API | No identity, pairing, scanning or radio enable |
| rfcomm | Create generic insecure RFCOMM server socket, immediately close | Never accept/connect; no iAP2/CarPlay UUID or Bluetooth authentication |
| localOnlyHotspot | API26+ actual start callback, immediately close temporary reservation | No getWifiConfiguration/SSID/password; no AP traffic, iPhone or channel-quality proof |
| multicast | Acquire/check/release a temporary non-reference-counted multicast lock | Cannot prove over-air multicast delivery/filter behavior |
| mdns | Create reusable UDP multicast socket, bind port5353, close | No Bonjour advertisement/discovery, peer names or remote reachability |
| tcpBind | Bind/listen loopback ephemeral TCP server, close without accept | Not a CarPlay AP/peer transport test |
| udpBind | Bind loopback ephemeral UDP socket, close | No receive/send or remote peer evidence |
| networkBinding | Bind unconnected TCP and UDP sockets to the actual active Network, close | No process network change, DNS/connect or CarPlay AP selection |
| surface | Check the actual Activity-created SurfaceHolder Surface is valid | No decoder or physical display/frame test; borrowed Surface is not released |
| audioTrack | Create PCM16/stereo/48k AudioTrack, check initialized state, release | Never play/write/focus/microphone; no TS7 speaker or phone-audio proof |

LocalOnlyHotspot is more than an API-symbol lookup: PASS requires an actual reservation
and successful close. Other probes remain independent if it is denied, unavailable, fails or times out.
A late hotspot reservation is still closed on its dedicated cleanup thread.
No TLink/ZLink/Carlinkit content, extracted MFi material, certificates or private keys are read.

## Fixed public contract

The existing schemaVersion1 / reportType`carplay-alpha` envelope has appVersion`0.2.1-platform`.
`platformReadiness` contains exactly the 12 keys above. Each result contains exactly:

```json
{"status":"PASS","durationMs":12,"errorCode":"NONE"}
```

Only these statuses are valid: PASS, FAIL, PERMISSION_DENIED, UNAVAILABLE, NOT_TESTED.
durationMs is a monotonic elapsed duration clamped to integer0..60000; no wall-clock identifier.
Status is derived from the fixed code, never arbitrary caller text:

- PASS: NONE.
- PERMISSION_DENIED: PERMISSION_MISSING.
- UNAVAILABLE: API_UNAVAILABLE, SERVICE_UNAVAILABLE, HARDWARE_UNAVAILABLE, RADIO_DISABLED,
  NETWORK_UNAVAILABLE, HOTSPOT_UNSUPPORTED, HOTSPOT_INCOMPATIBLE, HOTSPOT_DISALLOWED, SURFACE_UNAVAILABLE.
- FAIL: PROBE_FAILED, PROBE_TIMEOUT, CORE_INIT_FAILED, JNI_LOAD_FAILED, RFCOMM_CREATE_FAILED,
  HOTSPOT_START_FAILED, MULTICAST_FAILED, MDNS_BIND_FAILED, TCP_BIND_FAILED, UDP_BIND_FAILED,
  NETWORK_BIND_FAILED, SURFACE_INVALID, AUDIO_CREATE_FAILED, RESOURCE_RELEASE_FAILED.
- NOT_TESTED: NOT_RUN, TEST_CANCELLED, ABI_NOT_ARMV7, PREVIOUS_PROBE_RUNNING.

The Android model has no free-text field or raw-exception serializer. The server revalidates
all keys, integer bounds and status/code associations, reconstructs the allowlisted object and
renders a fixed table. Unknown nested fields, identifiers, credentials, certificate data,
free-form errors and success claims outside the authentication boundary are rejected before GitHub.
No SSID/BSSID/MAC/IP/peer name/device identity/credential/certificate/raw exception is recorded.
Existing sanitized counters/radio metrics and newest200 fixed event entries are preserved.
The endpoint remains `/api/carplay-diagnostics`, fixed `nnnc8/ts7-carplay-lite` Issue13;
client-selected destination is forbidden. No GitHub credential in APK, no automatic telemetry/upload.
Old v0.1-alpha and v0.2-alpha reports remain accepted without readiness. Diagnostic v0.2/fixed5
and its privacy sanitizer are unchanged. All readiness PASS still cannot authorize CARPLAY/STREAMING.

## Verification evidence boundary

Host tests cover typed privacy, isolation, bounded timeout, cancel, late result rejection and
repeat-run guarding across Activity recreation. Backend tests cover strict nested validation,
private data denial, fixed13, old-client/fixed5 compatibility and blocked-auth claims.
Source audit locks all nine renderer/audio/touch/asset files to the existing byte hashes.

API27 CI launches the normal APK first and then a separate instrumentation APK. Readiness
instrumentation runs without location permission, verifies later real binds/Surface/AudioTrack,
grants permission for an actual hotspot attempt, emits only the public JSON and checks that JSON
against the actual backend validator offline (no GitHub comment). Existing H.26490+frame/reset/stop
smoke follows separately. The instrumented APK is not a release asset.

An x86_64 emulator cannot execute the ARMv7 library or validate TS7 Bluetooth/RFCOMM,
Wi-Fi AP/channel/SELinux/multicast delivery, SPRD/display/audio hardware or vendor service stability.
Unavailable emulator radios/hotspot are honest outcomes, not mocked PASS results.
Emulated TCP/UDP/Network/Surface/AudioTrack results prove only that emulator instance.
Real TS7 results, and any iPhone/auth/video/audio/touch session, remain UNKNOWN until captured.
Update2026-10-08: [first TS7 platform report](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6050562144) has11PASS/hotspot permission denied; [second](https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6050623491) has11PASS including hotspot reservation+close, Network unavailable after client Wi-Fi loss. These prove each individual probe across two runs, not AP channel/over-air traffic/coexistence or a phone session. Current runner puts hotspot last and footer says authentication NOT TESTED by platform probes; JSON key order unchanged.
Exact commit, hosted CI, downloadable APK hash and deployment checks are recorded in release notes.

Official API references: [Network.bindSocket](https://developer.android.com/reference/android/net/Network),
[WifiManager LocalOnlyHotspot](https://developer.android.com/reference/android/net/wifi/WifiManager),
[BluetoothAdapter RFCOMM](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter),
[MulticastLock](https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock),
[AudioTrack](https://developer.android.com/reference/android/media/AudioTrack).
