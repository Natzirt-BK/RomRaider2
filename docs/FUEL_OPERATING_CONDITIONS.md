# Named operating conditions — 1.1.3 development source

JavaFX MAF and Injector saved-log analysis now exposes the eight scalar condition
categories from the legacy workflow alongside the existing three custom filters
and [recorded-time transient filter](FUEL_RATE_FILTER.md). Public 1.1.2 downloads
do not include these additions.

Expand **Operating conditions** in the analysis sidebar. It starts collapsed,
with every condition disabled. Enabling a condition reveals its channel and
limit fields. The collapsed heading shows the enabled count; imported named
conditions expand for review. No channels, numeric limits or ECU state codes are
chosen automatically.

| Condition | Required comparison |
| --- | --- |
| Closed-loop / open-loop state | Exact numeric value |
| Air/fuel ratio | Inclusive minimum and maximum |
| Engine speed | Inclusive minimum and maximum |
| Mass airflow | Inclusive minimum and maximum |
| MAF voltage | Inclusive minimum and maximum |
| Intake air temperature | Inclusive maximum only |
| Coolant temperature | Inclusive minimum only |
| Tip-in throttle | Exact numeric value |

All enabled conditions are ANDed together with custom filters and any enabled
rate condition. The three custom slots remain independent; named conditions do
not consume or relabel them. Each condition uses the selected CSV column's own
units. There is no Celsius/Fahrenheit, AFR/lambda or other signal-unit conversion.
Verify the definition's units and state encoding before entering limits and
confirming the analysis inputs. A label does not prove a column is appropriate.

State and tip-in comparisons are exact, not truncated integers or approximate
matches. One-sided conditions have no invented opposite bound. Required limits
must be finite and ordered, and an enabled but unmapped/incomplete condition
blocks analysis, setup export and condition-link activation. Disabled draft
fields do not affect calculations and are not exported.

Missing/nonfinite values in any enabled condition reject that sample as invalid;
they cannot bypass the condition. Valid values outside a limit count as filtered.
This uses the same immutable accepted-sample pipeline as custom/rate filtering,
so binned results and curve fits replay the same conditions. It does not infer
that an accepted sample is suitable for tuning.

## Saved setups and links

Setups with named conditions use strict `.rr2analysis` schema version 3. The file
stores each enabled condition's kind, exact channel label/unit identity and its
required bounds, plus existing mappings, custom filters, fuel assumptions and
optional rate condition. It still excludes captured samples, source paths, ROM
data, sample indices and unit confirmation. At most one of each of the eight
named kinds is accepted; duplicate kinds, unknown fields, invalid comparison
shapes and malformed limits fail before inputs change.

Exports without named conditions remain version 1 (no rate filter) or version 2
(with rate filter). Those older imports explicitly clear named conditions while
preserving their defined rate behavior. Older application readers reject a
version-3 file rather than silently dropping its conditions. Existing bounded
UTF-8, regular-file and atomic-replacement safeguards remain in force.

On import, missing or ambiguous channel identities leave the condition enabled
and its limits visible. The user must remap it or explicitly disable it; it is
never discarded automatically. Confirmation is cleared and the sample range
resets for review. Failed or wrong-kind imports preserve current inputs.

An explicit MAF/Injector condition link shares all eight enabled states, channel
choices and bounds in addition to range, custom filters and the rate condition.
The review lists each enabled named condition and its one-based CSV column.
Long reviews are scrollable and default to Cancel so the buttons remain reachable
on compact displays.
Subsequent edits mirror invalid drafts too, clear both confirmations, and
invalidate stale results/curves and pending MAF transfer reviews. Dataset/setup
replacement or closing a pane disconnects the link as before.

## Migration scope

The categories and comparison shapes follow the retained legacy
[MAF controls](../src/main/java/com/romraider/logger/ecu/ui/tab/maf/MafControlPanel.java)
and [injector controls](../src/main/java/com/romraider/logger/ecu/ui/tab/injector/InjectorControlPanel.java).
The new saved-log controls deliberately do not copy their Subaru-specific state
constant, default tuning limits, optional-input bypass, or wall-clock derivative
behavior. State matching is exact rather than truncating a floating value.
Recorded-time adjacency is defined separately in the rate-filter guide.

This completes exposing the named scalar gates for explicit saved-log use, not
live-capture parity, automatic vehicle-specific presets or calibration approval.
The separate Log Analysis cursor/statistics view is not linked by this feature.
Injector table transfer, log-to-map tracing, broader comparison tools and the
remaining mobile work continue separately. No vehicle connection, ECU write or
automatic ROM change is introduced.

Synthetic tests cover all eight conditions together, independent violations,
missing inputs, inclusive/one-sided limits, exact state matching, disabled
conditions, injector projection and raw replay. Setup tests exercise version-3
round trips with and without rate filtering, duplicate kinds, strict field
validation and old-schema clearing. Native JavaFX checks cover defaults,
required mappings, imports, unresolved channels, linked invalid drafts, combined
named/custom/rate analysis, curve invalidation and dataset replacement, including
the expanded controls in a 1,000 × 640 workspace. These are software checks, not
vehicle or calibration acceptance.

Local qualification on September 6, 2026: six named-condition engine tests and
all ten setup-store tests pass (including two new schema/duplicate-kind checks).
All 117 display-enabled JavaFX tests and 35 Compose tests pass with no skips in
those suites. Seven new JavaFX tests cover the native integration and bounded
review dialog. The full Ant suite/Linux compilation (existing optional corpus
skips), portable checks and Linux staging also pass. Public downloads remain
1.1.2 pending separate release qualification.
