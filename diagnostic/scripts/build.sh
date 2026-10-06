#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD="$ROOT/build/diagnostic"
DIST="$ROOT/dist"
VERSION="0.1"
APK_BASENAME="TS7-Diagnostic-v${VERSION}"

CC_BIN="${CC:-clang}"
if ! command -v "$CC_BIN" >/dev/null 2>&1; then
  printf 'error: C compiler not found: %s\n' "$CC_BIN" >&2
  exit 1
fi

# macOS Homebrew keeps lld keg-only. Detect it without requiring every caller
# to edit PATH; Linux CI normally exposes ld.lld after installing the lld package.
if ! command -v ld.lld >/dev/null 2>&1 && command -v brew >/dev/null 2>&1; then
  LLD_PREFIX="$(brew --prefix lld 2>/dev/null || true)"
  if [[ -x "${LLD_PREFIX}/bin/ld.lld" ]]; then
    PATH="${LLD_PREFIX}/bin:${PATH}"
    export PATH
  fi
fi

if ! command -v ld.lld >/dev/null 2>&1; then
  printf '%s\n' 'error: ld.lld not found; install LLVM LLD or add ld.lld to PATH' >&2
  exit 1
fi

mkdir -p "$BUILD/lib/armeabi-v7a" "$BUILD/lib/armeabi" "$DIST"
rm -rf "$BUILD/META-INF" "$BUILD/unsigned.apk" "$BUILD/${APK_BASENAME}.apk"

python3 "$ROOT/diagnostic/tools/build_manifest.py" --output "$BUILD/AndroidManifest.xml"

"$CC_BIN" \
  -target armv7a-linux-androideabi21 \
  -shared -fPIC -nostdlib -Os \
  -ffunction-sections -fdata-sections \
  -Wl,-soname,libmain.so \
  -Wl,--no-undefined \
  -Wl,--gc-sections \
  -o "$BUILD/lib/armeabi-v7a/libmain.so" \
  "$ROOT/diagnostic/native/ts7diag.c"

cp "$BUILD/lib/armeabi-v7a/libmain.so" "$BUILD/lib/armeabi/libmain.so"

(
  cd "$BUILD"
  rm -f unsigned.apk
  zip -q -9 unsigned.apk AndroidManifest.xml lib/armeabi-v7a/libmain.so lib/armeabi/libmain.so
)

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
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=TS7 Diagnostic Test,O=TS7 CarPlay Lite,C=TW" >/dev/null 2>&1
fi

cp "$BUILD/unsigned.apk" "$BUILD/${APK_BASENAME}.apk"
jarsigner \
  -keystore "$KEYSTORE" \
  -storepass "$STOREPASS" \
  -keypass "$KEYPASS" \
  -sigalg SHA256withRSA \
  -digestalg SHA-256 \
  "$BUILD/${APK_BASENAME}.apk" "$ALIAS" >/dev/null

jarsigner -verify "$BUILD/${APK_BASENAME}.apk" >/dev/null
cp "$BUILD/${APK_BASENAME}.apk" "$DIST/${APK_BASENAME}.apk"

printf 'Built %s\n' "$DIST/${APK_BASENAME}.apk"
file "$BUILD/lib/armeabi-v7a/libmain.so"
ls -lh "$DIST/${APK_BASENAME}.apk"
