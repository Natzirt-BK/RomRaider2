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

These checks do not detect every semantically wrong definition or establish
vehicle-specific conversion correctness. Legacy editor table-overlay behavior
and dataflow simulations need separate missing-data review; they are not gauge
or CSV acceptance evidence. Android's hosted lifecycle run at `4e2fcc88` failed
while the instrumentation used a terminated Activity worker; app/lifecycle
diagnostics were added for investigation. That run is not counted as a pass.
