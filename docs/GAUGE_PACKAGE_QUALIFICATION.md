# Gauge source/package checkpoint — 1.1.3

Verified September 6, 2026, at source commit `d0967195`.
Public downloads remain **1.1.2 RC1**; these results cover development source and
CI artifacts, not a new release or physical vehicle acceptance.

## Automated results

- Both desktop build runs passed ([first run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34071510111),
  [second run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34071510104)), including
  Linux native-window tests and Windows packaging.
- [All platform-package jobs passed](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34071509936):
  Android, SteamOS Desktop Mode, macOS arm64 and macOS x64.
- The rebuilt core passed its complete Ant suite and shared-core checks. Local
  desktop suites passed all 40 Compose and 253 JavaFX tests with native checks enabled.
  The three Compose native-window tests then passed three further runs on fresh
  isolated Xvfb/Openbox displays.
- [DimeMod channel validation](DIMEMOD_CHANNEL_AUDIT.md#published-channel-address-spans)
  rejects all 756 crossing cases while preserving all 888 valid endpoint/alias
  cases across nine synthetic layouts. No discovery was attempted on an adapter.

An earlier duplicate Linux run reported native-window failures. The Swing test
now waits for exact window-manager placement instead of asserting size during
the transition. CI retains failed XML reports and prints full exception messages.
Both Linux runs above passed; this is not a claim that every window manager or
physical platform is qualified.

## Android artifact inspection

Both APKs downloaded from the platform run above passed their supplied SHA-256
checksums and APK v2 signature verification. Both report version **1.1.3**, code
**110407**, minimum SDK **26** and target SDK **36**. Their certificate matches
the repository's [pinned signing identity](../packaging/android/signing-certificate.sha256).
The standard and side-by-side test packages retain distinct application IDs.

| Artifact | SHA-256 |
| --- | --- |
| Standard Android APK | `190a18360794ee3861178f35c15dcfeb15f66ad07b350a3a22338629e56f4fc7` |
| Side-by-side test APK | `4a38c0097061d8cef1f3c5c0ad3476c0e90d10f8ad4b6c4805b91403a8b98d81` |

This inspection did not install either APK, clear application data or access a
phone. Private signing material was neither accessed nor included in the audit.

## Visible behavior and remaining acceptance

The [desktop gauge guide](DESKTOP_GAUGE_DISPLAY.md) includes actual native captures
and the verified menu, focus, geometry, minimize/restore and recording-continuity
contract. [Screen-awake requests](DESKTOP_DISPLAY_AWAKE.md) have scoped ownership
and an honest unavailable indicator; Windows/macOS native API probes are separate
from physical screen-idle behavior.

Physical Windows/macOS/Deck behavior, SteamOS Gaming Mode service availability,
Android document-provider/USB/background sessions and supervised Forester/EVO
testing remain open. ECU-bound dynamic-address provenance, RAM-tune metadata and
negotiation cleanup remain software audit work. No production flashing or live
tuning is enabled by this checkpoint.
