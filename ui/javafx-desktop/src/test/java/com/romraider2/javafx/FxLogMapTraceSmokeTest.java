/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import com.romraider.maps.*;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.editor.document.EditorDocumentController;
import com.romraider.swing.JProgressPane;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableView;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLogMapTraceSmokeTest {
    private static Table3D target() throws Exception {
        Table3D table = new Table3D(); table.setName("Synthetic fuel target"); table.setSizeX(2); table.setSizeY(2);
        table.setStorageType(1); table.setStorageAddress(0);
        table.getXAxis().setName("Load"); table.getXAxis().setStorageType(1); table.getXAxis().setStorageAddress(4); table.getXAxis().setDataSize(2);
        table.getYAxis().setName("RPM"); table.getYAxis().setStorageType(1); table.getYAxis().setStorageAddress(6); table.getYAxis().setDataSize(2);
        Rom rom = new Rom(new RomID()); rom.setFileName("synthetic-trace.bin"); rom.addTableByName(table);
        rom.populateTables(new byte[] {12, 13, 14, 15, 1, 3, 2, 4}, new JProgressPane());
        table.getCurrentScale().setUnit("AFR"); table.getXAxis().getCurrentScale().setUnit("g/rev");
        table.getYAxis().getCurrentScale().setUnit("rpm"); table.getYAxis().getCurrentScale().setExpression("x*1000"); return table;
    }
    private static LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader("Time (msec),Load (g/rev),Speed (rpm)\n0,2,3000\n100,5,3000\n200,,3000\n300,3,4000\n"));
    }
    private static void map(FxLogMapTracePane pane, LogDataset data) throws Exception {
        ((ComboBox<LogChannel>) field(pane, "horizontal")).setValue(data.getChannels().get(1));
        ((ComboBox<LogChannel>) field(pane, "vertical")).setValue(data.getChannels().get(2));
        ((CheckBox) field(pane, "confirmed")).setSelected(true);
    }
    @Test void mappingsRequireConfirmationAndUnavailableSamplesClearHighlights() throws Exception {
        LogDataset data = data(); Table3D table = target(); byte[] bytes = table.getRom().getBinary().clone();
        FxTestRuntime.run(() -> {
            var pane = new FxLogMapTracePane(data);
            try {
                pane.install(LogMapTrace.capture(table), "synthetic-trace.bin"); assertNull(field(pane, "point")); map(pane, data);
                LogMapTrace.Point point = field(pane, "point"); assertEquals(4, point.neighbors().size());
                pane.showSample(1, false); assertEquals(LogMapTrace.State.OUT_OF_RANGE, ((LogMapTrace.Point) field(pane, "point")).state());
                pane.showSample(2, false); assertEquals(LogMapTrace.State.MISSING, ((LogMapTrace.Point) field(pane, "point")).state());
                pane.showSample(3, false); point = field(pane, "point"); assertEquals(1, point.neighbors().size()); assertEquals(1, point.neighbors().get(0).row());
                pane.showSample(3, true); assertNull(field(pane, "point")); assertTrue(((Label) field(pane, "status")).getText().contains("Apply"));
                pane.showSample(0, false); ((ComboBox<LogChannel>) field(pane, "horizontal")).setValue(null);
                assertFalse(((CheckBox) field(pane, "confirmed")).isSelected()); assertNull(field(pane, "point"));
                assertArrayEquals(bytes, table.getRom().getBinary()); assertFalse(RomEditHistory.getInstance().canUndo(table.getRom()));
            } finally { pane.close(); }
        });
    }
    @Test void nativeCaptureReviewCancelsApprovesAndRejectsStaleEditorSelection() throws Exception {
        LogDataset data = data(); Table3D table = target();
        FxTestRuntime.run(() -> {
            var pane = new FxLogMapTracePane(data); Stage owner = new Stage(); AtomicBoolean current = new AtomicBoolean(true);
            try {
                owner.setScene(new Scene(pane, 900, 560)); owner.show(); pane.install(LogMapTrace.capture(table), "previous");
                pane.setTarget(() -> new FxMapTraceTarget(table, "synthetic-trace.bin", current::get));
                for (int attempt = 0; attempt < 3; attempt++) {
                    int step = attempt; Object before = field(pane, "snapshot"); boolean[] answered = {false}; Throwable[] error = {null};
                    Platform.runLater(() -> {
                        Stage window = Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                                .filter(stage -> "Capture map for saved-log tracing?".equals(stage.getTitle())).findFirst().orElseThrow();
                        DialogPane dialog = (DialogPane) window.getScene().lookup(".dialog-pane");
                        ButtonType action = dialog.getButtonTypes().stream().filter(type -> type.getButtonData() == (step == 0 ? ButtonBar.ButtonData.CANCEL_CLOSE : ButtonBar.ButtonData.OK_DONE)).findFirst().orElseThrow();
                        try {
                            assertInstanceOf(ScrollPane.class, dialog.getContent()); assertTrue(dialog.getContentText().contains("snapshot stays frozen"));
                            if (step == 2) current.set(false); answered[0] = true;
                        } catch (Throwable failure) { error[0] = failure; }
                        finally { ((Button) dialog.lookupButton(action)).fire(); }
                    });
                    ((Button) field(pane, "capture")).fire(); assertTrue(answered[0]); if (error[0] != null) throw new AssertionError(error[0]);
                    if (step == 1) { assertNotSame(before, field(pane, "snapshot")); assertFalse(((CheckBox) field(pane, "confirmed")).isSelected()); }
                    else assertSame(before, field(pane, "snapshot"));
                    if (step == 2) assertTrue(((Label) field(pane, "status")).getText().contains("changed during review"));
                }
            } finally { pane.close(); owner.close(); }
        });
    }
    @Test void savedPlaybackCursorAndPendingRangeControlTheTraceAndCompactRender() throws Exception {
        LogDataset data = data(); Table3D table = target(); FxLogAnalysisPane[] log = {null}; Stage[] stage = {null};
        try {
            FxTestRuntime.run(() -> {
                log[0] = new FxLogAnalysisPane(null, data); FxLogMapTracePane pane = field(log[0], "mapTrace");
                pane.install(LogMapTrace.capture(table), "synthetic-trace.bin"); map(pane, data);
                ((LogPlaybackService) field(log[0], "playback")).seek(3);
            });
            FxTestRuntime.run(() -> {
                FxLogMapTracePane pane = field(log[0], "mapTrace"); assertTrue(((Label) field(pane, "status")).getText().startsWith("Sample 4"));
                log[0].invalidateSharedRange(); assertNull(field(pane, "point")); log[0].selectRange(LogRange.of(0, 1, 4));
                assertTrue(((Label) field(pane, "status")).getText().startsWith("Sample 1"));
                stage[0] = new Stage(); Scene scene = new Scene(log[0], 1000, 640); FxTheme.apply(stage[0], scene); stage[0].setScene(scene); stage[0].show();
                TabPane views = (TabPane) log[0].getCenter();
                views.getSelectionModel().select(views.getTabs().stream().filter(tab -> tab.getText().equals("Map trace")).findFirst().orElseThrow()); log[0].applyCss(); log[0].layout();
                TableView<?> grid = field(pane, "grid"); assertTrue(grid.getHeight() > 100);
                assertTrue(grid.lookupAll(".table-cell").stream().anyMatch(cell -> cell.getStyle().contains("#bcebe4")), "Visible geometric neighbors must be highlighted");
                String capture = System.getenv("RR2_MAP_TRACE_CAPTURE");
                if (capture != null && !capture.isBlank()) {
                    var snapshot = log[0].snapshot(null, null);
                    var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(capture)));
                }
                log[0].close(); assertNull(field(pane, "snapshot"));
            });
        } finally { FxTestRuntime.run(() -> { if (log[0] != null) log[0].close(); if (stage[0] != null) stage[0].close(); }); }
    }
    @Test void capturedTableRemainsExplicitlyFrozenAfterEditorModelChanges() throws Exception {
        LogDataset data = data(); Table3D table = target();
        FxTestRuntime.run(() -> {
            var pane = new FxLogMapTracePane(data);
            try {
                pane.install(LogMapTrace.capture(table), "synthetic-trace.bin"); map(pane, data);
                table.getCurrentScale().setExpression("x*2"); table.getXAxis().getCurrentScale().setExpression("x*10");
                pane.showSample(0, false); LogMapTrace snapshot = field(pane, "snapshot"); assertEquals(12, snapshot.valueAt(0, 0));
                assertEquals(1, snapshot.xAt(0)); assertEquals(LogMapTrace.State.COVERED, ((LogMapTrace.Point) field(pane, "point")).state());
                assertTrue(((Label) field(pane, "identity")).getText().contains("frozen snapshot"));
            } finally { pane.close(); }
        });
    }
    @Test void editorProviderRequiresOpenSelectedTableAndExpiresAfterClose() throws Exception {
        Table3D table = target(); FxEditorWindow[] editor = {null};
        try {
            FxTestRuntime.run(() -> {
                editor[0] = new FxEditorWindow(() -> {}, () -> {}); assertNull(editor[0].mapTraceTarget());
                EditorDocumentController controller = field(editor[0], "controller"); controller.getSession().openRom(table.getRom());
                assertNull(editor[0].mapTraceTarget()); controller.openTable(table.getRom(), table);
                FxMapTraceTarget selected = editor[0].mapTraceTarget(); assertNotNull(selected); assertSame(table, selected.table());
                assertTrue(selected.stillCurrent().getAsBoolean()); controller.closeTable(table.getRom(), table);
                assertFalse(selected.stillCurrent().getAsBoolean()); assertNull(editor[0].mapTraceTarget());
                FxWindowPlacement.show(field(editor[0], "stage"));
            });
        } finally { FxTestRuntime.run(() -> { if (editor[0] != null) editor[0].close(); }); }
    }
    @Test void curveNeedsOnlyHorizontalMappingAndRecaptureClearsOldMappings() throws Exception {
        LogDataset data = data(); Table3D surface = target();
        Table2D curve = new Table2D(); curve.setName("Synthetic curve"); curve.setDataSize(2); curve.getAxis().setDataSize(2);
        curve.addStaticDataCell("10"); curve.addStaticDataCell("20"); curve.getAxis().addStaticDataCell("1"); curve.getAxis().addStaticDataCell("3");
        FxTestRuntime.run(() -> {
            var pane = new FxLogMapTracePane(data);
            try {
                pane.install(LogMapTrace.capture(surface), "surface"); map(pane, data);
                pane.install(LogMapTrace.capture(curve), "curve"); assertNull(field(pane, "point"));
                ComboBox<LogChannel> x = field(pane, "horizontal"), y = field(pane, "vertical");
                assertNull(x.getValue()); assertNull(y.getValue()); assertFalse(y.isVisible()); assertFalse(y.isManaged());
                x.setValue(data.getChannels().get(1)); ((CheckBox) field(pane, "confirmed")).setSelected(true);
                assertEquals(2, ((LogMapTrace.Point) field(pane, "point")).neighbors().size());
            } finally { pane.close(); }
        });
    }
}
