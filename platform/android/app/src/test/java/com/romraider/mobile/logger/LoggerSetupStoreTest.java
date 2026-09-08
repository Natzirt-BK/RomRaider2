/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class LoggerSetupStoreTest {
    private File directory;
    @Before public void createDirectory() throws IOException {
        directory = Files.createTempDirectory("rr2-setup-test-").toFile();
    }
    @After public void cleanDirectory() throws IOException {
        for (File file : Objects.requireNonNull(directory.listFiles())) Files.delete(file.toPath());
        Files.delete(directory.toPath());
    }
    private LoggerSetupStore.Setup setup() {
        return new LoggerSetupStore.Setup(PortableLoggerProtocol.SSM, "définition.xml",
                "synthetic definition".getBytes(StandardCharsets.UTF_8), "Selected profile.xml",
                new PortableLoggerProfile("SSM", List.of(
                        new PortableLoggerProfile.Selection("rpm", "rpm"),
                        new PortableLoggerProfile.Selection("battery", "V")),
                        List.of("External wideband unavailable")));
    }
    @Test public void roundTripRetainsDefinitionProfileOrderUnitsAndUnsupported() throws Exception {
        LoggerSetupStore.Setup original = setup();
        LoggerSetupStore.save(directory, original);
        LoggerSetupStore.Setup restored = LoggerSetupStore.restore(directory);
        assertEquals(original.protocol, restored.protocol);
        assertEquals(original.definitionName, restored.definitionName);
        assertArrayEquals(original.definitionBytes(), restored.definitionBytes());
        assertEquals(original.profileName, restored.profileName);
        assertEquals(3, restored.profile.size());
        assertEquals("rpm", restored.profile.selections().get(0).getId());
        assertEquals("V", restored.profile.selections().get(1).getUnits());
        assertEquals(original.profile.unsupported(), restored.profile.unsupported());
    }
    @Test public void missingSnapshotIsNormalFirstLaunch() throws Exception {
        assertNull(LoggerSetupStore.restore(directory));
    }
    @Test public void emptyCustomSelectionStaysEmpty() throws Exception {
        LoggerSetupStore.save(directory, new LoggerSetupStore.Setup(PortableLoggerProtocol.SSM,
                "definition.xml", new byte[] {1}, "Custom channels",
                new PortableLoggerProfile("SSM", List.of(), List.of())));
        assertEquals(0, LoggerSetupStore.restore(directory).profile.size());
    }
    @Test public void protocolChangeReplacesEntireSavedSetup() throws Exception {
        LoggerSetupStore.save(directory, setup());
        LoggerSetupStore.save(directory, new LoggerSetupStore.Setup(PortableLoggerProtocol.MUT2,
                "", new byte[0], "", null));
        LoggerSetupStore.Setup restored = LoggerSetupStore.restore(directory);
        assertEquals(PortableLoggerProtocol.MUT2, restored.protocol);
        assertEquals(0, restored.definitionBytes().length);
        assertNull(restored.profile);
    }
    @Test public void profileBeforeDefinitionCanBeRestored() throws Exception {
        LoggerSetupStore.save(directory, new LoggerSetupStore.Setup(PortableLoggerProtocol.SSM,
                "", new byte[0], "Profile", setup().profile));
        assertEquals(3, LoggerSetupStore.restore(directory).profile.size());
    }
    @Test public void snapshotOwnsItsDefinitionBytes() {
        byte[] definition = {1};
        LoggerSetupStore.Setup snapshot = new LoggerSetupStore.Setup(PortableLoggerProtocol.SSM,
                "D", definition, "", null);
        definition[0] = 2;
        snapshot.definitionBytes()[0] = 3;
        assertEquals(1, snapshot.definitionBytes()[0]);
    }
    @Test public void failedSaveRetainsLastValidSnapshotAndCleansTemporary() throws Exception {
        LoggerSetupStore.save(directory, setup());
        try {
            LoggerSetupStore.save(directory, new LoggerSetupStore.Setup(PortableLoggerProtocol.SSM,
                    "X".repeat(70_000), new byte[0], "", null));
            fail("Oversized metadata accepted");
        } catch (IOException expected) { }
        assertEquals("Selected profile.xml", LoggerSetupStore.restore(directory).profileName);
        assertEquals(1, Objects.requireNonNull(directory.listFiles()).length);
    }
    @Test public void truncatedAndTrailingDataAreRejectedWithoutDeletingSource() throws Exception {
        LoggerSetupStore.save(directory, setup());
        Path file = new File(directory, "logger-setup.workspace").toPath();
        byte[] original = Files.readAllBytes(file);
        for (byte[] corrupt : List.of(Arrays.copyOf(original, original.length - 1),
                Arrays.copyOf(original, original.length + 1), new byte[] {0, 1, 2, 3})) {
            Files.write(file, corrupt);
            try { LoggerSetupStore.restore(directory); fail("Corrupt snapshot accepted"); }
            catch (IOException expected) { }
            assertArrayEquals(corrupt, Files.readAllBytes(file));
        }
    }
    @Test public void invalidLengthAndProtocolAreRejected() throws Exception {
        Path file = new File(directory, "logger-setup.workspace").toPath();
        for (String protocol : List.of("SSM", "invalid")) {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                out.writeInt(0x5252324c); out.writeInt(1); out.writeUTF(protocol);
                out.writeUTF("D"); out.writeInt(Integer.MAX_VALUE);
            }
            try { LoggerSetupStore.restore(directory); fail("Invalid snapshot accepted"); }
            catch (IOException expected) { }
        }
    }
    @Test public void mut2DefinitionAndRawUnitsRoundTrip() throws Exception {
        byte[] config = "; XXRR2-MUT-IIXX\ntype=mut2\nparamname=RPM\nparamid=0x21\n".getBytes(StandardCharsets.UTF_8);
        LoggerSetupStore.save(directory, new LoggerSetupStore.Setup(PortableLoggerProtocol.MUT2,
                "logcfg.txt", config, "Custom channels", new PortableLoggerProfile("MUT2",
                List.of(new PortableLoggerProfile.Selection("0x21", "raw")), List.of())));
        assertArrayEquals(config, LoggerSetupStore.restore(directory).definitionBytes());
        assertEquals("raw", LoggerSetupStore.restore(directory).profile.selections().get(0).getUnits());
    }
    @Test public void definitionCopyRejectsEmptyAndOversizedStreams() throws Exception {
        assertArrayEquals(new byte[] {1, 2}, LoggerSetupStore.readDefinition(
                new ByteArrayInputStream(new byte[] {1, 2})));
        try { LoggerSetupStore.readDefinition(new ByteArrayInputStream(new byte[0])); fail(); }
        catch (IOException expected) { }
        try {
            LoggerSetupStore.readDefinition(new InputStream() {
                public int read() { return 1; }
                public int read(byte[] b, int off, int len) { Arrays.fill(b, off, off + len, (byte) 1); return len; }
            });
            fail("Unbounded definition accepted");
        } catch (IOException expected) { }
    }
    @Test(expected = IllegalArgumentException.class)
    public void mismatchedProfileCannotBeSavedAsAnotherProtocol() {
        new LoggerSetupStore.Setup(PortableLoggerProtocol.MUT2, "", new byte[0], "", setup().profile);
    }
}
