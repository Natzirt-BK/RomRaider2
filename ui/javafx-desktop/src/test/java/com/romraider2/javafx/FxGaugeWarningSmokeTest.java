/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import java.util.List;
import java.util.Map;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxGaugeWarningSmokeTest {
    @Test void coalescedReadingsDetachedGaugeAndConversionChangeUseRealBus() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> { });
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                FxWindowPlacement.show(stage); // No startup, adapter, or connection.
                LoggerWorkspaceContext context = context(window[0]);
                context.getPreferences().setGaugeConfiguration("warning-fixture",
                        new LoggerGaugeConfiguration(0.0, 120.0, null, 100.0, 5).forConversion("C"));
                context.getPreferences().setDashboardTile("warning-fixture", new LoggerDashboardTile(
                        LoggerDashboardTileRole.ALARM, LoggerDashboardTileSize.STANDARD, 0));
                context.getChannels().replaceChannels(List.of(channel("C")));
                context.getLiveData().publish(sample(101, "C"));
                context.getLiveData().publish(sample(98, "C")); // Both arrive before the UI can paint.
            });
            FxTestRuntime.run(() -> {
                assertEquals("HIGH WARNING", alarm(dashboard(window[0])));
                var detach = FxLoggerWindow.class.getDeclaredMethod("detachGauge", LiveDataSample.class);
                detach.setAccessible(true);
                detach.invoke(window[0], sample(98, "C"));
                Map<String, Stage> detached = FxEditorControlsSmokeTest.field(window[0], "detachedGauges");
                assertEquals("HIGH WARNING", alarm(detached.get("warning-fixture").getScene().getRoot()));
                context(window[0]).getLiveData().publish(sample(95, "C"));
            });
            FxTestRuntime.run(() -> {
                assertEquals("NORMAL", alarm(dashboard(window[0])));
                context(window[0]).getLiveData().publish(sample(Double.NaN, "C"));
            });
            FxTestRuntime.run(() -> {
                assertEquals("NO VALID DATA", alarm(dashboard(window[0])));
                context(window[0]).getChannels().replaceChannels(List.of(channel("F")));
                context(window[0]).getLiveData().publish(sample(176, "F"));
            });
            FxTestRuntime.run(() -> {
                assertEquals("LIMITS NOT SET", alarm(dashboard(window[0])));
                assertNotNull(context(window[0]).getPreferences().getGaugeConfiguration("warning-fixture"));
                context(window[0]).getChannels().replaceChannels(List.of());
            });
            FxTestRuntime.run(() -> {
                Map<String, Stage> detached = FxEditorControlsSmokeTest.field(window[0], "detachedGauges");
                assertTrue(detached.get("warning-fixture").getScene().getRoot().lookupAll(".label").stream()
                        .anyMatch(node -> ((Label) node).getText().contains("Channel removed")));
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    context(window[0]).getPreferences().setGaugeConfiguration("warning-fixture", null);
                    context(window[0]).getPreferences().setDashboardTile("warning-fixture", null);
                    window[0].close();
                }
            });
        }
    }
    private static LoggerWorkspaceContext context(FxLoggerWindow window) throws Exception {
        return FxEditorControlsSmokeTest.field(window, "context");
    }
    private static FlowPane dashboard(FxLoggerWindow window) throws Exception {
        return FxEditorControlsSmokeTest.field(window, "dashboard");
    }
    private static String alarm(Parent node) {
        return ((Label) node.lookup(".alarm-state")).getText();
    }
    private static LoggerChannel channel(String identity) {
        return new LoggerChannel("warning-fixture", "Synthetic", identity, LoggerChannelKind.PARAMETER,
                true, List.of(), identity);
    }
    private static LiveDataSample sample(double value, String identity) {
        return new LiveDataSample("warning-fixture", "Synthetic", value, Double.toString(value), identity, 1, identity);
    }
}
