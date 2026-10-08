# TS7 DiPlay Android 8.1 port

PRIMARY_BASE: DiPlay Legacy c8884adcc75bfda3c134db63877bd6c6f83beb74.
Actual source: third_party/diplay-base. Own code: AuthenticationProvider bridge,
proof/epoch gate and compressed H264/LPCM adapter. Actual upstream Controller
wireless lifecycle retained. Missing provider fails closed before radios/ports.
User selected pinned experimental local authentication on2026-10-08, separate from authorized MFi.

Use JDK17, Python3, zip, SDK27, build-tools35.0.0, NDK25.2.9519653.
Official Kotlin2.2.10 / Java8 runtime downloads SHA-256 checked.
No Gradle/upstream UI/Compose/API37 SDK required.

```sh
sdkmanager 'platforms;android-27' 'build-tools;35.0.0' 'ndk;25.2.9519653'
./diplay-port/build.sh
./diplay-port/test.sh
python3 diplay-port/tools/verify_source.py
./receiver/test.sh
TS7_CARPLAY_UPLOAD_URL=https://ts7-carplay-lite-relay.vercel.app/api/carplay-diagnostics TS7_SKIP_CORE_BUILD=1 ./receiver/build.sh
```

Output: build/diplay-port/core.jar, source-built API27 ARMv7/x86_64 JNI,
dist/TS7-CarPlay-Lite-DiPlay-v1.0.0-dev.1.apk (identity-free).
Host fake provider only in src/test; not packaged. Tests do not prove iPhone success.
Ignored persistent local test key / ephemeral CI key, not production signing.
Source/API/ABI/digests reproducible; signing/timestamps not byte-identical promised.
Use authoritative release APK/hash, not separately signed CI artifacts for upgrades.

HardwareMfi/RemoteAuthorized remain unavailable contract templates: no automatic
hardware scanning, helper access, guessed keys or inferred authorization.
ExperimentalDiPlayAuthenticationProvider uses bounded explicitly supplied assets only,
matching official upstream v0.2.7; available is not isAuthorized or phone trust.
Future provider needs legal authorization, real protocol major and finite operations,
phone-confirmed AA05/SAP/pair-verify. Generated pairing key is not an MFi identity.

Reconnect invalidates callbacks/keys; reject until old asynchronous teardown ends,
so stale service.detach cannot tear down a fresh session. Exact AP/peer media binding.
API27 location permission/system location switch for discovery/LOHS only; no GPS/upload.
No Siri/recording. Media-only LPCM44.1/48k1/2channels; mainH264720p.
Two actual TS7 platform reports cover12 individualPASS results; AP channel/peer traffic/SELinux and full phone session remain UNKNOWN.
See [experimental build/provenance/evidence](../docs/EXPERIMENTAL_AUTHENTICATION.md).
GPL corresponding source/licenses/build instructions supplied with release.
