/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.editor.document.EditorDocument;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Read-only metadata; inspecting properties never recalculates ROM checksums. */
final class FxRomProperties {
    private FxRomProperties() { }

    static void show(Window owner, EditorDocument document) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("ROM Properties");
        dialog.setHeaderText(document.getName());
        TextArea details = new TextArea(describe(document.getRom(), document.isDirty()));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(20);
        details.setPrefColumnCount(70);
        details.setAccessibleText("Read-only ROM properties; text can be selected and copied");
        dialog.getDialogPane().setContent(details);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        FxTheme.applyDialog(dialog.getDialogPane());
        dialog.showAndWait();
    }

    static String describe(Rom rom, boolean dirty) {
        RomID id = rom.getRomID();
        StringBuilder text = new StringBuilder();
        line(text, "File", rom.getFullFileName());
        line(text, "State", dirty ? "Unsaved changes" : "No unsaved changes");
        line(text, "Image size", rom.getBinary().length + " bytes");
        line(text, "Definition file", rom.getDefinitionPath());
        line(text, "Definition ID", id.getXmlid());
        line(text, "Internal ID", id.getInternalIdString());
        line(text, "ECU ID", id.getEcuId());
        line(text, "Make", id.getMake()); line(text, "Model", id.getModel());
        line(text, "Trim", id.getSubModel()); line(text, "Year", id.getYear());
        line(text, "Market", id.getMarket()); line(text, "Transmission", id.getTransmission());
        line(text, "Memory model", id.getMemModel());
        line(text, "Defined flash method", id.getFlashMethod());
        line(text, "Loaded tables", rom.getTables().size());
        line(text, "Defined checksum routine", id.getChecksum());
        try {
            line(text, "Current in-memory SHA-256", HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rom.getBinary())));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        text.append("\nMetadata comes from the loaded definition; it is not vehicle identification or flash approval. "
                + "SHA-256 identifies the current buffer, not ECU checksum validity. No file or ECU is changed by this view.");
        return text.toString();
    }

    private static void line(StringBuilder text, String key, Object value) {
        String displayed = value == null || value.toString().isBlank() ? "Not specified" : value.toString();
        text.append(key).append(": ").append(displayed).append('\n');
    }
}
