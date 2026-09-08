package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import java.io.File;
import java.util.List;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in, synthetic-only audit captures: never starts the logger controller. */
@EnabledIfEnvironmentVariable(named = "RR2_LOGGER_AUDIT_CAPTURE_DIR", matches = ".+")
class FxLoggerAuditCaptureTest {
    @Test void capturesEveryTabAtCompactAndLaptopSizes() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> { });
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                FxWindowPlacement.show(stage);
                LoggerWorkspaceContext context = FxEditorControlsSmokeTest.field(window[0], "context");
                context.getChannels().replaceChannels(List.of(
                        new LoggerChannel("audit-rpm", "Synthetic engine speed", "rpm", LoggerChannelKind.PARAMETER, true),
                        new LoggerChannel("audit-speed", "Synthetic vehicle speed", "mph", LoggerChannelKind.PARAMETER, true)));
                context.getLiveData().readingData();
                for (int index = 0; index < 20; index++) {
                    context.getLiveData().publish(new LiveDataSample("audit-rpm", "Synthetic engine speed", 2000 + index * 100, "3000", "rpm", index * 100L));
                    context.getLiveData().publish(new LiveDataSample("audit-speed", "Synthetic vehicle speed", 20 + index, "30", "mph", index * 100L));
                }
                var dataset = new com.romraider.logger.analysis.RomRaiderCsvLogParser().parse("synthetic-audit.csv",
                        new java.io.StringReader("Time (msec),MAF (V),Learning (%),Correction (%),Pulse (ms),Load (g/rev)\n0,2.31,4,-1,2.25,1.2\n100,2.32,2,3,2.26,1.4\n200,2.34,3,2,2.3,1.5\n"));
                var show = FxLoggerWindow.class.getDeclaredMethod("showDataset", File.class,
                        com.romraider.logger.analysis.LogDataset.class);
                show.setAccessible(true);
                show.invoke(window[0], null, dataset);
            });
            for (int width : new int[] {1024, 1280}) {
                for (int index = 0; index < 8; index++) {
                    final int selected = index;
                    FxTestRuntime.run(() -> {
                        Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                        stage.setWidth(width); stage.setHeight(width == 1280 ? 800 : 768);
                        TabPane tabs = FxEditorControlsSmokeTest.field(window[0], "views");
                        assertEquals(8, tabs.getTabs().size());
                        tabs.getSelectionModel().select(selected);
                    });
                    FxTestRuntime.run(() -> {
                        Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                        var image = stage.getScene().getRoot().snapshot(null, null);
                        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
                            bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                        javax.imageio.ImageIO.write(bitmap, "png", new File(System.getenv("RR2_LOGGER_AUDIT_CAPTURE_DIR"), width + "-tab-" + selected + ".png"));
                    });
                }
            }
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    LoggerWorkspaceContext context = FxEditorControlsSmokeTest.field(window[0], "context");
                    context.getLiveData().stopped();
                    window[0].close();
                }
            });
        }
    }
}
