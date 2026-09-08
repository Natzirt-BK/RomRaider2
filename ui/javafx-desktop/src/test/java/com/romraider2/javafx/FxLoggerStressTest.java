package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import java.util.*;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Accelerated synthetic stress, not an adapter test or a wall-clock endurance certification. */
@EnabledIfEnvironmentVariable(named = "RR2_LOGGER_STRESS", matches = "1")
class FxLoggerStressTest {
    @Test void repeatedTabAndGaugeSwitchingKeepsBoundedHistoryAndRecordingState() throws Exception {
        FxLoggerWindow[] window = {null}; LoggerWorkspaceContext[] context = {null};
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                context[0] = FxEditorControlsSmokeTest.field(window[0], "context");
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage"); FxWindowPlacement.show(stage);
                var channels = new ArrayList<LoggerChannel>();
                for (int i = 0; i < 8; i++) channels.add(new LoggerChannel("stress-" + i, "Synthetic channel " + i, "V", LoggerChannelKind.PARAMETER, true));
                context[0].getChannels().replaceChannels(channels);
                context[0].getLiveData().loggingData(); // State only: never opens a writer or controller.
            });
            for (int cycle = 0; cycle < 40; cycle++) {
                final int iteration = cycle;
                FxTestRuntime.run(() -> {
                    for (int sample = 0; sample < 100; sample++) for (int channel = 0; channel < 8; channel++) {
                        int value = iteration * 100 + sample;
                        context[0].getLiveData().publish(new LiveDataSample("stress-" + channel, "Synthetic channel " + channel,
                                value, "" + value, "V", value * 10L));
                    }
                    TabPane tabs = FxEditorControlsSmokeTest.field(window[0], "views");
                    for (int tab = 0; tab < 8; tab++) tabs.getSelectionModel().select(tab);
                    window[0].setGaugesOnly(true); window[0].setGaugesOnly(false);
                    assertEquals(LoggerSessionState.RECORDING, context[0].getSession().getState());
                });
            }
            FxTestRuntime.run(() -> {
                var history = context[0].getLiveData().getRecentSamples();
                assertEquals(8, history.size());
                for (var samples : history.values()) { assertEquals(2000, samples.size()); assertEquals(3999, samples.getLast().getRawValue()); }
                assertEquals(8, context[0].getChannels().getChannels().stream().filter(LoggerChannel::isSelected).count());
                context[0].getLiveData().stopped();
            });
        } finally {
            FxTestRuntime.run(() -> { if (window[0] != null) { context[0].getLiveData().stopped(); window[0].close(); } });
        }
    }
}
