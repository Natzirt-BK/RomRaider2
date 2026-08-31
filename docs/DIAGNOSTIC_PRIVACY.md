# Diagnostic privacy policy

RomRaider2 does not automatically upload exceptions, logs, ROMs, definitions,
settings, or telemetry. Error dialogs generate a local diagnostic report that
the user can review, save, or copy. Sharing it is always a separate manual
action performed by the user.

The generated report includes only:

- RomRaider2, operating-system, architecture, and Java version metadata;
- the exception class;
- a sanitized exception message when it contains no detected private data;
- application class/method/source-line stack frames.

The report deliberately excludes ROM bytes, definitions, logger captures,
settings, usernames, local paths, filenames found in sensitive error messages,
serial ports, IP/MAC addresses, email addresses, and vehicle identifiers. If an
exception message contains a local location or identifying value, the complete
message is replaced rather than partially exposing it.

Raw application logs remain local and are never attached automatically. A
support maintainer may ask the user to upload a specific log when the report is
insufficient. Users should inspect any requested log before
uploading it because logs created by third-party libraries may contain values
outside RomRaider2's diagnostic-report redactor.
