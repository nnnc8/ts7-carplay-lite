# DiPlay module map (decision before import/build)

Base: Legacy c8884adcc75bfda3c134db63877bd6c6f83beb74.
Scope: TS7, Android8.1/API27, ARM32/2GB, wireless first.

| Upstream path/module | Purpose | TS7 action |
| --- | --- | --- |
| mobile app | manifests, standalone UI, auth assets packaging | REMOVE; own framework Activity/FrameLayout/SurfaceView/TextView/buttons |
| common Activity/service/bootstrap | AppCompat UI, AGPL-derived layout/settings, credential import | REMOVE; no Compose/AndroidX UI or auth import |
| shared/orchestration/CarPlayController | real DiPlay wireless lifecycle, RFCOMM, iAP2→Wi-Fi tunnel handoff | ADAPT; keep wireless algorithm; inject authorized authenticator, remove USB/MFi scanning/BYD/location/cluster routing |
| shared/orchestration/CarPlayRuntimeConfig | multicar/wired/auth selections | REWRITE to single TS7 wireless config; no host/token/private-key controls |
| shared/orchestration/WirelessConnectionProof | separates auth/session/render proof | KEEP/ADAPT; never equate socket state with CarPlay success |
| shared/airplay | RTSP/pairing/encryption/media dispatch | KEEP selected GPL protocol; no captures/brand icon/HUD; H264-only, bounded media lengths, explicit interface binding |
| shared/airplay/CarPlayMediaEngine | decrypted video/audio→MediaSink seam; iAP2 tunnel | ADAPT to existing TS7 renderer; no upstream AndroidMediaSink or alternate renderer |
| shared/media/AndroidMediaSink | upstream codec/Surface implementation | REMOVE; compare documented in API27 port report; no duplicate video pipeline |
| shared/media capture/Opus/microphone workers | audio decoders/mic/debug capture | no capture/debug recorder; PCM seam first; compressed codec/Siri physical proof remains separate |
| shared/network/LocalOnlyHotspotManager | API26 LOHS reservation and AP interface | ADAPT for API27 overload; no forced5GHz, no invented channel, no station disconnect |
| shared/network/LegacyHotspotRadio + JNI | read-only WEXT radio frequency | KEEP source-built armv7 + x86_64 smoke ABI; permission/driver failure stays UNKNOWN |
| shared/network/Bonjour/TcpLiveness | interface-local discovery and probe | KEEP/ADAPT; bind selected AP, multicast lock; no arbitrary helper/server |
| shared/network/CarPlayVpnService | wireless AirPlay listener plus wired VPN/NCM | ADAPT to ordinary bound Service, wireless only; no VPN permission/consent/tun |
| shared/network/WiFiP2p/Manual/BYD fallbacks | Android10+ / vehicle-specific AP selection | REMOVE in initial API27 LOHS port; do not advertise unsupported fallback |
| shared/iap2 + selected transport | framing/link/identification/wireless configuration/control | KEEP; inherited subscription/location/vehicle protocol definitions only where hard dependencies, no platform GPS/CAN/providers |
| shared/mfi/Iap2MfiAuthenticationClient | real certificate/challenge→AA05 exchange | KEEP; external AuthenticationProvider bridge only |
| shared/mfi/Local/Remote/Scanner/Probe; transport/LinuxI2c/CH341 | offline extracted-identity loaders or automatic hardware/helper probes | REMOVE; no credentials, bus open, arbitrary endpoint or automatic discovery |
| wired USB/NCM/Lockdown/AndroidAuto | non-wireless features | REMOVE except shared static subscription definitions until factored; Issues10/11 DEFERRED |
| shared/hud, adb, vendor/location providers | BYD HUD/navigation/CAN/ADB/GPS | REMOVE completely |
| shared assets, mobile icons, site/fonts | restricted artwork and AGPL site | REMOVE, never downloaded/imported |
| existing receiver renderer/diag/backend | physically proved Surface path and fixed Issue13 relay | PRESERVE; extend only typed core seams/version allowlist |

No code import precedes this map. Actual included/modified files and original hashes
are machine-readable in third_party/diplay-base/SOURCE_MANIFEST.json; audit source
and APK independently. No hidden feature framework or additional default workers.

