/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.PortableLogSession;
import com.romraider.portable.logger.*;
import com.romraider.portable.logger.definition.*;
import com.romraider.portable.logger.dimemod.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class DimeModLoggerSessionTest {
    static PortableLoggerDefinition base() {
        return new PortableLoggerDefinition("test", "SSM", List.of(new PortableLoggerParameter(
                "P8", "RPM", "", 1, Map.of("", List.of(new PortableLoggerAddress(14, 2))), List.of(),
                List.of(new PortableLoggerConversion("rpm", "x/4", "0", "uint16", "big")))));
    }
    static PortableLoggerProfile profile() {
        return new PortableLoggerProfile("SSM", List.of(new PortableLoggerProfile.Selection("DM911", "%")), List.of());
    }

    @Test public void actualHandshakeChannelsDecodeAndSaveRomRaiderCsv() throws Exception {
        Harness h = new Harness(DimeModDiscovery.Mode.DISCOVER_AND_LOG);
        h.session.run();
        assertEquals(1, h.discoveries); assertEquals(1, h.polls); assertEquals(1, h.closes);
        assertEquals(72.5, h.values.get(0).getValue(), 0);
        assertNotNull(h.catalog.parameter("DM911"));
        assertNull(h.base.parameter("DM911"));
        StringWriter csv = new StringWriter(); h.log.writeRomRaiderCsv(csv);
        assertTrue(csv.toString().startsWith("Time (msec),DimeMod: FlexFuel Ethanol Content (%)\n"));
        assertTrue(csv.toString().contains("72.5"));
        assertEquals(2, csv.toString().split("\n").length);
    }

    @Test public void discoveryOnlyPublishesChannelsWithoutAnyLoggingReads() {
        Harness h = new Harness(DimeModDiscovery.Mode.DISCOVER_ONLY);
        h.session.run();
        assertEquals(1, h.discoveries); assertEquals(0, h.polls); assertEquals(0, h.log.size());
        assertNotNull(h.catalog.parameter("DM911")); assertTrue(h.message.contains("channels discovered"));
    }

    @Test public void offNeverInvokesDiscoveryAndCannotUseDynamicChannels() {
        Harness h = new Harness(DimeModDiscovery.Mode.OFF);
        h.session.run();
        assertEquals(0, h.discoveries); assertEquals(0, h.polls);
        assertNull(h.catalog.parameter("DM911"));
        assertTrue(h.message.contains("no parameters"));
    }

    @Test public void metadataIsEcuBoundAndConflictsNeverReplaceDefinitionChannels() throws Exception {
        PortableDimeModMetadata metadata = DimeModDiscovery.discover(new DimeModDiscoveryTest.Wire(), () -> false);
        PortableLoggerDefinition catalog = DimeModDiscovery.merge(base(), "ECU_A", metadata);
        assertEquals(1, PortableLoggerSelectionService.resolve(catalog, profile(), "ECU_A", 1).ready().size());
        assertTrue(PortableLoggerSelectionService.resolve(catalog, profile(), "ECU_B", 1).ready().isEmpty());
        assertTrue(PortableLoggerSelectionService.resolve(catalog, profile(), null, 1).ready().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> DimeModDiscovery.merge(catalog, "ECU_A", metadata));
    }

    @Test public void newSessionUsesNewMetadataEvenWhenEcuIdIsUnchanged() {
        Harness first = new Harness(DimeModDiscovery.Mode.DISCOVER_AND_LOG); first.session.run();
        Harness second = new Harness(DimeModDiscovery.Mode.DISCOVER_AND_LOG);
        second.ethanolAddress = 0x6000;
        second.session.run();
        assertEquals(1, second.polls); assertEquals(1, second.discoveries);
        assertNotEquals(first.polledAddress, second.polledAddress);
    }

    @Test public void stopDuringDiscoveryNeverPublishesLateMetadata() {
        Harness h = new Harness(DimeModDiscovery.Mode.DISCOVER_ONLY); h.stopDuringDiscovery = true;
        h.session.run();
        assertNull(h.catalog); assertEquals(0, h.polls); assertEquals(1, h.closes);
    }

    @Test public void uncertainCleanupRemainsVisibleAfterStop() {
        Harness h = new Harness(DimeModDiscovery.Mode.DISCOVER_ONLY);
        h.uncertainCleanup = true;
        h.session.run();
        assertTrue(h.message.contains("exit was not confirmed"));
        assertNull(h.catalog); assertEquals(0, h.polls);
    }

    @Test public void recordingOwnerRetainsCatalogOnlyForItsOriginalDefinition() {
        Harness transport = new Harness(DimeModDiscovery.Mode.OFF);
        PortableLoggerDefinition base = base();
        ReadOnlyRecording recording = new ReadOnlyRecording(cancelled -> new ReadOnlyRecording.Resources(
                transport, new PortableLogSession(), () -> { }), base, profile(), Runnable::run,
                System::nanoTime, DimeModDiscovery.Mode.DISCOVER_ONLY);
        assertSame(base, recording.catalogFor(base));
        recording.start();
        assertEquals(ReadOnlyRecording.Phase.STOPPED, recording.snapshot().phase());
        assertNotNull(recording.catalogFor(base).parameter("DM911"));
        assertTrue(recording.discoveryStatusFor(base).contains("channels discovered"));
        PortableLoggerDefinition reloaded = base();
        assertSame(reloaded, recording.catalogFor(reloaded));
        assertEquals("", recording.discoveryStatusFor(reloaded));
        assertEquals(0, transport.polls);
    }

    static final class Harness implements ReadOnlyLoggerTransport, DimeModDiscovery.Transport, ReadOnlyLoggerSession.Listener {
        final PortableLoggerDefinition base = base();
        final PortableLogSession log = new PortableLogSession();
        final ReadOnlyLoggerSession session;
        PortableLoggerDefinition catalog;
        List<PortableLoggerValue> values;
        int discoveries, polls, closes, polledAddress, ethanolAddress = 0x20B0;
        String message;
        boolean stopDuringDiscovery, uncertainCleanup;
        Harness(DimeModDiscovery.Mode mode) {
            session = new ReadOnlyLoggerSession(this, base, profile(), log, this, mode);
        }
        public String identifyEcu(PortableLoggerProtocol protocol) { assertEquals(PortableLoggerProtocol.SSM, protocol); return "ECU_A"; }
        public PortableDimeModMetadata discoverDimeMod(BooleanSupplier stopped) throws IOException {
            discoveries++;
            if (uncertainCleanup) {
                session.stop();
                throw new DimeModDiscovery.UncertainStateException("DimeMod exit was not confirmed", null);
            }
            DimeModDiscoveryTest.Wire wire = new DimeModDiscoveryTest.Wire();
            ByteBuffer.wrap(wire.bytes).putInt(60, ethanolAddress);
            PortableDimeModMetadata result = DimeModDiscovery.discover(wire, stopped);
            if (stopDuringDiscovery) session.stop();
            return result;
        }
        public byte[] read(PortableLoggerQueryBatch batch) {
            polls++;
            assertEquals(4, batch.getAddresses().length);
            polledAddress = batch.getAddresses()[0];
            assertEquals(ethanolAddress, polledAddress);
            assertEquals(0xA8, batch.request()[4] & 255);
            return ByteBuffer.allocate(4).putFloat(72.5f).array();
        }
        public void closeReadOnlyKLine() { closes++; }
        public void onCatalog(String ecuId, PortableLoggerDefinition catalog, String status) { this.catalog = catalog; }
        public void onIdentified(String id, int ready, int unavailable) { assertEquals(1, ready); }
        public void onValues(String id, long time, List<PortableLoggerValue> values, int samples) { this.values = values; session.stop(); }
        public void onStopped(String message) { this.message = message; }
    }
}
