#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1280x720
adb shell wm density 160
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 27 ]]
adb install -r dist/TS7-CarPlay-Lite-DiPlay-v0.2.1-platform.apk
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
adb install -r dist/TS7-CarPlay-Lite-DiPlay-v0.2.1-platform-instrumented.apk
adb shell pm revoke io.ts7.carplay android.permission.ACCESS_FINE_LOCATION
timeout 150s adb shell am instrument -w io.ts7.carplay/io.ts7.carplay.ReadinessInstrumentation > dist/platform-readiness-smoke.txt &
TASK_READINESS_PID=$!
for task_readiness_round in $(seq 1 140); do
  if grep -Fq 'PLATFORM_READINESS_READY' dist/platform-readiness-smoke.txt; then
    adb shell screencap -p /sdcard/ts7-platform-ci.png
    adb pull /sdcard/ts7-platform-ci.png dist/platform-readiness-smoke.png
    break
  fi
  if ! kill -0 "$TASK_READINESS_PID" 2>/dev/null; then break; fi
  sleep 1
done
TASK_READINESS_STATUS=0
wait "$TASK_READINESS_PID" || TASK_READINESS_STATUS=$?
[[ "$TASK_READINESS_STATUS" -eq 0 ]]
grep -Fq 'PASS: Android 8.1 readiness 12 probes' dist/platform-readiness-smoke.txt
grep -Fq 'INSTRUMENTATION_CODE: -1' dist/platform-readiness-smoke.txt
[[ -s dist/platform-readiness-smoke.png ]]
node receiver/tools/verify_readiness_report.js dist/platform-readiness-smoke.txt
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
