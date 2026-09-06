/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.logger.*;
import com.romraider.portable.logger.definition.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic definitions/bytes only; never an adapter or vehicle session. */
public final class PortableCalculatedChannelCheck {
    private static int assertions;
    private static final String XML = "<logger version='370'><protocol id='SSM'>"
            + leaf("P8", "RPM", "0x0e", 2, conversion("rpm", "x/4") + conversion("krpm", "x/4000"))
            + leaf("P12", "MAF", "0x13", 2, conversion("g/s", "x/100") + conversion("lb/min", "x/755.987"))
            + leaf("P21", "Pulse", "0x20", 1, conversion("µs", "x*256") + conversion("ms", "x*256/1000"))
            + derived("P200", "g/rev", "(P12*60)/P8", "P12", "P8")
            + derived("P201", "%", "(P8*[P21:ms])/1200", "P8", "P21")
            + derived("Nested", "combined", "P200+P201*0.01", "P200", "P201")
            + derived("Ratio", "ratio", "[P21:µs]/[P21:ms]", "P21") + "</protocol></logger>";

    public static void main(String[] args) throws Exception {
        expressions(); outputsAndFreshness(); defaultConversionOrder(); invalidFloatInputs(); invalidGraphs(); mut2();
        System.out.println("Portable calculated channel checks passed: " + assertions);
    }

    private static void expressions() {
        PortableExpression expression = PortableExpression.compile("P8*[P21: ms]/1200", Set.of("P8", "P21"));
        equal(6.4, expression.evaluate(Map.of("P8", 3000.0, "[P21:ms]", 2.56)), "Named/unit-bound arithmetic");
        require(expression.getVariables().equals(Set.of("P8", "[P21:ms]")), "Exact normalized reference identities");
        require(Double.isNaN(expression.evaluate(Map.of("P8", 3000.0))), "Missing input is unavailable");
        require(Double.isNaN(expression.evaluate(Map.of("P8", Double.NaN, "[P21:ms]", 2.56))), "Invalid input is unavailable");
        rejects(() -> expression.evaluate(3));
        for (String invalid : List.of("P9+1", "[P9:ms]", "[P21]", "[P21:]", "[P21:ms", "exec(P8,1,2)")) {
            rejects(() -> PortableExpression.compile(invalid, Set.of("P8", "P21")));
        }
        rejects(() -> PortableExpression.compile("(".repeat(65) + "x" + ")".repeat(65)));
        rejects(() -> PortableExpression.compile("!".repeat(65) + "x"));
        rejects(() -> PortableExpression.compile("x+".repeat(300) + "x"));
        rejects(() -> PortableExpression.compile(" ".repeat(4096) + "x"));
        for (String invalid : List.of("if(x/0,1,0)", "(x/0)>0", "!BitWise(1,x/0,1)", "x*1e999")) {
            require(Double.isNaN(PortableExpression.compile(invalid).evaluate(1)), "Invalid intermediate cannot become a flag");
        }
        equal(0, PortableExpression.compile("if(x==0,0,1/x)").evaluate(0), "Explicit definition guard remains lazy");
    }

    private static void outputsAndFreshness() throws Exception {
        PortableLoggerDefinition definition = read(XML);
        PortableLoggerProfile profile = profile("SSM", "P201", "%", "P200", "g/rev", "P8", "krpm", "P12", "lb/min", "Nested", "combined", "Ratio", "ratio");
        PortableLoggerSelection selection = PortableLoggerSelectionService.resolve(definition, profile, "TEST", 1);
        require(selection.unavailable().isEmpty() && selection.ready().size() == 6, "Derived roots resolve with hidden leaves");
        PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready());
        require(plan.batches().size() == 1 && plan.batches().get(0).getAddresses().length == 5, "Hidden inputs share exactly five concrete bytes");
        List<PortableLoggerValue> values = plan.decode(response(plan, false));
        equal(6.4, values.get(0).getValue(), "P201 explicit milliseconds ignores default microseconds");
        equal(2, values.get(1).getValue(), "P200 default dependency units ignore display-unit choices");
        equal(3, values.get(2).getValue(), "Requested RPM output uses krpm");
        equal(10000 / 755.987, values.get(3).getValue(), "Requested mass-flow output keeps selected units");
        equal(2.064, values.get(4).getValue(), "Nested calculations use converted dependency values");
        equal(1000, values.get(5).getValue(), "Two units of one dependency share bytes, not converted values");
        require(values.stream().noneMatch(v -> v.getSelection().getParameter().getId().equals("P21")), "Hidden pulse input is not an output column");
        List<PortableLoggerValue> zeroRpm = plan.decode(response(plan, true));
        require(Double.isNaN(zeroRpm.get(1).getValue()) && Double.isNaN(zeroRpm.get(4).getValue()), "Zero denominator propagates unavailable through nested calculations");
        equal(0, zeroRpm.get(0).getValue(), "A real zero multiplication stays zero");
        rejects(() -> plan.decode(Collections.emptyList()));
        equal(2, plan.decode(response(plan, false)).get(1).getValue(), "Recovery uses fresh bytes, not previous-cycle values");
        PortableLoggerCycle cycle = new PortableLoggerCycle(plan);
        int[] reads = {0};
        equal(6.4, cycle.read(batch -> { reads[0]++; return response(plan, false).get(0); }).get(0).getValue(), "Cycle evaluates complete dependency plan");
        require(reads[0] == 1, "Only deduplicated concrete reads reach transport");
    }

    private static void defaultConversionOrder() throws Exception {
        String xml = "<logger><protocol id='SSM'>"
                + leaf("Input", "Input", "0x10", 1, conversion("scaled", "x*2") + conversion("raw", "x"))
                + derived("Result", "scaled", "Input+1", "Input") + "</protocol></logger>";
        PortableLoggerSelection selection = PortableLoggerSelectionService.resolve(
                read(xml), profile("SSM", "Result", "scaled"), "TEST", 1);
        PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready());
        equal(7, plan.decode(Collections.singletonList(new byte[] {3})).get(0).getValue(),
                "Bare dependency chooses first conversion, not an unscaled alternative");
        rejects(() -> new PortableLoggerConversion("", "x", "0.00", "", ""));
    }

    private static void invalidFloatInputs() throws Exception {
        String xml = "<logger><protocol id='SSM'>"
                + leaf("Float", "Float", "0x10", 4,
                        "<conversion units='raw' expr='x' storagetype='float' endian='big' format='0.00'/>")
                + derived("Result", "raw", "Float*2", "Float") + "</protocol></logger>";
        PortableLoggerSelection selection = PortableLoggerSelectionService.resolve(
                read(xml), profile("SSM", "Result", "raw"), "TEST", 1);
        require(selection.unavailable().isEmpty(), "Float dependency resolves");
        PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready());
        for (float input : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            byte[] bytes = java.nio.ByteBuffer.allocate(4).putFloat(input).array();
            require(Double.isNaN(plan.decode(Collections.singletonList(bytes)).get(0).getValue()),
                    "Nonfinite raw float propagates unavailable to calculated output");
        }
        equal(3, plan.decode(Collections.singletonList(java.nio.ByteBuffer.allocate(4).putFloat(1.5f).array()))
                .get(0).getValue(), "Calculated float recovers from fresh finite bytes");
    }

    private static void invalidGraphs() throws Exception {
        String base = leaf("Good", "Good", "1", 1, conversion("raw", "x"));
        String[] invalid = {
                derived("Bad", "raw", "Missing+1", "Missing"),
                derived("Bad", "raw", "Other+1", "Good"),
                derived("Bad", "raw", "[Good:unknown]", "Good"),
                derived("Bad", "raw", "Bad+1", "Bad"),
                derived("Bad", "raw", "Cycle+1", "Cycle") + derived("Cycle", "raw", "Bad+1", "Bad"),
                "<parameter id='Bad' name='Mixed'><address>2</address><depends><ref parameter='Good'/></depends><conversions>" + conversion("raw", "Good") + "</conversions></parameter>",
                derived("Bad", "raw", "Hidden", "Hidden") + "<parameter id='Hidden' name='Wrong module' target='2'><address>2</address><conversions>" + conversion("raw", "x") + "</conversions></parameter>",
                derived("Bad", "raw", "Hidden", "Hidden") + "<parameter id='Hidden' name='Wrong ECU'><ecu id='OTHER'><address>2</address></ecu><conversions>" + conversion("raw", "x") + "</conversions></parameter>"
        };
        for (String extra : invalid) {
            PortableLoggerDefinition definition = read("<logger><protocol id='SSM'>" + base + extra + "</protocol></logger>");
            PortableLoggerSelection result = PortableLoggerSelectionService.resolve(definition, profile("SSM", "Bad", "raw", "Good", "raw"), "TEST", 1);
            require(result.ready().size() == 1 && result.ready().get(0).getParameter().getId().equals("Good") && result.unavailable().size() == 1,
                    "Invalid dependency reports unavailable without replacing a valid output");
        }
        StringBuilder deep = new StringBuilder(base);
        for (int i = 0; i < 35; i++) deep.append(derived("D" + i, "raw", i == 34 ? "Good" : "D" + (i + 1), i == 34 ? "Good" : "D" + (i + 1)));
        PortableLoggerSelection tooDeep = PortableLoggerSelectionService.resolve(read("<logger><protocol id='SSM'>" + deep + "</protocol></logger>"), profile("SSM", "D0", "raw"), "TEST");
        require(tooDeep.ready().isEmpty() && tooDeep.unavailable().get(0).contains("depth"), "Deep graph fails boundedly");
        for (String expression : List.of("x", "unknown(x,1,2)")) {
            String leaf = leaf("Bad", "Bad", "2", 1, conversion("raw", expression).replace("format='0.00'", "format='0.00' storagetype='garbage'"));
            PortableLoggerSelection result = PortableLoggerSelectionService.resolve(read("<logger><protocol id='SSM'>" + leaf + "</protocol></logger>"), profile("SSM", "Bad", "raw"), "TEST");
            require(result.ready().isEmpty(), "Unsupported conversion fails before reading");
        }
    }

    private static void mut2() throws Exception {
        PortableLoggerDefinition definition = PortableLoggerDefinitionReader.read(new ByteArrayInputStream(("<logger><protocol id='MUT2'>"
                + leaf("RPM", "RPM", "33", 1, conversion("rpm", "x*31.25"))
                + derived("DoubleRPM", "rpm", "RPM*2", "RPM") + "</protocol></logger>").getBytes(StandardCharsets.UTF_8)), "MUT2");
        PortableLoggerSelection selection = PortableLoggerSelectionService.resolve(definition, profile("MUT2", "DoubleRPM", "rpm"), "MUT2_GENERIC", 1);
        PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready(), PortableLoggerProtocol.MUT2);
        require(plan.batches().size() == 1 && plan.batches().get(0).getAddresses()[0] == 33, "MUT2 reads only the required PID");
        equal(5000, plan.decode(Collections.singletonList(new byte[] {80})).get(0).getValue(), "MUT2 calculation uses converted PID response");
    }

    private static List<byte[]> response(PortableLoggerQueryPlan plan, boolean zeroRpm) {
        List<byte[]> values = new ArrayList<>();
        for (PortableLoggerQueryBatch batch : plan.batches()) {
            int[] addresses = batch.getAddresses(); byte[] bytes = new byte[addresses.length];
            for (int i = 0; i < addresses.length; i++) {
                switch (addresses[i]) {
                    case 14: bytes[i] = zeroRpm ? 0 : (byte) 0x2e; break;
                    case 15: bytes[i] = zeroRpm ? 0 : (byte) 0xe0; break;
                    case 19: bytes[i] = 0x27; break;
                    case 20: bytes[i] = 0x10; break;
                    case 32: bytes[i] = 10; break;
                    default: throw new AssertionError("Unexpected synthetic address");
                }
            }
            values.add(bytes);
        }
        return values;
    }
    private static PortableLoggerDefinition read(String xml) throws Exception {
        return PortableLoggerDefinitionReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "SSM");
    }
    private static PortableLoggerProfile profile(String protocol, String... choices) {
        List<PortableLoggerProfile.Selection> selections = new ArrayList<>();
        for (int i = 0; i < choices.length; i += 2) selections.add(new PortableLoggerProfile.Selection(choices[i], choices[i + 1]));
        return new PortableLoggerProfile(protocol, selections, Collections.emptyList());
    }
    private static String conversion(String units, String expression) { return "<conversion units='" + units + "' expr='" + expression + "' format='0.00'/>"; }
    private static String leaf(String id, String name, String address, int length, String conversions) {
        return "<parameter id='" + id + "' name='" + name + "'><address length='" + length + "'>" + address + "</address><conversions>" + conversions + "</conversions></parameter>";
    }
    private static String derived(String id, String units, String expression, String... dependencies) {
        StringBuilder value = new StringBuilder("<parameter id='" + id + "' name='" + id + "'><depends>");
        for (String dependency : dependencies) value.append("<ref parameter='").append(dependency).append("'/>");
        return value + "</depends><conversions>" + conversion(units, expression) + "</conversions></parameter>";
    }
    private static void require(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void equal(double expected, double actual, String message) { require(Math.abs(expected - actual) < 1e-9, message + ": " + actual); }
    private static void rejects(Runnable action) { assertions++; try { action.run(); throw new AssertionError("Invalid input accepted"); } catch (IllegalArgumentException | IllegalStateException expected) { } }
}
