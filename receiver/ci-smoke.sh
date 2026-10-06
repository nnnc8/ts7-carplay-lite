#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1280x720
adb shell wm density 160
adb install -r dist/TS7-CarPlay-Lite-v0.1-alpha-instrumented.apk
adb shell am instrument -w io.ts7.carplay/io.ts7.carplay.RendererInstrumentation > dist/renderer-smoke.txt &
TASK_INSTRUMENT_PID=$!
for task_round in $(seq 1 45); do
  if rg -q 'SURFACE_FRAMES_READY' dist/renderer-smoke.txt; then
    adb shell screencap -p /sdcard/ts7-alpha-ci.png
    adb pull /sdcard/ts7-alpha-ci.png dist/renderer-smoke.png
    break
  fi
  if ! kill -0 "$TASK_INSTRUMENT_PID" 2>/dev/null; then break; fi
  sleep 1
done
wait "$TASK_INSTRUMENT_PID"
adb logcat -d -t 500 > dist/renderer-logcat.txt
rg -q 'PASS: Android 8.1' dist/renderer-smoke.txt
rg -q 'INSTRUMENTATION_CODE: -1' dist/renderer-smoke.txt
