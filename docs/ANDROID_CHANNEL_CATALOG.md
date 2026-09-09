# Android channel catalog

The channel picker labels each entry with its parameter ID, units and source
type. Identical names do not imply identical parameters: ECU-specific variants
must retain their separate IDs. Transmission-only entries are omitted from the
engine picker unless already selected in the imported profile.

When the recording owner retains an identified ECU for the same definition
object, the picker offers **Mapped for last ECU** and **All engine channels**.
The first view resolves address mappings and calculated dependencies using the
existing engine selection resolver. Selected unavailable entries remain visible
so filtering cannot silently remove them. Definition replacement discards this
hint. No identity hint is persisted or used to authorize ECU reads.

"Mapped" means resolvable in the definition, not hardware-verified support.
Standard SSM availability-bit filtering remains separate work. Offline browsing
therefore still includes legitimate same-name variants, now distinguishable.

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

Automated tests cover same-name IDs, mappings and dependencies, retained
selections, both runtime layouts, stock-ECU fallback, cancellation, timeouts,
malformed metadata, real transport packet decoding with fake USB, session
isolation, ethanol decoding and CSV output. Hardware validation remains pending;
no actual ECU communication was performed during development.

Local qualification: 110 Android unit tests passed, Android lint passed, the
signed 1.1.6 APK built successfully, and shared-core checks passed (including
the five-million-value CSV test with a 64 MiB heap). No Android device UI test
or actual vehicle test is claimed by this checkpoint.
