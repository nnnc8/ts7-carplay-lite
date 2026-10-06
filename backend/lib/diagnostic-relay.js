"use strict";

const crypto = require("node:crypto");

const MAX_PAYLOAD_BYTES = 32 * 1024;
const MAX_COMMENT_BYTES = 60 * 1024;
const DEFAULT_RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000;
const DEFAULT_RATE_LIMIT_MAX = 10;
const DUPLICATE_WINDOW_MS = 24 * 60 * 60 * 1000;

const REQUIRED_TOP_LEVEL_FIELDS = [
  "schemaVersion",
  "appVersion",
  "timestamp",
  "device",
  "memory",
  "display",
  "storage",
  "graphics",
  "network",
  "bluetooth",
  "usb",
  "mediaCodec",
  "probes",
];

const ALLOWED_TOP_LEVEL_FIELDS = new Set(REQUIRED_TOP_LEVEL_FIELDS);
const SENSITIVE_KEY = /(^|_|-)(imei|imsi|sim|phone|account|email|contact|location|gps|ssid|bssid|mac|androidid|serial|ip|password|fingerprint|userfile|devicepath|path)($|_|-)/i;

const SECTION_FIELDS = {
  device: new Set([
    "androidRelease",
    "sdkInt",
    "model",
    "manufacturer",
    "brand",
    "device",
    "product",
    "hardware",
    "board",
    "buildDisplay",
    "cpuAbi",
    "cpuAbi2",
    "osArch",
    "supportedAbis",
  ]),
  memory: new Set(["logicalCores", "totalRamMb", "availableRamMb", "lowMemory", "thresholdMb"]),
  display: new Set(["realWidth", "realHeight", "densityDpi", "refreshRateHz", "orientation"]),
  storage: new Set(["dataTotalMb", "dataAvailableMb"]),
  graphics: new Set(["openGlEsVersion", "renderer", "features"]),
  network: new Set(["wifiConnected", "wifiEnabled", "linkSpeedMbps", "rssiDbm", "frequencyMHz"]),
  bluetooth: new Set(["hasAdapter", "enabled"]),
  usb: new Set(["deviceCount", "devices"]),
  mediaCodec: new Set(["videoAvcDecoderCount", "decoders", "startupMode", "advancedTest"]),
};

const rateBuckets = new Map();
const duplicateCache = new Map();

class RelayError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = "RelayError";
    this.status = status;
    this.code = code;
  }
}

async function handleDiagnostics(req, res) {
  try {
    if (req.method !== "POST") {
      return sendJson(res, 405, { success: false, error: "method_not_allowed" }, { Allow: "POST" });
    }
    const contentType = header(req, "content-type");
    if (!contentType.toLowerCase().startsWith("application/json")) {
      return sendJson(res, 415, { success: false, error: "content_type_must_be_json" });
    }
    const forwardedProtocol = header(req, "x-forwarded-proto");
    if (forwardedProtocol && forwardedProtocol.split(",")[0].trim().toLowerCase() !== "https") {
      return sendJson(res, 400, { success: false, error: "https_required" });
    }
    if (!allowRequest(clientAddress(req))) {
      return sendJson(res, 429, { success: false, error: "rate_limited" }, { "Retry-After": "900" });
    }

    const rawBody = await readBody(req);
    let payload;
    try {
      payload = JSON.parse(rawBody);
    } catch (error) {
      throw new RelayError(400, "invalid_json", "The request body must be valid JSON.");
    }

    const validationErrors = validatePayload(payload);
    if (validationErrors.length > 0) {
      return sendJson(res, 400, {
        success: false,
        error: "invalid_payload",
        details: validationErrors.slice(0, 5),
      });
    }

    const publicPayload = sanitizePublicPayload(payload);
    const serialized = JSON.stringify(publicPayload);
    const reportHash = crypto.createHash("sha256").update(serialized, "utf8").digest("hex");
    const duplicate = getFreshDuplicate(reportHash);
    if (duplicate) {
      return sendJson(res, 200, {
        success: true,
        duplicate: true,
        githubUrl: duplicate.githubUrl,
        reportHash,
      });
    }

    const comment = formatMarkdown(publicPayload);
    if (Buffer.byteLength(comment, "utf8") > MAX_COMMENT_BYTES) {
      throw new RelayError(413, "comment_too_large", "The sanitized report is too large for an issue comment.");
    }
    const githubUrl = await postGithubComment(comment);
    duplicateCache.set(reportHash, {
      githubUrl,
      expiresAt: Date.now() + DUPLICATE_WINDOW_MS,
    });
    return sendJson(res, 201, { success: true, githubUrl, reportHash });
  } catch (error) {
    if (error instanceof RelayError) {
      return sendJson(res, error.status, { success: false, error: error.code });
    }
    console.error("diagnostic relay failed", error && error.name ? error.name : "UnknownError");
    return sendJson(res, 500, { success: false, error: "internal_error" });
  }
}

function validatePayload(payload) {
  const errors = [];
  if (!isPlainObject(payload)) {
    return ["body must be an object"];
  }
  for (const key of Object.keys(payload)) {
    if (!ALLOWED_TOP_LEVEL_FIELDS.has(key)) {
      errors.push(`unknown field: ${key}`);
    }
  }
  for (const key of REQUIRED_TOP_LEVEL_FIELDS) {
    if (!Object.prototype.hasOwnProperty.call(payload, key)) {
      errors.push(`missing field: ${key}`);
    }
  }
  if (payload.schemaVersion !== 1) {
    errors.push("schemaVersion must be 1");
  }
  if (typeof payload.appVersion !== "string" || !/^0\.2(?:\.\d+)?$/.test(payload.appVersion)) {
    errors.push("appVersion must be a v0.2 version");
  }
  if (typeof payload.timestamp !== "string" || !Number.isFinite(Date.parse(payload.timestamp))) {
    errors.push("timestamp must be an ISO date string");
  }

  validateDevice(payload.device, errors);
  validateSimpleObject(payload.memory, "memory", SECTION_FIELDS.memory, errors);
  validateSimpleObject(payload.display, "display", SECTION_FIELDS.display, errors);
  validateSimpleObject(payload.storage, "storage", SECTION_FIELDS.storage, errors);
  validateGraphics(payload.graphics, errors);
  validateSimpleObject(payload.network, "network", SECTION_FIELDS.network, errors);
  validateSimpleObject(payload.bluetooth, "bluetooth", SECTION_FIELDS.bluetooth, errors);
  validateUsb(payload.usb, errors);
  validateMediaCodec(payload.mediaCodec, errors);
  validateProbes(payload.probes, errors);
  return errors;
}

function validateDevice(value, errors) {
  if (!validateObjectKeys(value, "device", SECTION_FIELDS.device, errors)) return;
  for (const key of ["androidRelease", "model", "manufacturer", "brand", "device", "product", "hardware", "board", "buildDisplay", "cpuAbi", "cpuAbi2", "osArch"]) {
    optionalString(value, key, `device.${key}`, errors);
  }
  optionalInteger(value, "sdkInt", "device.sdkInt", errors);
  optionalStringArray(value, "supportedAbis", "device.supportedAbis", errors);
}

function validateSimpleObject(value, path, allowedFields, errors) {
  if (!validateObjectKeys(value, path, allowedFields, errors)) return;
  for (const key of Object.keys(value)) {
    const field = value[key];
    if (typeof field === "string") {
      boundedString(field, `${path}.${key}`, errors);
    } else if (typeof field === "number") {
      finiteNumber(field, `${path}.${key}`, errors);
    } else if (typeof field !== "boolean") {
      errors.push(`${path}.${key} has an unsupported type`);
    }
  }
}

function validateGraphics(value, errors) {
  if (!validateObjectKeys(value, "graphics", SECTION_FIELDS.graphics, errors)) return;
  optionalString(value, "openGlEsVersion", "graphics.openGlEsVersion", errors);
  optionalString(value, "renderer", "graphics.renderer", errors);
  if (value.features !== undefined) {
    if (!validateObjectKeys(value.features, "graphics.features", new Set(["wifi", "bluetooth", "bluetoothLe", "usbHost", "touchscreen"]), errors)) return;
    for (const key of Object.keys(value.features)) {
      if (typeof value.features[key] !== "boolean") errors.push(`graphics.features.${key} must be boolean`);
    }
  }
}

function validateUsb(value, errors) {
  if (!validateObjectKeys(value, "usb", SECTION_FIELDS.usb, errors)) return;
  optionalInteger(value, "deviceCount", "usb.deviceCount", errors);
  if (value.devices !== undefined) {
    if (!Array.isArray(value.devices) || value.devices.length > 32) {
      errors.push("usb.devices must be an array of at most 32 items");
      return;
    }
    const fields = new Set(["vendorId", "productId", "deviceClass", "deviceSubclass", "deviceProtocol"]);
    value.devices.forEach((item, index) => {
      if (!validateObjectKeys(item, `usb.devices[${index}]`, fields, errors)) return;
      for (const key of Object.keys(item)) optionalInteger(item, key, `usb.devices[${index}].${key}`, errors);
    });
  }
}

function validateMediaCodec(value, errors) {
  if (!validateObjectKeys(value, "mediaCodec", SECTION_FIELDS.mediaCodec, errors)) return;
  optionalInteger(value, "videoAvcDecoderCount", "mediaCodec.videoAvcDecoderCount", errors);
  optionalString(value, "startupMode", "mediaCodec.startupMode", errors);
  if (value.advancedTest !== undefined) {
    if (!validateObjectKeys(value.advancedTest, "mediaCodec.advancedTest", new Set(["decoderInstantiation", "warning"]), errors)) return;
    optionalString(value.advancedTest, "decoderInstantiation", "mediaCodec.advancedTest.decoderInstantiation", errors);
    optionalString(value.advancedTest, "warning", "mediaCodec.advancedTest.warning", errors);
  }
  if (value.decoders !== undefined) {
    if (!Array.isArray(value.decoders) || value.decoders.length > 32) {
      errors.push("mediaCodec.decoders must be an array of at most 32 items");
      return;
    }
    const fields = new Set(["name", "classification", "supportedWidths", "supportedHeights", "supportedFrameRates", "bitrateRange", "capabilityStatus"]);
    value.decoders.forEach((item, index) => {
      if (!validateObjectKeys(item, `mediaCodec.decoders[${index}]`, fields, errors)) return;
      for (const key of Object.keys(item)) optionalString(item, key, `mediaCodec.decoders[${index}].${key}`, errors);
    });
  }
}

function validateProbes(value, errors) {
  if (!isPlainObject(value)) {
    errors.push("probes must be an object");
    return;
  }
  for (const key of Object.keys(value)) {
    if (!/^[A-Za-z][A-Za-z0-9]{0,31}$/.test(key)) {
      errors.push(`invalid probe name: ${key}`);
      continue;
    }
    const item = value[key];
    const allowed = new Set(["status", "durationMs", "error"]);
    if (!validateObjectKeys(item, `probes.${key}`, allowed, errors)) continue;
    if (item.status !== undefined && !["PASS", "FAILED", "TIMEOUT", "UNAVAILABLE"].includes(item.status)) {
      errors.push(`probes.${key}.status is invalid`);
    }
    optionalInteger(item, "durationMs", `probes.${key}.durationMs`, errors);
    optionalString(item, "error", `probes.${key}.error`, errors);
  }
}

function validateObjectKeys(value, path, allowedFields, errors) {
  if (!isPlainObject(value)) {
    errors.push(`${path} must be an object`);
    return false;
  }
  for (const key of Object.keys(value)) {
    if (SENSITIVE_KEY.test(key)) {
      errors.push(`${path}.${key} is not allowed`);
    } else if (!allowedFields.has(key)) {
      errors.push(`unknown field: ${path}.${key}`);
    }
  }
  return true;
}

function optionalString(object, key, path, errors) {
  if (object[key] !== undefined) {
    if (typeof object[key] !== "string") errors.push(`${path} must be a string`);
    else boundedString(object[key], path, errors);
  }
}

function optionalStringArray(object, key, path, errors) {
  if (object[key] === undefined) return;
  if (!Array.isArray(object[key]) || object[key].length > 16) {
    errors.push(`${path} must be an array of at most 16 strings`);
    return;
  }
  object[key].forEach((item, index) => {
    if (typeof item !== "string") errors.push(`${path}[${index}] must be a string`);
    else boundedString(item, `${path}[${index}]`, errors);
  });
}

function optionalInteger(object, key, path, errors) {
  if (object[key] !== undefined && (!Number.isInteger(object[key]) || object[key] < -1 || object[key] > 1000000000)) {
    errors.push(`${path} must be a bounded integer`);
  }
}

function boundedString(value, path, errors) {
  if (value.length > 512 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(value)) {
    errors.push(`${path} is too long or contains control characters`);
  }
}

function finiteNumber(value, path, errors) {
  if (!Number.isFinite(value) || Math.abs(value) > 1000000000) errors.push(`${path} must be bounded`);
}

function sanitizePublicPayload(payload) {
  return sanitizeValue(payload, null, 0);
}

function sanitizeValue(value, key, depth) {
  if (value === null || depth > 8 || (key && SENSITIVE_KEY.test(key))) return undefined;
  if (Array.isArray(value)) return value.map((item) => sanitizeValue(item, key, depth + 1)).filter((item) => item !== undefined);
  if (isPlainObject(value)) {
    const output = {};
    for (const [childKey, childValue] of Object.entries(value)) {
      const safe = sanitizeValue(childValue, childKey, depth + 1);
      if (safe !== undefined) output[childKey] = safe;
    }
    return output;
  }
  if (typeof value === "string") return value.slice(0, 512);
  if (typeof value === "number" || typeof value === "boolean") return value;
  return undefined;
}

function formatMarkdown(report) {
  const lines = [
    "## TS7 Diagnostic v0.2 submission",
    "",
    `Timestamp: ${markdownValue(report.timestamp)}`,
    "",
    `App: ${markdownValue(report.appVersion)}`,
    "",
  ];
  appendSection(lines, "Device", report.device);
  appendSection(lines, "Memory", report.memory);
  appendSection(lines, "Display", report.display);
  appendSection(lines, "Storage", report.storage);
  appendSection(lines, "Graphics", report.graphics);
  appendSection(lines, "Network", report.network);
  appendSection(lines, "Bluetooth", report.bluetooth);
  appendSection(lines, "USB", report.usb);
  appendSection(lines, "H.264 / MediaCodec", report.mediaCodec);
  appendSection(lines, "Probe status", report.probes);
  lines.push("", "_Only sanitized hardware diagnostic information is included._");
  return lines.join("\n");
}

function appendSection(lines, title, value) {
  lines.push(`### ${title}`);
  if (!value || typeof value !== "object") {
    lines.push("- unavailable", "");
    return;
  }
  for (const [key, item] of Object.entries(value)) {
    lines.push(`- ${markdownValue(key)}: ${renderValue(item)}`);
  }
  lines.push("");
}

function renderValue(value) {
  if (Array.isArray(value)) return value.map((item) => renderValue(item)).join(", ");
  if (value && typeof value === "object") {
    return Object.entries(value).map(([key, item]) => `${markdownValue(key)}=${renderValue(item)}`).join("; ");
  }
  return markdownValue(value);
}

function markdownValue(value) {
  return String(value === undefined || value === null ? "" : value)
    .replace(/\r?\n/g, " ")
    .replace(/[\\`*_{}[\]()#+.!|>~-]/g, "\\$&");
}

async function postGithubComment(comment) {
  const token = process.env.GITHUB_TOKEN && process.env.GITHUB_TOKEN.trim();
  if (!token) {
    throw new RelayError(503, "relay_not_configured", "GITHUB_TOKEN is not configured.");
  }
  const owner = process.env.GITHUB_OWNER || "nnnc8";
  const repo = process.env.GITHUB_REPO || "ts7-carplay-lite";
  const issue = process.env.GITHUB_ISSUE_NUMBER || "5";
  const endpoint = `https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/issues/${encodeURIComponent(issue)}/comments`;
  let response;
  try {
    response = await fetch(endpoint, {
      method: "POST",
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "User-Agent": "ts7-diagnostic-relay",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      body: JSON.stringify({ body: comment }),
    });
  } catch (error) {
    throw new RelayError(502, "github_relay_error", "GitHub request failed.");
  }
  if (!response.ok) {
    throw new RelayError(502, "github_relay_error", `GitHub returned HTTP ${response.status}.`);
  }
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw new RelayError(502, "github_relay_error", "GitHub returned invalid JSON.");
  }
  if (!body || typeof body.html_url !== "string" || !body.html_url.startsWith("https://github.com/")) {
    throw new RelayError(502, "github_relay_error", "GitHub response did not contain a safe comment URL.");
  }
  return body.html_url;
}

function readBody(req) {
  if (req.body !== undefined) {
    const raw = typeof req.body === "string" ? req.body : JSON.stringify(req.body);
    if (Buffer.byteLength(raw, "utf8") > MAX_PAYLOAD_BYTES) {
      throw new RelayError(413, "payload_too_large", "Payload exceeds 32 KB.");
    }
    return Promise.resolve(raw);
  }
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", (chunk) => {
      size += Buffer.byteLength(chunk);
      if (size > MAX_PAYLOAD_BYTES) {
        reject(new RelayError(413, "payload_too_large", "Payload exceeds 32 KB."));
        if (typeof req.destroy === "function") req.destroy();
        return;
      }
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", () => reject(new RelayError(400, "request_read_failed", "Request could not be read.")));
  });
}

function allowRequest(address) {
  const now = Date.now();
  const windowMs = boundedEnvNumber("RATE_LIMIT_WINDOW", DEFAULT_RATE_LIMIT_WINDOW_MS, 1000, 24 * 60 * 60 * 1000);
  const max = boundedEnvNumber("RATE_LIMIT_MAX", DEFAULT_RATE_LIMIT_MAX, 1, 1000);
  const bucket = rateBuckets.get(address) || { startedAt: now, count: 0 };
  if (now - bucket.startedAt >= windowMs) {
    bucket.startedAt = now;
    bucket.count = 0;
  }
  bucket.count += 1;
  rateBuckets.set(address, bucket);
  return bucket.count <= max;
}

function getFreshDuplicate(hash) {
  const item = duplicateCache.get(hash);
  if (!item) return null;
  if (item.expiresAt <= Date.now()) {
    duplicateCache.delete(hash);
    return null;
  }
  return item;
}

function boundedEnvNumber(name, fallback, min, max) {
  const parsed = Number(process.env[name]);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.floor(parsed)));
}

function clientAddress(req) {
  const forwarded = header(req, "x-forwarded-for");
  return (forwarded ? forwarded.split(",")[0].trim() : req.socket && req.socket.remoteAddress) || "unknown";
}

function header(req, name) {
  const wanted = name.toLowerCase();
  for (const [key, value] of Object.entries(req.headers || {})) {
    if (key.toLowerCase() === wanted) return Array.isArray(value) ? value[0] : String(value || "");
  }
  return "";
}

function sendJson(res, status, body, headers) {
  res.statusCode = status;
  if (typeof res.setHeader === "function") {
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    for (const [key, value] of Object.entries(headers || {})) res.setHeader(key, value);
  }
  res.end(JSON.stringify(body));
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function resetTestState() {
  rateBuckets.clear();
  duplicateCache.clear();
}

module.exports = {
  handleDiagnostics,
  formatMarkdown,
  sanitizePublicPayload,
  validatePayload,
  resetTestState,
  MAX_PAYLOAD_BYTES,
};
