package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLoggerAuditRegressionTest {
    @Test void dynoClearsResultsWhenInputsChangeOrCalculationFails() throws Exception {
        FxLoggerWindow[] window = {null};
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage"); FxWindowPlacement.show(stage);
                LoggerWorkspaceContext context = context(window[0]);
                var channels = List.of(new LoggerChannel("rpm", "Engine speed", "rpm", LoggerChannelKind.PARAMETER, true),
                        new LoggerChannel("speed", "Vehicle speed", "mph", LoggerChannelKind.PARAMETER, true));
                context.getChannels().replaceChannels(channels);
                for (int i = 0; i < 4; i++) {
                    context.getLiveData().publish(new LiveDataSample("rpm", "Engine speed", 2000 + i * 100, "", "rpm", i * 1000));
                    context.getLiveData().publish(new LiveDataSample("speed", "Vehicle speed", 20 + i, "", "mph", i * 1000));
                }
                FxDynoPane dyno = FxEditorControlsSmokeTest.field(window[0], "dyno"); dyno.refresh(channels);
                var calculate = FxDynoPane.class.getDeclaredMethod("calculate"); calculate.setAccessible(true); calculate.invoke(dyno);
                javafx.scene.chart.LineChart<?, ?> chart = FxEditorControlsSmokeTest.field(dyno, "chart"); assertEquals(2, chart.getData().size());
                TextField mass = FxEditorControlsSmokeTest.field(dyno, "mass"); mass.setText("invalid"); assertTrue(chart.getData().isEmpty());
                calculate.invoke(dyno); assertTrue(chart.getData().isEmpty());
                Label peak = FxEditorControlsSmokeTest.field(dyno, "peakPower"); assertTrue(peak.getText().startsWith("—"));
            });
        } finally { FxTestRuntime.run(() -> { if (window[0] != null) { context(window[0]).getLiveData().stopped(); window[0].close(); } }); }
    }
    @Test void liveUpdatesKeepDashboardMenuAndResizeTargetAttached() throws Exception {
        FxLoggerWindow[] window = {null}; VBox[] card = {null}; MenuButton[] menu = {null};
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage"); FxWindowPlacement.show(stage);
                TabPane tabs = FxEditorControlsSmokeTest.field(window[0], "views"); tabs.getSelectionModel().select(3);
                LoggerWorkspaceContext context = context(window[0]);
                context.getChannels().replaceChannels(List.of(new LoggerChannel("audit", "Synthetic RPM", "rpm", LoggerChannelKind.PARAMETER, true)));
                context.getLiveData().readingData(); context.getLiveData().publish(sample(2000));
            });
            FxTestRuntime.run(() -> {
                FlowPane dashboard = FxEditorControlsSmokeTest.field(window[0], "dashboard");
                card[0] = (VBox) dashboard.getChildren().getFirst();
                card[0].applyCss(); card[0].layout();
                menu[0] = (MenuButton) card[0].lookup(".menu-button"); assertNotNull(menu[0]); menu[0].show();
                card[0].setPrefWidth(300);
                for (int i = 0; i < 100; i++) context(window[0]).getLiveData().publish(sample(2000 + i));
            });
            FxTestRuntime.run(() -> {
                FlowPane dashboard = FxEditorControlsSmokeTest.field(window[0], "dashboard");
                assertSame(card[0], dashboard.getChildren().getFirst());
                assertSame(menu[0], card[0].lookup(".menu-button")); assertTrue(menu[0].isShowing());
                assertEquals(300, card[0].getPrefWidth());
                assertTrue(card[0].getChildren().get(1).getAccessibleText().contains("2099"));
                menu[0].hide();
            });
        } finally {
            FxTestRuntime.run(() -> { if (window[0] != null) { if (menu[0] != null) menu[0].hide(); context(window[0]).getLiveData().stopped(); window[0].close(); } });
        }
    }

    @Test void compactAnalysisUsesAccessibleSetupDrawers() throws Exception {
        FxTestRuntime.run(() -> {
            for (FxFuelAnalysisPane.Mode mode : FxFuelAnalysisPane.Mode.values()) {
                try (FxFuelAnalysisPane pane = new FxFuelAnalysisPane(mode, () -> {})) {
                    pane.resize(650, 600); pane.layout();
                    assertNull(pane.getLeft());
                    TitledPane drawer = (TitledPane) pane.getBottom(); assertNotNull(drawer);
                    drawer.setExpanded(true); pane.applyCss(); pane.layout();
                    assertNotNull(drawer.getContent());
                    pane.resize(1100, 700); pane.layout();
                    assertInstanceOf(ScrollPane.class, pane.getLeft()); assertNull(pane.getBottom());
                }
            }
        });
    }

    private static LoggerWorkspaceContext context(FxLoggerWindow w) throws Exception { return FxEditorControlsSmokeTest.field(w, "context"); }
    private static LiveDataSample sample(int value) { return new LiveDataSample("audit", "Synthetic RPM", value, "" + value, "rpm", value); }
}
