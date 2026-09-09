# Android logger profiles

**Open Logger Profile** reads a RomRaider XML profile. **Save Logger Profile**
saves the current logger selections as XML through Android's file picker.
The snapshot preserves channel IDs, units, switch categories and selected-column
order. DimeMod IDs waiting for discovery remain in the profile. Saving does not
start a session, change the definition or grant support to an unavailable channel.

This saves the Android logger configuration, not a byte-for-byte copy of an
imported desktop profile. Desktop graph/dashboard selections are not separate
Android settings. Unavailable external input selections are retained, but their
original desktop units/preferences are not available in the imported model;
the app explains that limitation and recommends a new file before saving such
a profile. Unrecognized unsupported entries are rejected rather than discarded.
Destination failures are reported; document providers do not guarantee atomic
replacement, so use a new filename when retaining an original is important.

**More Profile Options** contains the existing `.rr2logger` import/export tools.
This is an RR2-specific selection transfer format with a fingerprint requiring
the exact same loaded definition. It is not a logger XML profile and contains
no definition contents, ROM, recorded data or connection state. Normal profile
saving no longer requires this transfer format.

## Verification

The 1.1.7 Android checks include XML escaping, switch/parameter categorization,
ordered round trips, pending DimeMod IDs, retained external selections, size
limits, duplicate IDs and rejected invalid content. Emulator coverage exercises
the Activity's XML destination writer and cancelled-save snapshot cleanup,
alongside existing `.rr2logger` transfer tests. This does not enable Android
external sensor logging or restore desktop-only display preferences.
