# Scoped dependency advisory checks

This follow-up improves A8 of the [project audit](PROJECT_AUDIT_2026-09-06.md).
It does not turn the earlier dependency-integrity checks into a vulnerability
certificate or enable/change GitHub repository security settings.

Run `python3 packaging/security/check_advisories.py` from a Git checkout. It uses
only Python's standard library. It queries public Maven names/versions from the
JavaFX and Compose Gradle locks, embedded Maven metadata in tracked bundled JARs,
and SHA-256-bound mappings for JNA's renamed core/platform JARs. JNA provenance
is recorded in `licenses/JNA-5.19.1.txt`. It sends package coordinates and paging
tokens to the [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/),
not source files, vehicle data, settings, signing material or repository history.

The JSON report distinguishes findings, incomplete scans, unmapped artifacts,
excluded retired libraries and other coverage limits. API failures, incomplete
responses, invalid/repeated pagination, or changed mapping hashes fail the check
instead of reporting success. Exit codes: 0 = no advisories returned for the
queried subset; 1 = advisories found; 2 = incomplete/error. `--inventory-only`
does not contact OSV and explicitly produces no advisory verdict.

The dedicated GitHub workflow runs on relevant dependency/scanner changes or
manual dispatch, without secrets or write permissions. It retains the report
even when a scan fails. Synthetic unit tests cover response cardinality,
findings, pagination, malformed data, batch association, inventory metadata,
unknown artifacts, hash drift and empty locks.

## September 6, 2026 UTC snapshot

The initial check queried **227 unique Maven name/version pairs** and OSV
returned no known advisories for that subset. This includes 221 locked pairs,
JNA/JNA-platform 5.19.1, jSerialComm 2.11.4, and Log4j api/core/1.2-api 2.26.1.
This is a point-in-time database result, not proof that these libraries or this
application cannot be exploited. A dependency advisory still requires relevance
and reachability assessment; it is not automatically an application exploit.

Eight bundled JARs currently have no verified Maven mapping in this check:
args4j, com4j, jama, jamlab, jcommon, JEP, JFreeChart and phidget21. They remain
explicitly unqueried. A Maven Central checksum-search attempt timed out; no
guessed mapping was accepted as verified. The four retired Graph3d/Java3D/vecmath
JARs excluded by current image builders are listed separately, not marked safe.

Native libraries/drivers, the JDK/OS, unlocked Android/build-tool transitives,
GitHub Action provenance, complete licensing, secret history and source-code
vulnerabilities remain outside this scanner's scope. Continue resolving those
gaps separately. No dependency was upgraded solely to make a report look clean,
and no vulnerability-free/full-project security verdict is claimed.
