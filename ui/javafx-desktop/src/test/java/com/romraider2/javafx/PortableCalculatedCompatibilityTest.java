/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.Settings;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.ui.handler.dash.GaugeMinMax;
import com.romraider.portable.logger.*;
import com.romraider.portable.logger.definition.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/** Compare portable calculated results with the actual desktop/JEP converters. */
class PortableCalculatedCompatibilityTest {
    private PortableLoggerConversion conversion(String units, String expression) {
        return new PortableLoggerConversion(units, expression, "0.00", "uint16", "");
    }
    private PortableLoggerParameter leaf(String id, int address, PortableLoggerConversion... conversions) {
        return new PortableLoggerParameter(id, id, "Synthetic", 1,
                Map.of("", List.of(new PortableLoggerAddress(address, 2))), List.of(), List.of(conversions));
    }
    private EcuData desktop(PortableLoggerParameter parameter) {
        EcuDataConvertor[] conversions = parameter.getConversions().stream().map(c -> new EcuParameterConvertorImpl(
                c.getUnits(), c.getExpression(), c.getFormat(), -1, c.getStorageType(), Settings.Endian.BIG,
                Map.of(), new GaugeMinMax(0, 10000, 100))).toArray(EcuDataConvertor[]::new);
        return new EcuParameterImpl(parameter.getId(), parameter.getName(), "Synthetic",
                new EcuAddressImpl(String.format("0x%06X", parameter.addressesFor("").get(0).getAddress()), 2, -1),
                null, null, null, conversions);
    }

    @Test void loadAndDutyMatchDesktopAcrossSyntheticRawValuesAndExplicitUnits() throws Exception {
        PortableLoggerParameter rpm = leaf("P8", 14, conversion("rpm", "x/4"), conversion("krpm", "x/4000"));
        PortableLoggerParameter maf = leaf("P12", 19, conversion("g/s", "x/100"), conversion("lb/min", "x/755.987"));
        PortableLoggerParameter pulse = leaf("P21", 32, conversion("µs", "x*256"), conversion("ms", "x*256/1000"));
        compare("P200", "g/rev", "(P12*60)/P8", List.of(maf, rpm));
        compare("P201", "%", "(P8*[P21:ms])/1200", List.of(rpm, pulse));
    }

    @Test void explicitPressureUnitsMatchTheDesktopConverter() throws Exception {
        PortableLoggerParameter manifold = leaf("P7", 10, conversion("psi", "x/100"), conversion("bar", "x/1450.377"));
        PortableLoggerParameter atmosphere = leaf("P24", 20, conversion("psi", "x/100"), conversion("bar", "x/1450.377"));
        for (String units : List.of("psi", "bar")) compare("P202", units, "[P7:" + units + "]-[P24:" + units + "]", List.of(manifold, atmosphere));
    }

    private void compare(String id, String units, String expression, List<PortableLoggerParameter> inputs) throws Exception {
        PortableLoggerParameter derived = new PortableLoggerParameter(id, id, "Synthetic", 1, Map.of(),
                inputs.stream().map(PortableLoggerParameter::getId).toList(), List.of(conversion(units, expression)));
        List<PortableLoggerParameter> parameters = new ArrayList<>(inputs); parameters.add(derived);
        List<PortableLoggerProfile.Selection> outputs = new ArrayList<>();
        outputs.add(new PortableLoggerProfile.Selection(id, units));
        // Display each source using its alternate unit. Derived inputs still bind
        // to definition defaults or explicit [id:units], never these display units.
        for (PortableLoggerParameter input : inputs) outputs.add(new PortableLoggerProfile.Selection(input.getId(), input.getConversions().get(1).getUnits()));
        PortableLoggerSelection selection = PortableLoggerSelectionService.resolve(new PortableLoggerDefinition("test", "SSM", parameters),
                new PortableLoggerProfile("SSM", outputs, List.of()), "SYNTHETIC", 1);
        assertTrue(selection.unavailable().isEmpty());
        PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready());
        EcuDerivedParameterConvertorImpl reference = new EcuDerivedParameterConvertorImpl(units, expression, "0.00", Map.of(), new GaugeMinMax(0, 100, 1));
        reference.setEcuDatas(inputs.stream().map(this::desktop).toArray(EcuData[]::new));
        Random random = new Random(42);
        for (int sample = 0; sample < 100; sample++) {
            Map<Integer, Byte> byAddress = new HashMap<>(); ByteArrayOutputStream raw = new ByteArrayOutputStream();
            for (PortableLoggerParameter input : inputs) {
                int value = sample == 0 ? 0 : random.nextInt(65536);
                int address = input.addressesFor("").get(0).getAddress();
                byte high = (byte) (value >>> 8), low = (byte) value;
                byAddress.put(address, high); byAddress.put(address + 1, low); raw.write(high); raw.write(low);
            }
            List<byte[]> response = new ArrayList<>();
            for (PortableLoggerQueryBatch batch : plan.batches()) {
                int[] addresses = batch.getAddresses(); byte[] bytes = new byte[addresses.length];
                for (int i = 0; i < addresses.length; i++) bytes[i] = byAddress.get(addresses[i]);
                response.add(bytes);
            }
            double expected = reference.convert(raw.toByteArray()), actual = plan.decode(response).get(0).getValue();
            if (Double.isNaN(expected)) assertTrue(Double.isNaN(actual), id + " missing result");
            else assertEquals(expected, actual, Math.max(1e-9, Math.abs(expected) * 1e-12), id + " sample " + sample);
        }
    }
}
