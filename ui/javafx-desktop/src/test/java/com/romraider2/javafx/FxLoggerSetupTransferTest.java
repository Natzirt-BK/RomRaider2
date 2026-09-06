/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.Settings;
import com.romraider.util.SettingsManager;
import com.romraider.logger.api.*;
import com.romraider.logger.runtime.*;
import com.romraider.logger.ecu.definition.*;
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

    private final class Fixture implements AutoCloseable {
        final Settings settings = SettingsManager.getSettings();
        final String oldDefinition = settings.getLoggerDefinitionFilePath(), oldProfile = settings.getLoggerProfileFilePath(), oldProtocol = settings.getLoggerProtocol();
        final String oldTransport = settings.getTransportProtocol(), oldTarget = settings.getTargetModule();
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
