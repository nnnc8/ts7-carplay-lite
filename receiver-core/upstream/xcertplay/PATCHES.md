# TS7 API27 source port modifications — 2026-10-07

Base: `17c92439413638dfd1d7f91d7e1c2e7358398762`, GPL-3.0.
SOURCE_MANIFEST.json records original and port hashes for all copied source; see its modified flag.
No upstream UI/assets/native/credential loaders or Android location provider imported.

Modified source:

- `shared/src/main/java/com/shilapi/xcertplay/iap2/session/Iap2Session.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/RtspMessage.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/Tlv8Codec.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/BplistCodec.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/AudioStream.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlayMediaEngine.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/ScreenStream.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/AirPlaySession.kt`
- `shared/src/main/java/com/shilapi/xcertplay/transport/Iap2IdentificationClient.kt`
- `shared/src/main/java/com/shilapi/xcertplay/transport/Iap2WiredControlClient.kt`
- `shared/src/main/java/com/shilapi/xcertplay/transport/Iap2WirelessControlClient.kt`
- `shared/src/main/java/com/shilapi/xcertplay/mfi/MfiAuthenticationClient.kt`
- `shared/src/main/java/com/shilapi/xcertplay/airplay/IapTunnel.kt`

Changes by purpose:

- MfiAuthenticationClient retains authenticator contract, bounds and typed exceptions only.
  Active I2C register operations are removed; AuthenticationProvider is a separate lawful seam.
- Identification/WiredControl/WirelessControl share a local timeout exception instead of importing
  wired USB host implementation. WiredControl is present only for shared subscription helpers.
  WirelessControl adds a typed callback strictly after real phone AA05.
- Iap2Session disables frame formatting and trace callbacks even when a caller supplies one.
  AirPlaySession/ScreenStream/AudioStream/CarPlayMediaEngine use a silent sink; AirPlay
  payload, peer identity, errors, certificate/challenge/response are never diagnostics/logcat.
- MediaEngine removes packet/file recording and keeps microphone disabled in the TS7 adapter.
  Screen/audio/event/keepalive/tunnel bind exact local addresses; screen/event/tunnel require
  the control peer, finite accept/read timeouts. Tunnel removes wildcard/secondary listeners,
  caps package/buffer sizes and disallows nonce-counter reuse after peer EOF.
- AirPlaySession SETUP/RECORD require verified pairing, encrypted control and an MFi-SAP response.
  Typed adapter additionally requires lawful provider provenance, AA05, explicit network,
  established session, sink readiness, accepted VCL and real rendered output.
- RTSP bounds:16KiB header/256KiB total, reject overflow/negative/duplicate lengths.
  TLV8:64KiB, reject truncation. Bplist:256KiB input,4096 objects,32 depth,8192 visits,
 512KiB decoded expansion; check references/counts/ranges before allocation.
  Screen:256KiB+tag, reject unsealed frames; existing renderer and asset are untouched.

Local GPL-3.0-only additions under the vendor namespace:
`airplay/SilentProtocolLog.kt`, `transport/ProtocolTransportException.kt`.
Our adapters/providers are under receiver-core/src and receiver/src, not presented as upstream.

Compiler/host fixtures/Java-only ARMv7 packaging are engineering evidence, not a real phone
session or authorization grant. Release provider remains unavailable. Provisioned OEM
identity, lawful provider, network/Bonjour deployment and real TS7 validation remain gates.
