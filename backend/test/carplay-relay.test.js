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
const READINESS_FIELDS = [
  "coreInitialization", "jni", "bluetoothApi", "rfcomm", "localOnlyHotspot", "multicast",
  "mdns", "tcpBind", "udpBind", "networkBinding", "surface", "audioTrack",
];
const READINESS_ERROR_CODES = {
  PASS: ["NONE"],
  PERMISSION_DENIED: ["PERMISSION_MISSING"],
  UNAVAILABLE: [
    "API_UNAVAILABLE", "SERVICE_UNAVAILABLE", "HARDWARE_UNAVAILABLE", "RADIO_DISABLED", "NETWORK_UNAVAILABLE",
    "HOTSPOT_UNSUPPORTED", "HOTSPOT_INCOMPATIBLE", "HOTSPOT_DISALLOWED", "SURFACE_UNAVAILABLE",
  ],
  FAIL: [
    "PROBE_FAILED", "PROBE_TIMEOUT", "CORE_INIT_FAILED", "JNI_LOAD_FAILED", "RFCOMM_CREATE_FAILED",
    "HOTSPOT_START_FAILED", "MULTICAST_FAILED", "MDNS_BIND_FAILED", "TCP_BIND_FAILED", "UDP_BIND_FAILED",
    "NETWORK_BIND_FAILED", "SURFACE_INVALID", "AUDIO_CREATE_FAILED", "RESOURCE_RELEASE_FAILED",
  ],
  NOT_TESTED: ["NOT_RUN", "TEST_CANCELLED", "ABI_NOT_ARMV7", "PREVIOUS_PROBE_RUNNING"],
};
function platformPayload() {
  return {
    ...validPayload(), appVersion: "0.2.1-platform",
    platformReadiness: Object.fromEntries(READINESS_FIELDS.map((field, index) => [field,
      { status: "PASS", durationMs: index, errorCode: "NONE" }])),
  };
}
function request(payload) {
  return { method: "POST", headers: { "content-type": "application/json", "x-forwarded-proto": "https", "x-forwarded-for": "test" }, body: payload };
}

test("development identity readiness is distinct from phone trust; old previews remain blocked", () => {
  for (const version of ["1.0.0-dev", "1.0.0-dev.1"]) {
  const development = { ...platformPayload(), appVersion: version, authentication: "EXPERIMENTAL_IDENTITY_AVAILABLE" };
  assert.deepEqual(relay.validate(development), []);
  for (const version of ["0.1-alpha", "0.2-alpha", "0.2.1-platform"])
    assert.ok(relay.validate({ ...development, appVersion: version }).includes("authentication boundary"));
  for (const change of [{ mode: "CARPLAY" }, { carplayState: "STREAMING" }, { authentication: "APPLE_CERTIFIED" },
    { authentication: "PHONE_CONFIRMED_SESSION" }])
    assert.ok(relay.validate({ ...development, ...change }).includes("authentication boundary"));
  const streaming = { ...development, mode: "CARPLAY", carplayState: "STREAMING",
    authentication: "PHONE_CONFIRMED_SESSION", bluetoothState: "BOOTSTRAP_CONFIRMED", wifiState: "SESSION_LINK_CONFIRMED" };
  assert.deepEqual(relay.validate(streaming), []);
  assert.ok(relay.validate({ ...streaming, renderedFrames: 0 }).includes("streaming evidence missing"));
  const missing = { ...development }; delete missing.platformReadiness;
  assert.ok(relay.validate(missing).includes("missing platform readiness"));
  }
});
function response() {
  return { statusCode: 200, setHeader() {}, end(body) { this.body = JSON.parse(body); } };
}
test("connection repair accepts fixed stage/failure codes and rejects arbitrary error text", async () => {
  const report = { ...platformPayload(), appVersion: "1.0.0-dev.1", authentication: "EXPERIMENTAL_IDENTITY_AVAILABLE",
    events: ["CONNECTION_TEARDOWN_PENDING", "WIRELESS_HOTSPOT_START", "HOTSPOT_CHANNEL_UNKNOWN",
      "LOCAL_BLUETOOTH_ADDRESS_UNAVAILABLE", "CONNECTION_ATTEMPT_TIMEOUT"].map((code) => ({elapsedMs: 30, code, value: 0})) };
  assert.deepEqual(relay.validate(report), []);
  await assertRejectedBeforeGithub({...report, events: [{elapsedMs: 30, code: "PRIVATE_CANARY 00:11:22:33:44:55", value: 0}]},
    "private connection failure text");
  assert.ok(relay.validate({...report, appVersion: "1.0.0-dev.999"}).includes("unsupported report version"));
});
test("development rejects contradictory phone/session/streaming evidence before GitHub", async () => {
  for (const version of ["1.0.0-dev", "1.0.0-dev.1"]) {
  const streaming = { ...platformPayload(), appVersion: version, mode: "CARPLAY", carplayState: "STREAMING",
    authentication: "PHONE_CONFIRMED_SESSION", bluetoothState: "BOOTSTRAP_CONFIRMED", wifiState: "SESSION_LINK_CONFIRMED" };
  for (const change of [{ mode: "TEST_PATTERN", carplayState: "IDLE" }, { carplayState: "BT_DISCOVERY" },
    { mode: "IDLE" }, { decoderName: "NOT_STARTED" }, { decoderName: "UNKNOWN" },
    { videoWidth: 0 }, { videoHeight: 0 }, { renderedFrames: 0 }])
    await assertRejectedBeforeGithub({ ...streaming, ...change }, "contradictory development evidence");
  assert.deepEqual(relay.validate({ ...streaming, carplayState: "CARPLAY_NEGOTIATING", decoderName: "NOT_STARTED",
    renderedFrames: 0, videoWidth: 0, videoHeight: 0 }), []);
  }
});
async function assertRejectedBeforeGithub(payload, label) {
  resetTestState();
  let calls = 0;
  global.fetch = async () => { calls++; throw new Error("GitHub must not be called"); };
  assert.notEqual(relay.validate(payload).length, 0, label);
  const res = response();
  await relay.handleCarplayDiagnostics(request(payload), res);
  assert.equal(res.statusCode, 400, label);
  assert.equal(res.body.error, "invalid_payload", label);
  assert.equal(calls, 0, label);
  assert.doesNotMatch(JSON.stringify(res.body), /PRIVATE_CANARY|192\.0\.2\.123|00:11:22:33:44:55|BEGIN CERTIFICATE/, label);
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

test("DiPlay v0.2 keeps the same strict schema and fixed Issue 13; old alpha remains compatible", async () => {
  assert.deepEqual(relay.FIELDS, Object.keys(validPayload()));
  for (const appVersion of ["0.1-alpha", "0.2-alpha"]) {
    resetTestState();
    const payload = { ...validPayload(), appVersion };
    global.fetch = async (url, options) => {
      assert.equal(url, "https://api.github.com/repos/nnnc8/ts7-carplay-lite/issues/13/comments");
      assert.ok(JSON.parse(options.body).body.includes(`v${appVersion} diagnostics`));
      assert.doesNotMatch(JSON.parse(options.body).body, /Platform readiness/);
      return { ok: true, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-1" }) };
    };
    assert.deepEqual(relay.validate(payload), []);
    const res = response(); await relay.handleCarplayDiagnostics(request(payload), res);
    assert.equal(res.statusCode, 201);
    payload.ssid = "private-canary";
    assert.ok(relay.validate(payload).length);
    delete payload.ssid;
    payload.carplayState = "STREAMING";
    assert.ok(relay.validate(payload).includes("authentication boundary"));
  }
  assert.ok(relay.validate({ ...validPayload(), appVersion: "0.3" }).length);
});

test("platform preview posts all 12 sanitized readiness rows to fixed Issue 13 while authentication is blocked", async () => {
  const payload = platformPayload();
  const results = [
    ["FAIL", 60000, "CORE_INIT_FAILED"], ["FAIL", 18, "JNI_LOAD_FAILED"],
    ["PERMISSION_DENIED", 0, "PERMISSION_MISSING"], ["FAIL", 4, "RFCOMM_CREATE_FAILED"],
    ["UNAVAILABLE", 2, "HOTSPOT_INCOMPATIBLE"], ["PASS", 3, "NONE"],
    ["NOT_TESTED", 0, "PREVIOUS_PROBE_RUNNING"], ["PASS", 8, "NONE"],
    ["FAIL", 60000, "PROBE_TIMEOUT"], ["UNAVAILABLE", 0, "NETWORK_UNAVAILABLE"],
    ["UNAVAILABLE", 1, "SURFACE_UNAVAILABLE"], ["NOT_TESTED", 0, "ABI_NOT_ARMV7"],
  ];
  for (const [index, field] of READINESS_FIELDS.entries()) {
    const [status, durationMs, errorCode] = results[index];
    payload.platformReadiness[field] = { status, durationMs, errorCode };
  }
  payload.platformReadiness = Object.fromEntries(Object.entries(payload.platformReadiness).reverse());
  let comment;
  let calls = 0;
  process.env.GITHUB_ISSUE_NUMBER = "5";
  global.fetch = async (url, options) => {
    calls++;
    assert.equal(url, "https://api.github.com/repos/nnnc8/ts7-carplay-lite/issues/13/comments");
    comment = JSON.parse(options.body).body;
    return { ok: true, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-1" }) };
  };
  assert.deepEqual(relay.validate(payload), []);
  const res = response();
  await relay.handleCarplayDiagnostics(request(payload), res);
  assert.equal(res.statusCode, 201);
  assert.equal(calls, 1);
  assert.match(comment, /v0\.2\.1-platform diagnostics/);
  assert.match(comment, /authentication: BLOCKED_BY_AUTHENTICATION_REQUIREMENT/);
  assert.match(comment, /NOT YET A FUNCTIONAL CARPLAY RECEIVER/);
  assert.match(comment, /PASS does not authorize CARPLAY\/STREAMING/);
  assert.match(comment, /4000ms FIRST_FRAME 0/);
  const rows = comment.split("\n").filter((line) => line.startsWith("| "));
  assert.deepEqual(rows, ["| Probe | Status | Duration (ms) | Error code |", "| --- | --- | ---: | --- |",
    ...READINESS_FIELDS.map((field, index) => `| ${field} | ${results[index].join(" | ")} |`)]);
  assert.doesNotMatch(comment, /\[object Object\]/);
});

test("readiness is optional on both old alpha versions and strictly validated when present", async () => {
  for (const appVersion of ["0.1-alpha", "0.2-alpha"]) {
    resetTestState();
    const payload = { ...platformPayload(), appVersion };
    global.fetch = async (url, options) => {
      assert.equal(url, "https://api.github.com/repos/nnnc8/ts7-carplay-lite/issues/13/comments");
      assert.match(JSON.parse(options.body).body, /\| audioTrack \| PASS \| 11 \| NONE \|/);
      return { ok: true, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-1" }) };
    };
    assert.deepEqual(relay.validate(payload), []);
    const res = response();
    await relay.handleCarplayDiagnostics(request(payload), res);
    assert.equal(res.statusCode, 201);
    payload.platformReadiness.jni.errorCode = "PRIVATE_CANARY";
    await assertRejectedBeforeGithub(payload, appVersion);
  }
});

test("every probe accepts exactly the specified status/code pairs and integer duration boundaries", () => {
  for (const field of READINESS_FIELDS) {
    for (const [status, codes] of Object.entries(READINESS_ERROR_CODES)) {
      for (const errorCode of codes) {
        for (const durationMs of [0, 60000]) {
          const payload = platformPayload();
          payload.platformReadiness[field] = { status, durationMs, errorCode };
          assert.deepEqual(relay.validate(payload), [], `${field}: ${status}/${errorCode}/${durationMs}`);
        }
      }
    }
  }
});

test("requires readiness for the platform version and preserves schemaVersion/reportType restrictions", async () => {
  const missing = { ...validPayload(), appVersion: "0.2.1-platform" };
  await assertRejectedBeforeGithub(missing, "missing platformReadiness");
  for (const [field, value] of [["schemaVersion", 2], ["schemaVersion", "1"], ["reportType", "diagnostic"],
    ["appVersion", "0.2.1"], ["appVersion", "0.2.1-platform-unknown"]]) {
    const payload = platformPayload();
    payload[field] = value;
    await assertRejectedBeforeGithub(payload, field);
  }
});

test("rejects malformed readiness sections and every missing, extra or renamed probe before GitHub", async () => {
  for (const value of [undefined, null, false, 42, "PRIVATE_CANARY", [], {}]) {
    const payload = platformPayload();
    payload.platformReadiness = value;
    await assertRejectedBeforeGithub(payload, "readiness must be a complete object");
  }
  for (const field of READINESS_FIELDS) {
    const missing = platformPayload();
    delete missing.platformReadiness[field];
    await assertRejectedBeforeGithub(missing, `missing ${field}`);
    const renamed = platformPayload();
    renamed.platformReadiness.unknownProbe = renamed.platformReadiness[field];
    delete renamed.platformReadiness[field];
    await assertRejectedBeforeGithub(renamed, `renamed ${field} with 12 keys`);
  }
  const extra = platformPayload();
  extra.platformReadiness.unknownProbe = { status: "PASS", durationMs: 0, errorCode: "NONE" };
  await assertRejectedBeforeGithub(extra, "extra probe");
});

test("each of the 12 readiness results requires exactly status, durationMs and errorCode", async () => {
  for (const field of READINESS_FIELDS) {
    for (const value of [null, false, 42, "PRIVATE_CANARY", [], {}]) {
      const payload = platformPayload();
      payload.platformReadiness[field] = value;
      await assertRejectedBeforeGithub(payload, `${field}: malformed result`);
    }
    for (const key of ["status", "durationMs", "errorCode"]) {
      const missing = platformPayload();
      delete missing.platformReadiness[field][key];
      await assertRejectedBeforeGithub(missing, `${field}: missing ${key}`);
      const renamed = platformPayload();
      renamed.platformReadiness[field].unknownField = renamed.platformReadiness[field][key];
      delete renamed.platformReadiness[field][key];
      await assertRejectedBeforeGithub(renamed, `${field}: renamed ${key} with 3 keys`);
    }
    const extra = platformPayload();
    extra.platformReadiness[field].message = "PRIVATE_CANARY";
    await assertRejectedBeforeGithub(extra, `${field}: extra result field`);
  }
});

test("readiness durations must be finite integers from 0 to 60000 without coercion", async () => {
  for (const field of READINESS_FIELDS) {
    for (const durationMs of [-1, 60001, 0.5, "0", null, true, Infinity, -Infinity, NaN, {}, []]) {
      const payload = platformPayload();
      payload.platformReadiness[field].durationMs = durationMs;
      await assertRejectedBeforeGithub(payload, `${field}: invalid duration`);
    }
  }
});

test("rejects unsupported statuses, arbitrary codes and every status/code mismatch before GitHub", async () => {
  for (const status of ["FAILED", "TIMEOUT", "pass", "PASS\n", "constructor", "toString", "__proto__", "PRIVATE_CANARY", null, true, [], {}]) {
    const payload = platformPayload();
    payload.platformReadiness.coreInitialization.status = status;
    await assertRejectedBeforeGithub(payload, "invalid readiness status");
  }
  for (const errorCode of ["UNKNOWN", "PRIVATE_CANARY | injected\n# heading", "constructor", null, true, [], {}]) {
    const payload = platformPayload();
    payload.platformReadiness.coreInitialization.errorCode = errorCode;
    await assertRejectedBeforeGithub(payload, "invalid readiness code");
  }
  const allCodes = Object.values(READINESS_ERROR_CODES).flat();
  for (const [status, codes] of Object.entries(READINESS_ERROR_CODES)) {
    for (const errorCode of allCodes.filter((code) => !codes.includes(code))) {
      const payload = platformPayload();
      payload.platformReadiness.jni = { status, durationMs: 1, errorCode };
      await assertRejectedBeforeGithub(payload, `${status}/${errorCode}`);
    }
  }
});

test("private identifiers, credentials, certificates and raw exceptions are rejected at every readiness depth", async () => {
  const fixtures = {
    ssid: "PRIVATE_CANARY_NETWORK", bssid: "00:11:22:33:44:55", mac: "00:11:22:33:44:55", ip: "192.0.2.123",
    peer: { name: "PRIVATE_CANARY_PHONE" }, device: "PRIVATE_CANARY_DEVICE",
    credentials: { password: "PRIVATE_CANARY_PASSWORD" }, certs: "-----BEGIN CERTIFICATE-----\nPRIVATE_CANARY\n-----END CERTIFICATE-----",
    rawException: "java.lang.Exception: PRIVATE_CANARY", repository: "PRIVATE_CANARY_DESTINATION", issueNumber: "5",
    "PRIVATE_CANARY | unknown key": "PRIVATE_CANARY",
  };
  for (const [key, value] of Object.entries(fixtures)) {
    for (const depth of ["payload", "platformReadiness", ...READINESS_FIELDS]) {
      const payload = platformPayload();
      const section = depth === "payload" ? payload : depth === "platformReadiness"
        ? payload.platformReadiness : payload.platformReadiness[depth];
      section[key] = value;
      await assertRejectedBeforeGithub(payload, `${depth}: private field`);
    }
    for (const key of ["status", "durationMs", "errorCode"]) {
      const payload = platformPayload();
      payload.platformReadiness.localOnlyHotspot[key] = value;
      await assertRejectedBeforeGithub(payload, `private value in ${key}`);
    }
  }
});

test("all-PASS readiness cannot authorize CARPLAY, STREAMING or an authentication success claim", async () => {
  for (const [field, value] of [["mode", "CARPLAY"], ["carplayState", "STREAMING"], ["authentication", "LEGAL_IMPLEMENTATION_AVAILABLE"]]) {
    const payload = platformPayload();
    payload[field] = value;
    assert.ok(relay.validate(payload).includes("authentication boundary"));
    await assertRejectedBeforeGithub(payload, field);
  }
});

test("deduplication includes sanitized readiness results and ignores their input key order", async () => {
  const comments = [];
  global.fetch = async (url, options) => {
    assert.equal(url, "https://api.github.com/repos/nnnc8/ts7-carplay-lite/issues/13/comments");
    comments.push(JSON.parse(options.body).body);
    return { ok: true, json: async () => ({ html_url: "https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-2" }) };
  };
  const first = response();
  await relay.handleCarplayDiagnostics(request(platformPayload()), first);
  const reordered = platformPayload();
  reordered.platformReadiness = Object.fromEntries(Object.entries(reordered.platformReadiness).reverse()
    .map(([field, result]) => [field, { errorCode: result.errorCode, durationMs: result.durationMs, status: result.status }]));
  const duplicate = response();
  await relay.handleCarplayDiagnostics(request(reordered), duplicate);
  const changed = platformPayload();
  changed.platformReadiness.jni = { status: "FAIL", durationMs: 1, errorCode: "JNI_LOAD_FAILED" };
  const different = response();
  await relay.handleCarplayDiagnostics(request(changed), different);
  assert.equal(first.statusCode, 201);
  assert.equal(duplicate.statusCode, 200);
  assert.equal(duplicate.body.duplicate, true);
  assert.equal(duplicate.body.reportHash, first.body.reportHash);
  assert.equal(different.statusCode, 201);
  assert.notEqual(different.body.reportHash, first.body.reportHash);
  assert.equal(comments.length, 2);
  assert.match(comments[0], /\| jni \| PASS \| 1 \| NONE \|/);
  assert.match(comments[1], /\| jni \| FAIL \| 1 \| JNI_LOAD_FAILED \|/);
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
