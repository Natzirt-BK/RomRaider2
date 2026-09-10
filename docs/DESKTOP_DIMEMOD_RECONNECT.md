# Desktop DimeMod reconnect handling

Development after 1.1.9. These changes are not in the published packages.
They apply to the desktop logger, including the retained Swing interface, not
Android's separate acquisition service.

## Cache verification

Discovered metadata retains a frozen record of the complete SSM identification
reply, module address/tester, logical adapter/port, protocol, transport and serial
configuration, plus the advertised metadata block address and length.

A reconnect does not trust an ECU-ID match alone. Before reading cached runtime
addresses, the connection identifies the module and reads the complete original
metadata block. Both identification and metadata bytes must match. Missing
provenance, a different connection configuration, changed bytes, invalid replies
or cancellation cannot authorize runtime reads.

Initialization and polling currently open separate connections. Dynamic channels
carry their metadata owner, so the polling connection performs its own read-only
verification before its first dynamic query. Verification is retained only for
that connection and metadata object; closure or a polling failure removes it.
Module/configuration checks remain in front of each dynamic query batch. Standard
definition-file channels do not invoke DimeMod verification or discovery.
Unsupported backends reject retained DimeMod query rows before transmission,
including the interval before an asynchronous catalog refresh removes them.
Connection shutdown still closes and detaches the transport if line cleanup fails.

Verification restarts the polling stream without changing the user's fast-polling
choice. DimeMod channel addresses always encode three wire bytes, including low
addresses. Runtime snapshots retain provenance without sharing mutable error or
input state. Diagnostic runtime reads use the same verification boundary.

## Discovery versus automatic retry

A new logger run retains the existing initial DimeMod discovery opportunity,
including a run started by the configured automatic-startup option.
That legacy negotiation includes writes; it is not a read-only discovery API.
The opportunity is consumed once per run, not renewed by automatic retries.

A rejected cache becomes unavailable and its capabilities become unknown, not
“DimeMod not present.” Identification compares the complete reply in both desktop
owners. Clearing the displayed cache during identification does not turn its
verification candidate into a discovery miss.

Standard logging may continue after optional DimeMod initialization fails.
Automatic retries do not replace a rejected cache through additional negotiation
writes. To request rediscovery after rejection, explicitly Disconnect, then
Connect again. Reload the logger profile after discovery if its previously
unavailable channels need selecting. Restarting a logger run is the existing
explicit discovery action; no new ECU-writing tool is added.

## What verification establishes

This checks the current identification and metadata used to obtain channel
addresses. It is not a hash of the entire firmware image, a hardware serial-number
check, or cryptographic authentication of the vehicle. Two modules exposing
identical identification and metadata cannot be distinguished by those replies.
Unreported hardware replacement during uninterrupted polling cannot be detected
without another protocol observation. Reconnects and new connections reverify.

The advertised metadata span remains bounded to the existing 24-bit SSM address
space and metadata length limit. No new fixed ECU address, firmware fingerprint
location or write-based verification probe is assumed.

## Acceptance

Synthetic transport checks exercise native K-line and CAN framing, valid cache
reuse, changed identification/metadata, missing provenance, module/adapter
changes, malformed replies, cancellation, polling-connection verification,
fast-poll preservation and prevention of extra discovery writes. Owner tests
cover capability invalidation and expired/closed callback rejection.

Local qualification passed: 753 core tests (750 passed, three optional skips),
Linux and Windows core builds, shared portable checks and 372 desktop UI tests
(370 passed, two optional skips). Native-window checks ran on an isolated
Xvfb/Openbox display. These results do not qualify physical adapter behavior.

Physical Forester reconnect and stop/start tests remain required before this
change is qualified for release. No vehicle or adapter was accessed during
development. Production flashing and live tuning remain unavailable.
