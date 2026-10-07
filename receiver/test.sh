#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$TASK_ROOT/build/receiver-tests"
TASK_TEST="$(mktemp -d "$TASK_ROOT/build/receiver-tests/run.XXXXXX")"
TASK_SOURCE="$TASK_ROOT/receiver/src/main/java/io/ts7/carplay"
javac --release 8 -d "$TASK_TEST" \
  "$TASK_SOURCE/VideoProfile.java" "$TASK_SOURCE/EventCode.java" "$TASK_SOURCE/EventRing.java" \
  "$TASK_SOURCE/RetryBudget.java" "$TASK_SOURCE/TouchMapper.java" "$TASK_SOURCE/AnnexB.java" \
  "$TASK_SOURCE/VideoQueue.java" "$TASK_SOURCE/AvcConfig.java" "$TASK_SOURCE/SessionMachine.java" \
  "$TASK_SOURCE/ReceiverCore.java" "$TASK_ROOT/receiver/src/test/java/io/ts7/carplay/ReceiverTest.java" \
  "$TASK_SOURCE/PlatformReadiness.java" "$TASK_SOURCE/PlatformReadinessRunner.java" \
  "$TASK_SOURCE/PlatformResourceGuard.java" \
  "$TASK_ROOT/receiver/src/test/java/io/ts7/carplay/PlatformReadinessTest.java" \
  "$TASK_ROOT/receiver/src/test/java/io/ts7/carplay/PlatformCleanupTest.java"
java -cp "$TASK_TEST" io.ts7.carplay.ReceiverTest "$TASK_ROOT/receiver/src/main/assets/ts7-pattern.h264"
java -cp "$TASK_TEST" io.ts7.carplay.PlatformReadinessTest
java -cp "$TASK_TEST" io.ts7.carplay.PlatformCleanupTest immediate
java -cp "$TASK_TEST" io.ts7.carplay.PlatformCleanupTest late
java -cp "$TASK_TEST" io.ts7.carplay.PlatformCleanupTest admission
