"use strict";

const assert = require("node:assert/strict");
const { afterEach, beforeEach, test } = require("node:test");
const relay = require("../lib/diagnostic-relay");

function validPayload() {
  return {
    schemaVersion: 1,
    appVersion: "0.2",
    timestamp: "2026-10-06T00:00:00Z",
    device: {
      androidRelease: "8.1.0",
      sdkInt: 27,
      model: "TS7",
      manufacturer: "Example",
      brand: "Example",
      device: "ts7",
      product: "ts7",
      hardware: "SL8141E",
      board: "sl8141e",
      buildDisplay: "V12.1.1",
      cpuAbi: "armeabi-v7a",
      cpuAbi2: "",
      osArch: "armv7l",
      supportedAbis: ["armeabi-v7a"],
    },
    memory: { logicalCores: 4, totalRamMb: 2048, availableRamMb: 900, lowMemory: false, thresholdMb: 256 },
    display: { realWidth: 1024, realHeight: 600, densityDpi: 160, refreshRateHz: 60, orientation: "landscape" },
    storage: { dataTotalMb: 32000, dataAvailableMb: 12000 },
    graphics: { openGlEsVersion: "3.0", renderer: "not probed", features: { wifi: true, bluetooth: true, bluetoothLe: false, usbHost: true, touchscreen: true } },
    network: { wifiConnected: true, wifiEnabled: true, linkSpeedMbps: 72, rssiDbm: -50, frequencyMHz: 2412 },
    bluetooth: { hasAdapter: true, enabled: true },
    usb: { deviceCount: 1, devices: [{ vendorId: 1234, productId: 5678, deviceClass: 0, deviceSubclass: 0, deviceProtocol: 0 }] },
    mediaCodec: { videoAvcDecoderCount: 1, startupMode: "safe enumeration only", decoders: [{ name: "OMX.example.avc.decoder", classification: "likely hardware/vendor", supportedWidths: "[16, 1920]", supportedHeights: "[16, 1080]", supportedFrameRates: "[1, 60]", bitrateRange: "[1, 20000000]" }] },
    probes: { device: { status: "PASS", durationMs: 3 }, mediaCodec: { status: "PASS", durationMs: 8 } },
  };
}

function responseRecorder() {
  const headers = {};
  return {
    headers,
    statusCode: 200,
    body: "",
    setHeader(key, value) { headers[key] = value; },
    end(value) { this.body = value || ""; },
  };
}

function request(body) {
  return {
    method: "POST",
    headers: { "content-type": "application/json", "x-forwarded-proto": "https", "x-forwarded-for": "test-client" },
    body,
  };
}

let originalFetch;
beforeEach(() => {
  originalFetch = global.fetch;
  process.env.GITHUB_TOKEN = "test-token-not-a-real-secret";
  delete process.env.RATE_LIMIT_MAX;
  relay.resetTestState();
});

afterEach(() => {
  global.fetch = originalFetch;
  delete process.env.GITHUB_TOKEN;
  delete process.env.RATE_LIMIT_MAX;
});

test("accepts a valid payload and creates a GitHub comment", async () => {
  let requestBody;
  global.fetch = async (url, options) => {
    requestBody = JSON.parse(options.body);
    return { ok: true, status: 201, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-1" }) };
  };
  const res = responseRecorder();
  await relay.handleDiagnostics(request(validPayload()), res);
  assert.equal(res.statusCode, 201);
  assert.equal(JSON.parse(res.body).success, true);
  assert.match(requestBody.body, /TS7 Diagnostic v0\.2 submission/);
});

test("rejects an oversized payload before GitHub is called", async () => {
  let called = false;
  global.fetch = async () => { called = true; throw new Error("must not call"); };
  const oversized = JSON.stringify(validPayload()).replace("TS7", "TS7" + "x".repeat(relay.MAX_PAYLOAD_BYTES));
  const res = responseRecorder();
  await relay.handleDiagnostics(request(oversized), res);
  assert.equal(res.statusCode, 413);
  assert.equal(called, false);
});

test("rejects unknown sensitive fields", async () => {
  const payload = validPayload();
  payload.network.ssid = "private-network";
  const res = responseRecorder();
  await relay.handleDiagnostics(request(payload), res);
  assert.equal(res.statusCode, 400);
  assert.match(res.body, /invalid_payload/);
});

test("rejects missing required sections", async () => {
  const payload = validPayload();
  delete payload.mediaCodec;
  const res = responseRecorder();
  await relay.handleDiagnostics(request(payload), res);
  assert.equal(res.statusCode, 400);
  assert.match(res.body, /missing field: mediaCodec/);
});

test("escapes Markdown and does not render HTML", async () => {
  const payload = validPayload();
  payload.device.model = "TS7 [test] <script>alert(1)</script>";
  let requestBody;
  global.fetch = async (url, options) => {
    requestBody = JSON.parse(options.body);
    return { ok: true, status: 201, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-2" }) };
  };
  const res = responseRecorder();
  await relay.handleDiagnostics(request(payload), res);
  assert.equal(res.statusCode, 201);
  assert.doesNotMatch(requestBody.body, /<script>/);
  assert.match(requestBody.body, /\\\[test\\\]/);
});

test("turns a GitHub API failure into a safe relay error", async () => {
  global.fetch = async () => ({ ok: false, status: 500, json: async () => ({}) });
  const res = responseRecorder();
  await relay.handleDiagnostics(request(validPayload()), res);
  assert.equal(res.statusCode, 502);
  assert.match(res.body, /github_relay_error/);
});

test("suppresses identical payloads during the duplicate window", async () => {
  let calls = 0;
  global.fetch = async () => {
    calls += 1;
    return { ok: true, status: 201, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-3" }) };
  };
  const first = responseRecorder();
  const second = responseRecorder();
  await relay.handleDiagnostics(request(validPayload()), first);
  await relay.handleDiagnostics(request(validPayload()), second);
  assert.equal(first.statusCode, 201);
  assert.equal(second.statusCode, 200);
  assert.equal(JSON.parse(second.body).duplicate, true);
  assert.equal(calls, 1);
});
