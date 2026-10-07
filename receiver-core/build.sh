#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
TASK_ANDROID="$TASK_SDK/platforms/android-27/android.jar"
[[ -f "$TASK_ANDROID" ]] || { printf '%s\n' 'API 27 SDK required' >&2; exit 1; }
TASK_DEPS="$TASK_ROOT/build/core-dependencies"
TASK_OUT="$TASK_ROOT/build/receiver-core"
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
# Only official open-source compiler/runtime dependencies, never firmware/credentials/vendor APKs.
task_fetch https://github.com/JetBrains/kotlin/releases/download/v2.2.10/kotlin-compiler-2.2.10.zip \
  "$TASK_DEPS/kotlin-compiler-2.2.10.zip" 302d1d8e671e5c3207e6ed62ff11fb555462a628e22a1158254dcaaf7e7394bc
if [[ ! -x "$TASK_DEPS/kotlin/kotlinc/bin/kotlinc" ]]; then
  unzip -q "$TASK_DEPS/kotlin-compiler-2.2.10.zip" -d "$TASK_DEPS/kotlin"
fi
task_fetch https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.79/bcprov-jdk18on-1.79.jar \
  "$TASK_DEPS/bcprov-jdk18on-1.79.jar" 0d81ecc3124536b539bce9aa3fe9621b7f84c9cee371b635a5b31c78b79ab1da
TASK_RUN="$(mktemp -d "$TASK_OUT/run.XXXXXX")"
mkdir -p "$TASK_RUN/java"
find "$TASK_ROOT/receiver-core/src/main/java" -name '*.java' -type f -print | sort > "$TASK_RUN/java-sources.txt"
javac -source 8 -target 8 -bootclasspath "$TASK_ANDROID" -d "$TASK_RUN/java" @"$TASK_RUN/java-sources.txt"
TASK_STDLIB="$TASK_DEPS/kotlin/kotlinc/lib/kotlin-stdlib.jar"
"$TASK_DEPS/kotlin/kotlinc/bin/kotlinc" \
  "$TASK_ROOT/receiver-core/upstream/xcertplay/src" "$TASK_ROOT/receiver-core/src/main/kotlin" \
  -no-jdk -no-reflect -no-stdlib -jvm-target 1.8 \
  -classpath "$TASK_ANDROID:$TASK_RUN/java:$TASK_STDLIB:$TASK_DEPS/bcprov-jdk18on-1.79.jar" \
  -d "$TASK_RUN/core.jar"
jar uf "$TASK_RUN/core.jar" -C "$TASK_RUN/java" .
cp "$TASK_RUN/core.jar" "$TASK_OUT/core.jar"
cp "$TASK_STDLIB" "$TASK_OUT/kotlin-stdlib.jar"
cp "$TASK_DEPS/bcprov-jdk18on-1.79.jar" "$TASK_OUT/bcprov.jar"
printf '%s\n' 'Core compile PASS: actual API 27 bootclasspath, JVM 8, no native library'
