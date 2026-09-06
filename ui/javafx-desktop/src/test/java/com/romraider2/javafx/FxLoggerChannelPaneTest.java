/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import com.romraider.logger.api.*;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLoggerChannelPaneTest {
    @Test void categoriesAndSearchNeverChangeSelection() throws Exception {
        FxTestRuntime.run(() -> {
            Harness h = new Harness();
            assertEquals(4, h.checks().size());
            for (FxLoggerChannelPane.Category category : FxLoggerChannelPane.Category.values()) {
                h.category().setValue(category);
                int expected = category == FxLoggerChannelPane.Category.ALL ? 4
                        : category == FxLoggerChannelPane.Category.PARAMETERS ? 2 : 1;
                assertEquals(expected, h.checks().size());
            }
            h.category().setValue(FxLoggerChannelPane.Category.PARAMETERS);
            h.search().setText("rpm");
            assertEquals(List.of("rpm"), h.checks().stream().map(CheckBox::getUserData).toList());
            h.search().setText("absent");
            assertTrue(h.checks().isEmpty());
            assertTrue(h.selections.isEmpty());
            assertEquals(3, h.service.getChannels().stream().filter(LoggerChannel::isSelected).count());
        });
    }

    @Test void searchIsLocaleIndependentAndMatchesIdsAndUnits() throws Exception {
        FxTestRuntime.run(() -> {
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                Harness h = new Harness();
                h.search().setText("S-I");
                assertEquals(1, h.checks().size());
                h.search().setText("degC");
                assertEquals(1, h.checks().size());
                assertTrue(h.selections.isEmpty());
            } finally { Locale.setDefault(previous); }
        });
    }

    @Test void clearCategoryIncludesSearchHiddenChannelsButNotOtherCategories() throws Exception {
        FxTestRuntime.run(() -> {
            Harness h = new Harness();
            h.category().setValue(FxLoggerChannelPane.Category.PARAMETERS);
            h.search().setText("rpm");
            h.button("logger-clear-category").fire();
            assertEquals(List.of(List.of("rpm", "temp")), h.selections);
            assertTrue(h.confirmation.contains("2 channel(s)"));
            assertTrue(h.confirmation.contains("hidden by search"));
            assertEquals(List.of("S-I"), h.service.getChannels().stream()
                    .filter(LoggerChannel::isSelected).map(LoggerChannel::getParameterId).toList());
        });
    }

    @Test void cancelDoesNothingAndClearAllIgnoresBothFilters() throws Exception {
        FxTestRuntime.run(() -> {
            Harness h = new Harness();
            h.category().setValue(FxLoggerChannelPane.Category.EXTERNAL);
            h.search().setText("absent");
            h.approve = false;
            h.button("logger-clear-all").fire();
            assertTrue(h.selections.isEmpty());
            h.approve = true;
            h.button("logger-clear-all").fire();
            assertEquals(List.of(List.of("rpm", "temp", "S-I")), h.selections);
            assertTrue(h.button("logger-clear-all").isDisabled());
        });
    }

    @Test void channelsAddedDuringConfirmationAreNotCleared() throws Exception {
        FxTestRuntime.run(() -> {
            Harness h = new Harness();
            h.duringConfirmation = () -> {
                List<LoggerChannel> next = new ArrayList<>(h.service.getChannels());
                next.add(new LoggerChannel("new", "New", "V", LoggerChannelKind.PARAMETER, true));
                h.service.replaceChannels(next);
            };
            h.button("logger-clear-all").fire();
            assertEquals(List.of("new"), h.service.getChannels().stream()
                    .filter(LoggerChannel::isSelected).map(LoggerChannel::getParameterId).toList());
        });
    }

    @Test void checkboxIssuesOnlyExplicitSingleChannelCommand() throws Exception {
        FxTestRuntime.run(() -> {
            Harness h = new Harness();
            h.checks().get(0).fire();
            assertEquals(List.of(List.of("rpm")), h.selections);
            assertFalse(h.service.getChannels().get(0).isSelected());
        });
    }

    @Test void unitsUseOptionIdsAndAreLockedDuringRecordingIncludingStaleControls() throws Exception {
        FxTestRuntime.run(() -> {
            Harness h = new Harness();
            ComboBox<LoggerChannelUnitOption> units = h.units();
            assertEquals("c", units.getValue().getId());
            assertTrue(h.unitCommands.isEmpty(), "Rendering must not issue commands");
            units.setValue(units.getItems().get(1));
            assertEquals(List.of("temp:f"), h.unitCommands);
            h.pane.setRecording(true);
            assertTrue(h.units().isDisabled());
            units.setValue(units.getItems().get(0));
            assertEquals(1, h.unitCommands.size(), "Old detached selector must also be guarded");
            h.pane.setRecording(false);
            assertFalse(h.units().isDisabled());
        });
    }

    @Test void recordingLocksCommandsBeforeQueuedUiNotificationArrives() throws Exception {
        FxTestRuntime.run(() -> {
            Harness h = new Harness();
            ComboBox<LoggerChannelUnitOption> units = h.units();
            h.backendRecording = true;
            units.setValue(units.getItems().get(1));
            assertTrue(h.unitCommands.isEmpty());
        });
    }

    private static final class Harness {
        final List<List<String>> selections = new ArrayList<>();
        final List<String> unitCommands = new ArrayList<>();
        final LoggerChannelService service;
        final FxLoggerChannelPane pane;
        boolean approve = true;
        boolean backendRecording;
        String confirmation;
        Runnable duringConfirmation = () -> {};
        Harness() {
            AtomicReference<LoggerChannelService> reference = new AtomicReference<>();
            service = new LoggerChannelService((ids, selected) -> {
                selections.add(List.copyOf(ids));
                LoggerChannelService target = reference.get();
                target.replaceChannels(target.getChannels().stream()
                        .map(channel -> ids.contains(channel.getParameterId())
                                ? channel.withSelected(selected) : channel).toList());
            }, (id, option) -> unitCommands.add(id + ":" + option), failure -> fail(failure));
            reference.set(service);
            service.replaceChannels(List.of(
                    new LoggerChannel("rpm", "Engine speed", "rpm", LoggerChannelKind.PARAMETER, true),
                    new LoggerChannel("temp", "Temperature", "degC", LoggerChannelKind.PARAMETER, true,
                            List.of(new LoggerChannelUnitOption("c", "degC", true),
                                    new LoggerChannelUnitOption("f", "degF", false))),
                    new LoggerChannel("S-I", "Ignition switch", "", LoggerChannelKind.SWITCH, true),
                    new LoggerChannel("ext", "Wideband", "AFR", LoggerChannelKind.EXTERNAL, false)));
            pane = new FxLoggerChannelPane(service, (title, message) -> {
                confirmation = message;
                duringConfirmation.run();
                return approve;
            }, () -> backendRecording);
            new Scene(pane);
            pane.applyCss();
            service.addListener(pane::update);
        }
        @SuppressWarnings("unchecked") ComboBox<FxLoggerChannelPane.Category> category() {
            return (ComboBox<FxLoggerChannelPane.Category>) pane.lookup("#logger-channel-category");
        }
        TextField search() { return (TextField) pane.lookup("#logger-channel-search"); }
        Button button(String id) { return (Button) pane.lookup("#" + id); }
        List<CheckBox> checks() {
            VBox rows = (VBox) pane.lookup("#logger-channel-rows");
            return rows.getChildren().stream().filter(VBox.class::isInstance)
                    .map(VBox.class::cast).map(row -> (CheckBox) row.getChildren().get(0)).toList();
        }
        @SuppressWarnings("unchecked") ComboBox<LoggerChannelUnitOption> units() {
            VBox rows = (VBox) pane.lookup("#logger-channel-rows");
            return (ComboBox<LoggerChannelUnitOption>) rows.getChildren().stream()
                    .filter(VBox.class::isInstance).map(VBox.class::cast)
                    .flatMap(row -> row.getChildren().stream())
                    .filter(ComboBox.class::isInstance).findFirst().orElseThrow();
        }
    }
}
