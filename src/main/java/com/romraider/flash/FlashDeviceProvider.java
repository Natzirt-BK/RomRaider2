/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.List;

/** Replaceable discovery/open boundary for J2534, serial, and other devices. */
public interface FlashDeviceProvider {
    String getId();
    String getDisplayName();
    List<FlashDeviceDescriptor> discoverDevices() throws Exception;
    FlashDevice open(FlashDeviceDescriptor descriptor) throws Exception;
}
