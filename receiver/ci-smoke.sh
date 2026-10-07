#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1280x720
adb shell wm density 160
adb install -r dist/TS7-CarPlay-Lite-v0.1-alpha-core-preview-instrumented.apk
timeout 60s adb shell am instrument -w io.ts7.carplay/io.ts7.carplay.RendererInstrumentation > dist/renderer-smoke.txt &
TASK_INSTRUMENT_PID=$!
for task_round in $(seq 1 45); do
  if grep -Fq 'SURFACE_FRAMES_READY' dist/renderer-smoke.txt; then
    adb shell screencap -p /sdcard/ts7-alpha-ci.png
    adb pull /sdcard/ts7-alpha-ci.png dist/renderer-smoke.png
    break
  fi
  if ! kill -0 "$TASK_INSTRUMENT_PID" 2>/dev/null; then break; fi
  sleep 1
done
TASK_INSTRUMENT_STATUS=0
wait "$TASK_INSTRUMENT_PID" || TASK_INSTRUMENT_STATUS=$?
adb logcat -d -t 500 > dist/renderer-logcat.txt
[[ "$TASK_INSTRUMENT_STATUS" -eq 0 ]]
grep -Fq 'PASS: Android 8.1' dist/renderer-smoke.txt
grep -Fq 'CORE_API27_RUNTIME_PASS' dist/renderer-smoke.txt
grep -Fq 'INSTRUMENTATION_CODE: -1' dist/renderer-smoke.txt
[[ -s dist/renderer-smoke.png ]]
