# TS7 patches to DiPlay Legacy

Base: https://github.com/programmerguohuajing/DiPlay-Legacy-Android
Commit: c8884adcc75bfda3c134db63877bd6c6f83beb74. Modified 2026-10-08.
SOURCE_MANIFEST.json records 72 selected source/notice files, original Git blobs,
ported SHA-256 and whether modified. JNI makefiles replaced with minimal TS7 recipes.
UPSTREAM-README.md is upstream README.md renamed as reference, NOT auth instructions.

- CarPlayController: retain actual wireless RFCOMM/iAP2/LOHS/AirPlay handoff.
  Failure callbacks now preserve only exact allowlisted local codes; tunnel errors
  use a fixed generic code, never exception/peer data. Own startup retires its idle
  controller before Connect; finite retry backoff waits for attempt completion.
  Remove vendor initialization, wired/NCM/USB/CH341/I2C scanning, credential loaders,
  auth servers, persistent phone-pair databases, GPS/CAN/HUD. Explicit authenticator
  required before starting radios. RuntimeConfig: single selected wireless phone.
- CarPlayVpnService: ordinary non-exported Service, selected AP address, one session,
  generation guards, accepted-socket timeout; no VPN permission or wired transport.
- API27 hotspot: API26 callback+Handler / WifiConfiguration only; no API28+ typed refs,
  station disconnect, force5GHz, guessed channel36, manual/P2P/vendor fallback.
  Unknown AP interface/BSSID/channel fails closed. TS7 temporary reservation PASS, actual channel/peer traffic UNKNOWN.
- JNI: read-only local_hotspot_radio.c, NDK25.2/API27 armeabi-v7a and x86_64.
  No MFi/I2C native library. Empty-name smoke returns EINVAL without socket/ioctl.
- Bonjour: explicit interface, bounded queues/status line, multicast/socket cleanup;
  slf4j-nop / no raw logs. RFCOMM/tunnel bounded producers notified after consumption.
- AirPlaySession: SAP + verified pairing + encryption required for SETUP/RECORD;
  request encryption provenance fixed per parsed batch. Reject complete or partial
  plaintext following final pair-verify; encrypted RECORD is explicit media proof.
  Separate authenticated control SETUP eligibility from RECORD/streaming proof;
  stream ports may be established before RECORD, but TS7 sink remains gated.
  Event write failures release the writer lock before media/owner teardown callbacks.
  bounded RTSP/control/event. NTP/event/keepalive bound to session local address.
  Event peer/timeout checked, repeated timing setup refused, max three distinct
  advertised stream types, no repeated active stream replacement or listener leak.
- MediaEngine/Screen/Audio/IapTunnel: main H264, LPCM media only; exact AP/peer;
  no wildcard/secondary listeners/capture. Packets bounded; replaced tunnels closed.
- Bplist/RTSP: reject cycles/depth/object/reference/offset/length overflow/duplicate
  headers before allocating/converting Long to Int.
- PairingStore: RAM-only/synchronized, eight peers, cloned32-byte public keys; wipe.
  Local generated Ed25519 pairing identity is NOT an Apple/MFi identity.
- Iap2FrameFormatter/traces: fixed IDs/counts; no Log payload/context/exception.
  SSID/passphrase/cert/signature never written to logcat/reports.
- Iap2WiredControlClient: shared subscription constants only, no wired implementation.
  Location/vehicle are pure hard-dependency protocol definitions, no GPS/CAN providers.
  Inherited MicrophonePacketizer is a pure type; microphone unadvertised/unrecorded.

Own adapter/provider/proof gate/build: diplay-port/. Nine existing renderer/audio/
touch/asset files frozen by renderer-lock.json. API27 SDK / Kotlin JVM8 compile.
Compare exact upstream commit against each manifest path to reproduce diff; renamed
UPSTREAM-README maps to README.md. On2026-10-08 user explicitly selected the official matching v0.2.7 runtime identity. Only two bounded runtime inputs may be supplied outside Git/source/CI; no upstream receiver binary/UI copied.
LocalMfiAuthenticationClient retains upstream P-256/NONEwithECDSA/raw64 signing with bounded direct-byte loading and close cleanup. Protocol major3,32-byte already-digested challenge, matching public key self-check. Original GPL blob recorded in manifest; runtime data not GPL-relicensed.
Host/generated-identity fixtures are not phone trust or session evidence. Missing provider blocks radios; selected experimental provider is not isAuthorized/Apple certification.

Final adapter lifecycle review fixes: recovery/render callbacks outside media/owner
locks; captured-epoch failure invalidation; retain only profile/listener retry intent
until explicit stop. Retain one early audio format (not PCM), activate only after
authenticated session and UI sink acknowledgment. Nine frozen files remain unchanged.
Asynchronous proof/failure callbacks carry attempt validity through final UI delivery;
late bridge cleanup or queued notifications cannot interrupt a replacement attempt.
