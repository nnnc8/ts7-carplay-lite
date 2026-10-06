# Roadmap

## Phase 0 — Diagnose the TS7

Goal: determine the actual hardware/software ceiling before receiver development.

- Device / ABI / memory / display inventory
- H.264 MediaCodec inventory and instantiate test
- USB device enumeration while iPhone/CarPlay path is connected
- Wi‑Fi / Bluetooth state collection
- First real-device diagnostic report

Exit criterion: enough evidence to choose video decoder and wired transport architecture.

## Phase 1 — Wired CarPlay proof of concept

Goal: stable wired session before adding wireless complexity.

Planned constraints:

- Android 8.1 / API 27 target
- 32-bit ARM support
- one fullscreen Activity
- no WebView
- no Compose
- no animations unless required
- H.264 only initially
- native panel resolution where possible
- 30 fps maximum, lower fallback modes
- MediaCodec output directly to Surface

Exit criterion: 30–60 minutes of continuous wired use without decoder stalls, black screen or transport disconnect.

## Phase 2 — H.264 decode optimization and performance telemetry

Goal: make the video path measurable and keep the low-memory target within budget.

- verify the selected AVC decoder with real TS7 evidence
- constrain resolution and frame rate to the measured capability
- performance telemetry for queue depth, frame timing, dropped frames and memory pressure
- 30 fps default, with 25 fps and 20 fps stability modes

Exit criterion: a measured decoder path with no unexplained queue growth under the target load.

## Phase 3 — Audio / microphone / Siri

- audio output path
- microphone capture path
- Siri request / response handling
- audio focus and reconnect behavior

Exit criterion: wired video and audio remain stable during a representative drive session.

## Phase 4 — Touch input

- map touch coordinates to the native display
- test tap, swipe and long-press paths
- keep input handling allocation-light

Exit criterion: common CarPlay touch actions work without degrading video or audio.

## Phase 5 — Long-term stability

- transport state log
- decoder queue / frame timing log
- audio state log
- reconnect state machine
- watchdog for frozen video without killing the whole UI
- lightweight on-device diagnostics export

Exit criterion: failures can be classified instead of appearing as an unexplained freeze/disconnect.

## Phase 6 — Wireless CarPlay

Only after wired mode is stable.

- Bluetooth session bootstrap
- Wi‑Fi path characterization on TS7
- separate hotspot/network modes if required by old Android Wi‑Fi APIs
- packet loss / latency instrumentation
- recovery after radio interruption

Exit criterion: repeatable connection and acceptable latency without starving decode/audio.

## Phase 7 — TS7 production optimization

- minimal settings UI
- Wired / Wireless selector
- 30 / 25 / 20 fps stability profiles
- diagnostics button
- startup behavior suitable for head unit use
- signed versioned builds
- reproducible CI artifacts

Exit criterion: a documented, repeatable TS7 build and install path with results from extended wired and wireless testing.
