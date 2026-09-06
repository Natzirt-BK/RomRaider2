/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.definition;

import com.romraider.Settings;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.comms.query.ResponseImpl;
import com.romraider.logger.ecu.ui.handler.dash.*;
import com.romraider.logger.ecu.ui.handler.livedata.LiveDataRow;
import com.romraider.logger.external.core.ExternalDataItem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.romraider.logger.ecu.definition.xml.ConverterMaxMinDefaults.getDefault;

public class InvalidLoggerReadingTest {
    private EcuParameterConvertorImpl converter(String expression, String storage, Settings.Endian endian) {
        return new EcuParameterConvertorImpl("V", expression, "0.0", -1, storage, endian,
                Collections.emptyMap(), getDefault());
    }
    private EcuParameterImpl parameter(EcuDataConvertor converter) {
        return new EcuParameterImpl("fixture", "Fixture", "Synthetic", new EcuAddressImpl("000001", 1, -1),
                null, null, null, new EcuDataConvertor[] {converter});
    }

    @Test public void badArithmeticIsMissingButRealZeroAndFormattingSurvive() {
        for (String expression : new String[] {"x/0", "0/0", "-x/0"}) {
            EcuDataConvertor converter = converter(expression, "uint8", Settings.Endian.BIG);
            assertTrue(Double.isNaN(converter.convert(new byte[] {1})));
            for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                assertEquals("", converter.format(invalid));
            }
        }
        EcuDataConvertor valid = converter("x/2", "uint8", Settings.Endian.BIG);
        assertEquals(0, valid.convert(new byte[] {0}), 0);
        assertEquals(127.5, valid.convert(new byte[] {(byte) 255}), 0);
        assertEquals("0.0", valid.format(0));
    }

    @Test public void badRawFloatsCannotBeMaskedByConstantExpressions() {
        for (Settings.Endian endian : Settings.Endian.values()) {
            for (float raw : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                byte[] bytes = ByteBuffer.allocate(4).order(endian == Settings.Endian.LITTLE
                        ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putFloat(raw).array();
                for (String expression : new String[] {"x", "42"}) {
                    assertTrue(Double.isNaN(converter(expression, "float", endian).convert(bytes)));
                }
            }
        }
    }

    @Test public void derivedConversionsRetainInvalidOperandsAndResults() {
        EcuParameterImpl source = parameter(converter("10/x", "uint8", Settings.Endian.BIG));
        for (String expression : new String[] {"[fixture:V]/0", "[fixture:V]*0+42"}) {
            EcuDerivedParameterConvertorImpl derived = new EcuDerivedParameterConvertorImpl("V", expression,
                    "0.0", Collections.emptyMap(), getDefault());
            derived.setEcuDatas(new EcuData[] {source});
            assertTrue(Double.isNaN(derived.convert(new byte[] {0})));
            assertEquals("", derived.format(Double.NaN));
            if (expression.endsWith("42")) assertEquals(42, derived.convert(new byte[] {2}), 0);
            else assertTrue(Double.isNaN(derived.convert(new byte[] {2})));
        }
    }

    @Test public void externalBadValuesAndExpressionsAreNotZero() {
        final double[] raw = {Double.NaN};
        ExternalDataItem item = new ExternalDataItem() {
            public String getName() { return "Synthetic"; }
            public String getDescription() { return "Synthetic"; }
            public double getData() { return raw[0]; }
            public EcuDataConvertor[] getConvertors() { return new EcuDataConvertor[0]; }
        };
        EcuDataConvertor constant = new ExternalDataConvertorImpl(item, "V", "42", "0.0", getDefault());
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            raw[0] = invalid;
            assertTrue(Double.isNaN(constant.convert(null)));
            assertEquals("", constant.format(invalid));
        }
        raw[0] = 1;
        assertEquals(42, constant.convert(null), 0);
        assertTrue(Double.isNaN(new ExternalDataConvertorImpl(item, "V", "x/0", "0.0",
                getDefault()).convert(null)));
    }

    @Test public void tablePeaksIgnoreGapsAndResetDoesNotFabricateZero() {
        LiveDataRow row = new LiveDataRow(parameter(converter("x", "uint8", Settings.Endian.BIG)));
        row.updateValue(Double.NaN);
        assertEquals("", row.getMinValue());
        row.updateValue(12);
        row.updateValue(Double.POSITIVE_INFINITY);
        assertEquals("", row.getCurrentValue());
        assertEquals("12.0", row.getMinValue());
        row.updateValue(0);
        assertEquals("0.0", row.getMinValue());
        assertEquals("12.0", row.getMaxValue());
        row.reset();
        assertEquals("", row.getCurrentValue());
        row.updateValue(14);
        assertEquals("14.0", row.getMaxValue());
    }

    @Test public void invalidSampleDisplayAndResponseAnalysisGateAreExplicit() {
        LoggerData data = parameter(converter("x", "uint8", Settings.Endian.BIG));
        ResponseImpl response = new ResponseImpl();
        response.setDataValue(data, 0);
        assertTrue(response.hasOnlyFiniteValues());
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            response.setDataValue(data, invalid);
            assertFalse(response.hasOnlyFiniteValues());
            assertEquals("—", new LiveDataSample("fixture", "Fixture", invalid, "0", "V", 0).getDisplayValue());
        }
        response.setDataValue(data, 12);
        assertTrue(response.hasOnlyFiniteValues());
    }

    @Test public void swingGaugeNeverPassesInvalidNumbersToLegacyStyles() throws Exception {
        final int[] updates = {0};
        GaugeStyle style = new GaugeStyle() {
            public void apply(JPanel panel) { panel.add(new JLabel("Synthetic gauge")); }
            public void refreshTitle() { }
            public void resetValue() { }
            public void updateValue(double value) { assertTrue(Double.isFinite(value)); updates[0]++; }
        };
        Gauge[] gauge = new Gauge[1];
        SwingUtilities.invokeAndWait(() -> gauge[0] = new Gauge(style));
        SwingUtilities.invokeAndWait(() -> gauge[0].updateValue(12));
        SwingUtilities.invokeAndWait(() -> assertTrue(gauge[0].getComponent(0).isVisible()));
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            gauge[0].updateValue(invalid);
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(gauge[0].getComponent(0).isVisible());
                assertEquals("NO VALID DATA", ((JLabel) gauge[0].getComponent(1)).getText());
            });
        }
        gauge[0].updateValue(0);
        SwingUtilities.invokeAndWait(() -> assertTrue(gauge[0].getComponent(0).isVisible()));
        assertEquals(2, updates[0]);
    }

    @Test public void swingGraphRetainsInvalidSamplesAsExplicitGaps() throws Exception {
        EcuParameterImpl data = parameter(converter("x", "uint8", Settings.Endian.BIG));
        com.romraider.logger.ecu.ui.handler.graph.GraphUpdateHandler[] handler =
                new com.romraider.logger.ecu.ui.handler.graph.GraphUpdateHandler[1];
        SwingUtilities.invokeAndWait(() -> {
            handler[0] = new com.romraider.logger.ecu.ui.handler.graph.GraphUpdateHandler(new JPanel());
            handler[0].registerData(data);
        });
        long timestamp = 0;
        for (double value : new double[] {12, Double.NaN, Double.POSITIVE_INFINITY, 0, 14}) {
            final long sampleTimestamp = timestamp++;
            ResponseImpl response = new ResponseImpl() {
                @Override public long getTimestamp() { return sampleTimestamp; }
            };
            response.setDataValue(data, value);
            handler[0].handleDataUpdate(response);
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.lang.reflect.Field field = handler[0].getClass().getDeclaredField("seriesMap");
                field.setAccessible(true);
                org.jfree.data.xy.XYSeries series = (org.jfree.data.xy.XYSeries)
                        ((java.util.Map<?, ?>) field.get(handler[0])).get(data);
                assertEquals(5, series.getItemCount());
                assertNull(series.getY(1));
                assertNull(series.getY(2));
                assertEquals(0, series.getY(3).doubleValue(), 0);
                assertEquals(14, series.getY(4).doubleValue(), 0);
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }
}
