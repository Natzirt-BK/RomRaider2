/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.io;

import java.io.File;

import com.romraider.activity.ProgressReporter;

/** User-interaction port used while matching and loading a ROM definition. */
public interface RomLoadInteraction extends ProgressReporter {
    void missingDefinition(File definition);

    void definitionLoadFailed(File definition, String message,
            Throwable failure);

    /** Returns an additional definition to try, or {@code null} to cancel. */
    File chooseDefinition(File image);

    boolean confirmForceLoad(File definition);
}
