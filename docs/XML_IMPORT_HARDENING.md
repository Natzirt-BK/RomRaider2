# Portable XML import hardening

This source follow-up repairs A5 from the [project audit](PROJECT_AUDIT_2026-09-06.md).
It applies to the shared logger definition, logger profile and offline ECU
definition readers used by Android and the portable desktop shells. It does not
replace the public 1.1.1 packages or claim an audit of every legacy XML reader.

## Boundary

Each reader retains its 16 MiB input cap. `PortableXmlInput` strictly decodes
the bounded bytes, rejects entity declarations in the decoded text, and hands
that exact text to SAX through a `StringReader`. The ECU reader reuses it for
both metadata and table passes. This prevents the scanner and parser from
interpreting the same input using different encodings. SAX specifies that a
[character stream takes precedence over the XML encoding declaration](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml/org/xml/sax/InputSource.html).

UTF-8 and UTF-16 BOMs/signatures are handled explicitly, along with ordinary
UTF-32 byte orders when the runtime supplies those charsets. ASCII-compatible
declared encodings such as ISO-8859-1 and Windows-1252 remain supported. The
signature/declaration distinction follows the [XML encoding detection guidance](https://www.w3.org/TR/xml/#sec-guessing).
Unsupported encodings, malformed byte sequences and contradictory encoding
declarations fail with an import error; they are not silently replaced.
Exotic encodings without a supported Unicode signature or ASCII-compatible
declaration are not supported.

Legitimate non-entity DTD declarations (`ELEMENT` and `ATTLIST`), predefined
references and numeric character references remain usable. The existing
conservative policy also rejects a literal `<!ENTITY` marker inside a comment
or CDATA. External-entity feature disabling and the empty external resolver
remain defense in depth; entity rejection no longer depends on optional parser
feature support. No imported external DTD is needed for these portable readers.

## Regression coverage

The shared-core check exercises all three public import readers with valid
non-ASCII labels and forbidden general, parameter and external entity
declarations in eight encodings. It also checks BOM variants, BOM-only input,
unrelated XML processing instructions, malformed/truncated bytes, conflicting
or unknown encodings, empty/oversized input and an ignored synthetic external
DTD. Existing exact-match, inherited-table, logger-selection and expression
checks continue to run.

The disposable Android automation app adds valid and forbidden fixtures across
the same eight encodings and all three readers to its emulator seed phase.
That phase still includes failed-save recovery checks and is followed by the
existing restart, upgrade, clear/corrupt-state and desktop CSV compatibility
checks. All fixtures are synthetic; no vehicle connection or private definition
is involved.

## Verification

Source `36339446` passes 190 XML regression assertions, alongside the existing
portable checks. Fresh local JavaFX (56), Compose (34), and Android automation
unit (40) tests pass with zero failures or skips. Android standard/diagnostic
APK builds and lint, plus automation and instrumentation APK builds/lint, pass.

The [Android emulator regression run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34011120364)
passes at that exact source, including the new XML checks and existing save,
restart, upgrade, clear/corrupt-state and desktop CSV compatibility phases.
The same source passes [Linux and Windows tests/package verification](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34011120384).
The subsequent verification-recording commit changes documentation only.

Gauge hysteresis/unit identity (A3/A4), next-release version/signing migration
and physical hardware qualification remain separate work. No new public
application release was published.
