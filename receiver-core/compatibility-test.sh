#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$TASK_ROOT/receiver-core/build.sh"
TASK_CORE="$TASK_ROOT/build/receiver-core"
TASK_COMPILER="$TASK_ROOT/build/core-dependencies/kotlin/kotlinc/bin/kotlinc"
TASK_TEST="$(mktemp -d "$TASK_CORE/fixtures.XXXXXX")"
TASK_CP="$TASK_CORE/core.jar:$TASK_CORE/kotlin-stdlib.jar:$TASK_CORE/bcprov.jar"
"$TASK_COMPILER" "$TASK_ROOT/receiver-core/src/test/kotlin" -jvm-target 1.8 -classpath "$TASK_CP" -d "$TASK_TEST/tests.jar"
java -cp "$TASK_TEST/tests.jar:$TASK_CP" io.ts7.carplay.core.ParserCompatibilityTest
