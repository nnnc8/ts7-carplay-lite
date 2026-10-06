# Wireless CarPlay architecture — v0.1-alpha

**TECHNICAL PREVIEW / NOT YET A FUNCTIONAL CARPLAY RECEIVER.**

The implemented renderer can accept original synthetic H.264. A lawful upstream
protocol/authentication provider is not integrated. Dashed/future paths below are
contracts, not claims of an iPhone connection. Target: captured Android 8.1/API 27,
ARMv7, 2 GB, 1280×720. Research revisions/licenses are in
[CARPLAY_IMPLEMENTATION_RESEARCH.md](CARPLAY_IMPLEMENTATION_RESEARCH.md).

## Data paths

```text
iPhone (future, lawful provider)
│
├── Bluetooth setup/discovery ──► bootstrap proof
│                                 │
└── Wi-Fi ◄────────────────────────┘
       │
       lawful authentication + CarPlay session [BLOCKED]
       │
       ├── complete Annex-B H.264 access units (SPS/PPS/IDR)
       │                         │
       │     developer-only      │
       │     generated pattern ──┤  (separate TEST_PATTERN mode)
       │                         ▼
       │       bounded encoded queue (4 × 256 KiB, age ≤250 ms)
       │                         │
       │       SPS/PPS validation + IDR resync
       │                         │
       │        MediaCodec (prefer OMX.sprd.h264.decoder)
       │                         │
       │                      Surface
       │                         │
       │                 1280×720 display
       │
       ├── future PCM ──► minimal AudioTrack sink ──► speakers
       │                  (implemented sink, media integration UNTESTED)
       │
       ◄── normalized single-finger DOWN/MOVE/UP ◄── Surface touch
       │                  (boundary implemented, actual delivery UNTESTED)
       │
       ◄── microphone PCM / Siri control [DEFERRED, no capture permission]

radio/session/codec/Surface events
       │
       ├── finite recovery: 1 / 2 / 5 s → safe stop when exhausted
       └── fixed metrics + 500-event ring
                  │
             user copy / confirmed HTTPS upload → fixed GitHub Issue #13
```

No decoded-pixel Bitmap/Canvas conversion, CPU resize, WebView, Compose, animation
framework, analytics, account access, vendor firmware or authentication key.
Surface compositor fits a 16:9 viewport; touch uses that viewport, not system bars.
Full-screen UI hiding is conditional on the first real authenticated CarPlay frame.
The developer pattern retains its explicit TEST PATTERN label and controls.

## Three independent state domains

| Domain | What is implemented | What it cannot prove |
| --- | --- | --- |
| Bluetooth | OFF/IDLE/DISCOVERING/LINK_OBSERVED from safe system observations; Android consent/settings UI | Peer identity, iPhone detection or CarPlay bootstrap |
| Wi-Fi | DISCONNECTED/NETWORK_OBSERVED plus RSSI/link speed/frequency | CarPlay network, data-path compatibility or sustained throughput |
| CarPlay | Evidence-gated state machine and unavailable core | An actual session without lawful authentication |

```text
IDLE → BT_DISCOVERY → BT_CONNECTED → WIFI_CONNECTING → WIFI_CONNECTED
                                                        │
                                                CARPLAY_NEGOTIATING
                                                        │
                                   authentication + first real rendered frame
                                                        ▼
                                                    STREAMING
                                                        │
                                            network / protocol loss
                                                        ▼
                                                   RECOVERING
                                            ┌───────────┴──────────┐
                                      fresh auth + real frame   retry exhausted
                                            │                     │
                                        STREAMING                ERROR

missing legal provider: IDLE → ERROR (BLOCKED_BY_AUTHENTICATION_REQUIREMENT)
explicit stop/lifecycle cancellation → IDLE
TEST_PATTERN playback does not enter this streaming chain.
```

BOOTSTRAP_CONFIRMED and SESSION_LINK_CONFIRMED are provider evidence, never inferred
from generic adapter/network flags. Periodic radio refresh does not overwrite proof
with a generic connected flag. Provider callbacks are scoped to a connection epoch;
stop/lifecycle invalidates them. Duplicated/out-of-order callbacks cannot start a
cancelled session or throw from the UI state transition. Recovery needs fresh
authentication and a fresh renderer's real rendered frame; returning true from
reconnect is not success.

## ReceiverCore integration contract

Current implementation is ReceiverCore.Unavailable: hasLawfulAuthentication=false.
No protocol is emulated or hand-written. Before replacing it, establish authorized
authentication provisioning, upstream exact-file license obligations, API 27 support
and ARM32 native packaging.

An adapter must emit actual bootstrap, Wi-Fi-link and authenticated-session proofs in
order. After authentication it **must wait for videoSinkReady()** before sending
the initial SPS/PPS/IDR. videoAccessUnit returns acceptance; sending before the
handshake is rejected, not silently queued without bounds. Input is copied once as
compressed bytes into reusable slots before returning; source ownership remains with
the provider. Callbacks never retain arbitrary source buffers or secrets.

Input contract: complete Annex-B access units, monotonic microsecond PTS, packet
≤256 KiB, progressive 8-bit AVC ≤1280×720, bounded parameter sets with matching
PPS→SPS. A future core supplies packet reassembly/AVCC conversion; no incomplete
network packet is misrepresented as a frame. A stream reset invalidates CSD and
requires new SPS/PPS/IDR. Session loss cancels media acceptance and fresh setup must
complete before resumed video. Keyframe requests are bounded, not a tight network loop.

## Decoder, queues and memory

Codec worker owns creation/configure/start/feed/drain/stop/release. Decoder candidate
order: named SPRD, other enumerated vendor AVC, enumerated Android/system AVC.
Creation **or configuration/start failure** releases/rejects that candidate and
tries the next; actual chosen name is reported. Generic emulator fallback is not
called hardware acceleration or SPRD validation.

Only SPS/PPS changes allocate small new configuration arrays/snapshots. Input queue,
packet metadata, timing slots and event ring are reused. Pattern asset is bounded
≤1 MiB and loaded only by explicit developer action; its AU index is bounded.
No decoded frame is copied through Java. Codec internal memory is implementation
dependent and must be measured on the TS7; 4 MiB of decoded 720p frames is not the
application queue. Queue itself is 1 MiB compressed storage plus metadata.

Overflow/stale input discards the backlog and dependent delta frames, then waits for
IDR/request-keyframe. No accumulating latency behind an arbitrary drop. SPS/PPS
validation occurs before codec configuration; dimensions outside profile are rejected.
Profiles: 1280×720 @ 30 default, 25 balanced, 20 stability. Pattern feed pacing changes
fps; a future core must negotiate equivalent frame-rate reduction, not pretend that
dropping input changes the iPhone's encoder.

## Telemetry and recovery

Rendered-frame callbacks count output, not just input submission or buffer release.
FPS and compressed-arrival-to-render-callback latency are estimates; they are not
glass-to-glass network latency. Session uptime is zero for the pattern. Report includes
actual codec, width/height, target/measured fps, drops, queue, decoder latency/restarts,
session retry attempts, available RAM/lowMemory, radio metrics, PCM rate/channels/
underruns and separate last session-disconnect/playback reasons.

Ring holds 500 primitive fixed-code events with relative time and numeric value;
newest 200 exported under 32 KiB. No exception string, peer name, SSID/BSSID/MAC/IP,
serial, Android ID, phone/account/contact/location/media contents or credential.
Copy and confirmed upload are user actions; no background telemetry.

Frame stall threshold: 3 s (5 s initial startup). Recovery releases/resyncs, waits
1/2/5 s, then stops with RECOVERY_EXHAUSTED. Stable output may reset the decoder
budget. Session retries have a separate budget and epoch/state-guarded delayed work.
Repeated failures cannot spin forever. A vendor native call missing worker heartbeat
for 5 s reports CODEC_CALL_TIMEOUT and refuses to create another worker while the old
one remains live.

On pause, stop is requested before Surface destruction. Destruction disables further
rendering and awaits normal worker stop up to 250 ms. The drain checks active/Surface
validity before rendering. **A vendor call already hung in native code cannot be
force-cancelled safely**: timeout is contained/reported, replacement refused; complete
Surface-lifetime guarantees in that fault case remain an OEM/runtime limitation.
No indefinite UI join or forced thread kill. Real TS7 lifecycle/recovery tests required.

## Audio / touch / microphone

PcmAudioOutput is a bounded nonblocking AudioTrack PCM16 sink (44.1/48 kHz, mono/stereo),
with underrun accounting and no effects/mixer/backlog. Core media decoding/routing and
audio focus integration remain incomplete; no actual CarPlay audio claim.

Touch uses normalized coordinates, accepted pointer tracking and guaranteed gesture
termination at last valid coordinate on outside-viewport UP/CANCEL or playback stop.
No multitouch gestures. Real CarPlay touch remains untested.

Microphone/Siri requires a later lawful session/core contract, AudioRecord permission,
focus/control events and real-device testing. Capture is not implemented or requested.

## Build / verification

Separate normal and instrumentation APKs. Normal binary has no instrumentation entry
point; CI-only APK exercises idle startup, actual API 27 Surface output, stream-reset
recovery and shutdown, collecting a synthetic screenshot/log. No automatic GitHub
upload from the emulator. JVM/schema/privacy tests complement, not replace, runtime
tests. No native receiver libraries: Java DEX is architecture-neutral and supports
armeabi-v7a. Real TS7 vendor rendering remains NOT YET VERIFIED.

Diagnostic v0.2 workflow/APK and fixed #5 route are preserved. Alpha route is fixed #13.
GitHub token resides only in Vercel; Android embeds only the public HTTPS endpoint.
See [device checklist](REAL_DEVICE_ALPHA_TEST.md) and [status](../PROJECT_STATUS.md).
