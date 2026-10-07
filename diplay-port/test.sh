#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_DEPS="$TASK_ROOT/build/core-dependencies"
TASK_CORE="$TASK_ROOT/build/diplay-port"
[[ -f "$TASK_CORE/core.jar" ]] || { printf '%s\n' 'Build diplay-port first' >&2; exit 1; }
TASK_TEST="$(mktemp -d "$TASK_CORE/tests.XXXXXX")"
TASK_CP="$TASK_CORE/core.jar:$TASK_CORE/kotlin-stdlib.jar:$TASK_CORE/bcprov.jar:$TASK_CORE/jmdns.jar:$TASK_CORE/slf4j-api.jar:$TASK_CORE/slf4j-nop.jar"
TASK_SRC="$TASK_ROOT/receiver/src/main/java/io/ts7/carplay"
find "$TASK_ROOT/diplay-port/src/test/java" -name '*.java' -type f -print | sort > "$TASK_TEST/sources.txt"
javac --release 8 -classpath "$TASK_CP" -d "$TASK_TEST" \
  "$TASK_SRC/ReceiverCore.java" "$TASK_SRC/VideoProfile.java" "$TASK_SRC/SessionMachine.java" \
  "$TASK_SRC/EventRing.java" "$TASK_SRC/EventCode.java" @"$TASK_TEST/sources.txt"
"$TASK_DEPS/kotlin/kotlinc/bin/kotlinc" "$TASK_ROOT/diplay-port/src/test/kotlin" \
  -jvm-target 1.8 -classpath "$TASK_CP:$TASK_TEST" -d "$TASK_TEST/fixtures.jar"
java -cp "$TASK_CP:$TASK_TEST" io.ts7.carplay.auth.AuthenticationBoundaryTest
java -cp "$TASK_CP:$TASK_TEST" io.ts7.carplay.core.ProtocolIntegrationTest
java -cp "$TASK_CP:$TASK_TEST:$TASK_TEST/fixtures.jar" io.ts7.carplay.core.DiPlayPortTestKt
