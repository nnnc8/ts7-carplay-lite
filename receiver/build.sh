#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
TASK_JAR="$TASK_SDK/platforms/android-27/android.jar"
TASK_TOOLS="$TASK_SDK/build-tools/35.0.0"
for task_tool in aapt2 d8 zipalign apksigner; do
  [[ -x "$TASK_TOOLS/$task_tool" ]] || { printf 'Missing build-tools 35.0.0: %s\n' "$task_tool" >&2; exit 1; }
done
[[ -f "$TASK_JAR" ]] || { printf '%s\n' 'Install SDK platforms;android-27' >&2; exit 1; }
[[ -f "$TASK_ROOT/receiver/src/main/assets/ts7-pattern.h264" ]] || { printf '%s\n' 'Run receiver/generate-pattern.sh' >&2; exit 1; }
mkdir -p "$TASK_ROOT/build/receiver" "$TASK_ROOT/build/receiver-signing" "$TASK_ROOT/dist"
TASK_BUILD="$(mktemp -d "$TASK_ROOT/build/receiver/build.XXXXXX")"
TASK_NAME="TS7-CarPlay-Lite-v0.1-alpha-core-preview"
bash "$TASK_ROOT/receiver-core/build.sh"
TASK_CORE="$TASK_ROOT/build/receiver-core"
mkdir -p "$TASK_BUILD/classes" "$TASK_BUILD/dex" "$TASK_BUILD/generated/io/ts7/carplay"
mkdir -p "$TASK_BUILD/legal-assets/licenses"
cp "$TASK_ROOT/receiver/LICENSE" "$TASK_BUILD/legal-assets/licenses/GPL-3.0.txt"
cp "$TASK_ROOT/receiver-core/licenses/"*.txt "$TASK_BUILD/legal-assets/licenses/"
cp "$TASK_ROOT/THIRD_PARTY_NOTICES.md" "$TASK_BUILD/legal-assets/licenses/THIRD_PARTY_NOTICES.md"
python3 "$TASK_ROOT/receiver/tools/build_config.py" \
  "$TASK_BUILD/generated/io/ts7/carplay/BuildConfig.java" "${TS7_CARPLAY_UPLOAD_URL:-}"
cp "$TASK_ROOT/receiver/src/main/AndroidManifest.xml" "$TASK_BUILD/AndroidManifest.xml"
if [[ "${1:-}" == "--instrumented" ]]; then
  TASK_NAME="${TASK_NAME}-instrumented"
  python3 "$TASK_ROOT/receiver/tools/instrument_manifest.py" "$TASK_BUILD/AndroidManifest.xml"
fi
find "$TASK_ROOT/receiver/src/main/java" "$TASK_BUILD/generated" -name '*.java' -type f -print | sort > "$TASK_BUILD/sources.txt"
if [[ "${1:-}" == "--instrumented" ]]; then
  find "$TASK_ROOT/receiver/src/androidTest/java" -name '*.java' -type f -print | sort >> "$TASK_BUILD/sources.txt"
fi
javac --release 8 -encoding UTF-8 -classpath "$TASK_JAR:$TASK_CORE/core.jar:$TASK_CORE/kotlin-stdlib.jar:$TASK_CORE/bcprov.jar" -d "$TASK_BUILD/classes" @"$TASK_BUILD/sources.txt"
find "$TASK_BUILD/classes" -name '*.class' -type f -print | sort > "$TASK_BUILD/classes.txt"
"$TASK_TOOLS/d8" --lib "$TASK_JAR" --min-api 27 --output "$TASK_BUILD/dex" @"$TASK_BUILD/classes.txt" \
  "$TASK_CORE/core.jar" "$TASK_CORE/kotlin-stdlib.jar" "$TASK_CORE/bcprov.jar"
"$TASK_TOOLS/aapt2" link -I "$TASK_JAR" --manifest "$TASK_BUILD/AndroidManifest.xml" \
  -A "$TASK_ROOT/receiver/src/main/assets" -A "$TASK_BUILD/legal-assets" -o "$TASK_BUILD/unsigned.apk"
(
  cd "$TASK_BUILD/dex"
  zip -q -0 "$TASK_BUILD/unsigned.apk" classes*.dex
)
"$TASK_TOOLS/zipalign" -f 4 "$TASK_BUILD/unsigned.apk" "$TASK_BUILD/aligned.apk"
TASK_KEY="${TS7_ALPHA_KEYSTORE:-$TASK_ROOT/build/receiver-signing/test.keystore}"
TASK_STOREPASS="${TS7_ALPHA_KEYSTORE_PASSWORD:-android}"
TASK_KEYPASS="${TS7_ALPHA_KEY_PASSWORD:-$TASK_STOREPASS}"
TASK_ALIAS="${TS7_ALPHA_KEY_ALIAS:-ts7alpha}"
if [[ ! -f "$TASK_KEY" ]]; then
  keytool -genkeypair -noprompt -keystore "$TASK_KEY" -storepass "$TASK_STOREPASS" \
    -keypass "$TASK_KEYPASS" -alias "$TASK_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=TS7 Alpha Test,O=TS7 CarPlay Lite,C=TW' >/dev/null 2>&1
fi
"$TASK_TOOLS/apksigner" sign --ks "$TASK_KEY" --ks-key-alias "$TASK_ALIAS" \
  --ks-pass "pass:$TASK_STOREPASS" --key-pass "pass:$TASK_KEYPASS" \
  --out "$TASK_ROOT/dist/$TASK_NAME.apk" "$TASK_BUILD/aligned.apk"
"$TASK_TOOLS/apksigner" verify --verbose "$TASK_ROOT/dist/$TASK_NAME.apk"
python3 "$TASK_ROOT/receiver/tools/inspect_apk.py" "$TASK_ROOT/dist/$TASK_NAME.apk" "$TASK_TOOLS/aapt2"
shasum -a 256 "$TASK_ROOT/dist/$TASK_NAME.apk"
