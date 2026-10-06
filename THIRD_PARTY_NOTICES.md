# Third-party notices

## OpenJDK JNI header

`diagnostic/native/jni.h` contains material from OpenJDK and retains its original copyright notice and GPLv2 + Classpath Exception language in the file header.

## Future CarPlay receiver dependencies

No third-party CarPlay receiver code is currently vendored into this repository. Before importing or adapting code from DiPlay or another project, record:

- upstream URL
- exact commit/release
- license
- files copied or adapted
- required attribution / source-distribution obligations

Do not import code until the license has been reviewed for the intended distribution model.

## v0.1-alpha research-only candidates — no code/assets bundled

| Repository | Inspected revision | License/caveats |
| --- | --- | --- |
| [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) | b26cd5443cf19707e7fdbbcc507b038bc37ad989 | Core GPL-3.0; mixed AGPL-3.0 UI/site, separately restricted assets |
| [PeratX/diplay](https://github.com/PeratX/diplay) | e6135d36e6a64ad85a94795b2dcbd54e826fa4c0 | DiPlay provenance/license caveats remain |
| [programmerguohuajing/DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android) | c8884adcc75bfda3c134db63877bd6c6f83beb74 | DiPlay-derived GPL/AGPL/asset caveats; imports need exact-file review |
| [shilapi/xcertplay](https://github.com/shilapi/xcertplay) | 17c92439413638dfd1d7f91d7e1c2e7358398762 | GPL-3.0; hardware-auth options researched only |
| [f-io/LIVI](https://github.com/f-io/LIVI) | 8851de944d9ce20069d1f49e4f68a6c3494851d9 | GPL-3.0; desktop/GStreamer, not Android drop-in |

See docs/CARPLAY_IMPLEMENTATION_RESEARCH.md for exact-source links, deps/API/ABI/auth findings and rejected credential paths. This is research attribution, not a claim that a core is bundled or a blanket repository license. No upstream icons, BYD artwork, extracted identities, prebuilt receiver or auth keys distributed.

## Original synthetic video

receiver/src/main/assets/ts7-pattern.h264 is generated locally by receiver/generate-pattern.sh from FFmpeg testsrc color bars/motion and relative time counter. No captured user/iPhone media or third-party artwork. FFmpeg/libx264 tools are not bundled. SHA-256: 97a807f52df8efbee7fa54ff57c86d03ea5c4463e303a9c97b2a09fffa75affc.

New Java receiver code is original. Existing files retain notices; review exact files and source-distribution obligations before any core import. Do not relicense legacy material by assumption.
