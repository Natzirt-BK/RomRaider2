# RomRaider2 Android foundation

This standalone Android project is an early portable client. It deliberately
ships no ECU writing path.

Implemented:

- open a ROM through Android's document picker;
- inspect the first 256 bytes, apply bounded hexadecimal edits, reset edits,
  and save a separate copy;
- open and summarize traditional RomRaider wide-column and RomRaider2
  portable long-form CSV logs;
- securely import an existing RomRaider logger definition and profile;
- resolve direct SSM parameters and switches against exact units, module
  targets, and ECU mappings;
- show selected serial external inputs as unavailable instead of silently
  dropping them;
- plan deduplicated, 64-address-or-smaller read batches and apply the same
  signed, unsigned, endian, float, arithmetic, conditional, and bitwise
  conversions used by the desktop logger;
- run a clearly labeled simulated logger session with updating values and save
  that session as portable CSV;
- enumerate attached USB devices;
- request Android USB permission for an OpenPort 2.0, claim its bulk interface,
  identify its firmware, and read vehicle battery voltage without querying the
  ECU;
- share a bounded OpenPort K-line stream decoder and read-only Subaru SSM
  init/address codec with no ECU write commands;
- open a foreground-only 4800-baud SSM K-Line session, identify the engine ECU,
  exclude transmission-only parameters, execute the selected read batches,
  display converted values, and record a live CSV.

Current preview limits:

- definition-backed table editing;
- calculated logger-parameter dependency evaluation;
- qualify the wired read-only OpenPort logger on a supported car during RC5;
- responsive large-screen and landscape layouts;
- release signing, Play Store packaging, and broader physical-device checks.

The debug APK has launched successfully on a Galaxy S25, and the traditional
logger CSV that exposed the original header gap now opens correctly. The newer
definition/profile import and offline logger preview still need a phone rerun.
The OpenPort preparation and live logger paths still need connected-device
checks. The live button is labeled as awaiting RC5 qualification, stops when
the app leaves the foreground, and keeps ECU writing absent.

See [Android preview testing](../../docs/ANDROID_PREVIEW_TESTING.md) before
sideloading the APK or reporting a result.

The Android SDK is intentionally separate from the desktop build. With a Java
21 JDK and Android SDK 36 installed:

```bash
cd platform/android
gradle :app:assembleDebug
```
