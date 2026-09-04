/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.desktop.DesktopApplicationProvider;

/** JavaFX-owned production desktop shell provider. */
public final class JavaFxDesktopApplicationProvider
        implements DesktopApplicationProvider {
    @Override
    public String getName() {
        return "JavaFX Desktop ECU Studio";
    }

    @Override
    public boolean supports(String[] arguments) {
        String requested = System.getProperty(
                "romraider2.desktop.shell", "javafx");
        return "javafx".equalsIgnoreCase(requested);
    }

    @Override
    public void launch(String[] arguments) {
        JavaFxDesktopRuntime.launch(arguments == null
                ? new String[0] : arguments.clone());
    }
}
