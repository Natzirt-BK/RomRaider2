/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.romraider.logger.ecu.definition.EcuDataConvertor;
import com.romraider.logger.ecu.definition.EcuDataType;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.ui.handler.dash.GaugeMinMax;

public class LoggerLiveDataBusTest {
    @Test
    public void resetHistoryRetainsReadingsAndRecordingState() {
        LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance();
        bus.clearSamples();
        try {
            bus.loggingData();
            bus.publish(new LiveDataSample("a", "A", 1, "1", "V", 1));
            bus.publish(new LiveDataSample("a", "A", 2, "2", "V", 2));
            bus.publish(new LiveDataSample("b", "B", 3, "3", "rpm", 2));
            bus.resetHistory();
            assertEquals(LoggerSessionState.RECORDING, bus.getState());
            assertEquals(2, bus.getLatestSamples().size());
            assertEquals(1, bus.getRecentSamples().get("a").size());
            assertEquals(2.0, bus.getRecentSamples().get("a").get(0).getRawValue(), 0);
            assertEquals(1, bus.getRecentSamples().get("b").size());
            bus.publish(new LiveDataSample("a", "A", 4, "4", "V", 3));
            assertEquals(2, bus.getRecentSamples().get("a").size());
            bus.clearSamples();
            bus.resetHistory();
            assertEquals(0, bus.getRecentSamples().size());
        } finally {
            bus.clearSamples();
            bus.stopped();
        }
    }
    @Test
    public void publishesConvertedSamplesAndConnectionState() {
        LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance();
        bus.clearSamples();
        bus.stopped();
        final AtomicReference<LoggerSessionState> state =
                new AtomicReference<LoggerSessionState>();
        final AtomicReference<LiveDataSample> sample =
                new AtomicReference<LiveDataSample>();
        final AtomicInteger removed = new AtomicInteger();
        LoggerLiveDataListener listener = new LoggerLiveDataListener() {
            public void sessionStateChanged(LoggerSessionState value) {
                state.set(value);
            }
            public void sampleUpdated(LiveDataSample value) { sample.set(value); }
            public void parameterRemoved(String parameterId) { removed.incrementAndGet(); }
        };
        bus.addListener(listener);
        try {
            FakeLoggerData data = new FakeLoggerData();
            bus.connecting();
            bus.reconnecting();
            assertEquals(LoggerSessionState.RECONNECTING, state.get());
            bus.readingData();
            for (int index = 0; index < 2_060; index++) {
                bus.publish(data, 10.0 + index / 10.0);
            }

            assertEquals(LoggerSessionState.LIVE_ECU, state.get());
            assertEquals("P-BOOST", sample.get().getParameterId());
            assertEquals("215.9", sample.get().getDisplayValue());
            assertEquals("psi", sample.get().getUnits());
            assertEquals(LoggerConversionIdentity.of(data.getSelectedConvertor()),
                    sample.get().getConversionIdentity());
            assertEquals(1, bus.getLatestSamples().size());
            assertEquals(2_000,
                    bus.getRecentSamples().get("P-BOOST").size());
            bus.remove(data);
            assertEquals(1, removed.get());
            assertEquals(0, bus.getLatestSamples().size());
        } finally {
            bus.removeListener(listener);
            bus.clearSamples();
            bus.stopped();
        }
    }

    @Test
    public void failingListenerDoesNotBlockLiveDataDelivery() {
        LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance();
        bus.clearSamples();
        LoggerLiveDataListener failing = new LoggerLiveDataListener() {
            public void sessionStateChanged(LoggerSessionState state) {
                throw new IllegalStateException("test listener failure");
            }
            public void sampleUpdated(LiveDataSample sample) {
                throw new IllegalStateException("test listener failure");
            }
            public void parameterRemoved(String parameterId) {
                throw new IllegalStateException("test listener failure");
            }
        };
        AtomicReference<LiveDataSample> received =
                new AtomicReference<LiveDataSample>();
        LoggerLiveDataListener working = new LoggerLiveDataListener() {
            public void sessionStateChanged(LoggerSessionState state) { }
            public void sampleUpdated(LiveDataSample sample) {
                received.set(sample);
            }
            public void parameterRemoved(String parameterId) { }
        };
        bus.addListener(failing);
        bus.addListener(working);
        try {
            bus.publish(new FakeLoggerData(), 12.0);
            assertEquals("P-BOOST", received.get().getParameterId());
        } finally {
            bus.removeListener(failing);
            bus.removeListener(working);
            bus.clearSamples();
        }
    }

    private static final class FakeLoggerData implements LoggerData {
        private final EcuDataConvertor convertor = new EcuDataConvertor() {
            public double convert(byte[] bytes) { return 0.0; }
            public String format(double value) {
                return String.format(java.util.Locale.ROOT, "%.1f", value);
            }
            public String getUnits() { return "psi"; }
            public GaugeMinMax getGaugeMinMax() { return null; }
            public String getFormat() { return "0.0"; }
            public String getExpression() { return "x"; }
            public String getDataType() { return "float"; }
        };
        private boolean selected;
        public String getId() { return "P-BOOST"; }
        public String getName() { return "Boost Pressure"; }
        public String getDescription() { return "Test parameter"; }
        public EcuDataConvertor getSelectedConvertor() { return convertor; }
        public EcuDataConvertor[] getConvertors() {
            return new EcuDataConvertor[] {convertor};
        }
        public void selectConvertor(EcuDataConvertor value) { }
        public EcuDataType getDataType() { return null; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean value) { selected = value; }
    }
}
