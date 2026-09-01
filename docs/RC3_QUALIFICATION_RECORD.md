# RomRaider2 1.1.0 RC3 qualification record

Create one copy of this record for each operating-system and hardware pass.
Do not edit the copy bundled in the candidate package.

## Candidate identity

- Result: `PASS`, `FAIL`, or `INCOMPLETE`
- Candidate archive:
- Candidate archive SHA-256:
- Source commit from `VERSION.txt`:
- GitHub Actions run:
- Test date and time zone:
- Operating system and architecture:
- Package extracted to a normal user-owned folder: `YES` or `NO`
- Separately installed Java required: `YES` or `NO` (expected: `NO`)

The archive checksum, source commit, and CI run must describe the same
candidate. Stop if any value is missing or mismatched.

## Test scope

- Checklist used: `WINDOWS_RELEASE_CHECKLIST.md` or
  `LINUX_IN_CAR_QUALIFICATION.md`
- Clean-machine application checks: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Editor load/edit/save/reopen checks: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Logger definition install/reload checks: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- J2534 discovery and architecture checks: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Normal connect/log/disconnect/reconnect: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Unexpected USB removal and recovery: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Sustained logging: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- External serial sensor: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Mitsubishi MUT-II: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Analysis/replay/marker checks: `PASS`, `FAIL`, `NOT RUN`, or `N/A`
- Clean shutdown and adapter release: `PASS`, `FAIL`, `NOT RUN`, or `N/A`

## Sanitized environment

- Vehicle family/model year (optional, no registration or VIN):
- ECU ID with identifying suffix redacted:
- Interface model and vendor driver version:
- Logger-definition version (no private path):
- External sensor model and connection type:
- Capture duration and approximate selected-channel count:

## Findings

Record concise observations, failed checklist item numbers, exception classes,
and sanitized timestamps. Do not paste private paths, usernames, ROM names,
definitions, profiles, raw captures, complete ECU identifiers, serial numbers,
network identifiers, or registration information.

- Findings:
- Relevant sanitized log excerpt stored at:
- Reproduction result:

## Final review

- `rr_system.log` reviewed for uncaught exceptions and reconnect loops: `YES`
  or `NO`
- Diagnostic/privacy review completed: `YES` or `NO`
- Flashing and ECU memory writing remained unavailable: `YES` or `NO`
- No private vehicle or owner material is attached: `YES` or `NO`
- Retest required from a rebuilt candidate: `YES` or `NO`
- Reviewer and review date:

A failed or incomplete required item prevents the affected qualification claim.
Do not change `FAIL` to `N/A` merely to promote the candidate.
