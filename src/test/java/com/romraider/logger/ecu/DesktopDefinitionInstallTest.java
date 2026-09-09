/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import com.romraider.Settings;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.definition.LoggerDefinitionInstaller;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real parser/installer with an isolated Swing owner, no controller or vehicle. */
public class DesktopDefinitionInstallTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Settings settings;
    private Settings currentSettings;
    private Path directory;
    private boolean stopped;
    private SwingLoggerInitialization owner;
    private Runnable save;
    private AtomicInteger saves;
    private String oldPath, oldProtocol, oldTransport, oldProfile, oldTarget, oldDevice;

    @Before public void setup() throws Exception {
        settings = new Settings();
        oldPath = settings.getLoggerDefinitionFilePath();
        oldProtocol = settings.getLoggerProtocol();
        oldTransport = settings.getTransportProtocol();
        oldProfile = settings.getLoggerProfileFilePath();
        oldTarget = settings.getTargetModule();
        oldDevice = settings.getJ2534Device();
        settings.setLoggerDefinitionFilePath("old-definition.xml");
        settings.setLoggerProtocol("SSM");
        settings.setTransportProtocol("iso9141");
        settings.setTargetModule("ecu");
        settings.setFileLoggingControllerSwitchId("S1");
        currentSettings = settings;
        directory = temporary.getRoot().toPath().resolve("settings");
        stopped = true;
        saves = new AtomicInteger();
        save = () -> saves.incrementAndGet();
        owner = new SwingLoggerInitialization(task -> { }, state -> { }, state -> { });
    }

    @After public void restoreStaticSettings() {
        settings.setLoggerDefinitionFilePath(oldPath);
        settings.setLoggerProtocol(oldProtocol);
        settings.setTransportProtocol(oldTransport);
        settings.setLoggerProfileFilePath(oldProfile);
        settings.setTargetModule(oldTarget);
        settings.setJ2534Device(oldDevice);
    }

    private DesktopDefinitionInstall request() throws Exception {
        return edt(() -> new DesktopDefinitionInstall(owner.beginReload(owner.snapshot()),
                () -> currentSettings, () -> directory, () -> stopped, () -> save.run()));
    }

    @Test public void preparesOffThreadAndCommitsCapturedBytesOnlyOnce() throws Exception {
        DesktopDefinitionInstall request = request();
        Path source = definition("candidate", "SSM");
        DesktopDefinitionInstall.Candidate candidate = request.prepare(source);
        Files.delete(source);
        assertFalse(Files.exists(directory));
        assertEquals(0, saves.get());
        edt(() -> {
            LoggerDefinitionInstaller.Installation installed = request.commit(candidate);
            assertNotNull(installed);
            assertEquals("candidate", installed.version());
            assertTrue(request.canActivate(candidate));
            assertEquals(installed.installedFile().toString(), settings.getLoggerDefinitionFilePath());
            assertNull(request.commit(candidate));
            assertEquals(1, saves.get());
            return null;
        });
    }

    @Test public void setupChangesRejectCommitWithoutCreatingManagedFiles() throws Exception {
        for (int change = 0; change < 10; change++) {
            // Each iteration captures the state left by the previous one.
            DesktopDefinitionInstall request = request();
            DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", settings.getLoggerProtocol()));
            final int kind = change;
            edt(() -> {
                switch (kind) {
                    case 0: settings.setLoggerDefinitionFilePath("newer.xml"); break;
                    case 1: settings.setLoggerProtocol("MUT2"); break;
                    case 2: settings.setTransportProtocol("CAN"); break;
                    case 3: settings.setTargetModule("tcu"); break;
                    case 4: settings.setFileLoggingControllerSwitchId("S2"); break;
                    case 5: settings.setLoggerPort("new-port"); break;
                    case 6: settings.setLoggerProfileFilePath("new-profile.xml"); break;
                    case 7: settings.setJ2534Device("new-device"); break;
                    case 8: settings.setAutoConnectOnStartup(!settings.getAutoConnectOnStartup()); break;
                    default: currentSettings = new Settings(); break;
                }
                assertFalse(request.isCurrent());
                assertNull(request.commit(candidate));
                assertEquals(0, saves.get());
                assertFalse(Files.exists(directory));
                return null;
            });
        }
    }

    @Test public void changedDirectoryDoesNotWriteEitherDestination() throws Exception {
        DesktopDefinitionInstall request = request();
        DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", "SSM"));
        Path oldDirectory = directory;
        edt(() -> {
            directory = temporary.getRoot().toPath().resolve("new-settings");
            assertNull(request.commit(candidate));
            assertFalse(Files.exists(directory));
            assertFalse(Files.exists(oldDirectory));
            return null;
        });
    }

    @Test public void newerCatalogCloseIdentityAndConnectionInvalidateOldRequest() throws Exception {
        for (int change = 0; change < 4; change++) {
            DesktopDefinitionInstall request = request();
            DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", "SSM"));
            final int kind = change;
            edt(() -> {
                switch (kind) {
                    case 0: owner.beginReload(owner.snapshot()); break;
                    case 1: owner.ecuCallback().callback(new EcuInit() {
                        public String getEcuId() { return "1234567890"; }
                        public byte[] getEcuInitBytes() { return new byte[128]; }
                    }); break;
                    case 2: owner.invalidateReload(); break; // Connect then stop still invalidates.
                    default: owner.close(); break;
                }
                assertNull(request.commit(candidate));
                assertFalse(Files.exists(directory));
                assertEquals("old-definition.xml", settings.getLoggerDefinitionFilePath());
                assertEquals(0, saves.get());
                return null;
            });
        }
    }

    @Test public void activeConnectionRejectsCommit() throws Exception {
        DesktopDefinitionInstall request = request();
        DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", "SSM"));
        edt(() -> {
            stopped = false;
            assertNull(request.commit(candidate));
            assertFalse(Files.exists(directory));
            return null;
        });
    }

    @Test public void invalidPreparationDoesNotRestoreNewerSettings() throws Exception {
        DesktopDefinitionInstall request = request();
        settings.setLoggerDefinitionFilePath("newer.xml");
        try {
            request.prepare(definition("candidate", "MUT2"));
            fail("Captured SSM protocol must be required");
        } catch (com.romraider.logger.ecu.exception.ConfigurationException expected) { }
        assertEquals("newer.xml", settings.getLoggerDefinitionFilePath());
        assertEquals("SSM", settings.getLoggerProtocol());
        assertFalse(Files.exists(directory));
        assertEquals(0, saves.get());
    }

    @Test public void saveFailureRestoresOwnFileAndPathBeforeReturning() throws Exception {
        LoggerDefinitionInstaller.Installation original = new LoggerDefinitionInstaller().install(
                definition("original", "SSM"), directory);
        byte[] before = Files.readAllBytes(original.installedFile());
        DesktopDefinitionInstall request = request();
        DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", "SSM"));
        save = () -> { if (saves.incrementAndGet() == 1) throw new IllegalStateException("disk failure"); };
        edt(() -> {
            try { request.commit(candidate); fail("Save failure must propagate"); }
            catch (IllegalStateException expected) { assertEquals("disk failure", expected.getMessage()); }
            assertEquals("old-definition.xml", settings.getLoggerDefinitionFilePath());
            assertArrayEquals(before, Files.readAllBytes(original.installedFile()));
            assertFalse(request.canActivate(candidate));
            assertEquals(2, saves.get());
            return null;
        });
    }

    @Test public void committedRequestNeverRollsBackAfterNewerCatalogTakesOver() throws Exception {
        DesktopDefinitionInstall request = request();
        DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", "SSM"));
        edt(() -> {
            LoggerDefinitionInstaller.Installation installed = request.commit(candidate);
            owner.beginReload(owner.snapshot());
            assertFalse(request.canActivate(candidate));
            assertNull(request.commit(candidate));
            assertTrue(Files.exists(installed.installedFile()));
            assertEquals(installed.installedFile().toString(), settings.getLoggerDefinitionFilePath());
            assertEquals(1, saves.get());
            return null;
        });
    }

    @Test public void settingsChangedDuringActivationStopRemainingStagesWithoutRollback() throws Exception {
        DesktopDefinitionInstall request = request();
        DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", "SSM"));
        edt(() -> {
            LoggerDefinitionInstaller.Installation installed = request.commit(candidate);
            settings.setTargetModule("ECU"); // Module button construction normalizes casing.
            assertTrue(request.canActivate(candidate));
            assertFalse(request.reload.runWhile(() -> request.canActivate(candidate),
                    () -> settings.setLoggerDefinitionFilePath("newer.xml"),
                    () -> fail("Stale catalog must not be published")));
            assertEquals("newer.xml", settings.getLoggerDefinitionFilePath());
            assertTrue(Files.exists(installed.installedFile()));
            assertEquals(1, saves.get());
            return null;
        });
    }

    @Test public void failedFirstInstallationRemovesItsFileAndRetainsRecoveryError() throws Exception {
        DesktopDefinitionInstall request = request();
        DesktopDefinitionInstall.Candidate candidate = request.prepare(definition("candidate", "SSM"));
        save = () -> { saves.incrementAndGet(); throw new IllegalStateException("disk failure"); };
        edt(() -> {
            try { request.commit(candidate); fail("Save failure must propagate"); }
            catch (IllegalStateException expected) { assertEquals(1, expected.getSuppressed().length); }
            assertFalse(Files.exists(directory.resolve("definitions/logger/logger.xml")));
            assertEquals("old-definition.xml", settings.getLoggerDefinitionFilePath());
            assertEquals(2, saves.get());
            assertFalse(request.canActivate(candidate));
            return null;
        });
    }

    @Test public void candidateCannotBeCommittedByAnotherRequest() throws Exception {
        DesktopDefinitionInstall first = request();
        DesktopDefinitionInstall.Candidate candidate = first.prepare(definition("candidate", "SSM"));
        DesktopDefinitionInstall second = request();
        edt(() -> {
            try { second.commit(candidate); fail("Wrong request accepted"); }
            catch (IllegalArgumentException expected) { }
            assertFalse(Files.exists(directory));
            return null;
        });
    }

    private Path definition(String version, String protocol) throws Exception {
        Path source = temporary.getRoot().toPath().resolve(version + ".xml");
        Files.writeString(source, "<?xml version=\"1.0\"?><!DOCTYPE logger SYSTEM \"logger.dtd\">"
                + "<logger version=\"" + version + "\"><protocols><protocol id=\"" + protocol
                + "\" baud=\"4800\" databits=\"8\" stopbits=\"1\" parity=\"0\" connect_timeout=\"2000\" send_timeout=\"55\">"
                + "<transports><transport id=\"iso9141\" name=\"K-Line\" desc=\"Test\">"
                + "<module id=\"ecu\" address=\"0x10\" desc=\"Engine\"/></transport></transports>"
                + "</protocol></protocols></logger>");
        return source;
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
