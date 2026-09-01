# Portable shared core

This module is the small, UI-free starting point for SteamOS, Android, and
macOS work. It currently provides bounded ROM byte editing and a bounded logger
sample/CSV model using only Java APIs available outside Swing/AWT. It securely
reads RomRaider logger definitions and profiles, keeps ECU-specific address and
module-target rules, plans bounded read batches, converts returned values, and
records them as CSV. It also contains bounded OpenPort control/K-line framing
and read-only Subaru SSM init/address requests for platform transports.

It does not parse calibration-table definitions, own a platform USB device,
flash, or write ECU memory. Desktop services will be moved behind this boundary
only when their behavior can stay identical and their existing tests continue
to pass.

Run `:platform:shared-core:check` with Gradle to compile it for Java 11 bytecode
and execute its dependency-free portability check.
