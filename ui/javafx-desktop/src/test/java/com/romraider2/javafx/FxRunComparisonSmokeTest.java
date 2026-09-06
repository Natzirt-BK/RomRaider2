/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxRunComparisonSmokeTest {
    @TempDir Path temporary;
    private LogDataset a() throws Exception { return new RomRaiderCsvLogParser().parse("run-a.csv", new StringReader("Time (msec),RPM (rpm),Value (AFR)\n0,0,10\n100,.5,20\n200,1,30\n300,3,40\n400,4,\n")); }
    private LogDataset b() throws Exception { return new RomRaiderCsvLogParser().parse("run-b.csv", new StringReader("Time (msec),Speed (rpm),Sensor (AFR)\n0,0,20\n80,.5,30\n160,1,25\n240,2,50\n320,3,40\n")); }
    private static void map(FxRunComparisonPane pane, LogDataset a, LogDataset b) throws Exception {
        ((ComboBox<LogChannel>) field(pane, "ax")).setValue(a.getChannels().get(1)); ((ComboBox<LogChannel>) field(pane, "av")).setValue(a.getChannels().get(2));
        ((ComboBox<LogChannel>) field(pane, "bx")).setValue(b.getChannels().get(1)); ((ComboBox<LogChannel>) field(pane, "bv")).setValue(b.getChannels().get(2));
        ((TextField) field(pane, "width")).setText("1"); confirm(pane);
    }
    private static void confirm(FxRunComparisonPane pane) throws Exception { ((CheckBox) field(pane, "confirmed")).setSelected(true); }
    private static Future<?> compare(FxRunComparisonPane pane) throws Exception { ((Button) field(pane, "compare")).fire(); return field(pane, "pending"); }
    private static LogRunComparison.Result result(FxRunComparisonPane pane) throws Exception { return field(pane, "result"); }
    @Test void plotsAndCanonicalCopiedTableKeepSharedBinsAndGaps() throws Exception {
        LogDataset a = a(), b = b(); FxRunComparisonPane[] pane = {null}; Future<?>[] job = {null}; Stage[] stage = {null};
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxRunComparisonPane(a); pane[0].installB(b); map(pane[0], a, b); job[0] = compare(pane[0]); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                var result = result(pane[0]); assertEquals(4, result.rows().size()); assertEquals(10, result.rows().get(0).difference()); assertEquals(-5, result.rows().get(1).difference());
                assertEquals(LogRunComparison.Coverage.B_ONLY, result.rows().get(2).coverage()); assertTrue(Double.isNaN(result.rows().get(2).difference()));
                TableView<LogRunComparison.Row> table = field(pane[0], "table"); var first = table.getColumns().remove(0); table.getColumns().add(first);
                ((Button) field(pane[0], "copy")).fire(); String text = Clipboard.getSystemClipboard().getString();
                assertTrue(text.contains("A: run-a.csv · samples 1–5")); assertTrue(text.contains("\n0.0\t1.0\t2\t2\t15.0\t25.0\t10.0\tBoth qualified"));
                ((ToggleButton) field(pane[0], "hideSetup")).setSelected(true);
                stage[0] = new Stage(); Scene scene = new Scene(pane[0], 1000, 640); FxTheme.apply(stage[0], scene); stage[0].setScene(scene); stage[0].show();
                ((TabPane) pane[0].getCenter()).getSelectionModel().select(1);
                for (boolean delta : new boolean[] {false, true}) {
                    ((CheckBox) field(pane[0], "difference")).setSelected(delta); pane[0].applyCss(); pane[0].layout();
                    FxRunComparisonPlot plot = field(pane[0], "plot"); assertTrue(plot.getHeight() > 250); assertSame(result, result(pane[0]));
                    String prefix = System.getenv("RR2_COMPARISON_CAPTURE");
                    if (prefix != null && !prefix.isBlank()) {
                        var snapshot = pane[0].snapshot(null, null);
                        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(image, "png", new File(prefix + (delta ? "-difference.png" : "-overlay.png"))));
                    }
                }
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); if (stage[0] != null) stage[0].close(); }); }
    }
    @Test void sameCsvRangesStayIndependentAndPendingSharedRangeBlocksComparison() throws Exception {
        LogDataset data = a(); FxLogAnalysisPane[] log = {null}; FxRunComparisonPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                log[0] = new FxLogAnalysisPane(null, data); pane[0] = field(log[0], "comparison"); pane[0].installB(data); map(pane[0], data, data);
                ((TextField) field(pane[0], "firstB")).setText("3"); ((TextField) field(pane[0], "lastB")).setText("4");
                log[0].invalidateSharedRange(); assertTrue(((Button) field(pane[0], "compare")).isDisabled());
                log[0].selectRange(LogRange.of(0, 2, 5)); confirm(pane[0]); job[0] = compare(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(2, result(pane[0]).acceptedA()); assertEquals(2, result(pane[0]).acceptedB());
                assertEquals("3", ((TextField) field(pane[0], "firstB")).getText()); log[0].selectRange(LogRange.all(data)); assertNull(result(pane[0]));
                assertFalse(((CheckBox) field(pane[0], "confirmed")).isSelected());
            });
        } finally { FxTestRuntime.run(() -> { if (log[0] != null) log[0].close(); }); }
    }
    @Test void realBoundedImportSuccessAndFailurePreserveSourceAndCompletedMetadata() throws Exception {
        LogDataset a = a(); Path good = temporary.resolve("second.csv"), bad = temporary.resolve("broken.csv");
        Files.writeString(good, "Time (msec),RPM (rpm),AFR (AFR)\n0,0,20\n"); Files.writeString(bad, "A,B\n1\n"); byte[] before = Files.readAllBytes(good);
        FxRunComparisonPane[] pane = {null}; Future<?>[] job = {null}; Object[] completed = {null}; String[] summary = {null};
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxRunComparisonPane(a); pane[0].loadB(good.toFile()); job[0] = field(pane[0], "loading"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { LogDataset b = field(pane[0], "b"); assertEquals("second.csv", b.getSourceName()); map(pane[0], a, b); job[0] = compare(pane[0]); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                completed[0] = result(pane[0]); summary[0] = field(pane[0], "completedSummary"); pane[0].loadB(null); assertSame(completed[0], result(pane[0]));
                pane[0].loadB(bad.toFile()); job[0] = field(pane[0], "loading");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertSame(completed[0], result(pane[0])); assertEquals(summary[0], field(pane[0], "completedSummary"));
                assertTrue(((Label) field(pane[0], "status")).getText().contains("previous run B retained"));
                ((Button) field(pane[0], "copy")).fire(); assertFalse(Clipboard.getSystemClipboard().getString().contains("import failed"));
            });
            assertArrayEquals(before, Files.readAllBytes(good));
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void lateImportCannotOverwriteInputsEditedWhileItWasLoading() throws Exception {
        LogDataset a = a(), b = b(); CountDownLatch release = new CountDownLatch(1); FxRunComparisonPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxRunComparisonPane(a, file -> { try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test gate timeout"); } catch (InterruptedException failure) { throw new IOException(failure); } return b; });
                pane[0].installB(a); pane[0].loadB(new File("synthetic.csv")); job[0] = field(pane[0], "loading");
                ((TextField) field(pane[0], "firstB")).setText("2");
            });
            release.countDown(); job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertSame(a, field(pane[0], "b")); assertEquals("2", ((TextField) field(pane[0], "firstB")).getText()); assertTrue(((Label) field(pane[0], "status")).getText().contains("Inputs changed")); });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void queuedAndCompletedComparisonsCannotRepublishAfterInputChanges() throws Exception {
        LogDataset a = a(), b = b(); CountDownLatch release = new CountDownLatch(1); FxRunComparisonPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxRunComparisonPane(a); pane[0].installB(b); map(pane[0], a, b);
                ((ExecutorService) field(pane[0], "worker")).submit(() -> { try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); } });
                job[0] = compare(pane[0]); ((TextField) field(pane[0], "width")).setText("2"); assertTrue(job[0].isCancelled());
            });
            release.countDown(); ((ExecutorService) field(pane[0], "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { confirm(pane[0]); compare(pane[0]).get(10, TimeUnit.SECONDS); pane[0].setRange(LogRange.all(a), true); });
            FxTestRuntime.run(() -> { assertNull(result(pane[0])); pane[0].close(); assertTrue(((ExecutorService) field(pane[0], "worker")).isShutdown()); assertTrue(((ExecutorService) field(pane[0], "imports")).isShutdown()); });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void confirmationAndMatchingUnitsAreRequired() throws Exception {
        LogDataset a = a(); LogDataset b = new RomRaiderCsvLogParser().parse("mismatch.csv", new StringReader("Time (msec),X (rpm),Y (ratio)\n0,0,10\n"));
        FxRunComparisonPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxRunComparisonPane(a); pane[0].installB(b); map(pane[0], a, b); ((CheckBox) field(pane[0], "confirmed")).setSelected(false);
                assertNull(compare(pane[0])); confirm(pane[0]); job[0] = compare(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertNull(result(pane[0])); assertTrue(((Label) field(pane[0], "status")).getText().contains("Run units differ")); });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void alreadyCompletedImportsCannotReplaceNewerSelectionOrClosedPane() throws Exception {
        LogDataset a = a(), b = b(); FxRunComparisonPane[] pane = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxRunComparisonPane(a, file -> file.getName().equals("first.csv") ? b : a);
                pane[0].loadB(new File("first.csv")); ((Future<?>) field(pane[0], "loading")).get(10, TimeUnit.SECONDS);
                pane[0].loadB(new File("second.csv")); ((Future<?>) field(pane[0], "loading")).get(10, TimeUnit.SECONDS);
            });
            FxTestRuntime.run(() -> {
                assertSame(a, field(pane[0], "b")); pane[0].loadB(new File("first.csv")); ((Future<?>) field(pane[0], "loading")).get(10, TimeUnit.SECONDS); pane[0].close();
            });
            FxTestRuntime.run(() -> assertNull(field(pane[0], "b")));
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void expandedSetupRemainsScrollableInsideTheCompactLogWorkspace() throws Exception {
        LogDataset data = a();
        FxTestRuntime.run(() -> {
            var log = new FxLogAnalysisPane(null, data); Stage stage = new Stage();
            try {
                FxRunComparisonPane pane = field(log, "comparison"); pane.installB(data); map(pane, data, data);
                Scene scene = new Scene(log, 1000, 640); FxTheme.apply(stage, scene); stage.setScene(scene); stage.show();
                TabPane views = (TabPane) log.getCenter(); views.getSelectionModel().select(views.getTabs().stream().filter(tab -> tab.getText().equals("Run comparison")).findFirst().orElseThrow());
                log.applyCss(); log.layout(); ScrollPane settings = (ScrollPane) pane.getTop();
                assertTrue(settings.getContent().getLayoutBounds().getHeight() > settings.getViewportBounds().getHeight());
                settings.setVvalue(1); log.layout(); TableView<?> table = field(pane, "table"); assertTrue(table.getHeight() > 70);
                Button compare = field(pane, "compare"); assertTrue(compare.localToScene(compare.getBoundsInLocal()).getMaxY() < 600);
            } finally { log.close(); stage.close(); }
        });
    }
}
