#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ROOT="$ROOT/diagnostic-app"
BUILD="$ROOT/build/diagnostic-v0.2"
DIST="$ROOT/dist"
VERSION="0.2"
APK_BASENAME="TS7-Diagnostic-v${VERSION}"

ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_SDK" ]]; then
  for candidate in /usr/local/lib/android/sdk /opt/android-sdk /opt/android-sdk-linux; do
    if [[ -d "$candidate" ]]; then
      ANDROID_SDK="$candidate"
      break
    fi
  done
fi
if [[ -z "$ANDROID_SDK" || ! -d "$ANDROID_SDK" ]]; then
  printf '%s\n' 'error: Android SDK not found; set ANDROID_HOME or ANDROID_SDK_ROOT' >&2
  exit 1
fi

ANDROID_JAR="${ANDROID_PLATFORM_JAR:-}"
if [[ -z "$ANDROID_JAR" ]]; then
  while IFS= read -r candidate; do
    ANDROID_JAR="$candidate"
  done < <(find "$ANDROID_SDK/platforms" -maxdepth 2 -name android.jar -type f 2>/dev/null | sort -V)
fi
if [[ -z "$ANDROID_JAR" || ! -f "$ANDROID_JAR" ]]; then
  printf '%s\n' 'error: no Android platform android.jar found; install an Android platform' >&2
  exit 1
fi

BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-}"
if [[ -z "$BUILD_TOOLS" ]]; then
  while IFS= read -r candidate; do
    BUILD_TOOLS="$candidate"
  done < <(find "$ANDROID_SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V)
fi
if [[ -z "$BUILD_TOOLS" || ! -d "$BUILD_TOOLS" ]]; then
  printf '%s\n' 'error: no Android build-tools directory found' >&2
  exit 1
fi

AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
for tool in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER"; do
  if [[ ! -x "$tool" ]]; then
    printf 'error: Android build tool not found: %s\n' "$tool" >&2
    exit 1
  fi
done

command -v javac >/dev/null 2>&1 || { printf '%s\n' 'error: javac not found' >&2; exit 1; }
command -v zip >/dev/null 2>&1 || { printf '%s\n' 'error: zip not found' >&2; exit 1; }
command -v keytool >/dev/null 2>&1 || { printf '%s\n' 'error: keytool not found' >&2; exit 1; }

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/generated/io/ts7diag/tool" "$DIST"

python3 - "${TS7_DIAGNOSTIC_UPLOAD_URL:-}" "$BUILD/generated/io/ts7diag/tool/BuildConfig.java" <<'PY'
import json
import pathlib
import sys

endpoint, output = sys.argv[1:]
path = pathlib.Path(output)
path.write_text(
    "package io.ts7diag.tool;\n\n"
    "public final class BuildConfig {\n"
    "    public static final String VERSION_NAME = \"0.2\";\n"
    "    public static final String DIAGNOSTIC_UPLOAD_URL = "
    + json.dumps(endpoint)
    + ";\n"
    "    private BuildConfig() {}\n"
    "}\n",
    encoding="utf-8",
)
PY

find "$APP_ROOT/src/main/java" "$BUILD/generated" -name '*.java' -type f -print | sort > "$BUILD/java-sources.txt"
javac \
  -source 8 \
  -target 8 \
  -encoding UTF-8 \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  @"$BUILD/java-sources.txt"

"$D8" \
  --lib "$ANDROID_JAR" \
  --min-api 21 \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class' -type f | sort)

"$AAPT2" link \
  --auto-add-overlay \
  -I "$ANDROID_JAR" \
  --manifest "$APP_ROOT/src/main/AndroidManifest.xml" \
  -o "$BUILD/aapt.apk"

cp "$BUILD/aapt.apk" "$BUILD/unsigned.apk"
(
  cd "$BUILD/dex"
  zip -q -0 "$BUILD/unsigned.apk" classes.dex
)

UNALIGNED="$BUILD/${APK_BASENAME}-unaligned.apk"
ALIGNED="$BUILD/${APK_BASENAME}-aligned.apk"
SIGNED="$BUILD/${APK_BASENAME}.apk"
"$ZIPALIGN" -f 4 "$BUILD/unsigned.apk" "$ALIGNED"

KEYSTORE="${TS7_KEYSTORE:-$BUILD/ts7diag-debug.keystore}"
STOREPASS="${TS7_KEYSTORE_PASSWORD:-android}"
KEYPASS="${TS7_KEY_PASSWORD:-$STOREPASS}"
ALIAS="${TS7_KEY_ALIAS:-ts7diag}"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storepass "$STOREPASS" \
    -keypass "$KEYPASS" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=TS7 Diagnostic Test,O=TS7 CarPlay Lite,C=TW" >/dev/null 2>&1
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$STOREPASS" \
  --key-pass "pass:$KEYPASS" \
  --out "$SIGNED" \
  "$ALIGNED"
"$APKSIGNER" verify --verbose "$SIGNED" >/dev/null
cp "$SIGNED" "$DIST/${APK_BASENAME}.apk"

printf 'Built %s\n' "$DIST/${APK_BASENAME}.apk"
file "$DIST/${APK_BASENAME}.apk"
ls -lh "$DIST/${APK_BASENAME}.apk"
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$DIST/${APK_BASENAME}.apk"
else
  sha256sum "$DIST/${APK_BASENAME}.apk"
fi
