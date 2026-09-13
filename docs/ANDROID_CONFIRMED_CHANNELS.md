# Vehicle-confirmed Android SSM channels

Included in 1.1.11 RC1. Desktop behavior is described in
[desktop confirmed channels](DESKTOP_CONFIRMED_CHANNELS.md).

After ECU identification, the SSM channel catalog requires evidence from the
loaded definition and vehicle response:

- Standard parameters and switches with capability flags must be advertised by
  the ECU's SSM initialization response.
- ECU-specific addresses must match the identified ECU. Runtime-discovered
  DimeMod channels use this mapping and remain available.
- Calculated channels require available, confirmed dependencies.
- Transmission-only channels are excluded from the engine logger.

A generic address without capability information is no longer sufficient.
Unverified channels are excluded from both selection and the session's polling
catalog, including when an imported profile references them. The definition
file and saved profile are not rewritten.

“Confirmed” means supported by the ECU response or explicitly mapped in the
definition. It does not independently validate a definition's address/scaling
or prove that an optional physical sensor has been installed.

This change applies to Android SSM. MUT-II retains its definition-based catalog;
it does not provide the SSM capability bitmap and must not be described as
having the same automatic support discovery.
