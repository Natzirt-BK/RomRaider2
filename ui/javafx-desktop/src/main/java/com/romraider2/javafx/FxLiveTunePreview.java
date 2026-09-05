package com.romraider2.javafx;

import java.util.List;
import java.util.HexFormat;
import com.romraider.editor.document.EditorDocumentSession;
import com.romraider.editor.workspace.LiveTunePlanProjectionService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.livetune.LiveTuneChange;
import com.romraider.livetune.LiveTuneDraft;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.platform.PlatformContext;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Read-only projection only: intentionally has no transport or write command. */
final class FxLiveTunePreview extends BorderPane {
    private final EditorDocumentSession session;
    private final ComboBox<String> scope = new ComboBox<>(FXCollections.observableArrayList("Selected table", "All changed tables"));
    private final TableView<LiveTuneChange> changes = new TableView<>();
    private final Label summary = new Label();
    private final TextArea evidence = new TextArea();

    FxLiveTunePreview(EditorDocumentSession session) {
        this.session = session;
        scope.getSelectionModel().selectFirst();
        scope.setOnAction(event -> refresh());
        Button refresh = new Button("Refresh evidence");
        refresh.setOnAction(event -> refresh());
        Label title = new Label("Live Tune · offline draft"); title.getStyleClass().add("title");
        Label safety = new Label("No connection or ECU writes are available here. Addresses are unqualified table projections, not approved RAM targets.");
        safety.setWrapText(true);
        summary.setWrapText(true);
        setTop(new VBox(8, title, safety, new FlowPane(8, 8, scope, refresh), summary));
        column("Table", LiveTuneChange::getTableName, 230);
        column("Projected address", value -> String.format("0x%06X", value.getAddress()), 150);
        column("Bytes", value -> Integer.toString(value.getLength()), 70);
        column("Saved bytes", value -> HexFormat.ofDelimiter(" ").formatHex(value.getExpected()), 280);
        column("Edited bytes", value -> HexFormat.ofDelimiter(" ").formatHex(value.getReplacement()), 280);
        setCenter(changes);
        BorderPane.setMargin(changes, new Insets(10, 0, 10, 0));
        evidence.setEditable(false); evidence.setWrapText(true); evidence.setPrefRowCount(6);
        setBottom(evidence); setPadding(new Insets(14));
        refresh();
    }

    static LiveTuneDraft project(Rom rom, Table selected, boolean all) {
        List<Table> tables = rom == null ? List.of() : all
                ? rom.getTables().stream().filter(table -> RomChangeSummary.countChangedCells(table) > 0).toList()
                : selected == null ? List.of() : List.of(selected);
        return LiveTunePlanProjectionService.preview(rom, tables);
    }

    void refresh() {
        var snapshot = session.snapshot();
        try {
            LiveTuneDraft draft = project(snapshot.getActiveRom(), snapshot.getActiveTable(), scope.getSelectionModel().getSelectedIndex() == 1);
            changes.getItems().setAll(draft.getChanges());
            summary.setText(draft.getTableCount() + " tables · " + draft.getTotalBytes() + " changed bytes · refresh after runtime evidence changes");
        } catch (RuntimeException failure) {
            changes.getItems().clear(); summary.setText(FxDialogs.rootMessage(failure));
        }
        PlatformContext context = PlatformContext.getInstance();
        evidence.setText("Reported platform/module: " + context.getPlatform() + " / " + context.getModule()
                + "\nReported DimeMod state: " + context.getDimeModState()
                + "\nRAM Tune advertised: " + context.isRamTuneRuntimeAvailable()
                + "\nStructurally qualified runtime metadata: " + context.hasQualifiedRamTuneMetadata()
                + "\nExact ECU/ROM identity has not been bound to this draft."
                + "\nProduction ECU writes remain disabled, regardless of the reported evidence.");
    }

    private void column(String label, java.util.function.Function<LiveTuneChange, String> value, double width) {
        TableColumn<LiveTuneChange, String> column = new TableColumn<>(label);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width); changes.getColumns().add(column);
    }

    static void show(Window owner, EditorDocumentSession session) {
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Live Tune · offline draft");
        FxLiveTunePreview view = new FxLiveTunePreview(session);
        EditorDocumentSession.Listener listener = snapshot -> Platform.runLater(() -> {
            if (stage.isShowing()) view.refresh();
        });
        session.addListener(listener);
        stage.setOnHidden(event -> session.removeListener(listener));
        Scene scene = new Scene(view, 1000, 650); FxTheme.apply(stage, scene);
        FxTheme.closeOnEscape(stage, scene); stage.setScene(scene); FxWindowPlacement.show(stage);
    }
}
