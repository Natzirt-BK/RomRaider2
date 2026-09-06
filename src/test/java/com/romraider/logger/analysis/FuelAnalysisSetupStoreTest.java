/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FuelAnalysisSetupStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final FuelAnalysisSetupStore store = new FuelAnalysisSetupStore();
    private Path file(String name) { return temporary.getRoot().toPath().resolve(name); }
    private FuelAnalysisSetup.Channel channel(String label, String units) { return new FuelAnalysisSetup.Channel(label, units); }
    private FuelAnalysisSetup setup(boolean maf) {
        return new FuelAnalysisSetup(maf ? FuelAnalysisSetup.Kind.MAF : FuelAnalysisSetup.Kind.INJECTOR,
                channel(maf ? "Débit (V)" : "Pulse (ms)", maf ? "V" : "ms"),
                channel(maf ? "Learning (%)" : "Load (g/rev)", maf ? "%" : "g/rev"),
                maf ? channel("Correction (%)", "%") : null, .05, 14.7, 732,
                List.of(new FuelAnalysisSetup.Filter(channel("State", ""), 8, 8)));
    }
    private interface Action { void run() throws Exception; }
    private void rejects(Action action) throws Exception {
        try { action.run(); fail("Malformed setup was accepted"); }
        catch (IOException | IllegalArgumentException expected) { }
    }

    @Test public void roundTripsBothKindsAndOnlyPortableInputs() throws Exception {
        Path path = file("saved.rr2analysis");
        for (boolean maf : new boolean[] {true, false}) {
            FuelAnalysisSetup setup = setup(maf); store.write(path, setup);
            assertEquals(setup, store.read(path));
            String content = Files.readString(path);
            assertFalse(content.contains("source.path")); assertFalse(content.contains("sample.first"));
            assertFalse(content.contains("confirmed="));
            assertTrue(content.contains("format.version=1"));
        }
        try (var files = Files.list(temporary.getRoot().toPath())) { assertEquals(1, files.count()); }
    }

    @Test public void exactIdentityDoesNotGuessUnitsOrDuplicateHeaders() throws Exception {
        LogDataset data = new RomRaiderCsvLogParser().parse("private-not-stored.csv",
                new StringReader("Time (msec),MAF (V),MAF (V),MAF (mV),Temp (°C)\n0,1,2,3,4\n"));
        assertNull(channel("MAF (V)", "V").resolve(data));
        assertNull(channel("MAF (mV)", "V").resolve(data));
        assertNull(channel("Time (msec)", "msec").resolve(data));
        assertEquals(4, channel("Temp (°C)", "°C").resolve(data).getIndex());
    }

    @Test public void rejectsVersionUnknownDuplicateAndMissingFields() throws Exception {
        Path path = file("invalid.rr2analysis"); store.write(path, setup(true));
        String valid = Files.readString(path);
        for (String invalid : List.of(valid.replace("format.version=1", "format.version=99"),
                valid + "source.path=/private/log.csv\n", valid + "kind=INJECTOR\n",
                valid.replace("filter.count=1", "filter.count=4"), valid.replace("kind=MAF", "kind=UNKNOWN"),
                valid.replace("bin.width=0.05", "bin.width=NaN"),
                valid.replace("stoich.afr=14.7", "stoich.afr=0"),
                valid.replace("filter.0.minimum=8.0", "filter.0.minimum=9.0"),
                valid.replace("bin.width=0.05", "missing.width=0.05"))) {
            Files.writeString(path, invalid); rejects(() -> store.read(path));
        }
    }

    @Test public void boundsAndUtf8AreStrict() throws Exception {
        Path path = file("invalid.rr2analysis");
        Files.write(path, new byte[FuelAnalysisSetupStore.MAX_BYTES + 1]); rejects(() -> store.read(path));
        Files.write(path, new byte[] {(byte) 0xc3, 0x28}); rejects(() -> store.read(path));
        Files.writeString(path, "kind=\\uXYZW\n"); rejects(() -> store.read(path));
        rejects(() -> channel("x".repeat(513), "V"));
        rejects(() -> channel("Embedded\nline", "V"));
        rejects(() -> channel("Broken surrogate \ud800", "V"));
    }

    @Test public void invalidExportsPreserveExistingFilesAndRejectCsvAndDirectories() throws Exception {
        Path csv = file("capture.csv"); Files.writeString(csv, "original captured data");
        rejects(() -> store.write(csv, setup(true)));
        assertEquals("original captured data", Files.readString(csv));
        Path path = file("existing.rr2analysis"); store.write(path, setup(true)); byte[] original = Files.readAllBytes(path);
        rejects(() -> store.write(path, null)); assertArrayEquals(original, Files.readAllBytes(path));
        Path directory = file("directory.rr2analysis"); Files.createDirectory(directory);
        Files.writeString(directory.resolve("keep"), "untouched");
        rejects(() -> store.write(directory, setup(true))); rejects(() -> store.read(directory));
        assertEquals("untouched", Files.readString(directory.resolve("keep")));
    }

    @Test public void linkedSetupFilesAreNotFollowedOrReplaced() throws Exception {
        Path original = file("original.rr2analysis"), link = file("link.rr2analysis");
        store.write(original, setup(true)); byte[] bytes = Files.readAllBytes(original);
        try { Files.createSymbolicLink(link, original); }
        catch (UnsupportedOperationException | IOException failure) { org.junit.Assume.assumeNoException(failure); }
        rejects(() -> store.read(link)); rejects(() -> store.write(link, setup(false)));
        assertTrue(Files.isSymbolicLink(link)); assertArrayEquals(bytes, Files.readAllBytes(original));
    }
}
