/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 * GPL 2.0 or later.
 */
package com.romraider.editor.ecu;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import javax.swing.JFileChooser;
import javax.swing.SwingWorker;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.editor.io.RomLoadInteraction;
import com.romraider.editor.io.RomLoadResult;
import com.romraider.editor.io.RomLoadService;
import com.romraider.editor.recovery.RecoverySnapshot;
import com.romraider.editor.recovery.RomRecoveryService;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.swing.DefinitionFilter;
import com.romraider.swing.IntegratedFileChooser;
import com.romraider.swing.IntegratedOptionDialog;
import com.romraider.util.SettingsManager;

/** Swing compatibility adapter around the UI-neutral ROM load service. */
public class OpenImageWorker extends SwingWorker<Void, Void> {
    private static final Logger LOGGER = Logger.getLogger(OpenImageWorker.class);

    private final File inputFile;
    private final RecoverySnapshot recoverySnapshot;
    private Rom rom;
    private String finalStatus;

    public OpenImageWorker(File inputFile) {
        this(inputFile, null);
    }

    public OpenImageWorker(RecoverySnapshot recoverySnapshot) {
        this(recoverySnapshot.getBinaryPath().toFile(), recoverySnapshot);
    }

    private OpenImageWorker(File inputFile, RecoverySnapshot recoverySnapshot) {
        this.inputFile = inputFile;
        this.recoverySnapshot = recoverySnapshot;
    }

    public Rom getRom() { return rom; }

    @Override
    protected Void doInBackground() {
        Thread.currentThread().setName("Open Image Thread");
        final ECUEditor editor = ECUEditorManager.getECUEditor();
        try {
            RomLoadResult result = new RomLoadService().load(inputFile,
                    new SwingLoadInteraction(editor));
            if (!result.isLoaded()) return null;

            rom = result.getRom();
            for (Table table : rom.getTableCatalog()) {
                com.romraider.logger.ecu.ui.handler.table.TableUpdateHandler
                        .getInstance().registerTable(table);
            }
            if (result.getTotalChecksums() == 0) {
                finalStatus = ECUEditor.rb.getString("STATUSREADY");
            } else {
                finalStatus = String.format(
                        ECUEditor.rb.getString("CHECKSUMSTATE"),
                        result.getValidChecksums(),
                        result.getTotalChecksums());
            }
        } catch (IOException failure) {
            LOGGER.error("Unable to read ROM image " + inputFile, failure);
            showMessageDialog(editor, message(failure),
                    MessageFormat.format(ECUEditor.rb.getString("ERRORFILE"),
                            inputFile.getName()), ERROR_MESSAGE);
        }
        return null;
    }

    public void propertyChange(PropertyChangeEvent event) {
        SwingWorker<?, ?> source = (SwingWorker<?, ?>) event.getSource();
        if (source != null && "state".equals(event.getPropertyName())
                && (source.isDone() || source.isCancelled())) {
            source.removePropertyChangeListener(
                    ECUEditorManager.getECUEditor().getStatusPanel());
        }
    }

    @Override
    public void done() {
        ECUEditor editor = ECUEditorManager.getECUEditor();
        if (rom != null) {
            if (recoverySnapshot != null) {
                rom.setFullFileName(null);
                rom.setFileName("Recovered - "
                        + recoverySnapshot.getSourceName());
            }
            editor.addRom(rom);
            if (recoverySnapshot != null) {
                RomChangeService.markUnsaved(rom);
                try {
                    RomRecoveryService.getInstance().discardAll(
                            recoverySnapshot);
                } catch (IOException failure) {
                    LOGGER.warn("Recovered ROM opened, but its old recovery "
                            + "files could not be cleared", failure);
                }
                RomRecoveryService.getInstance().schedule(rom);
            }
            rom = null;
            editor.getStatusPanel().complete(finalStatus);
            editor.setCursor(null);
            editor.refreshAfterNewRom();
        } else {
            editor.getStatusPanel().ready(
                    ECUEditor.rb.getString("STATUSREADY"));
            editor.setCursor(null);
        }
    }

    private final class SwingLoadInteraction implements RomLoadInteraction {
        private final ECUEditor editor;

        private SwingLoadInteraction(ECUEditor editor) {
            this.editor = editor;
        }

        public void update(String status, int percent) {
            editor.getStatusPanel().setStatus(status);
            setProgress(Math.max(0, Math.min(100, percent)));
        }

        public void missingDefinition(File definition) {
            String path = definition == null ? "(not set)"
                    : definition.getAbsolutePath();
            String name = definition == null ? "(not set)"
                    : definition.getName();
            showMessageDialog(editor, MessageFormat.format(
                    ECUEditor.rb.getString("MISSINGMOVED"), path),
                    MessageFormat.format(
                            ECUEditor.rb.getString("MISSINGFILE"), name),
                    ERROR_MESSAGE);
        }

        public void definitionLoadFailed(File definition, String message,
                Throwable failure) {
            String name = definition == null ? inputFile.getName()
                    : definition.getName();
            showMessageDialog(editor, name + ": " + message,
                    MessageFormat.format(ECUEditor.rb.getString("ERRORFILE"),
                            inputFile.getName()), ERROR_MESSAGE);
        }

        public File chooseDefinition(File image) {
            Object[] options = {ECUEditor.rb.getString("YES"),
                    ECUEditor.rb.getString("NO")};
            int answer = IntegratedOptionDialog.show(editor,
                    ECUEditor.rb.getString("DEFNOTFOUND"),
                    ECUEditor.rb.getString("EDCONFIG"), WARNING_MESSAGE,
                    options, options[0]);
            if (answer != 0) return null;

            Settings settings = SettingsManager.getSettings();
            JFileChooser chooser = new IntegratedFileChooser(
                    settings.getLastDefinitionDir());
            chooser.setFileFilter(new DefinitionFilter());
            if (chooser.showOpenDialog(editor) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            File selected = chooser.getSelectedFile();
            settings.setLastDefinitionDir(selected.getParentFile());
            return selected;
        }

        public boolean confirmForceLoad(File definition) {
            Object[] options = {ECUEditor.rb.getString("YES"),
                    ECUEditor.rb.getString("NO")};
            return IntegratedOptionDialog.show(editor,
                    ECUEditor.rb.getString("DEFNOMATCH"),
                    ECUEditor.rb.getString("EDCONFIG"), INFORMATION_MESSAGE,
                    options, options[0]) == 0;
        }
    }

    private static String message(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? ECUEditor.rb.getString("LOADEXCEPTION") : message;
    }
}
