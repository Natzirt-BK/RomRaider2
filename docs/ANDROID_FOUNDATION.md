# Android foundation

Android is being treated as a portable companion, not a desktop package in a
smaller window. The first useful scope is offline ROM editing and log review.
ECU writing stays out until the USB transport and recovery behavior have been
tested on real devices.

The first application slice under `platform/android` can open and save ROM
documents through Android's document picker, apply bounded byte edits, review
traditional RomRaider wide-column and portable long-form logger CSV files, and
enumerate USB devices. It can also import a RomRaider logger definition and
profile, resolve selected direct SSM parameters and switches without
substituting missing ECU addresses, units, or module targets, and run those
selections through an offline logger preview. Selected serial external inputs
are listed as unavailable until their transport exists instead of being
silently dropped.
The preview exercises address batching, raw-value conversion, the live value
display, session recording, and CSV export with clearly marked simulated data.

The next transport slice can request Android USB
permission for an OpenPort 2.0, claim its bulk endpoints, identify the adapter
firmware, and read vehicle battery voltage. A separately labeled read-only live
path now opens the 4800-baud SSM K-Line channel, identifies the engine ECU,
resolves the profile against that exact ID, rejects transmission-only
parameters, executes the planned address batches, displays converted values,
and records CSV. The session stops when the application leaves the foreground.
This path is implemented but intentionally remains unqualified until RC5
connected testing.

The debug-signed APK builds locally against Android SDK 36. Android lint has
no errors. The first physical-device check on a Galaxy S25 confirmed that the
APK installs and opens normally. Play Protect showed the expected warning for
an unrecognized, sideloaded debug build. Traditional CSV import now passes on
the phone against the log that exposed the original header gap. The new logger
definition, profile, and offline-preview screen still needs a phone rerun. ROM
editing and USB behavior also need complete device checks.

The code shares the UI-free `platform/shared-core` module with future desktop
replacement screens. This keeps byte-range validation and portable log parsing
outside Android framework code. OpenPort USB identity, bounded control-response
handling, response markers, firmware parsing, and voltage parsing are shared as
well, so the same framing can support later SteamOS and macOS transports. The
shared core now also has a fragmented/coalesced OpenPort K-line decoder and a
bounded Subaru SSM codec that exposes only ECU initialization and address-read
requests. Its logger planner deduplicates addresses, keeps every multi-byte
parameter in one 64-address-or-smaller SSM request, maps response bytes back to
their parameters, and evaluates the arithmetic, comparison, `if`, and
`BitWise` conversions used by the v370 definition. It contains no memory-write
request API.

The existing Linux J2534 binary cannot be copied into the APK: it is built for
x86/glibc, while the Galaxy S25 uses Android ARM64/Bionic. The Android path uses
the platform USB host API directly and retains the BSD notice for the upstream
OpenPort command framing.

## Release gates

- Add definition-backed tables before describing this as a full ROM editor.
- Qualify the wired definition/profile, query, conversion, live-display, and
  CSV session pipeline on the read-only ISO9141 transport during RC5 hardware
  testing.
- Add calculated-parameter dependency evaluation; direct address-backed
  parameters are supported now.
- Confirm OpenPort behavior on supported Android hardware; a Linux J2534 library
  is not assumed to be Android-compatible.
- Test document access, rotation, background/foreground transitions, USB detach,
  and large logs on physical phones and tablets.
- Replace the public debug-signed test APK with a release-signed package only
  after the transport, upgrade-path, and privacy reviews.
