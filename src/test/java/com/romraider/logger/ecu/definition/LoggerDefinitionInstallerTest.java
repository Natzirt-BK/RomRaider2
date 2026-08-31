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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LoggerDefinitionInstallerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
