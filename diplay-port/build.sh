#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
TASK_ANDROID="$TASK_SDK/platforms/android-27/android.jar"
TASK_NDK="$TASK_SDK/ndk/25.2.9519653"
[[ -f "$TASK_ANDROID" && -x "$TASK_NDK/ndk-build" ]] || { printf '%s\n' 'API27 SDK and NDK25.2.9519653 required' >&2; exit 1; }
TASK_DEPS="$TASK_ROOT/build/core-dependencies"
TASK_OUT="$TASK_ROOT/build/diplay-port"
mkdir -p "$TASK_DEPS" "$TASK_OUT"
task_fetch() {
  local task_url="$1" task_file="$2" task_hash="$3"
  if [[ ! -f "$task_file" ]]; then
    curl --fail --location --proto '=https' --tlsv1.2 --retry 2 "$task_url" -o "$task_file"
  fi
  local task_actual
  task_actual="$(shasum -a 256 "$task_file")"
  [[ "${task_actual%% *}" == "$task_hash" ]] || { printf '%s\n' 'Dependency digest mismatch' >&2; exit 1; }
}
# Official compiler / GPL-compatible runtime only. No APK, firmware or credential downloads.
task_fetch https://github.com/JetBrains/kotlin/releases/download/v2.2.10/kotlin-compiler-2.2.10.zip \
  "$TASK_DEPS/kotlin-compiler-2.2.10.zip" 302d1d8e671e5c3207e6ed62ff11fb555462a628e22a1158254dcaaf7e7394bc
if [[ ! -x "$TASK_DEPS/kotlin/kotlinc/bin/kotlinc" ]]; then
  unzip -q "$TASK_DEPS/kotlin-compiler-2.2.10.zip" -d "$TASK_DEPS/kotlin"
fi
task_fetch https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.79/bcprov-jdk18on-1.79.jar \
  "$TASK_DEPS/bcprov-jdk18on-1.79.jar" 0d81ecc3124536b539bce9aa3fe9621b7f84c9cee371b635a5b31c78b79ab1da
task_fetch https://repo.maven.apache.org/maven2/org/jmdns/jmdns/3.6.3/jmdns-3.6.3.jar \
  "$TASK_DEPS/jmdns-3.6.3.jar" 6b6eb1623cb1d9e51467312ea62c567da005c2aa5a95347f566a11b8703c0ef1
task_fetch https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar \
  "$TASK_DEPS/slf4j-api-2.0.7.jar" 5d6298b93a1905c32cda6478808ac14c2d4a47e91535e53c41f7feeb85d946f4
task_fetch https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.7/slf4j-nop-2.0.7.jar \
  "$TASK_DEPS/slf4j-nop-2.0.7.jar" 5411a0d44e2725182271230b9fb4c2c4062c1b5fa7df2d83e00c0302733db173
TASK_RUN="$(mktemp -d "$TASK_OUT/run.XXXXXX")"
mkdir -p "$TASK_RUN/java" "$TASK_RUN/api"
find "$TASK_ROOT/diplay-port/src/main/java" -name '*.java' -type f -print | sort > "$TASK_RUN/java-sources.txt"
javac --release 8 -classpath "$TASK_ANDROID" -d "$TASK_RUN/java" @"$TASK_RUN/java-sources.txt"
TASK_RECEIVER="$TASK_ROOT/receiver/src/main/java/io/ts7/carplay"
javac --release 8 -classpath "$TASK_ANDROID" -d "$TASK_RUN/api" \
  "$TASK_RECEIVER/ReceiverCore.java" "$TASK_RECEIVER/VideoProfile.java" \
  "$TASK_RECEIVER/SessionMachine.java" "$TASK_RECEIVER/EventRing.java" "$TASK_RECEIVER/EventCode.java"
TASK_STDLIB="$TASK_DEPS/kotlin/kotlinc/lib/kotlin-stdlib.jar"
TASK_CLASSPATH="$TASK_ANDROID:$TASK_RUN/java:$TASK_RUN/api:$TASK_STDLIB:$TASK_DEPS/bcprov-jdk18on-1.79.jar:$TASK_DEPS/jmdns-3.6.3.jar:$TASK_DEPS/slf4j-api-2.0.7.jar"
"$TASK_DEPS/kotlin/kotlinc/bin/kotlinc" \
  "$TASK_ROOT/third_party/diplay-base/shared/src/main/java" "$TASK_ROOT/diplay-port/src/main/kotlin" \
  -no-jdk -no-reflect -no-stdlib -jvm-target 1.8 -classpath "$TASK_CLASSPATH" -d "$TASK_RUN/core.jar"
jar uf "$TASK_RUN/core.jar" -C "$TASK_RUN/java" .
cp "$TASK_RUN/core.jar" "$TASK_OUT/core.jar"
cp "$TASK_STDLIB" "$TASK_OUT/kotlin-stdlib.jar"
cp "$TASK_DEPS/bcprov-jdk18on-1.79.jar" "$TASK_OUT/bcprov.jar"
cp "$TASK_DEPS/jmdns-3.6.3.jar" "$TASK_OUT/jmdns.jar"
cp "$TASK_DEPS/slf4j-api-2.0.7.jar" "$TASK_OUT/slf4j-api.jar"
cp "$TASK_DEPS/slf4j-nop-2.0.7.jar" "$TASK_OUT/slf4j-nop.jar"
"$TASK_NDK/ndk-build" NDK_PROJECT_PATH=null \
  APP_BUILD_SCRIPT="$TASK_ROOT/third_party/diplay-base/shared/src/main/jni/Android.mk" \
  NDK_APPLICATION_MK="$TASK_ROOT/third_party/diplay-base/shared/src/main/jni/Application.mk" \
  NDK_OUT="$TASK_RUN/obj" NDK_LIBS_OUT="$TASK_OUT/native"
printf '%s\n' 'DiPlay compile PASS: API27 SDK, Kotlin JVM8, source-built armeabi-v7a and x86_64 JNI'
