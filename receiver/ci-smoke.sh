#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1280x720
adb shell wm density 160
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 27 ]]
adb install -r dist/TS7-CarPlay-Lite-DiPlay-v0.2-alpha.apk
adb shell am start -W -n io.ts7.carplay/.MainActivity > dist/normal-startup.txt
for task_startup_round in $(seq 1 20); do
  adb shell uiautomator dump /sdcard/ts7-startup.xml >/dev/null
  adb pull /sdcard/ts7-startup.xml dist/normal-startup.xml >/dev/null
  if grep -Fq 'DiPlay ready' dist/normal-startup.xml; then break; fi
  sleep 1
done
grep -Fq 'DiPlay ready' dist/normal-startup.xml
grep -Fq 'authentication blocked' dist/normal-startup.xml
adb shell screencap -p /sdcard/ts7-startup.png
adb pull /sdcard/ts7-startup.png dist/normal-startup.png
adb shell am force-stop io.ts7.carplay
adb install -r dist/TS7-CarPlay-Lite-DiPlay-v0.2-alpha-instrumented.apk
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
grep -Fq 'INSTRUMENTATION_CODE: -1' dist/renderer-smoke.txt
[[ -s dist/renderer-smoke.png ]]
