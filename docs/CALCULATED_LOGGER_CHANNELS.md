# Calculated logger channels — 1.1.3 development source

Android and the portable logger now resolve definition-backed calculated
parameters, including P200 engine load and P201 injector duty. Public 1.1.2
packages do not contain this change. This is read-only software support, not
new vehicle qualification or an ECU-writing feature.

## Selection and units

Import a logger definition and select the desired outputs in a profile or the
channel picker. Required input channels are resolved automatically, but do not
become selected outputs, extra gauges or CSV columns. The normal RomRaider CSV
header and selected output order are retained. Hidden inputs still add reads;
selecting fewer displayed channels does not necessarily mean fewer ECU bytes.

An explicit reference such as `[P21:ms]` uses that definition conversion. A bare
reference such as `P8` uses the parameter's **first definition conversion**,
independently of the profile's display-unit choice. This keeps a gauge-unit
change from silently changing a calculation. Definitions must use explicit
references when another input unit is required.

This is a deliberate difference from desktop's legacy derived converter, which
can use the currently selected conversion for a bare dependency. Desktop
comparisons therefore use definition-default bare inputs and explicitly bound
alternative units; they are not a claim of identical behavior for every desktop
display-unit configuration. Portable output gauges can independently use other
conversions without altering the calculation.

## Read and failure behavior

The existing SSM/MUT-II planner reads only concrete dependency addresses and
deduplicates shared bytes. Nested expressions are evaluated in dependency order
after all batches in the current cycle succeed. This is a complete software
cycle, not a simultaneous ECU snapshot; sequential reads still have time skew.

Missing or unsupported dependencies, wrong modules/ECU mappings, unavailable
units, cyclic graphs and malformed expressions make that selected output
unavailable before reads begin. Other valid selections remain available. No
address is guessed. Expressions are limited to 4,096 characters, 64 declared
dependencies and bounded parser complexity; dependency graphs are bounded to
4,096 nodes and 32 levels. Constant-only calculations with no input reads and
mixed calculated/address-backed definitions are unsupported.

Nonfinite raw values, nonfinite inputs/results and invalid division propagate
as unavailable, not measured zero. A failed batch publishes no partial cycle;
the next successful cycle cannot reuse an earlier cycle's inputs. Existing
foreground-only Android lifecycle and read-only transport restrictions remain.

## Verification and remaining work

Synthetic portable tests cover nested dependencies, explicit/default units,
shared bytes, selected output order, bad graphs, raw floating-point NaN/infinity,
zero RPM and recovery. Golden tests compare 400 synthetic formula results with
the actual desktop JEP-based converter. Android unit tests exercise the real
read-only session and disk-backed CSV writer with a fake MUT-II transport,
including mid-cycle disconnects.

The emulator-only `calculated-gauges` instrumentation phase checks import and
restart after source-document removal, calculated simulation, two-output gauge
selection, and a real session/writer using synthetic SSM responses while switching
through all 16 themes. It never opens an adapter. Physical phone/provider and
Forester/EVO acceptance remain deferred.

The owner's private Shinji setup was checked locally, without copying its files
into the repository: 16 ECU selections resolve, including P200/P201. Two DimeMod
IDs are absent from that catalog, and its external AEM selection has no portable
transport. Dynamic DimeMod discovery and serial AEM support remain separate work;
this change does not provide full Shinji-profile parity.

Local qualification on September 6, 2026: 55 calculated-channel portable checks,
81 display-enabled JavaFX tests and 35 Compose tests pass, with no JavaFX/Compose
skips. Ant tests/Linux compilation pass with the existing optional corpus skips.
Android automation unit tests (including three new calculated-session tests),
all three APK builds and their lint checks pass. The complete emulator lifecycle
script passes on the final code, including `calculated-gauges`. The local
reinstall used the same APK/version; increasing-version upgrade coverage belongs
to the hosted automation workflow, not that local reinstall. These build/test
results do not turn the development source into a published release.
