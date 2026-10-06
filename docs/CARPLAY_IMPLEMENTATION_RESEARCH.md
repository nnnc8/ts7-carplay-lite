# CarPlay implementation research — 2026-10-06

Decision: **original Java wireless session shell + MediaCodec/Surface technical preview**.
No complete CarPlay protocol is being hand-written. No third-party receiver core is
integrated in this revision. Authentication is `BLOCKED_BY_AUTHENTICATION_REQUIREMENT`.
An existing core can be integrated only after lawful authentication is available and
its API 27/ARM32 port is validated. xcertplay's hardware-provider design is the preferred
future candidate; selecting it for research is not claiming it runs on the TS7.

## Pinned candidates

These are the exact revisions inspected, not moving `main` links.

| Candidate / revision | License | Android 8.1 / ARMv7 | Wireless / video / audio / touch | Authentication and risk | Decision |
|---|---|---|---|---|---|
| [DiPlay](https://github.com/shihabal3amri/DiPlay/tree/b26cd5443cf19707e7fdbbcc507b038bc37ad989), `b26cd5443cf19707e7fdbbcc507b038bc37ad989` | Core GPL-3.0; adapted UI/site AGPL-3.0; separate noncommercial/Apple assets | minSdk 28; **NO** API 27 out of the box. Native ARMv7 filter present, not TS7-tested | Implemented upstream, including wireless hotspot/Same LAN; TS7 unverified | Source builds need external authentication. Published preview uses identity extracted from Carlinkit firmware; excluded under this task's explicit restriction. Compose/BYD overhead | Do not ship its APK or identity; do not import full UI |
| [PeratX/diplay](https://github.com/PeratX/diplay/tree/e6135d36e6a64ad85a94795b2dcbd54e826fa4c0), `e6135d36e6a64ad85a94795b2dcbd54e826fa4c0` | DiPlay GPL/AGPL notices | minSdk 28; **NO** API 27; ARMv7 upstream build support, runtime UNKNOWN | Upstream wired/wireless/video/audio/touch claims, not tested here | Same explicit external identity input; no inspected change removes the lawful-auth requirement | Not a TS7 compatibility solution |
| [DiPlay Legacy Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/tree/c8884adcc75bfda3c134db63877bd6c6f83beb74), `c8884adcc75bfda3c134db63877bd6c6f83beb74` | GPL core / AGPL-derived files; separate asset notices | minSdk 19; **PARTIAL** Android 7/8 compatibility claimed; explicit `armeabi-v7a` filter. No TS7 runtime evidence | Classic UI; Android 4.4–7 manual hotspot and Android 8 LocalOnlyHotspot; video/audio/touch ports claimed | Its documentation still describes extracted firmware identity. Lower minSdk does not establish a lawful authentication source or sustained decoder operation | Useful compatibility reference only; no APK/key extraction |
| [xcertplay](https://github.com/shilapi/xcertplay/tree/17c92439413638dfd1d7f91d7e1c2e7358398762), `17c92439413638dfd1d7f91d7e1c2e7358398762` | GPL-3.0 | minSdk 28 / native platform 28; **NO** direct API 27 compatibility; ARMv7 filter exists | Wireless and wired CarPlay, H.264/video, media audio, touch upstream | Hardware I²C/CH341 or remote/local-file authentication. No authorized MFi hardware/provider has been identified for this TS7. Java/Kotlin/NDK port still needed | Preferred future hardware-provider core, **not integrated** |
| [LIVI](https://github.com/f-io/LIVI/tree/8851de944d9ce20069d1f49e4f68a6c3494851d9), `8851de944d9ce20069d1f49e4f68a6c3494851d9` | GPL-3.0 | Linux/macOS, no Android/API 27 app; ARM Linux support is not an ARMv7 Android APK | Wireless/wired, GStreamer video, audio/mic, touch | Requires MFi coprocessor/network bridge; Linux services and desktop runtime do not fit this target | Architecture reference only |

## Primary-source checks

- DiPlay [mobile build](https://github.com/shihabal3amri/DiPlay/blob/b26cd5443cf19707e7fdbbcc507b038bc37ad989/mobile/build.gradle.kts), [shared build](https://github.com/shihabal3amri/DiPlay/blob/b26cd5443cf19707e7fdbbcc507b038bc37ad989/shared/build.gradle), [build instructions](https://github.com/shihabal3amri/DiPlay/blob/b26cd5443cf19707e7fdbbcc507b038bc37ad989/docs/BUILD.md), [notices](https://github.com/shihabal3amri/DiPlay/blob/b26cd5443cf19707e7fdbbcc507b038bc37ad989/docs/THIRD_PARTY_NOTICES.md).
- DiPlay dependencies: Kotlin, Compose/AndroidX, Bouncy Castle 1.79, JmDNS 3.6.3, SLF4J; SDK 37 / NDK 28.2.13676358. Native ARM32 declaration proves build intent only.
- Legacy [mobile build](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/blob/c8884adcc75bfda3c134db63877bd6c6f83beb74/mobile/build.gradle.kts), [shared build](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/blob/c8884adcc75bfda3c134db63877bd6c6f83beb74/shared/build.gradle), [README](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/blob/c8884adcc75bfda3c134db63877bd6c6f83beb74/README.md), [notices](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/blob/c8884adcc75bfda3c134db63877bd6c6f83beb74/docs/THIRD_PARTY_NOTICES.md). Its mobile path removes Compose, uses multidex and desugaring; shared uses Bouncy Castle/JmDNS and NDK 25.2.9519653. Minimum API alone does not prove all transitive APIs work.
- xcertplay [README](https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/README.md), [mobile build](https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/mobile/build.gradle.kts), [shared build](https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/build.gradle): Compose/AndroidX, Bouncy Castle, JmDNS, Media3 and Concentus; modern build toolchain.
- LIVI [README](https://github.com/f-io/LIVI/blob/8851de944d9ce20069d1f49e4f68a6c3494851d9/README.md) and [license](https://github.com/f-io/LIVI/blob/8851de944d9ce20069d1f49e4f68a6c3494851d9/LICENSE).
- [Apple MFi program](https://mfi.apple.com/) is the authoritative program entry point. Public availability of firmware credentials is not evidence of permission to extract or redistribute them.

The DiPlay-derived GPL/AGPL source licenses do not relicense third-party accessory
credentials, Apple artwork or unrelated donor assets. A future source integration must
preserve notices and meet corresponding-source obligations; this repository has not
been blanket-relicensed. No upstream source, binary, credential or icon was copied here.

## Authentication boundary and remaining investigation

`UNKNOWN`: whether this head unit contains an accessible, authorized MFi coprocessor;
the diagnostic USB enumeration returned zero devices and did not inspect authentication
hardware. This does not prove that hardware is absent. No vendor keys or firmware are
read. No remote signing service is configured. No security check is disabled.

The blocker is **missing lawful provisioning**, not a claim that implementing CarPlay
is intrinsically impossible. Authorized hardware/provider access would reopen the core
integration and API 27 backport task. Until then the shipped adapter fails closed and
real Bluetooth bootstrap, Wi-Fi session, video, audio and touch are unverified.

The TS7's measured 2.4 GHz / 2437 MHz / 65 Mbps / −41 dBm snapshot does not identify
the cause of TLink disconnects. Wi-Fi as a cause remains **UNKNOWN**. 5 GHz capability
is a later investigation and is not required by this alpha renderer.
