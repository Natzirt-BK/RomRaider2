/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import com.romraider.portable.PortableRomDocument;
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

    private Path recoveryPath() { return directory.toPath().resolve("unsaved-rom.workspace"); }
}
