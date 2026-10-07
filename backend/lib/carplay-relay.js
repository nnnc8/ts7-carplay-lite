"use strict";

const { createHandler } = require("./diagnostic-relay");
const FIELDS = [
  "schemaVersion", "reportType", "appVersion", "timestamp", "mode", "carplayState", "bluetoothState", "wifiState",
  "authentication", "decoderName", "lastDisconnectReason", "lastPlaybackReason", "sessionUptimeMs", "videoWidth", "videoHeight",
  "targetFps", "measuredFps", "renderedFrames", "droppedVideoFrames", "droppedPackets", "videoQueueDepth",
  "decoderLatencyMs", "decoderRestartCount", "carplayReconnectCount", "availableRamMb", "lowMemory",
  "networkConnected", "wifiRssiDbm", "wifiLinkSpeedMbps", "wifiFrequencyMHz", "audioSampleRate", "audioChannels",
  "audioUnderruns", "events",
];
const STATES = ["IDLE", "BT_DISCOVERY", "BT_CONNECTED", "WIFI_CONNECTING", "WIFI_CONNECTED", "CARPLAY_NEGOTIATING", "STREAMING", "RECOVERING", "ERROR"];
const REASONS = ["NONE", "BLOCKED_BY_AUTHENTICATION_REQUIREMENT", "NETWORK_LOSS", "SESSION_LOST", "DECODER_ERROR", "VIDEO_STALL", "RECOVERY_EXHAUSTED", "SURFACE_LOST", "USER_STOP", "INPUT_REJECTED"];
const EVENTS = new Set([
  "APP_OPEN", "BT_CONNECT", "BT_DISCONNECT", "WIFI_CONNECT", "NETWORK_LOSS", "AUTHENTICATION_BLOCKED",
  "CARPLAY_SESSION_START", "SESSION_LOST", "VIDEO_SPS_RECEIVED", "VIDEO_PPS_RECEIVED", "DECODER_CONFIGURED",
  "FIRST_FRAME", "DECODER_ERROR", "CODEC_CALL_TIMEOUT", "VIDEO_STALL", "RECOVERY_START", "RECOVERY_SUCCESS", "RECOVERY_EXHAUSTED",
  "KEYFRAME_REQUEST", "TEST_START", "TEST_STOP", "STREAM_RESET", "QUEUE_RESYNC", "INPUT_REJECTED",
  "SURFACE_LOST", "AUDIO_STARTED", "AUDIO_STOPPED", "AUDIO_ERROR", ...STATES.map((state) => "STATE_" + state),
]);

function object(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
function exactKeys(value, fields) {
  return object(value) && Object.keys(value).length === fields.length
    && fields.every((field) => Object.prototype.hasOwnProperty.call(value, field));
}
function validate(payload) {
  if (!exactKeys(payload, FIELDS)) return ["missing or unknown field"];
  const errors = [];
  if (payload.schemaVersion !== 1 || payload.reportType !== "carplay-alpha"
      || !["0.1-alpha", "0.2-alpha"].includes(payload.appVersion)) errors.push("unsupported report version");
  if (typeof payload.timestamp !== "string" || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(payload.timestamp)
      || !Number.isFinite(Date.parse(payload.timestamp))) errors.push("invalid timestamp");
  if (!["IDLE", "TEST_PATTERN", "CARPLAY"].includes(payload.mode) || !STATES.includes(payload.carplayState)) errors.push("invalid session state");
  if (!["OFF", "IDLE", "DISCOVERING", "LINK_OBSERVED", "BOOTSTRAP_CONFIRMED"].includes(payload.bluetoothState)) errors.push("invalid Bluetooth state");
  if (!["DISCONNECTED", "NETWORK_OBSERVED", "SESSION_LINK_CONFIRMED"].includes(payload.wifiState)) errors.push("invalid Wi-Fi state");
  // Both previews lack an authorized authentication provider: cannot report a real session.
  if (payload.authentication !== "BLOCKED_BY_AUTHENTICATION_REQUIREMENT" || payload.mode === "CARPLAY"
      || payload.carplayState === "STREAMING") errors.push("authentication boundary");
  if (!REASONS.includes(payload.lastDisconnectReason)) errors.push("invalid disconnect reason");
  if (!REASONS.includes(payload.lastPlaybackReason)) errors.push("invalid playback reason");
  if (typeof payload.decoderName !== "string" || !/^(NOT_STARTED|UNKNOWN|(?:OMX|c2)\.[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*){1,8})$/.test(payload.decoderName)
      || payload.decoderName.length > 128) errors.push("invalid decoder name");
  for (const field of ["lowMemory", "networkConnected"]) if (typeof payload[field] !== "boolean") errors.push("invalid boolean metric");
  const numeric = FIELDS.filter((field) => !["schemaVersion", "reportType", "appVersion", "timestamp", "mode", "carplayState", "bluetoothState", "wifiState",
    "authentication", "decoderName", "lastDisconnectReason", "lastPlaybackReason", "lowMemory", "networkConnected", "events"].includes(field));
  for (const field of numeric) {
    const value = payload[field];
    const minimum = field === "wifiRssiDbm" ? -127 : 0;
    if (typeof value !== "number" || !Number.isFinite(value) || value < minimum || value > 1000000000) errors.push("invalid numeric metric");
    if (!["measuredFps", "decoderLatencyMs"].includes(field) && !Number.isInteger(value)) errors.push("non-integer counter");
  }
  if (payload.videoWidth > 1280 || payload.videoHeight > 720 || payload.videoQueueDepth > 4
      || ![20, 25, 30].includes(payload.targetFps) || payload.measuredFps > 120) errors.push("invalid video metric");
  if (![0, 44100, 48000].includes(payload.audioSampleRate) || ![0, 1, 2].includes(payload.audioChannels)) errors.push("invalid audio metric");
  if (!Array.isArray(payload.events) || payload.events.length > 200) errors.push("invalid event count");
  else for (const event of payload.events) {
    if (!exactKeys(event, ["elapsedMs", "code", "value"]) || !EVENTS.has(event.code)
        || !Number.isInteger(event.elapsedMs) || event.elapsedMs < 0 || event.elapsedMs > 1000000000
        || !Number.isInteger(event.value) || Math.abs(event.value) > 1000000000) errors.push("invalid event");
  }
  return errors;
}

function sanitize(payload) {
  return Object.fromEntries(FIELDS.map((field) => [field, field === "events"
    ? payload.events.map(({ elapsedMs, code, value }) => ({ elapsedMs, code, value })) : payload[field]]));
}

function format(payload) {
  const lines = [`## TS7 CarPlay Lite v${payload.appVersion} diagnostics`, "",
    "**TECHNICAL PREVIEW — NOT YET A FUNCTIONAL CARPLAY RECEIVER**", "",
    "The TEST_PATTERN is synthetic H.264, not iPhone/CarPlay video.", ""];
  for (const field of FIELDS) if (field !== "events") lines.push(`- ${field}: ${payload[field]}`);
  lines.push("", "### Bounded event log (newest 200)", "", "```text");
  for (const event of payload.events) lines.push(`${event.elapsedMs}ms ${event.code} ${event.value}`);
  lines.push("```", "", "Only allowlisted metrics and fixed event/error codes are included. Uploaded by explicit user action.");
  return lines.join("\n");
}

const handleCarplayDiagnostics = createHandler({
  namespace: "carplay-alpha", validate, sanitize, format,
  destination: Object.freeze({ owner: "nnnc8", repo: "ts7-carplay-lite", issue: 13 }),
});
module.exports = { handleCarplayDiagnostics, validate, EVENTS, FIELDS };
