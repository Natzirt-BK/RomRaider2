/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import com.romraider.logger.analysis.*;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Real panes and marker storage, with deliberately controlled parse completion. */
@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLoggerLogLoadSmokeTest {
    @TempDir Path directory;

    @Test void reversedLoadsKeepMarkerWritesWithTheDisplayedCsv() throws Exception {
        File first = csv("first.csv");
        File second = csv("second.csv");
        LogMarkerStore markers = new LogMarkerStore();
        markers.save(first, List.of(new LogMarker(0, LogMarkerType.CUSTOM, "original")));
        byte[] originalSidecar = Files.readAllBytes(markers.sidecar(first));
        Harness h = new Harness();
        try {
            FxTestRuntime.run(() -> { h.open(); h.window.openLog(first); h.window.openLog(second); });
            h.pending.get(1).complete(new RomRaiderCsvLogParser().parse(second));
            FxTestRuntime.run(() -> {
                FxLogAnalysisPane pane = field(h.window, "analysisPane");
                assertEquals(second, field(pane, "source"));
                assertEquals("second.csv", ((LogDataset) field(pane, "dataset")).getSourceName());
                TabPane tabs = (TabPane) pane.getCenter();
                HBox controls = (HBox) ((BorderPane) tabs.getTabs().get(4).getContent()).getTop();
                Button add = controls.getChildren().stream().filter(node -> node instanceof Button)
                        .map(node -> (Button) node).filter(button -> button.getText().equals("Add at cursor"))
                        .findFirst().orElseThrow();
                add.fire();
            });
            h.pending.get(0).complete(new RomRaiderCsvLogParser().parse(first));
            FxTestRuntime.run(() -> {
                FxLogAnalysisPane pane = field(h.window, "analysisPane");
                assertEquals(second, field(pane, "source"));
                assertEquals(1, markers.load(second, 2).size());
                assertArrayEquals(originalSidecar, Files.readAllBytes(markers.sidecar(first)));
                assertEquals("Time (msec),Value\n0,2\n100,3\n", Files.readString(second.toPath()));
            });
        } finally { FxTestRuntime.run(h::close); }
    }

    @Test void replacingDatasetClosesPreviousPaneAndWindowClosesReplacement() throws Exception {
        File first = csv("first.csv");
        File second = csv("second.csv");
        Harness h = new Harness();
        FxLogAnalysisPane[] panes = new FxLogAnalysisPane[2];
        try {
            FxTestRuntime.run(() -> { h.open(); h.window.openLog(first); });
            h.pending.get(0).complete(new RomRaiderCsvLogParser().parse(first));
            FxTestRuntime.run(() -> {
                panes[0] = field(h.window, "analysisPane");
                h.window.openLog(second);
                assertSame(panes[0], field(h.window, "analysisPane"));
                assertEquals(first, field(panes[0], "source"));
                assertEquals(Boolean.FALSE, field(panes[0], "closed"));
            });
            h.pending.get(1).complete(new RomRaiderCsvLogParser().parse(second));
            FxTestRuntime.run(() -> {
                panes[1] = field(h.window, "analysisPane");
                assertNotSame(panes[0], panes[1]);
                assertEquals(Boolean.TRUE, field(panes[0], "closed"));
                assertEquals(Boolean.FALSE, field(panes[1], "closed"));
                h.close();
                assertEquals(Boolean.TRUE, field(panes[1], "closed"));
                assertEquals(1, h.closes.get());
            });
        } finally { FxTestRuntime.run(h::close); }
    }

    @Test void closeDuringLoadCannotCreateAnAnalysisPaneOrPublishFailure() throws Exception {
        for (boolean fail : new boolean[] {false, true}) {
            Harness h = new Harness();
            File source = csv(fail ? "failure.csv" : "success.csv");
            try {
                FxTestRuntime.run(() -> { h.open(); h.window.openLog(source); h.close(); });
                if (fail) h.pending.get(0).completeExceptionally(new java.io.IOException("late failure"));
                else h.pending.get(0).complete(new RomRaiderCsvLogParser().parse(source));
                FxTestRuntime.run(() -> {
                    assertNull(field(h.window, "analysisPane"));
                    assertEquals(Boolean.TRUE, field(h.window, "disposed"));
                    assertEquals(1, h.closes.get());
                    assertTrue(javafx.stage.Window.getWindows().stream()
                            .noneMatch(javafx.stage.Window::isShowing), "Late error opened a dialog");
                });
            } finally { FxTestRuntime.run(h::close); }
        }
    }

    private File csv(String name) throws Exception {
        return Files.writeString(directory.resolve(name), "Time (msec),Value\n0,2\n100,3\n").toFile();
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    private static final class Harness {
        final List<CompletableFuture<LogDataset>> pending = new ArrayList<>();
        final AtomicInteger closes = new AtomicInteger();
        FxLoggerWindow window;
        void open() throws Exception {
            window = new FxLoggerWindow(closes::incrementAndGet, file -> {
                CompletableFuture<LogDataset> future = new CompletableFuture<>();
                pending.add(future);
                return future;
            });
            Stage stage = field(window, "stage");
            FxWindowPlacement.show(stage); // Bypass setup and auto-connect.
        }
        void close() { if (window != null) window.close(); }
    }
}
