# RomRaider2 1.1.0 RC4 qualification record

Create one copy for each operating-system package pass. Do not edit the copy
bundled in the candidate.

## Candidate

- Result: `PASS`, `FAIL`, or `INCOMPLETE`
- Candidate archive:
- Candidate archive SHA-256:
- Source commit from `VERSION.txt`:
- GitHub Actions run:
- Test date and time zone:
- Operating system and architecture:
- Extracted to a normal user-owned folder: `YES` or `NO`
- Separate Java installation required: `YES` or `NO` (expected: `NO`)

The archive checksum, source commit, and workflow run must all describe the
same candidate.

## Desktop checks

- Package verification script: `PASS`, `FAIL`, or `NOT RUN`
- JavaFX provider identified in startup log: `PASS`, `FAIL`, or `NOT RUN`
- Matching OpenJFX platform modules present and Compose/Skia absent: `PASS`,
  `FAIL`, or `NOT RUN`
- Clean launch and shutdown: `PASS`, `FAIL`, or `NOT RUN`
- Package-owned settings used from an unrelated working directory: `PASS`,
  `FAIL`, or `NOT RUN`
- First-run definition prompt: `PASS`, `FAIL`, or `NOT RUN`
- Editor load/edit/save/reopen: `PASS`, `FAIL`, or `NOT RUN`
- JavaFX calibration direct edit, fine/coarse adjustment, restore, DTC switch,
  and 3D pitch/yaw: `PASS`, `FAIL`, or `NOT RUN`
- Calibration arrow keys, Ctrl+C/Ctrl+V, Ctrl+Z/Ctrl+Y, and wide/narrow scrolling:
  `PASS`, `FAIL`, or `NOT RUN`
- Calibration Shift+arrow/Shift+click range selection, Ctrl+A, block copy/paste,
  rejected oversized block, and one-step block undo: `PASS`, `FAIL`, or
  `NOT RUN`
- DimeMod, CarBerry, and MerpMod definition-evidence view: `PASS`, `FAIL`, or
  `NOT RUN`
- Logger Overview/Data/Graph/Dashboard/Dyno/Log Analysis: `PASS`, `FAIL`, or
  `NOT RUN`
- Logger Light/Dark and normal/narrow layouts: `PASS`, `FAIL`, or `NOT RUN`
- High Contrast is absent: `PASS`, `FAIL`, or `NOT RUN`
- Recording controls and offline log analysis: `PASS`, `FAIL`, or `NOT RUN`
- RC4 version shown in startup log and About screens: `PASS`, `FAIL`, or
  `NOT RUN`
- SteamOS welcome, first-run prompt, and 1280x800 handheld shell (SteamOS
  candidate only): `PASS`, `FAIL`, or `NOT RUN`

## Findings

Record concise observations and sanitized timestamps. Do not include private
paths, usernames, ROM names, definitions, profiles, raw captures, complete ECU
identifiers, serial numbers, network identifiers, or vehicle-owner details.

- Findings:
- Relevant sanitized log excerpt stored at:
- Reproduction result:

## Final review

- Diagnostics reviewed for uncaught exceptions: `YES` or `NO`
- No private vehicle or owner material is attached: `YES` or `NO`
- ECU flashing and production memory writing remained unavailable: `YES` or
  `NO`
- Retest required from a rebuilt candidate: `YES` or `NO`
- Reviewer and review date:

A failed or incomplete required item prevents publication of that package.
