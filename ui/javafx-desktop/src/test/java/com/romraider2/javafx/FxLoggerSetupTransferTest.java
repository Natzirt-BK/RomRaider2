/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.Settings;
import com.romraider.util.SettingsManager;
import com.romraider.logger.api.*;
import com.romraider.logger.runtime.*;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.platform.*;
import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.definition.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FxLoggerSetupTransferTest {
    @Test void namedProfileSaveReloadAndSaveAsKeepRecoverySeparate() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P2", "mV", "S1", "On/Off", "P1", "V"));
            String recovery = fixture.settings.getLoggerProfileFilePath();
            Path first = folder.resolve("chosen.xml"), second = folder.resolve("copy.XML");
            byte[] expected = fixture.runtime.captureLoggerProfile(fixture.runtime.captureChannelSetup()).getBytes();
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(folder.resolve("chosen").toFile())); await(fixture.transfer);
            assertArrayEquals(expected, Files.readAllBytes(first));
            assertEquals(first, fixture.transfer.profilePath());
            assertEquals(recovery, fixture.settings.getLoggerProfileFilePath());
            fixture.apply(ordered("P1", "mV"));
            FxTestRuntime.run(fixture.transfer::reloadProfile); await(fixture.transfer);
            assertEquals(List.of("P2", "S1", "P1"), fixture.selected());
            assertEquals("mV", fixture.runtime.captureChannelSetup().selectedChannels().getFirst().getUnits());
            fixture.apply(ordered("S1", "On/Off"));
            FxTestRuntime.run(fixture.transfer::showProfileSave); await(fixture.transfer);
            byte[] savedFirst = Files.readAllBytes(first);
            assertEquals(List.of("S1"), FxLoggerProfileFiles.parse(savedFirst).getSelectedIds());
            fixture.apply(ordered("P1", "V"));
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(second.toFile())); await(fixture.transfer);
            assertEquals(second, fixture.transfer.profilePath());
            assertArrayEquals(savedFirst, Files.readAllBytes(first));
            assertEquals(List.of("P1"), FxLoggerProfileFiles.parse(Files.readAllBytes(second)).getSelectedIds());
            assertEquals(recovery, fixture.settings.getLoggerProfileFilePath());
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
        }
    }

    @Test void loadedFileBecomesSaveAndReloadTargetOnlyAfterAcceptedLoad() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path first = folder.resolve("first.xml"), second = folder.resolve("second.xml");
            Files.writeString(first, "<profile protocol='SSM'><parameter id='P1' units='V' livedata='selected'/></profile>");
            Files.writeString(second, "<profile protocol='SSM'><parameter id='P2' units='mV' livedata='selected'/></profile>");
            FxTestRuntime.run(() -> fixture.transfer.loadProfile(first.toFile())); await(fixture.transfer);
            assertEquals(first, fixture.transfer.profilePath());
            fixture.accept.set(false);
            FxTestRuntime.run(() -> fixture.transfer.loadProfile(second.toFile())); await(fixture.transfer);
            assertEquals(first, fixture.transfer.profilePath());
            fixture.accept.set(true);
            fixture.apply(ordered("P2", "V"));
            FxTestRuntime.run(fixture.transfer::showProfileSave); await(fixture.transfer);
            assertEquals(List.of("P2"), FxLoggerProfileFiles.parse(Files.readAllBytes(first)).getSelectedIds());
            assertTrue(Files.readString(second).contains("mV"));
        }
    }

    @Test void cancelledOrStaleSaveDoesNotCreateFileOrChangeNamedTarget() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Path target = folder.resolve("cancelled.xml");
            fixture.accept.set(false);
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            assertFalse(Files.exists(target)); assertNull(fixture.transfer.profilePath());
            fixture.accept.set(true);
            fixture.onReview.set(fixture.runtime::reloadConfiguration);
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            assertFalse(Files.exists(target)); assertNull(fixture.transfer.profilePath());
            assertTrue(fixture.status.get().contains("changed"));
        }
    }

    @Test void failedSaveAndExternalEditsPreserveFilesAndCurrentSelection() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Path target = folder.resolve("saved.xml");
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            String changed = "<profile protocol='SSM'><parameter id='P2' units='mV' livedata='selected'/></profile>";
            Files.writeString(target, changed);
            FxTestRuntime.run(fixture.transfer::showProfileSave); await(fixture.transfer);
            assertEquals(changed, Files.readString(target));
            assertEquals(List.of("P1"), fixture.selected());
            assertTrue(fixture.status.get().contains("changed outside"));
            FxTestRuntime.run(fixture.transfer::reloadProfile); await(fixture.transfer);
            assertEquals(List.of("P2"), fixture.selected());
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(folder.resolve("missing/failed.xml").toFile())); await(fixture.transfer);
            assertEquals(target, fixture.transfer.profilePath());
            assertEquals(changed, Files.readString(target));
        }
    }

    @Test void destinationChangedDuringReviewIsNotOverwritten() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Path target = folder.resolve("raced.xml");
            String changed = "<profile protocol='SSM'/>";
            fixture.onReview.set(() -> { try { Files.writeString(target, changed); } catch (IOException failure) { throw new UncheckedIOException(failure); } });
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            assertEquals(changed, Files.readString(target));
            assertNull(fixture.transfer.profilePath());
            assertTrue(fixture.status.get().contains("changed outside"));
            try (var files = Files.list(folder)) { assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".rr2-profile-"))); }
        }
    }

    @Test void definitionWrongProtocolAndNonXmlDestinationsAreProtected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            for (String name : List.of("definition.xml", "other.xml", "saved.csv")) {
                Path target = folder.resolve(name);
                if (!name.equals("definition.xml")) Files.writeString(target, "<profile protocol='MUT2'/>");
                byte[] previous = Files.readAllBytes(target);
                FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
                assertArrayEquals(previous, Files.readAllBytes(target));
                assertNull(fixture.transfer.profilePath());
                assertTrue(fixture.status.get().startsWith("Logger setup action failed:"));
            }
        }
    }

    @Test void missingAndMalformedReloadDoNotReplaceSelectionsOrNamedPath() throws Exception {
        try (Fixture fixture = new Fixture()) {
            FxTestRuntime.run(fixture.transfer::reloadProfile);
            assertTrue(fixture.status.get().contains("Load or save"));
            fixture.apply(ordered("P1", "V"));
            Path target = folder.resolve("reload.xml");
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            for (boolean missing : List.of(false, true)) {
                if (missing) Files.delete(target); else Files.writeString(target, "<broken>");
                FxTestRuntime.run(fixture.transfer::reloadProfile); await(fixture.transfer);
                assertEquals(List.of("P1"), fixture.selected()); assertEquals(target, fixture.transfer.profilePath());
                assertTrue(fixture.status.get().startsWith("Logger setup action failed:"));
            }
        }
    }

    @Test void unavailableLoadedSelectionsAreDisclosedBeforeSave() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path source = folder.resolve("partial.xml");
            Files.writeString(source, "<profile protocol='SSM'><parameter id='P1' units='V' livedata='selected'/>"
                    + "<parameter id='DM_PENDING' units='%' livedata='selected'/></profile>");
            AtomicReference<String> review = new AtomicReference<>();
            try (var transfer = new FxLoggerSetupTransfer(null, fixture.runtime, fixture.status::set,
                    (title, detail) -> { review.set(detail); return true; })) {
                FxTestRuntime.run(() -> transfer.loadProfile(source.toFile())); await(transfer);
                FxTestRuntime.run(transfer::showProfileSave); await(transfer);
                assertTrue(review.get().contains("will not be saved: DM_PENDING"));
                assertEquals(List.of("P1"), FxLoggerProfileFiles.parse(Files.readAllBytes(source)).getSelectedIds());
            }
        }
    }

    @Test void busyAndClosedProfileActionsCannotWrite() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path target = folder.resolve("busy.xml");
            fixture.runtime.getWorkspaceContext().getLiveData().connecting();
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            assertFalse(Files.exists(target)); assertEquals(0, fixture.reviews.get());
            fixture.runtime.getWorkspaceContext().getLiveData().stopped();
            fixture.onReview.set(fixture.transfer::close);
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            assertFalse(Files.exists(target)); assertNull(fixture.transfer.profilePath());
        }
    }

    @Test void newlyAvailableSelectedChannelsAreNotReportedAsOmittedDuringSave() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path source = folder.resolve("discovered.xml"), target = folder.resolve("complete.xml");
            Files.writeString(source, "<profile protocol='SSM'><parameter id='P1' units='V' livedata='selected'/>"
                    + "<parameter id='LATER' units='V' livedata='selected'/></profile>");
            AtomicReference<String> review = new AtomicReference<>();
            try (var transfer = new FxLoggerSetupTransfer(null, fixture.runtime, fixture.status::set,
                    (title, detail) -> { review.set(detail); return true; })) {
                FxTestRuntime.run(() -> transfer.loadProfile(source.toFile())); await(transfer);
                Files.writeString(fixture.definition, XML.replace("</parameters>", parameter("LATER", "0x000004") + "</parameters>"));
                FxTestRuntime.run(fixture.runtime::reloadConfiguration);
                fixture.apply(ordered("P1", "V", "LATER", "V"));
                FxTestRuntime.run(() -> transfer.saveProfileTo(target.toFile())); await(transfer);
                assertFalse(review.get().contains("will not be saved"));
                assertEquals(List.of("P1", "LATER"), FxLoggerProfileFiles.parse(Files.readAllBytes(target)).getSelectedIds());
            }
        }
    }

    @Test void emptySelectionRoundTripsAsAnExplicitEmptyProfile() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered());
            Path target = folder.resolve("empty.xml");
            FxTestRuntime.run(() -> fixture.transfer.saveProfileTo(target.toFile())); await(fixture.transfer);
            fixture.apply(ordered("P1", "V"));
            FxTestRuntime.run(fixture.transfer::reloadProfile); await(fixture.transfer);
            assertTrue(fixture.selected().isEmpty()); assertTrue(fixture.recordedOrder().isEmpty());
        }
    }

    @Test void captureOptionsApplyAndRollbackTogetherWithoutConnecting() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var s = fixture.settings;
            s.setFileLoggingControllerSwitchId("S1"); fixture.runtime.reloadConfiguration();
            var next = new LoggerCaptureOptions(true, true, true, s.isUsNumberFormat(), "idle-test");
            fixture.capture(next, () -> {});
            assertEquals(next, LoggerCaptureOptions.from(s));
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
            var failed = new LoggerCaptureOptions(false, false, false, !s.isUsNumberFormat(), "other");
            var locale = Locale.getDefault();
            assertThrows(IllegalStateException.class, () -> fixture.capture(failed, () -> { throw new IllegalStateException("Save failed"); }));
            assertEquals(next, LoggerCaptureOptions.from(s));
            assertEquals(locale, Locale.getDefault());
        }
    }

    @Test void unsupportedAndActiveCaptureChangesAreRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var before = LoggerCaptureOptions.from(fixture.settings);
            assertThrows(RuntimeException.class, () -> fixture.capture(new LoggerCaptureOptions(false, true,
                    false, before.usNumbers(), ""), () -> fail("Missing switch must not save")));
            assertEquals(before, LoggerCaptureOptions.from(fixture.settings));
            Object controller = field(fixture.runtime, "controller");
            Field worker = controller.getClass().getDeclaredField("workerThread"); worker.setAccessible(true);
            Object previous = worker.get(controller);
            try {
                worker.set(controller, Thread.currentThread());
                assertThrows(IllegalStateException.class, () -> fixture.capture(new LoggerCaptureOptions(!before.fastPolling(),
                        false, false, before.usNumbers(), ""), () -> fail("Active capture changes must not save")));
            } finally { worker.set(controller, previous); }
            assertEquals(before, LoggerCaptureOptions.from(fixture.settings));
            fixture.settings.setDestinationTarget(new com.romraider.logger.ecu.definition.Module("tcu", new byte[] { 0x18 },
                    "Transmission", new byte[] { (byte) 0xf0 }, false));
            assertThrows(RuntimeException.class, () -> fixture.capture(new LoggerCaptureOptions(true, false,
                    false, before.usNumbers(), ""), () -> fail("Unsupported Fast Polling must not save")));
        }
    }

    @Test void logNamesCannotEscapeOutputDirectory() {
        for (String name : List.of("../other", "bad\\name", "bad:name", "bad\nname", "..", "name."))
            assertThrows(IllegalArgumentException.class, () -> new LoggerCaptureOptions(false, false, false, true, name));
        assertEquals("idle-test", new LoggerCaptureOptions(false, false, false, true, " idle-test ").logName());
    }
    @Test void xmlProfileLoadAppliesOrderedSelectionsAndUnitsWithoutConnectingOrEditingSource() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Path source = folder.resolve("normal-profile.xml");
            String xml = "<profile protocol='SSM'><parameters><parameter id='P2' units='mV' livedata='selected'/></parameters>"
                    + "<switches><switch id='S1' units='On/Off' graph='selected'/></switches></profile>";
            Files.writeString(source, xml);
            FxTestRuntime.run(() -> fixture.transfer.loadProfile(source.toFile())); await(fixture.transfer);
            assertEquals(List.of("P2", "S1"), fixture.selected());
            assertEquals("mV", fixture.runtime.captureChannelSetup().selectedChannels().getFirst().getUnits());
            assertEquals(xml, Files.readString(source));
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
            assertEquals(1, fixture.reviews.get());
        }
    }

    @Test void xmlProfileCancelMismatchMalformedAndStaleReviewLeaveSelectionUntouched() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Path source = folder.resolve("profile.xml");
            for (String xml : List.of("<profile protocol='MUT2'><parameter id='P2' livedata='selected'/></profile>",
                    "<logger/>", "<profile>", "<profile protocol='SSM'><parameter id='P2' units='invalid' livedata='selected'/></profile>")) {
                Files.writeString(source, xml);
                FxTestRuntime.run(() -> fixture.transfer.loadProfile(source.toFile())); await(fixture.transfer);
                assertEquals(List.of("P1"), fixture.selected());
            }
            Files.writeString(source, "<profile protocol='SSM'><parameter id='P2' units='V' livedata='selected'/></profile>");
            fixture.accept.set(false);
            FxTestRuntime.run(() -> fixture.transfer.loadProfile(source.toFile())); await(fixture.transfer);
            assertEquals(List.of("P1"), fixture.selected());
            fixture.accept.set(true);
            fixture.onReview.set(() -> fixture.runtime.reloadConfiguration());
            FxTestRuntime.run(() -> fixture.transfer.loadProfile(source.toFile())); await(fixture.transfer);
            assertEquals(List.of("P1"), fixture.selected());
        }
    }

    @Test void xmlProfileReportsUnavailableSelectionsBeforeApplyingAvailableOnes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path source = folder.resolve("profile.xml");
            Files.writeString(source, "<profile protocol='SSM'><parameter id='P2' units='V' livedata='selected'/>"
                    + "<parameter id='DM_PENDING' units='%' livedata='selected'/></profile>");
            AtomicReference<String> review = new AtomicReference<>();
            try (var transfer = new FxLoggerSetupTransfer(null, fixture.runtime, fixture.status::set,
                    (title, text) -> { review.set(text); return true; })) {
                FxTestRuntime.run(() -> transfer.loadProfile(source.toFile())); await(transfer);
                assertTrue(review.get().contains("DM_PENDING"));
                assertTrue(review.get().contains("will not be loaded"));
                assertEquals(List.of("P2"), fixture.selected());
            }
        }
    }

    @Test void blockedImportAndProfileLoadSurfaceErrorsBeforeOpeningPicker() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicReference<String> error = new AtomicReference<>();
            try (var transfer = new FxLoggerSetupTransfer(null, fixture.runtime, fixture.status::set,
                    (title, text) -> fail("No review while connected"), error::set)) {
                Object controller = field(fixture.runtime, "controller");
                Field worker = controller.getClass().getDeclaredField("workerThread"); worker.setAccessible(true);
                Object previous = worker.get(controller);
                try {
                    worker.set(controller, Thread.currentThread()); // No transport is opened.
                    FxTestRuntime.run(transfer::showImport);
                    assertTrue(error.get().contains("Disconnect"));
                    error.set(null);
                    FxTestRuntime.run(transfer::showProfileLoad);
                    assertTrue(error.get().contains("Disconnect"));
                } finally { worker.set(controller, previous); }
            }
        }
    }

    @Test void setupButtonsFitDefaultWindowInBothThemes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            FxTestRuntime.run(() -> {
                FxLoggerSetup.show(null, fixture.runtime, () -> {});
                var stage = (javafx.stage.Stage) javafx.stage.Window.getWindows().stream()
                        .filter(window -> window instanceof javafx.stage.Stage candidate
                                && "Logger Setup".equals(candidate.getTitle())).findFirst().orElseThrow();
                try {
                    var root = stage.getScene().getRoot();
                    for (boolean dark : List.of(false, true)) {
                        root.getStyleClass().remove("theme-dark");
                        if (dark) root.getStyleClass().add("theme-dark");
                        root.applyCss(); root.layout();
                        var buttons = root.lookupAll(".button").stream()
                                .filter(javafx.scene.control.Button.class::isInstance)
                                .map(javafx.scene.control.Button.class::cast)
                                .filter(button -> List.of("Browse…", "Disconnect").contains(button.getText())).toList();
                        assertEquals(3, buttons.size());
                        var outputLabel = root.lookupAll(".label").stream()
                                .filter(javafx.scene.control.Label.class::isInstance)
                                .map(javafx.scene.control.Label.class::cast)
                                .filter(label -> "Log output directory".equals(label.getText())).findFirst().orElseThrow();
                        assertTrue(outputLabel.getWidth() + 1 >= outputLabel.prefWidth(-1));
                        for (var button : buttons) {
                            assertTrue(button.getWidth() + 1 >= button.prefWidth(-1), button.getText() + " label clipped");
                            var bounds = button.localToScene(button.getBoundsInLocal());
                            assertTrue(bounds.getMinX() >= 0);
                            assertTrue(bounds.getMaxX() <= stage.getScene().getWidth());
                            assertTrue(bounds.getMaxY() <= stage.getScene().getHeight());
                            var viewport = root.lookup(".scroll-pane .viewport");
                            var visible = viewport.localToScene(viewport.getBoundsInLocal());
                            assertTrue(bounds.getMinY() >= visible.getMinY());
                            assertTrue(bounds.getMaxY() <= visible.getMaxY(), button.getText() + " is clipped by the scroll viewport");
                        }
                        String directory = System.getenv("RR2_SETUP_CAPTURE_DIR");
                        if (directory != null) {
                            var image = root.snapshot(null, null);
                            var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
                                bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                            javax.imageio.ImageIO.write(bitmap, "png", new File(directory, dark ? "setup-dark.png" : "setup-light.png"));
                        }
                    }
                    var tabs = (javafx.scene.control.TabPane) root.lookup(".tab-pane");
                    tabs.getSelectionModel().select(1); root.applyCss(); root.layout();
                    for (String id : List.of("logger-fast-polling", "logger-switch-recording", "logger-absolute-time", "logger-us-numbers", "logger-log-name")) {
                        var control = root.lookup("#" + id); assertNotNull(control, id);
                        var bounds = control.localToScene(control.getBoundsInLocal());
                        assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= stage.getScene().getWidth(), id);
                    }
                    String captureDirectory = System.getenv("RR2_SETUP_CAPTURE_DIR");
                    if (captureDirectory != null) {
                        var image = root.snapshot(null, null);
                        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
                            bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                        javax.imageio.ImageIO.write(bitmap, "png", new File(captureDirectory, "setup-recording.png"));
                    }
                } finally { stage.close(); }
            });
        }
    }

    @Test void disconnectButtonExplainsCancellationWithoutChangingStartupPreference() throws Exception {
        FxTestRuntime.run(() -> {
            AtomicInteger calls = new AtomicInteger();
            var button = FxLoggerSetup.disconnectButton(calls::incrementAndGet);
            assertEquals("Disconnect", button.getText());
            assertEquals(javafx.scene.layout.Region.USE_PREF_SIZE, button.getMinWidth());
            var tooltip = button.getTooltip();
            assertNotNull(tooltip);
            assertTrue(tooltip.isWrapText());
            assertEquals(360, tooltip.getMaxWidth());
            assertTrue(tooltip.getText().contains("connection or reconnection attempts"));
            assertTrue(tooltip.getText().contains("Does not change Connect automatically at startup"));
            assertEquals(tooltip.getText(), button.getAccessibleHelp());
            assertEquals(0, calls.get());
            button.fire();
            assertEquals(1, calls.get());
        });
    }

    @Test void setupRejectsInvalidDestinationsAndRestoresFailedSave() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P2", "mV"));
            var s = fixture.settings;
            String definition = s.getLoggerDefinitionFilePath(), port = s.getLoggerPort(), target = s.getTargetModule();
            boolean automatic = s.getAutoConnectOnStartup();
            for (String[] invalid : List.of(new String[]{"/missing/rr2-audit.xml", "ISO9141", target},
                    new String[]{definition, "unsupported", target}, new String[]{definition, "ISO9141", "unsupported"})) {
                assertThrows(RuntimeException.class, () -> fixture.runtime.applySetup(invalid[0], s.getLoggerOutputDirPath(),
                        "changed", "SSM", invalid[1], invalid[2], !automatic, () -> fail("Invalid setup must not be persisted")));
                assertEquals(definition, s.getLoggerDefinitionFilePath()); assertEquals(port, s.getLoggerPort());
                assertEquals(target, s.getTargetModule()); assertEquals(automatic, s.getAutoConnectOnStartup());
                assertEquals(3, fixture.runtime.getWorkspaceContext().getChannels().getChannels().size());
            }
            FxTestRuntime.run(() -> {
                assertThrows(RuntimeException.class, () -> fixture.runtime.applySetup(definition, s.getLoggerOutputDirPath(),
                        "changed", "SSM", "ISO9141", target, !automatic, () -> { throw new IllegalStateException("Synthetic disk failure"); }));
                assertEquals(port, s.getLoggerPort()); assertEquals(automatic, s.getAutoConnectOnStartup());
                assertEquals(3, fixture.runtime.getWorkspaceContext().getChannels().getChannels().size());
                assertEquals(List.of("P2"), fixture.selected());
                assertEquals("mV", fixture.runtime.captureChannelSetup().selectedChannels().getFirst().getUnits());
            });
            Path otherProtocol = folder.resolve("other-protocol.xml");
            Files.writeString(otherProtocol, XML.replace("id='SSM'", "id='OTHER'"));
            assertThrows(RuntimeException.class, () -> fixture.runtime.applySetup(otherProtocol.toString(), s.getLoggerOutputDirPath(), port,
                    "SSM", "ISO9141", target, automatic, () -> {}));
            assertEquals("SSM", s.getLoggerProtocol()); assertEquals("ISO9141", s.getTransportProtocol());
            assertEquals(definition, s.getLoggerDefinitionFilePath());
            Path otherModule = folder.resolve("other-module.xml");
            Files.writeString(otherModule, XML.replace("id='ecu'", "id='tcu'"));
            assertTrue(fixture.runtime.getTargetModuleChoices(otherModule.toString(), "SSM", "ISO9141", target).contains("tcu"));
            assertEquals(target, s.getTargetModule()); assertEquals(definition, s.getLoggerDefinitionFilePath());
        }
    }
    @Test void startupPreferenceCanChangeWhileConnectionWorkerIsActive() throws Exception {
        try (Fixture fixture = new Fixture()) {
            boolean original = fixture.settings.getAutoConnectOnStartup();
            Object controller = field(fixture.runtime, "controller");
            Field worker = controller.getClass().getDeclaredField("workerThread");
            worker.setAccessible(true);
            Object previousWorker = worker.get(controller);
            try {
                // Model the active-worker guard without starting any logger/transport.
                worker.set(controller, Thread.currentThread());
                assertThrows(IllegalStateException.class, fixture.runtime::requireConfigurationEditable);
                assertTrue(fixture.runtime.applyStartupPreferenceOnly(
                        fixture.settings.getLoggerDefinitionFilePath(), fixture.settings.getLoggerOutputDirPath(),
                        fixture.settings.getLoggerPort(), fixture.settings.getLoggerProtocol(),
                        fixture.settings.getTransportProtocol(), "ECU", false));
                assertFalse(fixture.settings.getAutoConnectOnStartup());
                assertFalse(fixture.runtime.applyStartupPreferenceOnly("changed.xml",
                        fixture.settings.getLoggerOutputDirPath(), fixture.settings.getLoggerPort(),
                        fixture.settings.getLoggerProtocol(), fixture.settings.getTransportProtocol(), "ECU", true));
                assertFalse(fixture.settings.getAutoConnectOnStartup(), "Rejected connection edits must not change startup preference");
                fixture.runtime.applySetup(fixture.settings.getLoggerDefinitionFilePath(), fixture.settings.getLoggerOutputDirPath(),
                        fixture.settings.getLoggerPort(), fixture.settings.getLoggerProtocol(), fixture.settings.getTransportProtocol(),
                        "ECU", true, () -> {});
                assertTrue(fixture.settings.getAutoConnectOnStartup(), "Actual setup save path must also work during attempts");
                assertThrows(IllegalStateException.class, () -> fixture.runtime.applySetup("changed.xml", fixture.settings.getLoggerOutputDirPath(),
                        fixture.settings.getLoggerPort(), fixture.settings.getLoggerProtocol(), fixture.settings.getTransportProtocol(),
                        "ECU", false, () -> fail("Active connection edits must not persist")));
                assertTrue(fixture.settings.getAutoConnectOnStartup());
                assertThrows(IllegalStateException.class, fixture.runtime::requireConfigurationEditable,
                        "Startup preference must not stop or replace the worker");
                assertEquals(3, fixture.runtime.getWorkspaceContext().getChannels().getChannels().size());
            } finally {
                worker.set(controller, previousWorker);
                fixture.settings.setAutoConnectOnStartup(original);
            }
        }
    }

    @Test void targetDropdownUsesDefinitionAndPreservesExistingSelection() throws Exception {
        try (Fixture fixture = new Fixture()) {
            String xml = Files.readString(fixture.definition).replace("</transport>",
                    "<module id='tcu' address='18' tester='F0' desc='Transmission' fastpoll='false'/></transport>");
            Files.writeString(fixture.definition, xml);
            FxTestRuntime.run(fixture.runtime::reloadConfiguration);
            var choices = fixture.runtime.getTargetModuleChoices("ssm", "iso9141", "ECU");
            assertEquals(Set.of("ecu", "tcu"), new HashSet<>(choices));
            assertEquals(List.of("custom"), fixture.runtime.getTargetModuleChoices("missing", "iso9141", "custom"));
            assertTrue(fixture.runtime.getTargetModuleChoices("ssm", "missing", "").isEmpty());
            FxTestRuntime.run(() -> {
                var selector = FxLoggerSetup.targetSelector(choices, "ECU");
                assertFalse(selector.isEditable());
                assertEquals("ecu", selector.getValue());
                selector.getSelectionModel().select("tcu");
                assertEquals("tcu", selector.getValue());
            });
            Files.delete(fixture.definition);
            EcuInitCallback callback = ecuCallback(fixture.runtime);
            FxTestRuntime.run(() -> callback.callback(syntheticEcu("2222222222")));
            assertTrue(fixture.runtime.getTargetModuleChoices("SSM", "ISO9141", "").isEmpty(),
                    "Failed reload must not retain stale module choices");
        }
    }

    @TempDir Path folder;
    private static final String XML = "<logger version='370'><protocols><protocol id='SSM' baud='4800' databits='8' stopbits='1' parity='0' connect_timeout='1000' send_timeout='1000'>"
            + "<transports><transport id='ISO9141' name='K-Line' desc='Synthetic'><module id='ecu' address='10' tester='F0' desc='Engine' fastpoll='true'/></transport></transports>"
            + "<parameters>" + parameter("P1", "0x000001") + parameter("P2", "0x000002") + "</parameters>"
            + "<switches><switch id='S1' name='Flag' desc='Synthetic' byte='0x000003' bit='0' units='On/Off' target='1'/></switches>"
            + "</protocol></protocols></logger>";
    private static String parameter(String id, String address) {
        return "<parameter id='" + id + "' name='Same name' desc='Synthetic' target='1'><address>" + address
                + "</address><conversions><conversion units='V' expr='x' format='0'/><conversion units='mV' expr='x*1000' format='0'/></conversions></parameter>";
    }

    @Test void failedOrMissingDefinitionRemovesSwitchAndPreservesRecoveryProfile() throws Exception {
        for (int failure = 0; failure < 3; failure++) {
            try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot()) {
                fixture.settings.setFileLoggingControllerSwitchId("S1");
                FxTestRuntime.run(fixture.runtime::reloadConfiguration);
                fixture.settings.setFileLoggingControllerSwitchActive(true);
                fixture.apply(ordered("P1", "V"));
                Object manager = field(field(fixture.runtime, "controller"), "queryManager");
                assertNotNull(field(manager, "fileLoggerBinding"));
                Path backup = Path.of(fixture.settings.getLoggerProfileFilePath());
                byte[] saved = Files.readAllBytes(backup);
                if (failure == 0) Files.writeString(fixture.definition, "<logger><broken>");
                else if (failure == 1) Files.delete(fixture.definition);
                else fixture.settings.setLoggerDefinitionFilePath("");
                EcuInitCallback callback = ecuCallback(fixture.runtime);
                FxTestRuntime.run(() -> callback.callback(syntheticEcu("2222222222")));
                assertNull(field(manager, "fileLoggerBinding"), "Old automatic recording address retained");
                assertFalse(fixture.settings.isFileLoggingControllerSwitchActive());
                assertNull(fixture.settings.getLoggerConnectionProperties());
                assertNull(fixture.settings.getDestinationTarget());
                assertTrue(fixture.runtime.getWorkspaceContext().getChannels().getChannels().isEmpty());
                assertTrue(fixture.settings.isLogExternalsOnly());
                assertFalse(((com.romraider.logger.ecu.comms.controller.LoggerController)
                        field(fixture.runtime, "controller")).isStarted());
                FxTestRuntime.run(fixture.runtime::close);
                assertArrayEquals(saved, Files.readAllBytes(backup), "Failed definition replaced recovery profile");
            }
        }
    }

    @Test void healthyReloadReplacesRecordingSwitchAndClosureRemovesIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.settings.setFileLoggingControllerSwitchId("S1");
            FxTestRuntime.run(fixture.runtime::reloadConfiguration);
            fixture.settings.setFileLoggingControllerSwitchActive(true);
            Object manager = field(field(fixture.runtime, "controller"), "queryManager");
            Object first = field(manager, "fileLoggerBinding");
            assertNotNull(first);
            FxTestRuntime.run(fixture.runtime::reloadConfiguration);
            assertNotSame(first, field(manager, "fileLoggerBinding"));
            assertNotNull(field(manager, "fileLoggerBinding"));
            assertTrue(fixture.settings.isFileLoggingControllerSwitchActive());
            FxTestRuntime.run(fixture.runtime::close);
            assertNull(field(manager, "fileLoggerBinding"));
        }
    }

    @Test void intentionalExternalOnlyStartupCanStillSaveItsEmptyProfile() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Path backup = Path.of(fixture.settings.getLoggerProfileFilePath());
            byte[] withEcu = Files.readAllBytes(backup);
            fixture.settings.setLoggerDefinitionFilePath("");
            fixture.reopen();
            assertTrue(fixture.runtime.getWorkspaceContext().getChannels().getChannels().isEmpty());
            FxTestRuntime.run(fixture.runtime::close);
            assertFalse(Arrays.equals(withEcu, Files.readAllBytes(backup)),
                    "Intentional external-only startup was mistaken for a failed loaded definition");
        }
    }

    @Test void failedDefinitionKeepsExternalSelectionAndLaterValidDefinitionRecovers() throws Exception {
        try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot()) {
            List<ExternalData> external = field(fixture.runtime, "externalData");
            external.add(new ExternalData() {
                final EcuDataConvertor converter = new EcuParameterConvertorImpl();
                public String getId() { return "E1"; }
                public String getName() { return "Synthetic external"; }
                public String getDescription() { return "No source or device"; }
                public EcuDataConvertor getSelectedConvertor() { return converter; }
                public EcuDataConvertor[] getConvertors() { return new EcuDataConvertor[] {converter}; }
                public void selectConvertor(EcuDataConvertor next) { }
                public EcuDataType getDataType() { return EcuDataType.EXTERNAL; }
                public boolean isSelected() { return false; }
                public void setSelected(boolean selected) { }
                public void addConvertorUpdateListener(ConvertorUpdateListener listener) { }
            });
            FxTestRuntime.run(fixture.runtime::reloadConfiguration);
            fixture.apply(ordered("P1", "V"));
            FxTestRuntime.run(() -> fixture.runtime.getWorkspaceContext().getChannels().setSelected("E1", true));
            Files.writeString(fixture.definition, "<logger><broken>");
            EcuInitCallback callback = ecuCallback(fixture.runtime);
            FxTestRuntime.run(() -> callback.callback(syntheticEcu("2222222222")));
            List<LoggerChannel> channels = fixture.runtime.getWorkspaceContext().getChannels().getChannels();
            assertEquals(1, channels.size());
            assertEquals("E1", channels.get(0).getParameterId());
            assertTrue(channels.get(0).isSelected(), "External selection lost during ECU failure");
            assertEquals(List.of("E1"), fixture.recordedOrder());
            Files.writeString(fixture.definition, XML);
            FxTestRuntime.run(fixture.runtime::reloadConfiguration);
            assertEquals(4, fixture.runtime.getWorkspaceContext().getChannels().getChannels().size());
            assertFalse(fixture.settings.isLogExternalsOnly());
            fixture.apply(ordered("P2", "V"));
            Path backup = Path.of(fixture.settings.getLoggerProfileFilePath());
            byte[] healthy = Files.readAllBytes(backup);
            FxTestRuntime.run(fixture.runtime::close);
            assertArrayEquals(healthy, Files.readAllBytes(backup));
        }
    }

    @Test void desktopAndPortableExchangePreservesOrderedUnitsAndRestartWithoutConnecting() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P2", "mV", "S1", "On/Off", "P1", "V"));
            Path exported = folder.resolve("desktop.rr2logger");
            FxTestRuntime.run(() -> fixture.transfer.exportTo(exported.toFile())); await(fixture.transfer);
            PortableLoggerSetup portable;
            try (InputStream input = Files.newInputStream(exported)) { portable = PortableLoggerSetup.read(input); }
            assertEquals(List.of("P2", "S1", "P1"), ids(portable.profile()));
            assertEquals("mV", portable.profile().selections().get(0).getUnits());
            assertArrayEquals(XML.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(fixture.definition));
            fixture.apply(ordered("P1", "mV"));
            FxTestRuntime.run(() -> fixture.transfer.load(exported.toFile())); await(fixture.transfer);
            assertEquals(List.of("P2", "S1", "P1"), fixture.selected());
            assertEquals(List.of("P2", "S1", "P1"), fixture.recordedOrder());
            assertTrue(fixture.status.get().contains("imported"));
            assertEquals("<profile protocol='SSM'/>", Files.readString(fixture.originalProfile));
            assertNotEquals(fixture.originalProfile.toString(), fixture.settings.getLoggerProfileFilePath());
            fixture.reopen();
            assertEquals(List.of("P2", "S1", "P1"), fixture.selected());
            assertEquals("mV", fixture.runtime.captureChannelSetup().selectedChannels().get(0).getUnits());
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
        }
    }

    @Test void exportUsesActuallyLoadedBytesRatherThanRereadingChangedOrRemovedSource() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            LoggerSetupSnapshot captured = fixture.runtime.captureChannelSetup();
            byte[] bytes = captured.definitionBytes(); bytes[0] = 0;
            Files.delete(fixture.definition);
            Path exported = folder.resolve("captured.rr2logger");
            FxTestRuntime.run(() -> fixture.transfer.exportTo(exported.toFile())); await(fixture.transfer);
            try (InputStream input = Files.newInputStream(exported)) {
                PortableLoggerSetup setup = PortableLoggerSetup.read(input);
                setup.validateAgainst(PortableLoggerProtocol.SSM, XML.getBytes(StandardCharsets.UTF_8),
                        PortableLoggerDefinitionReader.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)), "SSM"));
            }
            assertArrayEquals(XML.getBytes(StandardCharsets.UTF_8), captured.definitionBytes());
        }
    }

    @Test void failedCancelledAndStaleReviewsCannotReplaceCurrentSelection() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V")); Path source = fixture.portable(ordered("P2", "V"));
            fixture.accept.set(false);
            FxTestRuntime.run(() -> fixture.transfer.load(source.toFile())); await(fixture.transfer);
            assertEquals(List.of("P1"), fixture.selected());
            fixture.accept.set(true);
            fixture.onReview.set(() -> fixture.runtime.getWorkspaceContext().getChannels().setSelected("P2", true));
            FxTestRuntime.run(() -> fixture.transfer.load(source.toFile())); await(fixture.transfer);
            assertEquals(List.of("P1", "P2"), fixture.selected());
            assertTrue(fixture.status.get().contains("changed"));
            fixture.onReview.set(() -> {});
            Files.writeString(source, "invalid");
            FxTestRuntime.run(() -> fixture.transfer.load(source.toFile())); await(fixture.transfer);
            assertEquals(List.of("P1", "P2"), fixture.selected());
            assertTrue(fixture.status.get().contains("failed"));
        }
    }

    @Test void lateAndClosedImportsDiscardQueuedResults() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path source = fixture.portable(ordered("P1", "V"));
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService worker = field(fixture.transfer, "worker");
            worker.submit(() -> { release.await(10, TimeUnit.SECONDS); return null; });
            try {
                FxTestRuntime.run(() -> {
                    fixture.transfer.load(source.toFile());
                    fixture.runtime.getWorkspaceContext().getChannels().setSelected("P2", true);
                });
            } finally { release.countDown(); }
            await(fixture.transfer);
            assertEquals(List.of("P2"), fixture.selected()); assertEquals(0, fixture.reviews.get());
            FxTestRuntime.run(() -> {
                fixture.transfer.load(source.toFile());
                Future<?> pending = field(fixture.transfer, "pending"); pending.get(10, TimeUnit.SECONDS);
                fixture.transfer.close();
            });
            FxTestRuntime.run(() -> {});
            assertEquals(List.of("P2"), fixture.selected()); assertEquals(0, fixture.reviews.get());
            assertTrue(worker.isShutdown());
        }
    }

    @Test void validatesWholeReplacementAndRollsBackConverterFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P2", "V", "P1", "V"));
            LoggerSetupSnapshot snapshot = fixture.runtime.captureChannelSetup();
            assertThrows(IllegalArgumentException.class, () -> fixture.runtime.applyChannelSetup(snapshot, ordered("P1", "mV", "P2", "missing")));
            assertEquals(List.of("P2", "P1"), fixture.selected());
            assertEquals("V", fixture.runtime.captureChannelSetup().selectedChannels().get(1).getUnits());
            Map<String, LoggerData> data = field(fixture.runtime, "dataById"); AtomicBoolean failOnce = new AtomicBoolean(true);
            ((EcuParameter) data.get("P1")).addConvertorUpdateListener(changed -> {
                if (failOnce.getAndSet(false)) throw new IllegalStateException("Synthetic converter listener failure");
            });
            assertThrows(IllegalStateException.class, () -> fixture.runtime.applyChannelSetup(snapshot, ordered("P1", "mV")));
            assertEquals(List.of("P2", "P1"), fixture.selected());
            assertEquals(List.of("P2", "P1"), fixture.recordedOrder());
            assertEquals("V", fixture.runtime.captureChannelSetup().selectedChannels().get(1).getUnits());
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
        }
    }

    @Test void emptySetupClearsQueuedQueriesAndBusyStateRefusesTransfer() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V", "P2", "V"));
            fixture.runtime.getWorkspaceContext().getLiveData().connecting();
            assertThrows(IllegalStateException.class, fixture.runtime::captureChannelSetup);
            fixture.runtime.getWorkspaceContext().getLiveData().stopped();
            AtomicBoolean pending = field(fixture.runtime.getWorkspaceContext().getSession(), "commandPending");
            pending.set(true);
            try { assertThrows(IllegalStateException.class, fixture.runtime::captureChannelSetup); } finally { pending.set(false); }
            fixture.apply(ordered());
            assertEquals(List.of(), fixture.selected()); assertEquals(List.of(), fixture.recordedOrder());
            Object controller = field(fixture.runtime, "controller"); Object manager = field(controller, "queryManager");
            Method flush = manager.getClass().getDeclaredMethod("updateQueryList"); flush.setAccessible(true); flush.invoke(manager);
            Map<?, ?> queries = field(manager, "queryMap"); assertTrue(queries.isEmpty());
            fixture.reopen(); assertTrue(fixture.selected().isEmpty());
        }
    }

    @Test void exportRejectsProtectedTargetsAndCancelledReviewDoesNotWrite() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Path target = folder.resolve("unchanged.csv"); byte[] original = {1, 2, 3}; Files.write(target, original);
            FxTestRuntime.run(() -> fixture.transfer.exportTo(target.toFile())); await(fixture.transfer);
            assertArrayEquals(original, Files.readAllBytes(target)); assertTrue(fixture.status.get().contains("destination"));
            fixture.accept.set(false); Path cancelled = folder.resolve("cancelled.rr2logger");
            FxTestRuntime.run(() -> fixture.transfer.exportTo(cancelled.toFile())); await(fixture.transfer);
            assertFalse(Files.exists(cancelled));
        }
    }

    @Test void definitionReloadInvalidatesSnapshotsAndRejectsAnOldFingerprint() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path input = fixture.portable(ordered("P1", "V"));
            LoggerSetupSnapshot snapshot = fixture.runtime.captureChannelSetup();
            fixture.settings.setLoggerDefinitionFilePath(folder.resolve("other.xml").toString());
            assertThrows(IllegalStateException.class, () -> fixture.runtime.requireCurrentChannelSetup(snapshot));
            fixture.settings.setLoggerDefinitionFilePath(fixture.definition.toString());
            Files.writeString(fixture.definition, XML + "\n");
            FxTestRuntime.run(fixture.runtime::reloadConfiguration);
            assertThrows(IllegalStateException.class, () -> fixture.runtime.requireCurrentChannelSetup(snapshot));
            FxTestRuntime.run(() -> fixture.transfer.load(input.toFile())); await(fixture.transfer);
            assertTrue(fixture.selected().isEmpty()); assertEquals(0, fixture.reviews.get());
            assertTrue(fixture.status.get().contains("exact same logger definition"));
        }
    }

    @Test void persistenceFailureIsReportedWithoutClaimingAnUnappliedSelection() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path input = fixture.portable(ordered("P2", "mV"));
            Path blockedBackup = folder.resolve("profiles/profile_backup.xml");
            Files.createDirectories(blockedBackup);
            try {
                FxTestRuntime.run(() -> fixture.transfer.load(input.toFile())); await(fixture.transfer);
                assertEquals(List.of("P2"), fixture.selected());
                assertTrue(fixture.status.get().contains("persistence failed"));
                assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
            } finally { Files.delete(blockedBackup); }
        }
    }

    @Test void aFailedRollbackInhibitsFurtherTransferUntilRuntimeRecreation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V"));
            Map<String, LoggerData> data = field(fixture.runtime, "dataById");
            ((EcuParameter) data.get("P1")).addConvertorUpdateListener(changed -> { throw new IllegalStateException("Synthetic persistent listener fault"); });
            LoggerSetupSnapshot snapshot = fixture.runtime.captureChannelSetup();
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> fixture.runtime.applyChannelSetup(snapshot, ordered("P1", "mV")));
            assertTrue(failure.getMessage().contains("Close and reopen"));
            assertEquals(Boolean.TRUE, field(fixture.runtime, "setupRecoveryRequired"));
            assertThrows(IllegalStateException.class, fixture.runtime::captureChannelSetup);
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
            fixture.reopen();
            assertEquals(List.of("P1"), fixture.selected());
            assertEquals("V", fixture.runtime.captureChannelSetup().selectedChannels().get(0).getUnits());
        }
    }

    @Test void changingProtocolDoesNotReuseThePreviousProtocolsSelectedIds() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(ordered("P1", "V", "P2", "V"));
            Files.writeString(fixture.definition, XML.replace("id='SSM'", "id='MUT2'"));
            fixture.settings.setLoggerProtocol("MUT2");
            FxTestRuntime.run(fixture.runtime::reloadConfiguration);
            assertEquals("MUT2", fixture.runtime.captureChannelSetup().protocol());
            assertTrue(fixture.selected().isEmpty());
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
        }
    }

    @Test void closedRuntimeIgnoresLateEcuIdentification() throws Exception {
        try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot()) {
            EcuInitCallback callback = ecuCallback(fixture.runtime);
            EcuInit original = syntheticEcu("1111111111");
            callback.callback(original);
            fixture.runtime.close();
            PlatformContext.getInstance().setDimeModRuntime(DimeModState.NOT_PRESENT, false);
            callback.callback(syntheticEcu("2222222222"));
            assertSame(original, fixture.runtime.getEcuInit(), "Closed owner accepted a late ECU identity");
            assertEquals(DimeModState.NOT_PRESENT, PlatformContext.getInstance().getDimeModState());
            assertEquals(LoggerSessionState.STOPPED, fixture.runtime.getWorkspaceContext().getSession().getState());
        }
    }

    @Test void closedRuntimeIgnoresLateDimeMetadataAndForcedUpdates() throws Exception {
        try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot()) {
            DmInitCallback callback = dmCallback(fixture.runtime);
            fixture.runtime.close();
            PlatformContext.getInstance().setDimeModRuntime(DimeModState.NOT_PRESENT, false);
            callback.callback(syntheticDime(), true);
            assertNull(callback.getDmInit(), "Closed owner accepted late discovery metadata");
            assertEquals(DimeModState.NOT_PRESENT, PlatformContext.getInstance().getDimeModState());
            assertFalse(PlatformContext.getInstance().isRamTuneRuntimeAvailable());
            assertTrue(PlatformContext.getInstance().getRamTuneRuntimeMetadata().isEmpty());
        }
    }

    @Test void previousRuntimeCallbacksCannotOverwriteReopenedWorkspace() throws Exception {
        try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot()) {
            EcuInitCallback oldEcu = ecuCallback(fixture.runtime);
            DmInitCallback oldDm = dmCallback(fixture.runtime);
            fixture.reopen();
            EcuInit current = syntheticEcu("2222222222");
            DmInit metadata = syntheticDime();
            ecuCallback(fixture.runtime).callback(current);
            dmCallback(fixture.runtime).callback(metadata, true);
            assertEquals(DimeModState.ACTIVE, PlatformContext.getInstance().getDimeModState());
            oldEcu.callback(syntheticEcu("1111111111"));
            oldDm.callback(null, true);
            assertSame(current, fixture.runtime.getEcuInit());
            assertSame(metadata, dmCallback(fixture.runtime).getDmInit());
            assertEquals(DimeModState.ACTIVE, PlatformContext.getInstance().getDimeModState(),
                    "An old workspace overwrote the new owner's platform state");
        }
    }

    @Test void activeRuntimeRetainsSameIdCacheAndInvalidatesChangedEcuId() throws Exception {
        try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot()) {
            EcuInitCallback ecu = ecuCallback(fixture.runtime);
            DmInitCallback dm = dmCallback(fixture.runtime);
            ecu.callback(syntheticEcu("1111111111"));
            DmInit metadata = syntheticDime();
            dm.callback(metadata, true);
            ecu.callback(syntheticEcu("1111111111"));
            assertSame(metadata, dm.getDmInit(), "Existing same-ID cache behavior changed");
            ecu.callback(syntheticEcu("2222222222"));
            assertNull(dm.getDmInit());
            assertEquals(DimeModState.UNKNOWN, PlatformContext.getInstance().getDimeModState());
            assertFalse(((com.romraider.logger.ecu.comms.controller.LoggerController) field(fixture.runtime, "controller")).isStarted());
        }
    }

    @Test void expiredAttemptIsRecheckedInsideTheRuntimeOwnerLock() throws Exception {
        try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot()) {
            EcuInit original = syntheticEcu("1111111111");
            DmInit metadata = syntheticDime();
            EcuInitCallback ecu = ecuCallback(fixture.runtime);
            DmInitCallback dime = dmCallback(fixture.runtime);
            ecu.callback(original);
            dime.callback(metadata, true);
            for (int operation = 0; operation < 3; operation++) {
                var attempt = new com.romraider.logger.ecu.comms.query.InitializationAttempt();
                final int selected = operation;
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread callback = new Thread(() -> {
                    try {
                        if (selected == 0) ecu.callback(syntheticEcu("2222222222"), attempt);
                        else if (selected == 1) dime.callback(null, true, attempt);
                        else assertThrows(IllegalStateException.class, () -> dime.getDmInit(attempt));
                    } catch (Throwable error) { failure.set(error); }
                }, "synthetic callback waiting for desktop owner");
                callback.setDaemon(true);
                synchronized (fixture.runtime) {
                    callback.start();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (callback.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield();
                    assertEquals(Thread.State.BLOCKED, callback.getState());
                    attempt.close();
                }
                callback.join(2000);
                assertFalse(callback.isAlive());
                assertNull(failure.get());
                assertSame(original, fixture.runtime.getEcuInit());
                assertSame(metadata, dime.getDmInit());
                assertEquals(DimeModState.ACTIVE, PlatformContext.getInstance().getDimeModState());
            }
        }
    }

    @Test void activeAttemptPreservesCacheAndClosedOwnerRejectsScopedLookup() throws Exception {
        try (Fixture fixture = new Fixture(); DimeStateSnapshot ignored = new DimeStateSnapshot();
                var attempt = new com.romraider.logger.ecu.comms.query.InitializationAttempt()) {
            EcuInitCallback ecu = attempt.bind(ecuCallback(fixture.runtime));
            DmInitCallback dime = attempt.bind(dmCallback(fixture.runtime));
            ecu.callback(syntheticEcu("1111111111"));
            DmInit metadata = syntheticDime();
            dime.callback(metadata, true);
            ecu.callback(syntheticEcu("1111111111"));
            assertSame(metadata, dime.getDmInit());
            assertFalse(dime.needToInit());
            fixture.runtime.close();
            assertThrows(IllegalStateException.class, dime::getDmInit);
        }
    }

    private static EcuInitCallback ecuCallback(LoggerDesktopRuntime runtime) throws Exception {
        return field(field(field(runtime, "controller"), "queryManager"), "ecuInitCallback");
    }
    private static DmInitCallback dmCallback(LoggerDesktopRuntime runtime) throws Exception {
        return field(field(field(runtime, "controller"), "queryManager"), "dmInitCallback");
    }
    private static EcuInit syntheticEcu(String id) {
        return new EcuInit() {
            public String getEcuId() { return id; }
            public byte[] getEcuInitBytes() { return new byte[128]; }
        };
    }
    private static DmInit syntheticDime() {
        java.nio.ByteBuffer data = java.nio.ByteBuffer.allocate(112);
        data.put((byte) 2).put((byte) 3).putShort((short) 100).putInt(0x20000).putInt(0).putInt(0xDEAD0001);
        for (int i = 0; i < 24; i++) data.putInt(0x1000 + i * 16);
        DmInit value = new DmInit(data.array());
        value.updateRuntimeData(0, 1, new int[8], new int[8]);
        return value;
    }
    private static final class DimeStateSnapshot implements AutoCloseable {
        final PlatformContext context = PlatformContext.getInstance();
        final DimeModState state = context.getDimeModState();
        final boolean ram = context.isRamTuneRuntimeAvailable();
        final RamTuneRuntimeMetadata metadata = context.getRamTuneRuntimeMetadata().orElse(null);
        public void close() { context.setDimeModRuntime(state, ram, metadata); }
    }

    private final class Fixture implements AutoCloseable {
        final Settings settings = SettingsManager.getSettings();
        final LoggerCaptureOptions oldCapture = LoggerCaptureOptions.from(settings);
        final String oldLocale = settings.getLocale();
        final Locale oldProcessLocale = Locale.getDefault();
        final String oldDefinition = settings.getLoggerDefinitionFilePath(), oldProfile = settings.getLoggerProfileFilePath(), oldProtocol = settings.getLoggerProtocol();
        final String oldTransport = settings.getTransportProtocol(), oldTarget = settings.getTargetModule();
        final String oldControlSwitch = settings.getFileLoggingControllerSwitchId();
        final boolean oldControlActive = settings.isFileLoggingControllerSwitchActive();
        final boolean oldExternalOnly = settings.isLogExternalsOnly();
        final com.romraider.io.connection.ConnectionProperties oldConnectionProperties = settings.getLoggerConnectionProperties();
        final com.romraider.logger.ecu.definition.Module oldDestination = settings.getDestinationTarget();
        final Vector<File> oldDefinitions = settings.getEcuDefinitionFiles();
        final String oldPlugins = System.getProperty("romraider2.plugins.dir");
        final Object oldDirectory;
        final boolean oldTesting = SettingsManager.getTesting();
        final Path definition = folder.resolve("definition.xml");
        final Path originalProfile = folder.resolve("original-profile.xml");
        final AtomicBoolean accept = new AtomicBoolean(true);
        final AtomicInteger reviews = new AtomicInteger();
        final AtomicReference<Runnable> onReview = new AtomicReference<>(() -> {});
        final AtomicReference<String> status = new AtomicReference<>("");
        LoggerDesktopRuntime runtime; FxLoggerSetupTransfer transfer;
        Fixture() throws Exception {
            oldDirectory = staticField(SettingsManager.class, "settingsDir");
            setStatic(SettingsManager.class, "settingsDir", folder.toString());
            SettingsManager.setTesting(true); System.setProperty("romraider2.plugins.dir", folder.resolve("no-plugins").toString());
            Files.writeString(definition, XML);
            Files.writeString(originalProfile, "<profile protocol='SSM'/>");
            settings.setLoggerDefinitionFilePath(definition.toString()); settings.setLoggerProfileFilePath(originalProfile.toString());
            settings.setLoggerProtocol("SSM"); settings.setTransportProtocol("ISO9141"); settings.setTargetModule("ecu");
            settings.setEcuDefinitionFiles(new Vector<>());
            FxTestRuntime.run(() -> {
                runtime = new LoggerDesktopRuntime(); transfer = new FxLoggerSetupTransfer(null, runtime, status::set, (title, text) -> {
                    reviews.incrementAndGet(); onReview.get().run(); return accept.get();
                });
            });
            assertEquals(3, runtime.getWorkspaceContext().getChannels().getChannels().size());
        }
        void apply(Map<String, String> selections) throws Exception {
            FxTestRuntime.run(() -> assertTrue(runtime.applyChannelSetup(runtime.captureChannelSetup(), selections)));
        }
        void capture(LoggerCaptureOptions options, Runnable persist) {
            runtime.applySetup(settings.getLoggerDefinitionFilePath(), settings.getLoggerOutputDirPath(), settings.getLoggerPort(),
                    settings.getLoggerProtocol(), settings.getTransportProtocol(), settings.getTargetModule(),
                    settings.getAutoConnectOnStartup(), options, persist);
        }
        List<String> selected() { return runtime.captureChannelSetup().selectedChannels().stream().map(LoggerChannel::getParameterId).toList(); }
        List<String> recordedOrder() throws Exception {
            Object handler = field(runtime, "fileHandler"); Map<LoggerData, Integer> data = field(handler, "loggerDatas");
            return data.keySet().stream().map(LoggerData::getId).toList();
        }
        Path portable(Map<String, String> selections) throws Exception {
            List<PortableLoggerProfile.Selection> choices = new ArrayList<>(); selections.forEach((id, units) -> choices.add(new PortableLoggerProfile.Selection(id, units)));
            byte[] bytes = XML.getBytes(StandardCharsets.UTF_8);
            PortableLoggerSetup setup = PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes,
                    PortableLoggerDefinitionReader.read(new ByteArrayInputStream(bytes), "SSM"), new PortableLoggerProfile("SSM", choices, List.of()));
            Path source = folder.resolve("input.rr2logger"); Files.write(source, setup.encode()); return source;
        }
        void reopen() throws Exception {
            FxTestRuntime.run(() -> { transfer.close(); runtime.close(); runtime = new LoggerDesktopRuntime(); });
        }
        @Override public void close() throws Exception {
            try { FxTestRuntime.run(() -> { transfer.close(); runtime.close(); }); }
            finally {
                settings.setLoggerDefinitionFilePath(oldDefinition); settings.setLoggerProfileFilePath(oldProfile); settings.setLoggerProtocol(oldProtocol);
                settings.setTransportProtocol(oldTransport); settings.setTargetModule(oldTarget); settings.setEcuDefinitionFiles(oldDefinitions);
                settings.setFileLoggingControllerSwitchId(oldControlSwitch);
                settings.setFileLoggingControllerSwitchActive(oldControlActive);
                settings.setLogExternalsOnly(oldExternalOnly);
                settings.setLoggerConnectionProperties(oldConnectionProperties);
                settings.setDestinationTarget(oldDestination);
                settings.setFastPoll(oldCapture.fastPolling()); settings.setFileLoggingAbsoluteTimestamp(oldCapture.absoluteTimestamp());
                settings.setLogfileNameText(oldCapture.logName()); settings.setLocale(oldLocale); Locale.setDefault(oldProcessLocale);
                SettingsManager.setTesting(oldTesting); setStatic(SettingsManager.class, "settingsDir", oldDirectory);
                if (oldPlugins == null) System.clearProperty("romraider2.plugins.dir"); else System.setProperty("romraider2.plugins.dir", oldPlugins);
            }
        }
    }
    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]); return result;
    }
    private static List<String> ids(PortableLoggerProfile profile) { return profile.selections().stream().map(PortableLoggerProfile.Selection::getId).toList(); }
    private static void await(FxLoggerSetupTransfer transfer) throws Exception {
        for (int i = 0; i < 2; i++) { Future<?> pending = field(transfer, "pending"); if (pending != null) pending.get(10, TimeUnit.SECONDS); FxTestRuntime.run(() -> {}); }
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
    private static Object staticField(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static void setStatic(Class<?> type, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value); }
}
