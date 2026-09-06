# Gauge warning and conversion repairs

This source follow-up addresses A3/A4 of the [project audit](PROJECT_AUDIT_2026-09-06.md).
The public 1.1.1 downloads have not been replaced.

## Warning state

JavaFX and Compose use a shared, thread-safe `LoggerGaugeAlertTracker`. It
consumes every published reading, not just values that happen to be rendered.
Repeated paints, layout changes and JavaFX detached gauges read the same state
without advancing it. Recording transitions retain hysteresis; connect,
reconnect, stop, channel removal, conversion changes and configuration changes
reset it. A new configuration can be evaluated against the current reading
when saved. Missing/non-finite readings are unavailable, not NORMAL, and an
invalid reading clears previous hysteresis state. This applies to values that
reach the live-data API; it does not certify all protocol/converter failure paths.

High/low limit crossings take precedence over the opposite warning's
hysteresis band. Compose peak recall displays historic peaks but warnings
continue to follow current readings; the tile explicitly labels that distinction.
Removed detached channels show a no-data message instead of stale warnings.

## Conversion-scoped settings

Channel catalogs and live samples carry a stable fingerprint of the converter
implementation, units, expression, format and data type. An option index, object
identity or unit label alone is not used. Both the standalone runtime and the
legacy-hosted replacement workspace publish this identity. Reordering options
does not reinterpret custom scales or warnings; changed expressions with the
same label produce a different identity.

Settings schema 2 stores this identity with each channel's custom scale and
warning limits. Old schema-1 limits remain stored but inactive because their
conversion is unknown. Different-conversion limits also remain inactive and are
not guessed or numerically transformed. Returning to the exact saved conversion
reactivates its limits. Saving new limits replaces the one saved configuration
for that channel; this is not a multi-conversion preset library. Older application
versions do not understand schema 2, so preserve settings backups before a
downgrade.

JavaFX now provides a **Limits** editor on dashboard/detached cards. It validates
optional low/high warnings, both-or-neither scale endpoints, finite values and
nonnegative hysteresis, with explicit clear/cancel actions. Unknown conversions
cannot save limits. Both UIs bind newly saved limits to the displayed conversion
and reject a save if that conversion changed while the dialog was open.
Inactive legacy limits are not prefilled into a different conversion's editor.
No automatic vehicle safety limits, ECU writes or live tuning are introduced.

## Verification

Ten new core tests cover hysteresis, invalid values, opposite-limit crossings,
resets, inactive legacy/different-conversion configurations, settings XML round
trips, stable fingerprints, and actual profile loading/application after option
reordering. The full Ant unit suite and Linux core build pass; three existing
opt-in native/corpus tests remain skipped.

JavaFX passes 60 tests, including three limits-input tests and a new display
smoke test using the real live-data bus without connecting hardware. The latter
publishes 101 then 98 before repaint and verifies HIGH with threshold 100 and
hysteresis 5 in both attached and detached views, then tests clearing at 95,
invalid data, conversion change and removal. The initial test exposed an older
queued catalog callback clearing newer readings; filtering now occurs in event
order before queuing the UI refresh. Compose's 34 tests and portable-core checks
also pass. Hosted verification is recorded after completion.
