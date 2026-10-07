# Lawful authentication options — Phase 1.5

Reviewed: 2026-10-07. Scope: authentication/procurement only, not the parallel API 27 core-port audit.

## Decision and evidence boundary

**No purchasable, authorized provider has been proven compatible with this TS7 and this integration.** This is a result of the sources reviewed below, not a claim that no such provider exists. Hardware price, stock, lead time and personal-use eligibility remain **UNKNOWN**. Do not order a generic CH341 adapter, secure element or consumer CarPlay dongle expecting it to satisfy this gate.

Authentication hardware on this TS7: **UNKNOWN**. [Diagnostic USB deviceCount=0](https://github.com/nnnc8/ts7-carplay-lite/issues/5#issuecomment-6018940228) describes one Android USB enumeration, not internal I2C/UART, an OEM service, or authorization status. This review performed no hardware transaction, credential read or signing request.

The main integration may continue with protocol source, API 27 backport, transport, lifecycle, parser/fixture tests and the existing renderer. Procurement blocks **real authentication/session/video validation**, not that engineering work. Release authentication remains unavailable/fail-closed until both authorization and technical integration are established; synthetic video never supplies session evidence.

All xcertplay source references below are pinned to **`17c92439413638dfd1d7f91d7e1c2e7358398762`**, confirmed from the supplied source checkout. Its [GPL-3.0 source license][pin-license] does not establish rights to an accessory identity, Apple technology, a hardware supply chain or a remote service.

## What the public primary sources establish

- Apple directs **vehicle-system CarPlay integration**, separately from iPhone app development, to the MFi Program. A CarPlay app entitlement is not evidence of receiver authorization. [Apple CarPlay developer page](https://developer.apple.com/carplay/).
- Apple describes accessory certificate/challenge-response authentication and the authentication IC's role in CarPlay MFi-SAP. A USB bridge is not that IC or an authorization grant. [Apple Platform Security](https://support.apple.com/en-gb/guide/security/sec70a4f377d/web).
- Apple lists CarPlay, iAP2 and authentication coprocessors as MFi technologies/components. Procurement by manufacturers entails program requirements including a System Review. The FAQ identifies software-authentication services for other licensees, but gives no blanket CarPlay cloud-signing permission. Individuals' personal-use exclusion is not an offered coprocessor-purchase or receiver-certification entitlement. [Apple MFi FAQ](https://mfi.apple.com/en/faqs.html).
- The public process is product plan, development/component procurement, certification, then approved production. An authorized licensee may manufacture on another company's behalf. [Apple's process](https://mfi.apple.com/en/how-it-works.html).

These sources do not disclose a universal permission to repurpose an existing accessory's authentication, or prove this project's eligibility. Required agreements and the exact approved use must be confirmed through Apple/the supplying licensee; this document is an engineering/procurement assessment, not a legal opinion.

## A / B / C comparison

Complexity is a relative engineering estimate, **not** a supplier quote or elapsed-time promise. It excludes the shared protocol backport and certification/provisioning lead time.

| Option | Hardware / interface | Android API 27 integration and exact pinned path | Estimated complexity | Procurement / authorization gate |
| --- | --- | --- | --- | --- |
| **A — Authorized MFi coprocessor/module** | Provisioned authentication IC or module/dev board; native **I2C**, or **USB → CH341 → I2C**. Board supply, level shifting and reset must match the actual component. | `MfiTarget.I2C` → `LinuxI2cTransport` → `MfiRuntime.scan` → `MfiAuthenticationClient`; or `USB_CH341` → `Ch341UsbHost` / `Ch341I2cTransport` → the same client. Standard USB Host exists before API 27; native I2C needs authorized OEM node access and API-27/ARMv7 JNI build. No TS7 hardware validation yet. [Runtime][pin-controller], [USB][pin-usb], [CH341 I2C][pin-ch341], [native I2C][pin-i2c]. | **Medium** for an approved USB/I2C module; **high / possibly unavailable** for internal I2C requiring OEM cooperation. | Identify approved supplier, provisioning and project/use scope before purchase. Personal retail purchase eligibility and a compatible stocked SKU are unproven. MFi/OEM/vendor agreements depend on the approved arrangement. |
| **B — Explicitly authorized external CarPlay accessory/provider** | Authorized receiver/development accessory with a documented **USB**, **UART**, network or OEM-service SDK; not a dongle whose only interface is its bundled proprietary app. | **No generic external-accessory or UART auth target exists at this pin.** An authorized certificate/signing API could implement `MfiAuthenticator`; a complete external receiver instead requires its own session/media/touch adapter. Vendor SDK must explicitly support API 27 and ARMv7, bounded encoded H.264 output and this third-party host. [Targets][pin-config], [contract][pin-auth]. | **Medium–high** with a supported integration API; **unknown / not feasible as specified** without one. | Finished-product certification/purchase alone does not prove third-party signing, credential reuse or integration permission. Require written approval for the exact host/application and distribution model, plus SDK/GPL compatibility review. No compatible public offering proven. |
| **C — Authorized remote signing/provider, only if permitted** | Authorized service and its approved signing hardware/provisioning; TS7 needs a separately reachable service network. No local chip is necessarily needed on TS7. | `MfiTarget.REMOTE` → `RemoteMfiAuthenticationClient` → `MfiAuthenticator`. Uses HTTP(S)/Base64, not a provider-specific USB driver. API 27 can support those primitives; authorization, secure routing and hardening are separate gates. [Runtime][pin-controller], [client][pin-remote]. | **Medium** client work if the authorized API matches; **high** service/security/operations dependency. | Require Apple/vendor permission specifically covering remote **CarPlay** signing, devices/tenants and this integration. No authorized service, subscription price or end-user signup offering proven. An arbitrary public signing URL is not a candidate. |

### A — Technically closest match; hardware alone is insufficient

The pinned common interface is **`MfiAuthenticator`**, not this repository's future `AuthenticationProvider`: it exposes `certificateType`, `protocolMajor()`, bounded `readCertificate(...)`, `signChallenge(...)`, and a BAA-specific certificate method. `MfiAuthenticationClient` implements it over `I2cTransport`; `MfiSession` owns its transport lifetime. [Interface / chip client][pin-auth], [session / discovery][pin-runtime]. A TS7 wrapper still needs provenance, availability and cancellation policy.

USB/I2C topology, **conditional on a provisioned and authorized module**:

```text
TS7 USB host → approved USB/I2C bridge → provisioned MFi IC
    UsbManager / user permission      → MfiAuthenticationClient
alternative: OEM-granted /dev/i2c-N   → LinuxI2cTransport / xcertplay_i2c JNI
```

This USB cable is an **authentication-hardware connection**, not a change to wired iPhone CarPlay scope. [Android USB Host](https://developer.android.com/develop/connectivity/usb/host) documents API 12-era host support and per-device user permission; actual TS7 port/host power/permission behavior is **UNKNOWN**. The pinned matcher has no built-in VID/PID: configure only the supplied board's verified non-secret USB identity. [Matcher][pin-matcher].

Native I2C is not an ordinary Android runtime permission. The pinned implementation requires Linux device-node access, respects Unix/SELinux policy and loads `xcertplay_i2c`; lowering minSdk cannot grant access. Get an OEM-sanctioned node/service instead of root, policy bypass, proprietary-process hooks or private-storage access. [Native I2C transport][pin-i2c].

The supplier must confirm the component's protocol/certificate type and signing semantics against **both** consumers: [Bluetooth/iAP2 authentication][pin-iap-auth] and [AirPlay MFi-SAP][pin-sap]. Passing a chip presence probe or one signing call does not prove both. The pinned I2C client accepts 1–128-byte challenges; its register/read/STOP behavior is an upstream implementation, **not an official guarantee for every MFi generation**. [Chip client][pin-auth].

### B — Ask for an integration product, not keys from a consumer dongle

Two distinct contracts are possible; do not mix them:

1. **Authentication-only provider:** documented, expressly authorized certificate/signing API matching `MfiAuthenticator`. I2C-compatible modules use A; vendor USB/UART/RPC protocols need a new adapter. `MfiTarget` has only `USB_CH341`, `I2C`, `REMOTE`, `LOCAL_FILES`, so a serial adapter is not ready-made compatibility. [Target enum][pin-config].
2. **Complete external receiver:** vendor handles Bluetooth/Wi-Fi/auth/session, and exposes authorized session events plus encoded video/audio and input controls. This is a receiver-core/media boundary, not `RemoteMfiAuthenticationClient`. A product needing its private APK or raw pixels does not satisfy the present source-only, existing-Surface-pipeline design; any change needs a separate decision.

**UART is a vendor-defined possibility, not proven MFi support.** Require electrical levels, framing, commands, supported drivers and OEM permissions for internal serial nodes, or API-27 USB-host support for an external USB/UART bridge. There is no inspected pinned UART authenticator. No proprietary SDK or binary was downloaded or added by this review.

Do not infer permission to extract, move or share credentials from a dongle's packaging, certification claim or retail ownership. Ask the accessory supplier for **third-party application and host integration authorization** without requesting any private key or credential dump. Apple offers an [authorized-manufacturer discovery route](https://mfi.apple.com/en/how-it-works.html); the actual agreement still needs review.

### C — A client exists; authorization and production suitability are unproven

The pinned remote client uses `GET /mfi/certificate`, `POST /mfi/sign` with challenge/request ID, and `POST /mfi/reset`. Certificate JSON requires `protocolMajor`, `certificate` and `certificateSha256`; optional `type` defaults to `mfi`. Signing returns `signature`. These are interface field names, **not credentials or instructions to fetch an unapproved service**. [Client][pin-remote].

Its README says only the **BAA** path had been tested upstream. Neither that claim nor the presence of BAA branches proves authorization for this TS7's real CarPlay use; do not substitute simulator/developer-device identity or an undocumented attestation service. [Pinned README][pin-readme].

Narrow API 27 feasibility: [`java.util.Base64`](https://developer.android.com/reference/java/util/Base64) is API 26, and [`HttpURLConnection`](https://developer.android.com/reference/java/net/HttpURLConnection) predates API 27. This does **not** pass the whole-core compile audit or a hardware/session test. Upstream calls ordinary `URL.openConnection()`; an approved remote provider may need a different network from the local CarPlay transport. API-21 [`Network.openConnection(URL)`](https://developer.android.com/reference/android/net/Network#openConnection(java.net.URL)) supports network-scoped HTTP(S). Do not globally move all CarPlay sockets to the service's internet network.

Before enabling C, the wrapper must require authenticated HTTPS, validate the approved origin, prevent credential-bearing redirects, bind requests to the live session/epoch, bound total latency/retries/response sizes and close on cancellation. No server error text or payload enters diagnostics. Upstream accepts cleartext HTTP and optional bearer authentication; its server-provided certificate digest is an integrity check, **not independent authorization evidence**. [Remote source][pin-remote]. These are required adaptations, not implemented/verified claims.

Ask for per-accessory provisioning, device/tenant limits, concurrent-session isolation, reset/idempotency semantics, revocation, outage behavior, retention/logging policy and a latency SLA covering both iAP2 and MFi-SAP. The supplier must prove that signing challenges for **our** accessory/host is permitted; generic MFi participation or HomeKit software authentication does not establish that scope.

## Procurement findings — no recommended SKU or public provider price

| Primary lead checked | Decisive evidence / limitation | Procurement conclusion |
| --- | --- | --- |
| Apple's MFi process / FAQ | Component access and authorized manufacturing have a program workflow; no public TS7-compatible retail chip/module offering is identified there. [Process](https://mfi.apple.com/en/how-it-works.html), [FAQ](https://mfi.apple.com/en/faqs.html). | Start with eligibility/approved-use clarification and an authorized-supplier quote, not a component order. Membership is publicly listed as **USD 99/year plus applicable taxes/fees**; this is **not hardware, certification approval or a signing subscription price**. |
| xcertplay author's `ch341-to-mfi-chip` design | The [README at `eb5de2cb45687ef5e15396b3e1dd30335542910b`][board-readme] provides a CH341/MFi board design/BOM and a maker testing claim. It does not establish authorized IC sourcing/provisioning, sale eligibility, inventory, price or permission for this project. No explicit license file was identified in the inspected root listing; design reuse terms require clarification. | **Transport/design reference only, not a proven lawful purchasable provider.** BOM part names and upstream testing are not procurement recommendations or TS7 verification. No board/chip files were copied. |
| NXP public technical-support response, 2025-06-16 | An [NXP TechSupport reply](https://community.nxp.com/t5/i-MX-Processors/Query-Regarding-Apple-CarPlay-Support-and-Authentication-Chip/m-p/2117028) says its reference designs/BSP do not include built-in Apple authentication and points to MFi access and professional services. It supplies no TS7/API-27 module offer or xcertplay signing API. | **Consultation lead, not a compatible hardware candidate.** Do not purchase an unrelated development board assuming authentication is included. |

`AUTH_HARDWARE_CANDIDATES.md` is intentionally not created by this review: none of the checked leads proves the combined **lawful supply + permitted integration + purchasable provider + compatible interface** gate. Availability is unknown, not "out of stock"; prices are unknown, not zero. Reassess on a supplier's verifiable product/API and authorization evidence.

## Precisely what to request before buying

Most practical conditional hardware category: **a supplier-provisioned, authorized MFi authentication module/dev board exposing documented I2C, preferably through a TS7-compatible USB/I2C interface**, with the key remaining in the approved hardware. Prefer that topology for evaluation **only if** the supplier establishes the required scope and stock; it avoids assuming internal OEM I2C access. Do not select a particular chip generation or voltage from a hobby BOM.

Request written answers to these questions; no contact, purchase, NDA execution or supplier message has been performed:

1. **Eligibility and permitted use:** Can you supply to this end user/company and authorize this Android 8.1 aftermarket Wireless CarPlay integration? Who holds the applicable MFi/product responsibilities? What Apple/vendor approvals, NDA, product plan, provisioning, certification or contract are prerequisites for development and distribution?
2. **Provenance:** Is this newly and legitimately provisioned hardware/service, with traceable authorized supply? Does approval cover our host/software, not just the original donor product? Can you provide a non-secret approval reference without any certificate, private key or accessory serial in public correspondence?
3. **Authentication API:** Which MFi protocol/certificate type and signing input/output formats are supported? Does it cover the pinned `MfiAuthenticator` contract, **iAP2 and AirPlay MFi-SAP**, with no private-key export? What are certificate/signature bounds, operation deadline and concurrency/reset rules? Do not assume modern authentication generations match this pin.
4. **Electrical / bus specification:** For I2C, specify power and logic voltages, level shifting, pull-ups, supported bus clock, 7-bit address, STOP/read transaction behavior, reset/wake pins and timing. Ask for approved documentation and pinout before wiring; do not guess from board pictures.
5. **USB host access:** Provide exact VID/PID, interface/bulk endpoint descriptors, host power needs, disconnect/reset behavior, and confirmation of API-27 `UsbManager` access without root or a proprietary privileged process. Is the exposed interface CH341 I2C-compatible, not merely UART/EEPROM programming mode? TS7 port behavior must still be tested safely.
6. **UART / internal OEM path, if proposed instead:** Which authorized transport/service and commands exist? For internal nodes, will the OEM grant the app access under normal Unix/SELinux policy? For a USB/UART module, provide an API-27/ARMv7 supported bridge/SDK and documented electrical levels. No existing xcertplay UART provider is assumed.
7. **External receiver product, if B:** Does the licensed third-party API expose complete bounded Annex-B H.264 access units at 1280×720/30fps, lifecycle/authentication proof, PCM and normalized touch without decoded-frame copies? Can its API/terms coexist with GPL-derived core distribution? If only its proprietary APK is supported, say so explicitly.
8. **Remote service, if C:** Does Apple/vendor authorization expressly permit remote signing for this CarPlay accessory/host and scale? Supply approved API/TLS/client-authorization requirements, network reachability, SLA, revocation and retention policy; **not** an unreviewed URL/token or reusable donor identity.
9. **Commercial facts:** Quote module/board or subscription price, included provisioning/development support, MOQ, taxes/shipping, current stock, lead time, region/end-user eligibility, warranty and return terms if the agreed API-27 integration fails. No price or availability estimate is inferred from another market or product.

## Enablement and safe-inventory gates

These are integration requirements from this review, not proof that a future adapter already meets them:

- Keep release `UnavailableAuthenticationProvider` active until approved source/use, provisioning and implementation are verified. Hardware detection, bus ACK, successful HTTP, or a supplier's certificate-shaped payload cannot by themselves set authorization or CarPlay `STREAMING`.
- Record provider **type, implementation, authorized source, hardware identifier type, provisioning/license status** in a controlled non-secret provenance record. Public diagnostics contain only allowlisted status codes/counters, never supplier credential text, identity/serial, certificate, key, challenge, response, token or raw error.
- Passive inventory may enumerate non-secret USB class/VID/PID, declared features and permitted generic node availability. Missing/inaccessible USB or I2C does not prove all authentication absent; classify unproven devices **UNKNOWN**, access denial **PERMISSION_DENIED**. A bridge can be present while a coprocessor or its authorization remains unknown.
- Pinned [`MfiDeviceScanner`][pin-scan] performs active register-selection writes and version reads at candidate I2C addresses. Its nonzero version response is a heuristic, not cryptographic/provisioning evidence. Do **not** run it automatically across unknown TS7 buses as a passive inventory probe. [`CarPlayController` startup][pin-controller] is not an inventory API either: its CH341 path selects a certificate-bearing address, and the remote path resets/loads certificate information. Any later active access requires the exact authorized module/bus and safe vendor specification; inventory never invokes certificate/signing operations.
- If adopting upstream hardware transport, remove payload logging before integration: [`Ch341UsbSession.bulkWrite`][pin-usb-log] logs a hex representation on a short write, potentially including challenge data. Translate remote/transport failures to fixed codes and retain bounded worker ownership/cleanup; do not copy upstream debug logs wholesale.
- A provider with proper permission is still not session success. Require real bootstrap/network/authentication/session proof and a real received/rendered CarPlay frame. Preserve the already working MediaCodec → Surface path while these gates are pending.

## Independent-review outcome

Public evidence supports A/B/C as **conditional integration routes**, not an enabled legal provider. **A's documented I2C/CH341 topology is the closest match to the pinned core; procurement/provisioning remains unresolved.** B has no inspected drop-in provider API; C has source support but no verified authorization and requires security/network adaptations. No firmware, credentials, private keys, certificates, proprietary SDK or binary were extracted, fetched or added. The parallel main agent retains ownership of core code, CI, issue/status updates and the actual API-27/ARMv7 feasibility result.

[pin-license]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/LICENSE
[pin-readme]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/README.md
[pin-config]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayRuntimeConfig.kt#L13-L18
[pin-controller]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayController.kt#L495-L716
[pin-auth]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/mfi/MfiAuthenticationClient.kt
[pin-runtime]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/orchestration/MfiRuntime.kt
[pin-iap-auth]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/mfi/Iap2MfiAuthenticationClient.kt
[pin-sap]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/airplay/MfiSapAuthSetup.kt#L25-L67
[pin-usb]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/transport/Ch341UsbHost.kt
[pin-ch341]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/transport/Ch341I2cTransport.kt
[pin-usb-log]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/transport/Ch341UsbHost.kt#L173-L183
[pin-matcher]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/transport/Ch341DeviceMatcher.kt
[pin-i2c]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/transport/LinuxI2cTransport.kt
[pin-remote]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/mfi/RemoteMfiAuthenticationClient.kt
[pin-scan]: https://github.com/shilapi/xcertplay/blob/17c92439413638dfd1d7f91d7e1c2e7358398762/shared/src/main/java/com/shilapi/xcertplay/mfi/MfiDeviceScanner.kt
[board-readme]: https://github.com/shilapi/ch341-to-mfi-chip/blob/eb5de2cb45687ef5e15396b3e1dd30335542910b/README.md
