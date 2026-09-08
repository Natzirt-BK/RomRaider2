package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxElmAdapterTestSmokeTest {
    @TempDir Path directory;
    @Test void explicitStartRecordsSyntheticCsvAndRequiresStoppedLogger() throws Exception {
        Stage[] owner = new Stage[1]; FxElmAdapterTest[] dialog = new FxElmAdapterTest[1];
        ElmAdapterTestRun[] task = new ElmAdapterTestRun[1];
        AtomicBoolean stopped = new AtomicBoolean(false); AtomicInteger opened = new AtomicInteger();
        var link = new ElmAdapterTestFixture();
        try {
            FxTestRuntime.run(() -> {
                owner[0] = new Stage(); owner[0].setScene(new Scene(new StackPane(), 800, 600)); owner[0].show();
                dialog[0] = new FxElmAdapterTest(owner[0], directory.toFile(), stopped::get, config -> {
                    opened.incrementAndGet(); return task[0] = new ElmAdapterTestRun(config, (port, baud) -> link.session(), link.recorder());
                });
                dialog[0].show(); assertEquals(0, opened.get()); assertTrue(dialog[0].start.isDisabled());
                dialog[0].port.setText("synthetic-only"); dialog[0].confirmed.setSelected(true);
                dialog[0].start.fire(); assertEquals(0, opened.get()); assertTrue(dialog[0].status.getText().contains("Disconnect"));
                stopped.set(true); dialog[0].start.fire(); assertEquals(1, opened.get());
            });
            ElmAdapterTestRunTest.await(task[0]);
            FxTestRuntime.run(() -> {
                dialog[0].refresh(); assertTrue(task[0].isSuccessful(), task[0].status());
                assertTrue(dialog[0].values.getText().contains("Engine Speed")); assertTrue(dialog[0].stop.isDisabled());
                assertFalse(dialog[0].start.isDisabled());
                String capture = System.getenv("RR2_ELM_CAPTURE");
                if (capture != null && !capture.isBlank()) {
                    dialog[0].stage.setTitle("Synthetic adapter test — no vehicle connected");
                    dialog[0].stage.getScene().getRoot().applyCss(); dialog[0].stage.getScene().getRoot().layout();
                    var image = dialog[0].stage.getScene().getRoot().snapshot(null, null);
                    var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
                        bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(bitmap, "png", Path.of(capture).toFile());
                }
                dialog[0].stage.setWidth(480); dialog[0].stage.setHeight(650);
                dialog[0].stage.getScene().getRoot().applyCss(); dialog[0].stage.getScene().getRoot().layout();
                assertTrue(dialog[0].start.getWidth() > 0); assertTrue(dialog[0].stage.isShowing());
                var duration = dialog[0].stage.getScene().getRoot().lookupAll(".label").stream()
                        .filter(node -> node instanceof javafx.scene.control.Label label && label.getText().equals("Duration (s)"))
                        .findFirst().orElseThrow();
                assertEquals("Duration (s)", ((javafx.scene.text.Text) duration.lookup(".text")).getText());
            });
        } finally { FxTestRuntime.run(() -> { if (dialog[0] != null) dialog[0].close(); if (owner[0] != null) owner[0].close(); }); }
    }
    @Test void closeDuringOpenCancelsAndWaitsForCleanupWithoutBlockingUi() throws Exception {
        Stage[] owner = new Stage[1]; FxElmAdapterTest[] dialog = new FxElmAdapterTest[1]; ElmAdapterTestRun[] task = new ElmAdapterTestRun[1];
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        var link = new ElmAdapterTestFixture();
        try {
            FxTestRuntime.run(() -> {
                owner[0] = new Stage(); owner[0].show();
                dialog[0] = new FxElmAdapterTest(owner[0], directory.toFile(), () -> true, config -> task[0] = new ElmAdapterTestRun(config, (port, baud) -> {
                    entered.countDown();
                    boolean done = false;
                    while (!done) try { done = release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                    return link.session();
                }, link.recorder()));
                dialog[0].show(); dialog[0].port.setText("synthetic-only"); dialog[0].confirmed.setSelected(true); dialog[0].start.fire();
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            FxTestRuntime.run(() -> {
                dialog[0].stage.fireEvent(new WindowEvent(dialog[0].stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                assertTrue(dialog[0].stage.isShowing()); assertTrue(dialog[0].start.isDisabled());
            });
            release.countDown(); ElmAdapterTestRunTest.await(task[0]);
            FxTestRuntime.run(() -> { dialog[0].refresh(); assertFalse(dialog[0].stage.isShowing()); });
            assertEquals(1, link.closes); assertTrue(link.commands.isEmpty());
        } finally {
            release.countDown();
            FxTestRuntime.run(() -> { if (dialog[0] != null) dialog[0].close(); if (owner[0] != null) owner[0].close(); });
        }
    }
}
