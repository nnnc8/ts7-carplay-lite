#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/diagnostic-v0.2-tests"
rm -rf "$BUILD"
mkdir -p "$BUILD"

javac \
  -source 8 \
  -target 8 \
  -encoding UTF-8 \
  -d "$BUILD" \
  "$ROOT/diagnostic-app/src/main/java/io/ts7diag/tool/JsonEncoder.java" \
  "$ROOT/diagnostic-app/src/main/java/io/ts7diag/tool/ReportSanitizer.java" \
  "$ROOT/diagnostic-app/src/test/java/io/ts7diag/tool/ReportSanitizerTest.java"

java -cp "$BUILD" io.ts7diag.tool.ReportSanitizerTest
