# TS7 patches to DiPlay Legacy

Base: https://github.com/programmerguohuajing/DiPlay-Legacy-Android
Commit: c8884adcc75bfda3c134db63877bd6c6f83beb74. Modified 2026-10-07.
SOURCE_MANIFEST.json records 71 selected source/notice files, original Git blobs,
ported SHA-256 and whether modified. JNI makefiles replaced with minimal TS7 recipes.
UPSTREAM-README.md is upstream README.md renamed as reference, NOT auth instructions.

- CarPlayController: retain actual wireless RFCOMM/iAP2/LOHS/AirPlay handoff.
  Remove vendor initialization, wired/NCM/USB/CH341/I2C scanning, credential loaders,
  auth servers, persistent phone-pair databases, GPS/CAN/HUD. Explicit authenticator
  required before starting radios. RuntimeConfig: single selected wireless phone.
- CarPlayVpnService: ordinary non-exported Service, selected AP address, one session,
  generation guards, accepted-socket timeout; no VPN permission or wired transport.
- API27 hotspot: API26 callback+Handler / WifiConfiguration only; no API28+ typed refs,
  station disconnect, force5GHz, guessed channel36, manual/P2P/vendor fallback.
  Unknown AP interface/BSSID/channel fails closed. TS7 hotspot capability UNKNOWN.
- JNI: read-only local_hotspot_radio.c, NDK25.2/API27 armeabi-v7a and x86_64.
  No MFi/I2C native library. Empty-name smoke returns EINVAL without socket/ioctl.
- Bonjour: explicit interface, bounded queues/status line, multicast/socket cleanup;
  slf4j-nop / no raw logs. RFCOMM/tunnel bounded producers notified after consumption.
- AirPlaySession: SAP + verified pairing + encryption required for SETUP/RECORD;
  request encryption provenance fixed per parsed batch. Reject complete or partial
  plaintext following final pair-verify; encrypted RECORD is explicit media proof.
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
UPSTREAM-README maps to README.md. Never download upstream APK/auth assets.
Host fixtures are not iPhone/auth evidence. Default provider unavailable before radios.

Final adapter lifecycle review fixes: recovery/render callbacks outside media/owner
locks; captured-epoch failure invalidation; retain only profile/listener retry intent
until explicit stop. Retain one early audio format (not PCM), activate only after
authenticated session and UI sink acknowledgment. Nine frozen files remain unchanged.
