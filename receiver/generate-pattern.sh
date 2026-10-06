#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$TASK_ROOT/receiver/src/main/assets"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i 'testsrc=size=1280x720:rate=30:duration=4' \
  -c:v libx264 -threads 1 -preset veryfast -profile:v baseline -pix_fmt yuv420p \
  -x264-params 'aud=1:repeat-headers=1:keyint=30:min-keyint=30:scenecut=0:bframes=0' \
  -an -f h264 "$TASK_ROOT/receiver/src/main/assets/ts7-pattern.h264"
shasum -a 256 "$TASK_ROOT/receiver/src/main/assets/ts7-pattern.h264"
