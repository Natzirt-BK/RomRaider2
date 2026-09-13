# Running alongside original RomRaider

Included in 1.1.11 RC1.

RR2 uses a separate local startup endpoint from original RomRaider. Either
application can open while the other is running. Reopening RR2 still forwards
the launch request to the existing RR2 window; it does not open an additional
independent RR2 process.

Close any older RR2 process before testing the updated build: older builds
still occupy original RomRaider's startup endpoint.

Use only one application with a particular vehicle adapter at a time. When
editing the same ROM in both applications, save separate copies to avoid
overwriting changes made in the other application.

The shared desktop launcher uses loopback port 23273 for RR2 and leaves port
23272 to original RomRaider. This applies to all desktop packages, including
the legacy compatibility shell. No changes to original RomRaider are required.
