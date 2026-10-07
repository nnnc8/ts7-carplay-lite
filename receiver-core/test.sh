#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$TASK_ROOT/build/core-tests"
TASK_BUILD="$(mktemp -d "$TASK_ROOT/build/core-tests/run.XXXXXX")"
find "$TASK_ROOT/receiver-core/src/main/java" "$TASK_ROOT/receiver-core/src/test/java" -name '*.java' -print | sort > "$TASK_BUILD/sources.txt"
javac --release 8 -d "$TASK_BUILD" @"$TASK_BUILD/sources.txt"
java -cp "$TASK_BUILD" io.ts7.carplay.auth.AuthenticationBoundaryTest
java -cp "$TASK_BUILD" io.ts7.carplay.core.ProtocolIntegrationTest
