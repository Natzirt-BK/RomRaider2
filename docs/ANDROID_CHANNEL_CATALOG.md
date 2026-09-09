# Android channel catalog

The channel picker labels each entry with its parameter ID, units and source
type, with search by name, ID or units. Identical names do not imply identical
parameters: ECU-specific variants retain their separate IDs.

SSM users first run **Connect & Find Channels**, or start with an imported
profile. The logger and gauge pickers then use the same vehicle-specific catalog:
only engine channels from the loaded definition and successfully discovered
DimeMod metadata, filtered by ECU address mappings, standard SSM support flags
and resolvable calculated dependencies. The all-ECUs picker is removed.

The SSM support-byte indices match the desktop init-payload layout. Unsupported
flags, including flags beyond a short reply, exclude the channel and dependent
calculations. Filtering applies before both picker publication and recording
query planning, so importing an incompatible profile cannot bypass it. Channels
without support flags rely on the definition mapping; a matching mapping is not
independent validation of a custom definition's addresses or conversions.

Unavailable imported selections are hidden from the picker, counted in its
explanation, preserved in the saved profile, and excluded from polling/CSV.
Searching and editing visible choices preserve hidden selections and existing
CSV column order. **Clear all** explicitly clears the entire profile.
Transmission-only channels never appear in the engine picker.

The last identified catalog is retained only for the same loaded definition in
the current recording service. Replacing the definition or losing the service
requires identification again. Each new session rebuilds its catalog from its
own ECU reply; no retained identity authorizes reads. Before identification,
SSM users can import profiles but are not offered an unfiltered channel list.
MUT-II continues to offer channels from the loaded definition: its generic
response does not identify a calibration or supply SSM support flags, so users
must supply a vehicle-matching definition.

## Automatic DimeMod discovery

Android SSM setup now performs the author's discovery handshake automatically.
The protocol sequence follows the [upstream implementation](https://github.com/DimeSPb/RomRaider/blob/master/src/main/java/com/romraider/logger/ecu/comms/io/connection/SSMLoggerConnection.java).
There is no extra opt-in prompt. The screen says **Live Logger**, not read-only
connection setup: discovery writes `0xDE` to `0x000060`, expects `0xAD`, reads the
metadata size/address, and writes zero to `0x000000` to exit negotiation.
A non-DimeMod acknowledgement restores the saved register value before ordinary
SSM logging continues. Channel polling after setup remains read-only; this adds
no reset, flash, tuning or general-purpose memory-write API. MUT-II is unchanged.

**Connect & Find Channels** runs identification/discovery without polling a
recording profile. After adapter cleanup, choose the channels and prepare the
adapter again to record. Starting an SSM recording also performs discovery.
Channels whose inputs/features are not enabled are not invented. The version
2.0–2.3 parser retains the desktop field order, IDs and conversion expressions,
including `DM911` ethanol percentage and `DM912` sensor voltage. Unknown later
versions are rejected, not parsed using assumed layouts.

Metadata uses bounded K-line A0 memory reads (up to 96 bytes), strict frame and
checksum checks, and a 30-second discovery deadline. Only a complete catalog is
published. Confirmed negotiation entry receives one bounded exit even after
Stop or a header error. An unconfirmed entry is not retried or followed by a
guessed cleanup write. Unconfirmed entry/restoration/exit errors remain visible
even after Stop. No reconnect is automatic.

Each new recording rediscovers metadata; runtime addresses are bound to the ECU
identified in that session. The retained catalog is a channel-picker aid, not a
persistent address cache. Source XML is unchanged. Imported profile selections
by ID can resolve against discovered channels. Gauges can select those channels
from the same session catalog, and CSV retains RomRaider formatting.
Editing the picker before discovery retains unresolved profile IDs and preserves
the order of previously selected CSV columns. **Clear all** still explicitly
clears the entire profile.

Automated tests cover support-byte offsets, switches, short replies, malformed
flags, same-name ECU variants, calculated dependencies, profile retention,
unsupported-channel exclusion from polling/CSV, and stale-identity invalidation.
DimeMod tests cover runtime layouts, stock-ECU fallback, cancellation, timeouts,
malformed metadata, real transport packet decoding with fake USB, session
isolation, ethanol decoding and CSV output.

Local qualification: 118 Android unit tests, lint and shared-core checks passed,
including the five-million-value CSV test with a 64 MiB heap. Seven isolated
Android 36 emulator phases passed: setup seeding/restoration, gauge continuity,
demo Show/Hide, gauge setup, setup transfer, and vehicle-channel selection/search.
The vehicle-channel phase also checks gauge filtering and copy-from-logger.
These automated checks send no ECU commands. The new support-flag filtering
still needs a vehicle test; an earlier build's successful logging does not
validate this filtering change.

## Logger preview removal

The Android logger's Offline Preview and simulated CSV export are removed.
The separate **Show/Hide Gauge Demo** remains a clearly labeled visual sample,
with no log recording. Existing saved files are unchanged. UI automation now
uses an instrumentation-only fake transport through the recording service;
the production application has no offline logger simulation entry point.

The removal passed 110 unit tests, lint and seven isolated Android 36 emulator
phases: setup seeding/restoration, gauge view continuity, demo Show/Hide, gauge
setup/fullscreen controls, calculated channels and setup transfer. Those tests
use the automation package only; they do not connect to a vehicle.
