/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.editor.PortableRomChecksum;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class MobileRomSaveTest {
    private File directory;
    private PortableRomDocument document;
    private byte[] recoveryBefore;

    @Before public void setup() throws Exception {
        directory = Files.createTempDirectory("rr2-rom-save-test-").toFile();
        document = new PortableRomDocument("Synthetic ROM", new byte[] {0, 0});
        document.replace(0, new byte[] {1});
        MobileRomRecoveryStore.save(directory, document);
        recoveryBefore = Files.readAllBytes(recoveryPath());
    }

    @After public void cleanup() throws Exception {
        for (File file : Objects.requireNonNull(directory.listFiles())) Files.delete(file.toPath());
        Files.delete(directory.toPath());
    }

    @Test public void successPublishesOnlyAfterFlushAndClose() throws Exception {
        List<String> events = new ArrayList<>();
        ByteArrayOutputStream sink = new ByteArrayOutputStream() {
            @Override public void flush() {
                assertTrue(document.hasChanges());
                events.add("flush");
            }
            @Override public void close() {
                assertTrue(document.hasChanges());
                events.add("close");
            }
        };
        assertTrue(MobileRomSave.save(document, document.snapshot(), () -> sink));
        assertEquals(Arrays.asList("flush", "close"), events);
        assertArrayEquals(new byte[] {1, 0}, sink.toByteArray());
        assertFalse(document.hasChanges());
        MobileRomRecoveryStore.save(directory, document);
        assertFalse(Files.exists(recoveryPath()));
    }

    @Test public void nullDestinationRetainsDirtyStateAndRecovery() throws Exception {
        assertFailurePreservesRecovery(() -> null);
    }

    @Test public void openFailureRetainsDirtyStateAndRecovery() throws Exception {
        assertFailurePreservesRecovery(() -> { throw new IOException("open failed"); });
    }

    @Test public void writeFailureClosesAndRetainsDirtyStateAndRecovery() throws Exception {
        boolean[] closed = {false};
        assertFailurePreservesRecovery(() -> new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("write failed"); }
            @Override public void close() { closed[0] = true; }
        });
        assertTrue(closed[0]);
    }

    @Test public void flushFailureClosesAndRetainsDirtyStateAndRecovery() throws Exception {
        boolean[] closed = {false};
        assertFailurePreservesRecovery(() -> new ByteArrayOutputStream() {
            @Override public void flush() throws IOException { throw new IOException("flush failed"); }
            @Override public void close() { closed[0] = true; }
        });
        assertTrue(closed[0]);
    }

    @Test public void closeFailureRetainsDirtyStateAndRecovery() throws Exception {
        assertFailurePreservesRecovery(() -> new ByteArrayOutputStream() {
            @Override public void close() throws IOException { throw new IOException("close failed"); }
        });
    }

    @Test public void editsDuringCloseRemainDirtyAndRecoverable() throws Exception {
        assertFalse(MobileRomSave.save(document, document.snapshot(), () -> new ByteArrayOutputStream() {
            @Override public void close() { document.replace(1, new byte[] {2}); }
        }));
        assertTrue(document.hasChanges());
        MobileRomRecoveryStore.save(directory, document);
        assertArrayEquals(new byte[] {1, 2}, MobileRomRecoveryStore.restore(directory).snapshot());
    }

    @Test public void destinationCannotMutateTheCapturedSnapshot() throws Exception {
        byte[] captured = document.snapshot();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        assertTrue(MobileRomSave.save(document, captured, () -> {
            captured[0] = 9;
            return sink;
        }));
        assertArrayEquals(new byte[] {1, 0}, sink.toByteArray());
        assertArrayEquals(new byte[] {1, 0}, document.savedSnapshot());
    }

    private void assertFailurePreservesRecovery(MobileRomSave.Destination destination) throws Exception {
        try {
            MobileRomSave.save(document, document.snapshot(), destination);
            fail("Expected a save failure");
        } catch (IOException expected) { }
        assertTrue(document.hasChanges());
        assertArrayEquals(new byte[] {0, 0}, document.savedSnapshot());
        MobileRomRecoveryStore.save(directory, document);
        assertArrayEquals(recoveryBefore, Files.readAllBytes(recoveryPath()));
        assertArrayEquals(document.snapshot(), MobileRomRecoveryStore.restore(directory).snapshot());
    }

    private PortableRomChecksum checksumFixture() throws Exception {
        byte[] bytes = new byte[256];
        ByteBuffer.wrap(bytes).putInt(0, 0x54455354).putInt(64, 123)
                .putInt(128, 64).putInt(132, 68);
        document = new PortableRomDocument("Synthetic ROM", bytes);
        document.replace(64, new byte[] {1});
        String xml = "<roms><rom><romid><xmlid>TEST</xmlid><make>Subaru</make>"
                + "<internalidaddress>0</internalidaddress><internalidstring>TEST</internalidstring>"
                + "<filesize>256</filesize></romid><table name='Checksum Fix' type='Switch'"
                + " storageaddress='80' sizey='12'/><table name='Value' type='2D' sizey='1'"
                + " storageaddress='40' storagetype='uint8'><scaling expression='x' to_byte='x'/>"
                + "</table></rom></roms>";
        return PortableEcuDefinitionReader.read(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)), document).getChecksum();
    }

    @Test public void checksumCorrectionIsWrittenAndOnlyThenMarkedSaved() throws Exception {
        PortableRomChecksum plan = checksumFixture();
        byte[] snapshot = document.snapshot();
        ByteArrayOutputStream sink = new ByteArrayOutputStream() {
            @Override public void close() {
                assertArrayEquals(snapshot, document.snapshot());
                assertTrue(document.hasChanges());
            }
        };
        assertTrue(MobileRomSave.save(document, snapshot, plan, () -> sink));
        assertEquals(PortableRomChecksum.Status.VALID, plan.validate(sink.toByteArray()));
        assertArrayEquals(sink.toByteArray(), document.snapshot());
        assertFalse(document.hasChanges());
    }

    @Test public void checksumCloseFailureDoesNotPublishCorrections() throws Exception {
        PortableRomChecksum plan = checksumFixture();
        byte[] snapshot = document.snapshot();
        byte[] saved = document.savedSnapshot();
        try {
            MobileRomSave.save(document, snapshot, plan, () -> new ByteArrayOutputStream() {
                @Override public void close() throws IOException { throw new IOException("close failed"); }
            });
            fail("Expected close failure");
        } catch (IOException expected) { }
        assertArrayEquals(snapshot, document.snapshot());
        assertArrayEquals(saved, document.savedSnapshot());
        assertTrue(document.hasChanges());
    }

    @Test public void invalidChecksumDoesNotOpenDestination() throws Exception {
        PortableRomChecksum plan = checksumFixture();
        document.replace(131, new byte[] {65});
        try {
            MobileRomSave.save(document, document.snapshot(), plan, () -> {
                fail("Invalid checksum opened destination");
                return null;
            });
            fail("Invalid checksum accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void checksumSavePreservesConcurrentEdits() throws Exception {
        PortableRomChecksum plan = checksumFixture();
        ByteArrayOutputStream sink = new ByteArrayOutputStream() {
            @Override public void close() { document.replace(64, new byte[] {9}); }
        };
        assertFalse(MobileRomSave.save(document, document.snapshot(), plan, () -> sink));
        assertEquals(PortableRomChecksum.Status.VALID, plan.validate(sink.toByteArray()));
        assertEquals(9, document.byteAt(64));
        assertTrue(document.hasChanges());
    }

    private Path recoveryPath() { return directory.toPath().resolve("unsaved-rom.workspace"); }
}
