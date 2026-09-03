/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.desktop;

/** Service-provider boundary for the top-level desktop application shell. */
public interface DesktopApplicationProvider {
    String getName();

    /** Returns whether this provider can own the supplied launch mode. */
    boolean supports(String[] arguments);

    /** Opens the application and blocks until its last window closes. */
    void launch(String[] arguments);
}
