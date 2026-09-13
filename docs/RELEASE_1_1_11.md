# RomRaider2 1.1.11 RC1

## Changes

- Desktop and Android SSM channel catalogs require vehicle support evidence:
  capability flags, an exact ECU mapping, or confirmed calculation dependencies.
  Discovered DimeMod channels remain available. Generic addresses without
  support information no longer qualify on their own.
- Desktop SSM channels stay hidden until identification, and engine/transmission
  channel targets are respected. External sensors remain separate.
- Original RomRaider and RR2 can run side by side using separate startup ports.
- Windows/Linux editor: restored Refresh and 160 KB ↔ 192 KB Convert Image.
  Conversion exports a separate copy; Refresh confirms before replacing edits.
- Fixed clicking to open multiple DTC tabs with identical calibration values.

## Compatibility

Desktop packages include Java 21. Android requires Android 8.0 or later and
uses internal build code 110416. The OpenPort Test APK installs separately.
macOS packages are unsigned and retain their existing editor interface.

MUT-II channel filtering and adapter support are unchanged. SSM support evidence
does not independently validate a definition's scaling or physical sensor fitment.
No production flashing or live tuning is added.

This is a release candidate. Consolidated in-car and physical-device acceptance
remains pending. See the [qualification record](RELEASE_1_1_11_QUALIFICATION.md).
