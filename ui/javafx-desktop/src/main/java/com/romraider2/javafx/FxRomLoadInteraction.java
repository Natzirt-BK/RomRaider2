/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.util.function.BiConsumer;

import com.romraider.editor.io.RomLoadInteraction;
import com.romraider.util.SettingsManager;

import javafx.application.Platform;
import javafx.stage.Window;

final class FxRomLoadInteraction implements RomLoadInteraction {
    private final Window owner;
    private final BiConsumer<String, Integer> progress;

    FxRomLoadInteraction(Window owner,
            BiConsumer<String, Integer> progress) {
        this.owner = owner;
        this.progress = progress;
    }

    @Override
    public void update(String status, int percent) {
        Platform.runLater(() -> progress.accept(status,
                Math.max(0, Math.min(100, percent))));
    }

    @Override
    public void missingDefinition(File definition) {
        FxDialogs.error(owner, "ECU definition missing",
                "The configured definition could not be found:\n"
                        + (definition == null ? "(not configured)"
                        : definition.getAbsolutePath()));
    }

    @Override
    public void definitionLoadFailed(File definition, String message,
            Throwable failure) {
        FxDialogs.error(owner, "ECU definition failed",
                (definition == null ? "Definition" : definition.getName())
                        + ": " + message);
    }

    @Override
    public File chooseDefinition(File image) {
        return FxDialogs.runAndWait(() -> FxDialogs.chooseDefinition(owner,
                SettingsManager.getSettings().getLastDefinitionDir()));
    }

    @Override
    public boolean confirmForceLoad(File definition) {
        return FxDialogs.confirm(owner, "Force-load definition?",
                "The ROM identifier did not match " + definition.getName()
                        + ". Force loading can display invalid addresses.",
                "Force load");
    }
}
