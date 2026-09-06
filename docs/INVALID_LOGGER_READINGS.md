# Invalid logger readings

During the audit follow-up, both logger engines were found to replace non-finite
conversion results with `0.0`. That can make an invalid expression or raw float
look like a valid measurement, including to gauge warnings.

## Portable engine and Android repair

The portable converter now retains invalid results as `NaN` (missing data), and
rejects non-finite raw float samples even if a constant expression could mask
them. Valid zero readings and normal numeric conversion/formatting are unchanged.
The existing RomRaider-style CSV writer emits an empty cell for invalid samples.

Android shows an em dash / `NO VALID DATA`, hides the current needle and active
segments, and updates peaks only from finite samples. A later valid reading
recovers normally; resetting peaks during a gap waits for the next valid sample.
Unknown-channel scale calculation also handles missing/overflowing bounds.

Automated evidence: 18 portable assertions cover invalid arithmetic, raw floats
in both byte orders, constants masking invalid raw floats, valid/gap/recovery/zero
CSV output and import. Four Android model tests cover peaks, reset/recovery,
display wording and finite drawing coordinates. Shared-core checks, all 44
Android unit tests, standard/diagnostic APK assembly and lint passed locally.
No vehicle or physical phone was accessed; these are synthetic checks.

## Desktop engine and consumers

Raw, derived and external logger conversions now preserve invalid operands and
results as missing data. A derived expression cannot mask an invalid input by
multiplying it by zero. Converter formatting and the actual CSV row handler emit
empty cells; an empty cell still completes a row, unlike an unreceived value.
The shared live-sample display is an em dash regardless of a supplied numeric
display string. Swing table peaks ignore invalid samples and recover normally.

JavaFX and Compose warning states remain unavailable for invalid current values.
Compose statistics use finite samples only; progress calculations reject invalid
inputs. Both graph renderers break paths at invalid samples instead of connecting
across gaps. Legacy Swing graphs use explicit null data points; the Swing gauge
container hides its numeric style behind `NO VALID DATA`, preserving valid peaks
without feeding invalid numbers to old rendering code. MAF, injector and dyno
analysis reject a response containing a non-finite reading, including optional
filter channels, rather than treating that filter as absent.

Nine desktop core tests cover arithmetic/float/derived/external conversion,
table peaks, display/analysis gates, actual Swing gauge/graph handling and the
actual CSV row boundary with an in-memory sink. The display-enabled JavaFX
warning test checks the missing value label; a new Compose model test covers
invalid statistics/progress and valid zero recovery. The full core suite and
desktop build pass (three existing opt-in skips); 60 JavaFX and 35 Compose tests
pass locally. No adapter, editor write or physical vehicle was used.

## Dataflow and shared expression cache follow-up

Dataflow simulation now invalidates the stored output and presentation after an
invalid input/result, instead of returning zero and leaving a previous output
available downstream. Table actions reject missing/non-finite inputs and missing
tables, clear old input markers, and never perform a lookup with invalid inputs.
The simulation window clears table overlays for unavailable results. Four tests
cover chains, real zero/recovery, referenced-invalid versus unrelated variables,
missing tables and a synthetic table lookup that asserts all inputs are finite.

Running those tests together with logger tests exposed a real cross-feature
cache collision: `JEPUtil` keyed parsers only by expression text. A map-based
constant expression could leave a parser without `x`, making a later scalar
logger call throw; removing map variables could also reuse their previous values.
The cache now keys on expression plus the complete sorted variable-name set,
updates all bindings, provides the same standard/BitWise functions in both call
modes, and maintains its declared 32-entry LRU limit. Five ordered regression
tests cover cross-mode calls, removed/new/null bindings, standard functions and
eviction. The full core suite passes with these tests and the dataflow/logger
tests in one process, not only when run independently.

## Legacy editor logger overlays

Overlay updates are now marshalled to Swing's event thread with captured values,
and registration changes discard queued readings for closed/replaced bindings.
A non-finite bound channel clears its entire table's overlay and displays
`NO VALID DATA`; finite sibling-axis updates cannot reactivate that table until
the invalid channel recovers. Unrelated tables remain usable, and unbound logger
channels do not block overlays. Reset clears missing-state memory and traces.
Three tests cover the real handler/registry boundary with instrumented synthetic
views, including invalid-axis persistence, recovery with zero, unrelated tables,
reset, UI-thread confinement and queued deregistration. The complete core suite
and Linux build pass with these tests.

These checks do not detect every semantically wrong definition or establish
vehicle-specific conversion correctness. Missing-data handling does not certify
table overlays as synchronized ECU execution traces or qualify real tuning.

## Android lifecycle evidence

Hosted run `34013543594` at `4e2fcc88` failed while instrumentation used a terminated
Activity worker. Run `34013852775` at `73ea5783` passed after adding diagnostic
capture, but that alone does not resolve the intermittent failure. The harness
follow-up tracks resumed/recreated Activities instead of keeping the initial
instance, retries only a superseded/destroyed worker (not a live-worker failure),
and explicitly recreates an Activity before importing fixtures. It also draws
actual Android gauges through invalid/recovered samples and checks their
accessibility status. Local automation assembly, all 44 unit tests, portable
checks and lint pass. Hosted [recreation regression run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34014259076)
at `41138aad` passes, including the explicit recreation and actual gauge-drawing
checks, restart/upgrade/recording preservation, and desktop CSV compatibility.
