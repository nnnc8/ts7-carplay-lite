# DiPlay API27 / TS7 port audit

Base: Legacy c8884adcc75bfda3c134db63877bd6c6f83beb74.
Status: IMPLEMENTATION_IN_PROGRESS. Compile, emulator, physical TS7 and real iPhone
are distinct gates; none is inferred from upstream README or minSdk declarations.

| File / symbol | Upstream API or assumption | API27 plan / fallback |
| --- | --- | --- |
| LocalOnlyHotspotManager.requestHotspot/configuration | SoftApConfiguration API30 and later reflection; force5GHz/BYD station behavior | keep API26 callback+Handler and WifiConfiguration; no API28+ typed refs, no auto station disconnect |
| LocalOnlyHotspotRadioInfo | API30 SoftApCallback and vendor callback reflection | remove newer callbacks; source-built read-only WEXT query, UNKNOWN if denied |
| LegacyHotspotRadio / local_hotspot_radio.c | native Wireless Extensions driver query | compile NDKr25c/API27 armeabi-v7a; invalid name/errno fail closed; no AP setter |
| CarPlayController.startWirelessHotspot | Q WiFiP2p fallback, manual multicar settings | single API27 LOHS manager; bounded deadline; unsupported/unknown channel is fixed failure, never guessed36 |
| Controller auth startMfi | automatic assets/private files/CH341/I2C/helper loading | explicit AuthenticationProvider; Unavailable by default, no asset loader or hardware access |
| CarPlayVpnService.attachWireless | VpnService type with wired/NCM bridge | ordinary non-exported bound Service, selected local address only |
| Bonjour / liveness / AirPlay media ports | NSD alternate-interface/wildcard paths | explicit selected AP address, reject stale/ambiguous links; no global bindProcessToNetwork |
| Kotlin/JDK protocol source | source build JDK25; HexFormat/newer stdlib paths possible | pinned Kotlin2.2.10 JVM8; API27 SDK compile, no newer Java/Android runtime symbols |
| shared build | AGP9.3/SDK37/NDK25.2 | reuse small checked-in build, SDK27/build-tools35/NDKr25c; no full upstream UI Gradle |
| AirPlay media | H264/H265/secondary display / raw capture | main H264 only; no capture; bounded compressed queue→existing MediaCodec→Surface |
| Runtime permissions | discovery/hotspot may require location on Android8 | explicit local opt-in explanatory permission; never LocationManager/GPS/upload location |
| Bluetooth | paired device RFCOMM UUID and timeout | explicit selection from Android system pairing; paired/enabled is observation, not bootstrap |
| WLAN lifecycle | reservation close, multicast release, network callback invalidation | callback cancellation closes late reservations; lost session closes listeners/sockets |

Permission copy: “Android 8 requires this permission for Wi-Fi/Bluetooth discovery.
TS7 CarPlay Lite does not collect or upload location.”

Official API reference:
https://developer.android.com/reference/android/net/wifi/WifiManager#startLocalOnlyHotspot(android.net.wifi.WifiManager.LocalOnlyHotspotCallback,%20android.os.Handler)

## Renderer choice

Keep the TS7-proved renderer, not upstream AndroidMediaSink. Existing pipeline:
bounded compressed H264 queue → AnnexB/SPS/PPS/IDR → MediaCodec → Surface.
No Bitmap/Canvas/WebView/RGB conversion. Default1280×720/30;25/20 profiles retained.
SPRD vendor decoder preferred, other hardware vendor next, system/software fallback
last; actual selected decoder and queue/drop/restart counters remain visible.
Upstream MediaSink.onVideoConfig/onVideoFrame connects here through a small adapter.
Audio RTP/PCM and HID touch keep separate readiness/proof; no synthetic auth success.

## Physical evidence retained (not a new benchmark)

Issue13 user report: approximately29.85fps,8380 rendered test-pattern frames,
7 drops,0 decoder restarts,queue0,193ms measured latency.
8380/current FPS suggests about4m41s of frames, **a duration estimate**, not an
uploaded continuous timer. Seven drops are not a measured Wi-Fi packet-loss rate.
This proves real TS7 TEST_PATTERN Surface output only, not iPhone/CarPlay/auth,
nor the5/15-minute gate. Issue4 remains open.
https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6030102288

## Verification gates

PASS:71 exact file hashes, GPL/dependency notices, API27 SDK/JVM8 compile,
source-built ELF32 ARM JNI with Android API27 .note.android.ident,9 frozen renderer
hashes,2047 receiver +24auth +30gate +444DiPlay host fixtures,diagnostic sanitizer,
16 backend tests, signed/inspected normal APK.
Initial v0.2 API27 normal startup/authblocked/BCcrypto/JNI+Surface/reset/stop PASS
at87e9901, PR CI37604670362 and push37604605337; screenshots inspected.
Hardened77ef5cb PR CI37605750814 / push37605744850 PASS; normal startup,
crypto/native load, Surface/reset/recovery/stop remain PASS. Real BT/RFCOMM/AP
has not run. Android masked local BT MAC returns LOCAL_BLUETOOTH_ADDRESS_UNAVAILABLE;
a future legally supported hardware/API solution is required, no invented fallback.
See DIPLAY_PORT_VERIFICATION.md for final CI/release/download proof; do not treat
emulator x86_64 as real TS7 ARMv7 or actual iPhone verification.
