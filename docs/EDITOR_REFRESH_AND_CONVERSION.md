# Refresh and image conversion

Included in the Windows/Linux editor in 1.1.11 RC1.

The editor header includes **Refresh**, using the same saved-file reload action
as **File → Reload saved ROM**. It reloads the file and configured definitions,
asks before discarding unsaved changes, and keeps the existing document if
loading fails or is cancelled.

**Edit → Convert Image** restores the original two image-layout choices:

- **160 KB → 192 KB:** insert 32 KB of zeros at offset 0x20000.
- **192 KB → 160 KB:** remove offsets 0x20000 through 0x27FFF.

Only the choice matching the active file size is enabled. Conversion uses the
current image with normal save/checksum preparation, writes a separate copy,
and does not mark the original document saved or change its path. Open ROM
files cannot be selected as conversion destinations. Existing destination files
require overwrite confirmation.

Shrinking removes any data in that segment. This preserves the original
RomRaider conversion layout; it does not make firmware compatible with a
different ECU or vehicle. These commands do not communicate with a vehicle.
