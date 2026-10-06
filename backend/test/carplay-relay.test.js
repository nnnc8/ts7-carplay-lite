"use strict";
const assert = require("node:assert/strict");
const { test, beforeEach, afterEach } = require("node:test");
const relay = require("../lib/carplay-relay");
const { resetTestState } = require("../lib/diagnostic-relay");

function validPayload() {
  return {
    schemaVersion: 1, reportType: "carplay-alpha", appVersion: "0.1-alpha", timestamp: "2026-10-06T16:00:00Z",
    mode: "TEST_PATTERN", carplayState: "IDLE", bluetoothState: "LINK_OBSERVED", wifiState: "NETWORK_OBSERVED",
    authentication: "BLOCKED_BY_AUTHENTICATION_REQUIREMENT", decoderName: "OMX.sprd.h264.decoder",
    lastDisconnectReason: "NONE", lastPlaybackReason: "NONE", sessionUptimeMs: 0, videoWidth: 1280, videoHeight: 720,
    targetFps: 30, measuredFps: 29.7, renderedFrames: 150, droppedVideoFrames: 2, droppedPackets: 2,
    videoQueueDepth: 1, decoderLatencyMs: 34.5, decoderRestartCount: 0, carplayReconnectCount: 0,
    availableRamMb: 496, lowMemory: false, networkConnected: true, wifiRssiDbm: -41, wifiLinkSpeedMbps: 65,
    wifiFrequencyMHz: 2437, audioSampleRate: 0, audioChannels: 0, audioUnderruns: 0,
    events: [{ elapsedMs: 4000, code: "FIRST_FRAME", value: 0 }],
  };
}
function request(payload) {
  return { method: "POST", headers: { "content-type": "application/json", "x-forwarded-proto": "https", "x-forwarded-for": "test" }, body: payload };
}
function response() {
  return { statusCode: 200, setHeader() {}, end(body) { this.body = JSON.parse(body); } };
}
let originalFetch;
beforeEach(() => {
  originalFetch = global.fetch;
  process.env.GITHUB_TOKEN = "test-token-not-a-real-secret";
  resetTestState();
});
afterEach(() => { global.fetch = originalFetch; delete process.env.GITHUB_TOKEN; delete process.env.GITHUB_ISSUE_NUMBER; });

test("alpha writes only to fixed Issue 13 and labels synthetic video", async () => {
  let endpoint;
  let comment;
  process.env.GITHUB_ISSUE_NUMBER = "5";
  global.fetch = async (url, options) => {
    endpoint = url;
    comment = JSON.parse(options.body).body;
    return { ok: true, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-1" }) };
  };
  const res = response();
  await relay.handleCarplayDiagnostics(request(validPayload()), res);
  assert.equal(res.statusCode, 201);
  assert.equal(endpoint, "https://api.github.com/repos/nnnc8/ts7-carplay-lite/issues/13/comments");
  assert.match(comment, /NOT YET A FUNCTIONAL CARPLAY RECEIVER/);
  assert.match(comment, /TEST_PATTERN/);
});

test("rejects every private field and arbitrary destination before contacting GitHub", async () => {
  const privateFields = ["imei", "imsi", "phoneNumber", "appleId", "email", "ssid", "bssid", "mac", "ip", "location", "androidId", "serial", "contacts", "mediaLibrary", "repository", "issueNumber"];
  global.fetch = async () => { throw new Error("GitHub must not be called"); };
  for (const field of privateFields) {
    resetTestState(); // Each schema case has its own rate-limit window.
    const payload = validPayload();
    payload[field] = "private-canary";
    const res = response();
    await relay.handleCarplayDiagnostics(request(payload), res);
    assert.equal(res.statusCode, 400, field);
    assert.doesNotMatch(JSON.stringify(res.body), /private-canary/);
  }
});

test("rejects arbitrary event/decoder/error text", () => {
  for (const value of ["user@example.test", "192.0.2.1", "<script>", "00:11:22:33:44:55", "private SSID"]) {
    const payload = validPayload();
    payload.events[0].code = value;
    assert.notEqual(relay.validate(payload).length, 0);
    payload.events[0].code = "FIRST_FRAME";
    payload.decoderName = value;
    assert.notEqual(relay.validate(payload).length, 0);
    payload.decoderName = "OMX.sprd.h264.decoder";
    payload.lastDisconnectReason = value;
    assert.notEqual(relay.validate(payload).length, 0);
  }
  const nested = validPayload();
  nested.events[0].message = "private";
  assert.notEqual(relay.validate(nested).length, 0);
});

test("bounds events, video queues, resolution and metrics", () => {
  for (const [field, value] of [["videoQueueDepth", 5], ["videoWidth", 1920], ["videoHeight", 1080], ["targetFps", 60], ["measuredFps", Infinity], ["availableRamMb", "496"], ["wifiRssiDbm", -128]]) {
    const payload = validPayload();
    payload[field] = value;
    assert.notEqual(relay.validate(payload).length, 0, field);
  }
  const payload = validPayload();
  payload.events = Array.from({ length: 201 }, () => ({ elapsedMs: 1, code: "FIRST_FRAME", value: 0 }));
  assert.notEqual(relay.validate(payload).length, 0);
});

test("does not accept simulated CarPlay claims while authentication is blocked", () => {
  for (const [field, value] of [["mode", "CARPLAY"], ["carplayState", "STREAMING"], ["authentication", "LEGAL_IMPLEMENTATION_AVAILABLE"]]) {
    const payload = validPayload(); payload[field] = value;
    assert.ok(relay.validate(payload).includes("authentication boundary"));
  }
});

test("safe error when token is absent; no GitHub call", async () => {
  delete process.env.GITHUB_TOKEN;
  global.fetch = async () => { throw new Error("must not call"); };
  const res = response();
  await relay.handleCarplayDiagnostics(request(validPayload()), res);
  assert.equal(res.statusCode, 503);
  assert.equal(res.body.error, "relay_not_configured");
});

test("HTTPS, method, size and rate limits apply to alpha endpoint", async () => {
  const badMethod = response();
  await relay.handleCarplayDiagnostics({ method: "GET", headers: {} }, badMethod);
  assert.equal(badMethod.statusCode, 405);
  const insecure = request(validPayload()); insecure.headers["x-forwarded-proto"] = "http";
  const badProtocol = response(); await relay.handleCarplayDiagnostics(insecure, badProtocol);
  assert.equal(badProtocol.statusCode, 400);
  const oversized = response(); await relay.handleCarplayDiagnostics(request("x".repeat(32769)), oversized);
  assert.equal(oversized.statusCode, 413);
  resetTestState();
  for (let i = 0; i < 10; i++) await relay.handleCarplayDiagnostics(request({}), response());
  const limited = response(); await relay.handleCarplayDiagnostics(request({}), limited);
  assert.equal(limited.statusCode, 429);
});

test("suppresses identical alpha reports in the same warm instance", async () => {
  let calls = 0;
  global.fetch = async () => { calls++; return { ok: true, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-2" }) }; };
  const first = response(); const second = response();
  await relay.handleCarplayDiagnostics(request(validPayload()), first);
  await relay.handleCarplayDiagnostics(request(validPayload()), second);
  assert.equal(first.statusCode, 201); assert.equal(second.statusCode, 200);
  assert.equal(second.body.duplicate, true); assert.equal(calls, 1);
});
