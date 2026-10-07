# DiPlay TS7 base selection

PRIMARY_BASE: https://github.com/programmerguohuajing/DiPlay-Legacy-Android
EXACT_SHA: c8884adcc75bfda3c134db63877bd6c6f83beb74
Selection date: 2026-10-07. One port only: feature/diplay-ts7-port.

## Evidence and decision

The Legacy fixed commit was fetched and checked out; its mobile/common/shared,
Gradle, manifest, JNI and actual orchestration/network/media source were read.
CASKA's indexed canonical location is https://github.com/mrisX/DiPlay-Android8.1-CASKA.
Independent live repository, commits/main, search and git requests currently return
404 / no repository. ACCESS_UNPROVEN; latest exact SHA UNKNOWN. This does not prove
whether it was deleted, renamed or made private. Cached source is historical reference,
not a reproducible checkout and not evidence that anything works on TS7.

| Criterion | Legacy at immutable SHA | CASKA current evidence |
| --- | --- | --- |
| Android 8.1 / API27 | min19 declared; API27 runtime must be proved separately | Cached mobile min27; current source/runtime UNKNOWN |
| min / target / compile SDK | shared/mobile min19, target37, compile37 upstream | Cached mobile min27/target37/compile37; UNKNOWN current |
| API27 source compilation | Newer SoftApConfiguration / Q / R paths need removing or adapting | Cannot perform exact-revision compile |
| ARM32 | explicit armeabi-v7a; r25c NDK/native API19 declaration | Current JNI/ABI configuration UNKNOWN |
| Wireless Bluetooth | actual Controller RFCOMM → iAP2 identification/auth → Wi-Fi handoff | Cached claims only; actual current source UNKNOWN |
| Wi-Fi / hotspot | LOHS API26 overload available, but 5GHz/BYD assumptions and guessed channel require patch | Current source UNKNOWN |
| Video | decrypted ScreenStream → MediaSink seam; AndroidMediaSink direct Surface also available | Current source UNKNOWN |
| Audio / touch / Siri | AudioStream RTP, codec metadata; session HID/touch/Siri hooks; real TS7 unproved | Current source UNKNOWN |
| UI / AndroidX / Compose | common Activity/AppCompat, no Compose in inspected mobile/common; UI AGPL excluded | Cached Compose/AndroidX retained; exact current deps UNKNOWN |
| Vendor baggage | BYD HUD/navigation, wired VPN/USB/NCM, settings/assets can be excluded; shared hard dependencies documented | CASKA vehicle runtime specifics UNKNOWN |
| Native / toolchain | upstream AGP9.3, Kotlin2.2.10, SDK37, NDK25.2.9519653; source can use our small pinned build | Reproducibility UNKNOWN |
| Auth | offline firmware identity loaders exist upstream; source license DOES NOT authorize credentials | Cached external-auth packaging/extracted-APK instructions are not lawful provisioning |
| Licenses | GPL3 core; AGPL3 UI/site; separately restricted BYD/Apple assets | Inaccessible exact-file license audit; not suitable for import |
| Maintainability / removal | source public and pin reproducible; retain wireless orchestration, trim vendor/wired UI | Cannot estimate patches against unavailable source |
| API patch size / TS7 likelihood | closest inspectable generic legacy Android; API27/2GB adaptation required, not TS7 proof | historical Android8.1 work potentially useful but unqualified |

Decision: Legacy is the closest **inspectable, reproducible** general Android base,
not a claim that it is already compatible. Retain the actual DiPlay wireless Controller,
Bonjour, iAP2/AirPlay and media seams. Do not replace the primary strategy with the
old hand-integrated xcertplay experiment. See DIPLAY_MODULE_MAP.md before import.

## License and distribution boundary

Only selected GPL core source and source-built JNI are imported under
third_party/diplay-base, preserving the upstream namespace, license and provenance.
No common/mobile UI, site, icons, fonts, BYD images, release APK, firmware or
authentication identity is imported. The combined DiPlay preview is distributed
under GPL-3.0 with corresponding source, patches, build instructions and dependency
notices; this does not grant Apple credentials, trademarks or certification.
Upstream source is GPL; runtime provisioning is a separate authorization question.
Full upstream notices are retained for attribution and exclusion evidence.

## Preserved checkpoint

Old branch feature/lawful-carplay-core is pushed at
801d99e9775a50ee32d8486f7fe604250c047047.
It is an unfinished research checkpoint, not a tested/releasable core.
Auth contracts, bounded gate tests and renderer-lock may be reused; xcertplay-only
primary adapter/source are not brought forward. Main baseline:
4e98000c1652ed7e1bc4c9668ff10b37a779f78b.

