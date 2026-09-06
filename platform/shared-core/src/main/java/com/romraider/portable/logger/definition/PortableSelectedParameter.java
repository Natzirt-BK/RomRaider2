/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import com.romraider.portable.logger.PortableExpression;

/** An immutable concrete read or calculated dependency graph node. */
public final class PortableSelectedParameter {
    private final PortableLoggerParameter parameter;
    private final PortableLoggerConversion conversion;
    private final int[] addresses;
    private final Map<String, PortableSelectedParameter> inputs;
    private final PortableExpression calculation;

    public PortableSelectedParameter(PortableLoggerParameter parameter,
            PortableLoggerConversion conversion, int[] addresses) {
        this(parameter, conversion, addresses, Collections.emptyMap(), null);
    }

    private PortableSelectedParameter(PortableLoggerParameter parameter,
            PortableLoggerConversion conversion, int[] addresses,
            Map<String, PortableSelectedParameter> inputs, PortableExpression calculation) {
        if (parameter == null || conversion == null || addresses == null
                || (addresses.length == 0 && calculation == null)) {
            throw new IllegalArgumentException(
                    "Resolved parameter, conversion, and addresses are required");
        }
        this.parameter = parameter;
        this.conversion = conversion;
        this.addresses = Arrays.copyOf(addresses, addresses.length);
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        this.calculation = calculation;
    }

    public static PortableSelectedParameter calculated(PortableLoggerParameter parameter,
            PortableLoggerConversion conversion, Map<String, PortableSelectedParameter> inputs,
            PortableExpression expression) {
        if (expression == null || inputs == null || inputs.isEmpty()
                || inputs.containsValue(null) || !inputs.keySet().containsAll(expression.getVariables())) {
            throw new IllegalArgumentException("Complete calculated dependencies are required");
        }
        return new PortableSelectedParameter(parameter, conversion, new int[0], inputs, expression);
    }

    public boolean isCalculated() { return calculation != null; }
    public Map<String, PortableSelectedParameter> getInputs() { return inputs; }
    public PortableExpression getCalculation() { return calculation; }

    public PortableLoggerParameter getParameter() { return parameter; }
    public PortableLoggerConversion getConversion() { return conversion; }
    public int[] getAddresses() {
        return Arrays.copyOf(addresses, addresses.length);
    }

    public static int[] expand(List<PortableLoggerAddress> ranges) {
        List<Integer> values = new ArrayList<Integer>();
        for (PortableLoggerAddress range : ranges) {
            for (int address : range.expand()) values.add(address);
        }
        int[] expanded = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            expanded[index] = values.get(index);
        }
        return expanded;
    }
}
