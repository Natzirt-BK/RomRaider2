# Desktop logger profiles

The Windows/Linux/SteamOS logger has standard XML **Load Profile**, **Save
Profile**, **Save Profile As** and **Reload Profile** actions under File.
Save/Save As/Reload are development changes after 1.1.9; they are not in the
published 1.1.9 packages. Existing Swing profile actions are unchanged.

## Workflow

1. Load the matching logger definition and disconnect. Wait for pending commands
   to finish; profile actions do not stop or start a connection themselves.
2. **Load Profile** reviews compatible selections and unavailable IDs before
   replacing the selection. Channels remain editable afterward.
3. **Save Profile** writes the current selections and units to the named file
   loaded or saved in this logger window. Without a named file, it opens Save As.
4. **Save Profile As** chooses a new destination and makes it the window's current
   profile after a successful save. The previous file is unchanged.
5. **Reload Profile** rereads that named file and reviews the replacement before
   discarding current selections. Missing, invalid or cancelled loads leave the
   current selection and named-file association unchanged.

Save uses Ctrl+S (Command+S where applicable); Save As adds Shift. Reload is
disabled until a file has been explicitly loaded or saved in this window.
The named-file association is window-local, not inferred from automatic recovery.
After reopening the logger, load a named file again to use Save or Reload against
it; recovered channel selections remain independent of that choice.

## File contents and protection

XML saves preserve ordered selections, units and parameter/switch/external
categories. An empty selection is an explicit empty profile. The desktop uses
one channel selection for its live views; independent legacy tab-selection flags
are not retained when saving the current desktop selection. Gauge layouts,
recordings, definitions and connection settings are not included.

Unavailable selections are listed during load and again before saving the
resulting partial profile. They are not silently rediscovered or retained as
selected channels. Reload the original profile after identification/discovery
to review newly available channels, or Save As to keep the original intact.

Saving reviews the destination and current selection before writing. Files
changed since load/save or during review are rejected; reload or choose a new
filename. An existing destination must parse as a logger profile and must not
explicitly declare another protocol. Definition/settings XML, non-XML suffixes,
directories and symbolic-link targets are rejected. Extensionless names receive
`.xml`; existing `.XML` names are preserved.

Parsing and serialization use a 4 MiB limit. Output is written to a synced
temporary file and atomically replaced, with no non-atomic fallback. Failed
replacement preserves the previous destination. Cancellation and stale setup
checks run before commit; they cannot undo a replacement already completed.

Named-file saving does not repoint the runtime's automatic recovery profile.
**Import/Export channel setup** remains a separate `.rr2logger` exchange format
requiring the exact same definition; see [portable channel setups](PORTABLE_LOGGER_SETUP.md).
