# Desktop CSV repair and RC4 refresh — 2026-09-05

The user authorized finishing B1, updating the public RC4 release, publishing
Android preview3, and retaining only the latest desktop RC and Android preview
release entries. This is approval to refresh **prereleases**, not evidence of
hardware qualification or a stable-release promotion.

## B1 repair

- CSV requests capture their own absolute source file and generation.
- Only the latest selected request may publish a dataset or error on the UI
  thread, including when an older completion was queued before a newer choice.
- Cancelling the chooser leaves an already accepted load intact.
- Closing Logger invalidates queued/in-flight completions; late results cannot
  create a replacement analysis pane or error dialog.
- A replacement pane is constructed before the previous pane is closed. The
  previous dataset remains usable while the new CSV loads, and owns its original
  marker sidecar throughout.
- Parser failures retain the current pane and report failure for the correct
  source. No CSV data is written by loading or marker operations.

## Verification

- Ten deterministic coordinator tests cover reversed/queued ordering, stale
  errors, latest failure, repeated paths, chooser cancellation, close/disposal,
  synchronous errors and invalid parser/presentation results. These run without
  a toolkit on both Linux and Windows CI.
- Three native JavaFX tests exercise the actual window/pane, replacement cleanup,
  and closing during successful/failed loads. A synthetic marker write lands only
  in the displayed CSV's sidecar; the other sidecar and CSV bytes stay unchanged.
- Local JavaFX suite: 43 tests passed with native Xvfb smoke enabled. No ECU,
  private ROM, definition or captured vehicle log was used.
- New candidate CI, package verification and publication results are recorded
  after the artifacts have been built and checked.

## Release boundaries

The earlier [desktop repair audit](DESKTOP_REPAIR_AUDIT_2026-09-04.md) remains the
source for the broader acceptance matrix. B1 is repaired here; B2 (optional
Windows Innovate LM-2 MTS/COM4J compatibility), physical DPI/accessibility,
representative real-file workflows and on-car MUT-II/OpenPort tests remain open.
Linux, Windows and SteamOS use JavaFX; the macOS previews retain the separate
Compose shell. Do not transfer JavaFX UI results to macOS.

Android preview3 has its own version, source and [audit](ANDROID_MUT2_AUDIT_2026-09-05.md).
It is not a stable release or a vehicle-qualified logger. Production ECU memory
writing and flashing remain unavailable.
