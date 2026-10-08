"use strict";
const fs = require("node:fs");
const assert = require("node:assert/strict");
const { validate, sanitize } = require("../../backend/lib/carplay-relay");

const output = fs.readFileSync(process.argv[2], "utf8");
const authentication = process.argv[3] === "--authentication";
const line = output.split(/\r?\n/).find((value) => value.startsWith(authentication
  ? "INSTRUMENTATION_STATUS: authenticationReport=" : "INSTRUMENTATION_STATUS: platformReport="));
assert.ok(line, "Actual emulator public report required");
const payload = JSON.parse(line.slice(line.indexOf("=") + 1));
assert.deepEqual(validate(payload), [], "Android output must pass the actual server contract");
assert.deepEqual(sanitize(payload), payload, "No fields removed from fixed public report");
assert.equal(payload.appVersion, "1.0.0-dev");
if (authentication) {
  assert.equal(payload.authentication, "EXPERIMENTAL_IDENTITY_AVAILABLE");
  assert.equal(payload.mode, "IDLE");
  assert.equal(payload.renderedFrames, 0);
} else {
  assert.equal(payload.platformReadiness.jni.status, "NOT_TESTED");
  assert.equal(payload.platformReadiness.jni.errorCode, "ABI_NOT_ARMV7");
  assert.equal(payload.authentication, "BLOCKED_BY_AUTHENTICATION_REQUIREMENT");
}
console.log("Cross-component PASS: actual API27 public JSON accepted by fixed Issue13 validator; no GitHub write");
