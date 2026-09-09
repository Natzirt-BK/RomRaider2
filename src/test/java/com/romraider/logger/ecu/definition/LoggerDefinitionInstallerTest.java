/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.definition;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LoggerDefinitionInstallerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void preparationDoesNotReplaceTheManagedDefinitionOrBackup() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path settings = root.resolve("settings");
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
        installer.install(writeDefinition(root.resolve("original.xml"), "original"), settings);
        LoggerDefinitionInstaller.Installation current = installer.install(
                writeDefinition(root.resolve("current.xml"), "current"), settings);
        byte[] before = Files.readAllBytes(current.installedFile());
        byte[] backup = Files.readAllBytes(current.backupFile());
        LoggerDefinitionInstaller.PreparedDefinition prepared = installer.prepare(
                writeDefinition(root.resolve("candidate.xml"), "candidate"));
        assertEquals("candidate", prepared.version());
        org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(current.installedFile()));
        org.junit.Assert.assertArrayEquals(backup, Files.readAllBytes(current.backupFile()));
        current.rollback(); // Preparing a candidate must not supersede the existing rollback owner.
        assertEquals("original", LoggerDefinitionInstaller.validate(current.installedFile()));
    }

    @Test public void preparedBytesStayFrozenWhenSourceAndReturnedSnapshotChange() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path source = writeDefinition(root.resolve("source.xml"), "prepared");
        LoggerDefinitionInstaller.PreparedDefinition prepared = new LoggerDefinitionInstaller().prepare(source);
        assertEquals(source.toRealPath(), prepared.sourcePath());
        java.util.Arrays.fill(prepared.snapshot(), (byte) 0);
        Files.delete(source);
        LoggerDefinitionInstaller.Installation installed = prepared.install(root.resolve("settings"));
        assertEquals("prepared", LoggerDefinitionInstaller.validate(installed.installedFile()));
        assertEquals("prepared", installed.version());
    }

    @Test public void preparedInstallSnapshotsTheDestinationAtCommitNotPreparation() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path settings = root.resolve("settings");
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
        installer.install(writeDefinition(root.resolve("original.xml"), "original"), settings);
        LoggerDefinitionInstaller.PreparedDefinition prepared = installer.prepare(
                writeDefinition(root.resolve("candidate.xml"), "candidate"));
        installer.install(writeDefinition(root.resolve("intervening.xml"), "intervening"), settings);
        LoggerDefinitionInstaller.Installation committed = prepared.install(settings);
        assertEquals("candidate", LoggerDefinitionInstaller.validate(committed.installedFile()));
        committed.rollback();
        assertEquals("intervening", LoggerDefinitionInstaller.validate(committed.installedFile()));
    }

    @Test public void preparedSnapshotCanBeParsedWithoutChangingLoggerSettings() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LoggerDefinitionInstaller.PreparedDefinition prepared = new LoggerDefinitionInstaller().prepare(
                writeDefinition(root.resolve("candidate.xml"), "candidate"));
        com.romraider.Settings settings = com.romraider.util.SettingsManager.getSettings();
        String protocol = settings.getLoggerProtocol();
        String transport = settings.getTransportProtocol();
        EcuDataLoaderImpl loader = new EcuDataLoaderImpl();
        loader.loadConfigForDesktop(prepared.sourcePath().toString(), prepared.snapshot(), "SSM", "S1", null);
        assertEquals("candidate", loader.getDefVersion());
        assertEquals(protocol, settings.getLoggerProtocol());
        assertEquals(transport, settings.getTransportProtocol());
    }

    @Test public void rejectedPreparedProtocolLeavesSettingsAndInstalledFileUntouched() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
        LoggerDefinitionInstaller.Installation current = installer.install(
                writeDefinition(root.resolve("current.xml"), "current"), root.resolve("settings"));
        LoggerDefinitionInstaller.PreparedDefinition candidate = installer.prepare(
                writeDefinition(root.resolve("candidate.xml"), "candidate"));
        com.romraider.Settings settings = com.romraider.util.SettingsManager.getSettings();
        String protocol = settings.getLoggerProtocol();
        String transport = settings.getTransportProtocol();
        String definitionPath = settings.getLoggerDefinitionFilePath();
        try {
            new EcuDataLoaderImpl().loadConfigForDesktop(candidate.sourcePath().toString(),
                    candidate.snapshot(), "MISSING-PROTOCOL", "S1", null);
            fail("An unsupported protocol must not fall back to a different one");
        } catch (com.romraider.logger.ecu.exception.ConfigurationException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(protocol, settings.getLoggerProtocol());
        assertEquals(transport, settings.getTransportProtocol());
        assertEquals(definitionPath, settings.getLoggerDefinitionFilePath());
        assertEquals("current", LoggerDefinitionInstaller.validate(current.installedFile()));
        current.rollback();
        assertFalse(Files.exists(current.installedFile()));
    }

    @Test public void rollbackRejectsSymlinkReplacementWithoutTouchingItsTarget() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LoggerDefinitionInstaller.Installation installed = new LoggerDefinitionInstaller().install(
                writeDefinition(root.resolve("source.xml"), "original"), root.resolve("settings"));
        Path outside = writeDefinition(root.resolve("outside.xml"), "outside");
        Files.delete(installed.installedFile());
        try {
            Files.createSymbolicLink(installed.installedFile(), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            org.junit.Assume.assumeNoException(unavailable);
        }
        rejectRollback(installed);
        assertTrue(Files.isSymbolicLink(installed.installedFile()));
        assertEquals("outside", LoggerDefinitionInstaller.validate(outside));
    }

    @Test public void obsoleteRollbackCannotReplaceOrDeleteANewerInstallation() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        for (boolean initial : new boolean[] {false, true}) {
            for (boolean identical : new boolean[] {false, true}) {
                Path settings = root.resolve("owners-" + initial + "-" + identical);
                LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
                if (initial) installer.install(writeDefinition(root.resolve("initial.xml"), "initial"), settings);
                LoggerDefinitionInstaller.Installation older = installer.install(
                        writeDefinition(root.resolve("older.xml"), "older"), settings);
                LoggerDefinitionInstaller.Installation newer = new LoggerDefinitionInstaller().install(
                        writeDefinition(root.resolve("newer.xml"), identical ? "older" : "newer"), settings);
                String expected = Files.readString(newer.installedFile(), UTF_8);
                rejectRollback(older);
                assertEquals(expected, Files.readString(newer.installedFile(), UTF_8));
                newer.rollback();
                assertEquals("older", LoggerDefinitionInstaller.validate(newer.installedFile()));
                rejectRollback(older); // Restoring equal bytes must not revive an obsolete owner.
                assertEquals("older", LoggerDefinitionInstaller.validate(newer.installedFile()));
            }
        }
    }

    @Test public void rollbackUsesItsOwnSnapshotNotTheSharedBackup() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
        installer.install(writeDefinition(root.resolve("original.xml"), "original"), root.resolve("settings"));
        LoggerDefinitionInstaller.Installation replacement = installer.install(
                writeDefinition(root.resolve("replacement.xml"), "replacement"), root.resolve("settings"));
        writeDefinition(replacement.backupFile(), "unrelated");
        replacement.rollback();
        assertEquals("original", LoggerDefinitionInstaller.validate(replacement.installedFile()));
    }

    @Test public void rollbackDoesNotOverwriteAnExternalEdit() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LoggerDefinitionInstaller.Installation installed = new LoggerDefinitionInstaller().install(
                writeDefinition(root.resolve("original.xml"), "original"), root.resolve("settings"));
        writeDefinition(installed.installedFile(), "external");
        rejectRollback(installed);
        assertEquals("external", LoggerDefinitionInstaller.validate(installed.installedFile()));
    }

    @Test public void completedRollbackCannotDeleteALaterInstallation() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
        Path source = writeDefinition(root.resolve("source.xml"), "first");
        LoggerDefinitionInstaller.Installation first = installer.install(source, root.resolve("settings"));
        first.rollback();
        LoggerDefinitionInstaller.Installation second = installer.install(source, root.resolve("settings"));
        first.rollback();
        assertEquals("first", LoggerDefinitionInstaller.validate(second.installedFile()));
    }

    private static void rejectRollback(LoggerDefinitionInstaller.Installation installation) throws Exception {
        try {
            installation.rollback();
            fail("Rollback must reject a definition it no longer owns");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test public void concurrentInstallersKeepOnlyTheLatestRollbackOwner() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path firstSource = writeDefinition(root.resolve("concurrent-first.xml"), "first");
        Path secondSource = writeDefinition(root.resolve("concurrent-second.xml"), "second");
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<LoggerDefinitionInstaller.Installation> first = workers.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return new LoggerDefinitionInstaller().install(firstSource, root.resolve("settings"));
            });
            Future<LoggerDefinitionInstaller.Installation> second = workers.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return new LoggerDefinitionInstaller().install(secondSource, root.resolve("settings"));
            });
            LoggerDefinitionInstaller.Installation a = first.get(10, TimeUnit.SECONDS);
            LoggerDefinitionInstaller.Installation b = second.get(10, TimeUnit.SECONDS);
            boolean firstWon = "first".equals(LoggerDefinitionInstaller.validate(a.installedFile()));
            LoggerDefinitionInstaller.Installation latest = firstWon ? a : b;
            LoggerDefinitionInstaller.Installation older = firstWon ? b : a;
            rejectRollback(older);
            assertEquals(latest.version(), LoggerDefinitionInstaller.validate(latest.installedFile()));
            latest.rollback();
            assertEquals(older.version(), LoggerDefinitionInstaller.validate(latest.installedFile()));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test public void identicalExternalReplacementIsRejectedWhenFileIdentityIsAvailable() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path source = writeDefinition(root.resolve("source.xml"), "same");
        LoggerDefinitionInstaller.Installation installed = new LoggerDefinitionInstaller().install(source, root.resolve("settings"));
        Object key = Files.readAttributes(installed.installedFile(), BasicFileAttributes.class).fileKey();
        org.junit.Assume.assumeNotNull(key);
        Path replacement = root.resolve("external.xml");
        Files.copy(installed.installedFile(), replacement);
        Files.move(replacement, installed.installedFile(), StandardCopyOption.REPLACE_EXISTING);
        rejectRollback(installed);
        assertEquals("same", LoggerDefinitionInstaller.validate(installed.installedFile()));
    }

    @Test public void oversizedManagedSnapshotIsRejectedBeforeReplacement() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path source = writeDefinition(root.resolve("source.xml"), "original");
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
        LoggerDefinitionInstaller.Installation installed = installer.install(source, root.resolve("settings"));
        long tooLarge = LoggerDefinitionInstaller.MAX_DEFINITION_BYTES + 1;
        try (RandomAccessFile file = new RandomAccessFile(installed.installedFile().toFile(), "rw")) {
            file.setLength(tooLarge);
        }
        try {
            installer.install(source, root.resolve("settings"));
            fail("Oversized managed recovery snapshot was accepted");
        } catch (IOException expected) {
            assertEquals(tooLarge, Files.size(installed.installedFile()));
            assertFalse(Files.exists(installed.installedFile().resolveSibling("logger.previous.xml")));
        }
    }

    @Test
    public void validatesInstallsAndBacksUpManagedDefinition()
            throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path first = writeDefinition(root.resolve("first.xml"), "370");
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();

        LoggerDefinitionInstaller.Installation initial = installer.install(
                first, root.resolve("settings"));
        assertEquals("370", initial.version());
        assertTrue(Files.isRegularFile(initial.installedFile()));
        assertTrue(Files.isRegularFile(
                initial.installedFile().resolveSibling("logger.dtd")));

        Path second = writeDefinition(root.resolve("second.xml"), "371");
        LoggerDefinitionInstaller.Installation updated = installer.install(
                second, root.resolve("settings"));
        assertEquals("371", updated.version());
        assertNotNull(updated.backupFile());
        assertTrue(Files.readString(updated.backupFile(), UTF_8)
                .contains("version=\"370\""));
        assertTrue(Files.readString(updated.installedFile(), UTF_8)
                .contains("version=\"371\""));
    }

    @Test
    public void rejectsXmlThatIsNotALoggerDefinition() throws Exception {
        Path source = temporaryFolder.getRoot().toPath()
                .resolve("not-a-logger.xml");
        Files.writeString(source,
                "<?xml version=\"1.0\"?><!DOCTYPE logger SYSTEM \"logger.dtd\">"
                + "<settings><value>not a definition</value></settings>",
                UTF_8);
        try {
            new LoggerDefinitionInstaller().install(source,
                    temporaryFolder.getRoot().toPath().resolve("settings"));
            fail("Expected invalid definition to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test
    public void rollbackRestoresThePreviousManagedDefinition() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path settings = root.resolve("rollback-settings");
        Path first = writeDefinition(root.resolve("rollback-first.xml"),
                "first");
        Path second = writeDefinition(root.resolve("rollback-second.xml"),
                "second");
        LoggerDefinitionInstaller installer = new LoggerDefinitionInstaller();
        installer.install(first, settings);

        LoggerDefinitionInstaller.Installation replacement =
                installer.install(second, settings);
        replacement.rollback();

        assertEquals("first",
                LoggerDefinitionInstaller.validate(replacement.installedFile()));
    }

    @Test
    public void rollbackRemovesAFirstManagedInstallation() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path settings = root.resolve("rollback-empty-settings");
        LoggerDefinitionInstaller.Installation installation =
                new LoggerDefinitionInstaller().install(
                        writeDefinition(root.resolve("rollback-only.xml"),
                                "only"), settings);

        installation.rollback();

        assertFalse(Files.exists(installation.installedFile()));
    }

    @Test
    public void acceptsScopedIdsRepeatedAcrossProtocols() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path source = root.resolve("scoped-ids.xml");
        String protocol = " baud=\"4800\" databits=\"8\" stopbits=\"1\" "
                + "parity=\"0\" connect_timeout=\"2000\" send_timeout=\"55\">"
                + "<transports><transport id=\"iso9141\" name=\"K-Line\" "
                + "desc=\"Test\"><module id=\"ecu\" address=\"0x10\" "
                + "desc=\"Engine\"/></transport></transports></protocol>";
        Files.writeString(source,
                "<?xml version=\"1.0\"?><!DOCTYPE logger SYSTEM \"logger.dtd\">"
                + "<logger version=\"scoped\"><protocols>"
                + "<protocol id=\"SSM\"" + protocol
                + "<protocol id=\"MUT2\"" + protocol
                + "</protocols></logger>", UTF_8);

        LoggerDefinitionInstaller.Installation installed =
                new LoggerDefinitionInstaller().install(source,
                        root.resolve("settings"));

        assertEquals("scoped", LoggerDefinitionInstaller.validate(
                installed.installedFile()));
    }

    @Test
    public void acceptsOfficialStyleEmbeddedDtdWithScopedIds()
            throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path source = root.resolve("embedded-scoped-ids.xml");
        Files.writeString(source,
                "<?xml version=\"1.0\"?><!DOCTYPE logger ["
                + "<!ELEMENT logger (protocols)>"
                + "<!ATTLIST logger version CDATA #IMPLIED>"
                + "<!ELEMENT protocols (protocol+)>"
                + "<!ELEMENT protocol (transports)>"
                + "<!ATTLIST protocol id ID #REQUIRED>"
                + "<!ATTLIST protocol baud CDATA #REQUIRED>"
                + "<!ATTLIST protocol databits CDATA #REQUIRED>"
                + "<!ATTLIST protocol stopbits CDATA #REQUIRED>"
                + "<!ATTLIST protocol parity CDATA #REQUIRED>"
                + "<!ATTLIST protocol connect_timeout CDATA #REQUIRED>"
                + "<!ATTLIST protocol send_timeout CDATA #REQUIRED>"
                + "<!ELEMENT transports (transport+)>"
                + "<!ELEMENT transport (module+)>"
                + "<!ATTLIST transport id ID #REQUIRED>"
                + "<!ATTLIST transport name CDATA #REQUIRED>"
                + "<!ATTLIST transport desc CDATA #REQUIRED>"
                + "<!ELEMENT module EMPTY>"
                + "<!ATTLIST module id ID #REQUIRED>"
                + "<!ATTLIST module address CDATA #REQUIRED>"
                + "<!ATTLIST module desc CDATA #REQUIRED>"
                + "]><logger version=\"370\"><protocols>"
                + embeddedProtocol("SSM")
                + embeddedProtocol("MUT2")
                + "</protocols></logger>", UTF_8);

        LoggerDefinitionInstaller.Installation installed =
                new LoggerDefinitionInstaller().install(source,
                        root.resolve("settings"));

        assertEquals("370", installed.version());
        String managed = Files.readString(installed.installedFile(), UTF_8);
        assertTrue(managed.contains("module id CDATA #REQUIRED"));
        assertTrue(Files.readString(source, UTF_8)
                .contains("module id ID #REQUIRED"));
    }

    private static String embeddedProtocol(String id) {
        return "<protocol id=\"" + id + "\" baud=\"4800\" databits=\"8\" "
                + "stopbits=\"1\" parity=\"0\" connect_timeout=\"2000\" "
                + "send_timeout=\"55\" p1_max=\"40\"><transports>"
                + "<transport id=\"iso9141\" name=\"K-Line\" desc=\"Test\">"
                + "<module id=\"ecu\" address=\"0x10\" desc=\"Engine\"/>"
                + "</transport></transports></protocol>";
    }

    private static Path writeDefinition(Path destination, String version)
            throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE logger SYSTEM \"logger.dtd\">\n"
                + "<logger version=\"" + version + "\"><protocols>"
                + "<protocol id=\"SSM\" baud=\"4800\" databits=\"8\" "
                + "stopbits=\"1\" parity=\"0\" connect_timeout=\"2000\" "
                + "send_timeout=\"55\"><transports>"
                + "<transport id=\"iso9141\" name=\"K-Line\" desc=\"Test\">"
                + "<module id=\"ecu\" address=\"0x10\" desc=\"Engine\"/>"
                + "</transport></transports></protocol></protocols></logger>";
        Files.writeString(destination, xml, UTF_8);
        return destination;
    }
}
