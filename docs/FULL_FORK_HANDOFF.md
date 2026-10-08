# TS7 primary-receiver handoff

Date: 2026-10-08. The latest explicit Full-Fork request supersedes the earlier
Lite and experimental-identity plans for new development.

Primary receiver: https://github.com/nnnc8/ts7-diplay
Upstream: https://github.com/programmerguohuajing/DiPlay-Legacy-Android
Exact upstream: `c8884adcc75bfda3c134db63877bd6c6f83beb74`.
Untouched baseline: `baseline/upstream-legacy-0.2.7`.
Compatibility branch: `feature/ts7-android81-baseline`.
Engineering acceptance tracking: https://github.com/nnnc8/ts7-diplay/issues/1
Draft change: https://github.com/nnnc8/ts7-diplay/pull/2 (not merged).

The Fork retains all 428 tracked upstream files, all two exposed upstream
commits, licenses, Classic UI, mobile/common/shared/automotive, original wireless
controller, Bluetooth/iAP2/AirPlay, authentication interfaces, media/audio/touch,
lifecycle and shared wired dependencies. Original upstream history begins as a
public snapshot; do not claim a fabricated ancestry to the original DiPlay repo.

## This repository stays preserved

`ts7-carplay-lite` now holds diagnostics, actual hardware data, regression tests
and historical development. Diagnostic v0.2, all prior commits/releases, fixed
Issue5/Issue13 relay and uploaded reports remain. PR18 is OPEN/unmerged; no stable
main overwrite, relay redeploy, credential reuse or new Lite receiver changes.

Physical platform evidence remains Android8.1/API27/ARMv7/2GB/1280x720 with SPRD
strings; exact silicon UNKNOWN. The reported OMX.sprd.h264.decoder synthetic
~29.85fps/8380frames/7drops/0restarts is not a CarPlay session or duration gate.
Two earlier platform reports cover twelve individual checks, not full wireless
handoff/coexistence. Do not repeat those probes or synthetic5/15-minute car tests.

Dev.1 actual failure:
https://github.com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-6052472030
HOTSPOT_SECURITY_UNSUPPORTED occurred before media/session. The earlier custom
port accepted WPA_PSK(1); full upstream separately accepted WPA2_PSK(4). Actual
TS7 key-management bits are not in that report. The new compatible path accepts
only measured valid PSK configurations, never open fallback or guessed AP radio.

## Authentication and new engineering baseline

Historical firmware-derived identity availability is not legal authorization,
Apple certification or phone acceptance. The new public Fork/CI/APK/source/release
contains no accessory identity and rejects build-time identity assets. Do not
extract or access the historical ignored credential inputs. Upstream provider
interfaces remain for explicitly legally authorized external provisioning only.
Without one, AUTH_BLOCKED must stop the real phone connection path.

R1 is now published after all exact-source upstream/TS7 build, tests, ARMv7,
actual API27 emulator lifecycle/native/Surface and privacy/license gates passed:
https://github.com/nnnc8/ts7-diplay/releases/tag/ts7-fullfork-baseline-r1
Install APK:
https://github.com/nnnc8/ts7-diplay/releases/download/ts7-fullfork-baseline-r1/TS7-DiPlay-FullFork-Baseline-R1.apk
Exact new-Fork source: `78f58e776ef6ca1c7e0eb2578d1acbe5c9ba7f67`.
CI push37787544033 / PR37787553227 / original Android37787553404 all PASS;
pristine326 / patched361 tests, 17 checker regressions and all8 actual API27
emulator checks PASS. The actual original AndroidMediaSink produced Surface
callbacks/red pixels across initial, replaced and restarted Surface cycles.
The separately packaged corresponding source was extracted, rebuilt and inspected.
Published assets were downloaded again and all SHA-256 checks matched.
No raw logcat/test output or phone data was published; test media are synthetic.

Every R1 package says ENGINEERING BASELINE / NOT YET VERIFIED AS FUNCTIONAL
CARPLAY ON TS7. Real TS7 and real iPhone are separate unpassed acceptance gates;
this identity-free APK is not install-and-go functional CarPlay. Full Fork PR2
is draft/unmerged and both repositories' main branches remain untouched.

Next car milestone: install that engineering R1, open Classic UI, choose an
already-paired iPhone and observe explicit hotspot/preflight result, Bluetooth
stage and terminal reason. AUTH_BLOCKED is expected without an authorized
provider. Only a real accepted phone session permits audio/touch/reconnect and
optimization benchmarking. Operate while parked; no repeated platform inventory.
