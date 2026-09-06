/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import java.util.List;
import java.util.Map;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxMountedGaugesSmokeTest {
    @Test void switchesPreservePublishedRecordingStateAndBlankStaleReadings() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> { });
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                FxWindowPlacement.show(stage); // No startup or hardware commands.
                LoggerWorkspaceContext context = context(window[0]);
                context.getChannels().replaceChannels(List.of(new LoggerChannel("gauge-fixture",
                        "Synthetic boost", "psi", LoggerChannelKind.PARAMETER, true, List.of(), "psi")));
                context.getLiveData().loggingData(); // Publish synthetic state; does not open a writer/adapter.
                context.getLiveData().publish(sample(12.7));
            });
            FxTestRuntime.run(() -> {
                LoggerWorkspaceContext context = context(window[0]);
                Object session = context.getSession();
                int historySize = context.getLiveData().getRecentSamples().get("gauge-fixture").size();
                for (LoggerGaugeTheme theme : LoggerGaugeTheme.values()) {
                    context.getPreferences().setGaugeTheme(theme);
                    window[0].setGaugesOnly(true);
                    assertSame(session, context.getSession());
                    assertEquals(LoggerSessionState.RECORDING, context.getSession().getState());
                    FlowPane gauges = FxEditorControlsSmokeTest.field(window[0], "mountedGauges");
                    assertEquals(1, gauges.getChildren().size());
                    window[0].setGaugesOnly(false);
                    assertEquals(historySize, context.getLiveData().getRecentSamples().get("gauge-fixture").size());
                }
                context.getPreferences().setGaugeTheme(LoggerGaugeTheme.TWIN_ARC);
                Map<String, Long> times = FxEditorControlsSmokeTest.field(window[0], "receivedAt");
                times.put("gauge-fixture", System.nanoTime() - 4_000_000_000L);
                window[0].setGaugesOnly(true);
                assertTrue(face(window[0]).getAccessibleText().contains("NO RECENT DATA"));
                assertTrue(face(window[0]).getAccessibleText().contains("no valid data"));
                context.getLiveData().publish(sample(13.5));
            });
            FxTestRuntime.run(() -> {
                assertTrue(face(window[0]).getAccessibleText().contains("13.5"));
                context(window[0]).getLiveData().stopped();
            });
            FxTestRuntime.run(() -> {
                window[0].setGaugesOnly(false); window[0].setGaugesOnly(true);
                assertTrue(face(window[0]).getAccessibleText().contains("STOPPED"));
                assertTrue(face(window[0]).getAccessibleText().contains("no valid data"));
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    context(window[0]).getLiveData().stopped();
                    context(window[0]).getPreferences().setGaugeTheme(LoggerGaugeTheme.RR2_CLASSIC);
                    window[0].close();
                }
            });
        }
    }
    private static FxInstrumentView face(FxLoggerWindow window) throws Exception {
        FlowPane gauges = FxEditorControlsSmokeTest.field(window, "mountedGauges");
        return (FxInstrumentView) gauges.getChildren().getFirst();
    }
    private static LoggerWorkspaceContext context(FxLoggerWindow window) throws Exception {
        return FxEditorControlsSmokeTest.field(window, "context");
    }
    private static LiveDataSample sample(double value) {
        return new LiveDataSample("gauge-fixture", "Synthetic boost", value, Double.toString(value), "psi", 1, "psi");
    }
}
