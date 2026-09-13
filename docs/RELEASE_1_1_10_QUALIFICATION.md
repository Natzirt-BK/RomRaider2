# 1.1.10 RC1 qualification

Release preparation is in progress. This record will be completed after hosted
builds and downloaded-package checks; no new public packages are claimed here.

## Completed development checks

- Core: 759 tests, 756 passed, three optional skips; Linux and Windows core builds.
- Desktop: 337 JavaFX tests, 336 passed, one optional skip; 48 Compose tests passed.
- Android: 137 JVM tests, lint and isolated automation builds passed.
- Shared checks include CSV rounding/source precision and 132 gauge layouts.
- Isolated emulator checks passed Android CSV export/recovery, gauge recording
  controls, compact mounted rendering and recording continuity across views.
- Native-window checks passed fullscreen/maximized setup, recording-switch
  validation/rollback and log-name validation/rollback. Screenshots were reviewed.

## Remaining acceptance

Physical phone button feel and scaling, vendor document providers, Steam Deck,
interactive Windows/macOS behavior and the consolidated Forester adapter,
DimeMod reconnect, vehicle-switch and recording tests remain outstanding.
No emulator or synthetic test qualifies an adapter or vehicle channel.
