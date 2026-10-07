# TS7 CarPlay Lite DiPlay v0.2-alpha receiver

**NOT YET A FUNCTIONAL CARPLAY RECEIVER.**
Minimal framework Activity + actual selected GPL DiPlay Legacy core,
external AuthenticationProvider unavailable by default, no proprietary credentials.
Existing TS7-proved compressed queue/MediaCodec→Surface path preserved byte-identical.

JDK17,Python3,zip,SDK27/build-tools35.0.0/NDK25.2.9519653 required.
Pinned Kotlin2.2.10/BC/JmDNS/SLF4J downloaded from official sources/digest checked.

```sh
./receiver/test.sh
TS7_CARPLAY_UPLOAD_URL=https://ts7-carplay-lite-relay.vercel.app/api/carplay-diagnostics ./receiver/build.sh
./diplay-port/test.sh
python3 diplay-port/tools/verify_source.py
```

Normal output: dist/TS7-CarPlay-Lite-DiPlay-v0.2-alpha.apk.
Native read-only AP radio JNI built from source for API27 ARMv7 +x86_64 emulator.
Separate --instrumented build includes CI-only Instrumentation, never published as normal.
Ignored local test signing key; CI ephemeral test key. Separate builds may need
uninstall/reinstall if signatures differ (local settings lost); keep release hash authoritative.

Startup shows UI first, then DiPlayready/authblocked; no decoder/radio/receiver ports
started by unavailable provider. Central black Surface is expected while waiting.
Settings opt-in developer pattern1280×72030/25/20 exercises preserved decoder path;
pattern never claims CarPlay session. No microphone/storage/account/GPS permission.
API27 FINE_LOCATION is explicit user opt-in for WiFi/Bluetooth discovery/LOHS,
explained as not location collection/upload. Android may require system Location switch
for LOHS; real TS7 capability remains UNKNOWN. Select paired iPhone locally only.

Diagnostics fixed codes/counters,500 ring/newest200 export; explicit confirmation upload
to fixed13. No identifiers/SSID/passphrase/certs/exception text; offline copy works.
Diagnostic v0.2 separate APK/Issue5 unchanged.
LPCM44.1/48k1/2channels/media and normalized touch adapter fixture-tested;
real iPhone/bootstrap/auth/video/audio/touch NOT YET. Siri/wired deferred.
GPL corresponding source/licenses/patches/build instructions supplied with release.
