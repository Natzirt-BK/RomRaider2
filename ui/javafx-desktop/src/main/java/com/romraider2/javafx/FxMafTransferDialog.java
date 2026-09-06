/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.editor.calibration.ReviewedMafTransfer;
import java.math.BigDecimal;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Explicit local-buffer edit review. Creating or cancelling this dialog never edits a ROM. */
final class FxMafTransferDialog {
    static final ButtonType APPLY = new ButtonType("Apply to open ROM", ButtonBar.ButtonData.OK_DONE);
    private FxMafTransferDialog() { }

    static Dialog<ButtonType> create(Window owner, String documentName, ReviewedMafTransfer review) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Review MAF table changes"); dialog.setResizable(true);
        dialog.setHeaderText(documentName + " · " + review.getTable().getName());
        String unit = review.getTable().getCurrentScale().getUnit();
        Label summary = new Label(review.getSource()); summary.setWrapText(true);
        Label coverage = new Label(review.getChangedCount() + " cells change; " + review.getUncoveredCount()
                + " uncovered cells remain unchanged. Flow unit: " + unit + ". No extrapolation or clipping.");
        coverage.setWrapText(true);
        TableView<ReviewedMafTransfer.Row> rows = new TableView<>(FXCollections.observableArrayList(review.getRows()));
        rows.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        rows.getColumns().addAll(column("Cell", row -> Integer.toString(row.index() + 1)),
                column("Axis (V)", row -> number(row.volts())), column("Original", row -> number(row.original())),
                column("Correction (%)", row -> row.covered() ? number(row.correctionPercent()) : "Uncovered"),
                column("Requested", row -> number(row.requested())), column("Stored", row -> number(row.stored())),
                column("Result", row -> row.changes() ? "Change" : "Unchanged"));
        rows.setPrefHeight(285);
        Label warning = new Label("This edits only the open ROM buffer, in one undo step. It does not save a file or send data to an ECU. "
                + "A fitted correction is not a verified calibration. Check target compatibility, operating conditions, coverage and stored rounding before applying.");
        warning.setWrapText(true);
        CheckBox confirmed = new CheckBox("I reviewed the target, coverage and stored values.");
        confirmed.setWrapText(true); confirmed.setId("maf-transfer-confirmed");
        ScrollPane content = new ScrollPane(new VBox(9, summary, coverage, rows, warning, confirmed));
        content.setFitToWidth(true); content.setPrefViewportHeight(440); content.setMinHeight(180);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(940);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, APPLY);
        Button apply = (Button) dialog.getDialogPane().lookupButton(APPLY);
        apply.disableProperty().bind(confirmed.selectedProperty().not()); apply.setDefaultButton(false);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        FxTheme.applyDialog(dialog.getDialogPane());
        return dialog;
    }
    private static TableColumn<ReviewedMafTransfer.Row, String> column(String name, Function<ReviewedMafTransfer.Row, String> value) {
        TableColumn<ReviewedMafTransfer.Row, String> column = new TableColumn<>(name);
        column.setCellValueFactory(row -> new ReadOnlyStringWrapper(value.apply(row.getValue())));
        column.setStyle("-fx-alignment: CENTER;");
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setText(empty ? null : item);
                setTooltip(empty || item == null ? null : new Tooltip(item));
            }
        });
        column.setSortable(false); column.setMinWidth(65); column.setPrefWidth(name.equals("Correction (%)") ? 135 : 110); return column;
    }
    private static String number(double value) {
        BigDecimal exact = BigDecimal.valueOf(value).stripTrailingZeros();
        return Math.abs(value) >= 1e7 || (value != 0 && Math.abs(value) < 1e-4) ? exact.toString() : exact.toPlainString();
    }
}
