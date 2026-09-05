/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.logger.*;
import com.romraider.portable.logger.definition.*;
import com.romraider.portable.openport.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic protocol checks, with no USB or vehicle access. */
public final class PortableMut2Check {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        assertions = 0;
        check(PortableLoggerProtocol.fromId("MUT-II") == PortableLoggerProtocol.MUT2, "Protocol alias");
        check(PortableLoggerProtocol.MUT2.baud() == 15625, "MUT2 baud");
        check(ascii(OpenPortWireProtocol.openKLineRequest(PortableLoggerProtocol.MUT2))
                .equals("ato3 512 15625 0\r\n"), "Read-only K-line setup");
        check(ascii(OpenPortWireProtocol.openSsmKLineRequest()).equals("ato3 512 4800 0\r\n"), "SSM unchanged");
        String[] setup = {"ats3 7 1\r\n", "ats3 10 1\r\n", "ats3 12 0\r\n",
                "ats3 3 0\r\n", "ats3 32 0\r\n", "ats3 22 0\r\n"};
        byte[][] requests = OpenPortWireProtocol.kLineConfigurationRequests();
        check(requests.length == setup.length, "Fixed settings only");
        for (int index = 0; index < setup.length; index++) check(ascii(requests[index]).equals(setup[index]), "Timing/8N1 setting");
        check(Arrays.equals(OpenPortWireProtocol.kLinePassFilterRequest(),
                bytes("atf3 1 0 1\r\n\0\0")), "Pass filter mask and pattern");
        byte[] response = bytes("\r\nari p 1.17.4877\r\n");
        for (int length = 0; length < response.length; length++) {
            check(!OpenPortWireProtocol.hasCompleteResponse(response, length, "ari "), "Fragment is not acknowledgement");
        }
        check(OpenPortWireProtocol.hasCompleteResponse(response, response.length, "ari "), "Complete acknowledgement");
        reject(() -> OpenPortWireProtocol.parseFirmwareVersion(bytes("ari p 1"), 7));
        reject(() -> PortableLoggerProtocol.fromId("CAN"));
        reject(() -> ReadOnlyMut2Protocol.request(256));
        reject(() -> ReadOnlyMut2Protocol.request(-1));
        check(Arrays.equals(ReadOnlyMut2Protocol.request(0x21), new byte[] {0x21}), "Single-byte PID request");
        check((ReadOnlyMut2Protocol.value(0x21, new byte[] {(byte) 200}) & 255) == 200, "Unsigned response");
        check(ReadOnlyMut2Protocol.value(0x21, new byte[] {0x21, 80}) == 80, "Echo plus value");
        reject(() -> ReadOnlyMut2Protocol.value(0x21, new byte[] {0x20, 80}));
        reject(() -> ReadOnlyMut2Protocol.value(0x21, new byte[] {0x21, 80, 80}));
        reject(() -> ReadOnlyMut2Protocol.value(0x21, new byte[0]));
        reject(() -> ReadOnlyMut2Protocol.probeIdentity(new byte[] {79}));
        check(ReadOnlyMut2Protocol.probeIdentity(new byte[] {(byte) 180}).equals("MUT2_GENERIC"), "Generic, not calibration identity");

        String config = "type=mut2\nparamname=RPM\nparamid=0x21\nscalingrpn=x,31.25,*\n"
                + "paramname=Battery\nparamid=0x14\nscalingrpn=x,0.0733,*\npriority=2\n";
        PortableLoggerDefinition definition = read(config);
        check(definition.size() == 2, "Config catalog");
        List<PortableLoggerProfile.Selection> choices = List.of(
                new PortableLoggerProfile.Selection("RPM", "scaled"),
                new PortableLoggerProfile.Selection("Battery", "scaled"));
        PortableLoggerProfile profile = new PortableLoggerProfile("MUT2", choices, List.of());
        PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(
                PortableLoggerSelectionService.resolve(definition, profile, "MUT2_GENERIC", 1).ready(), PortableLoggerProtocol.MUT2);
        check(plan.batches().size() == 2, "One batch per PID");
        List<PortableLoggerValue> values = new PortableLoggerCycle(plan).read(batch -> {
            check(batch.getProtocol() == PortableLoggerProtocol.MUT2 && batch.request().length == 1, "No SSM frame in MUT2");
            return new byte[] {(byte) (batch.getAddresses()[0] == 0x21 ? 80 : 180)};
        });
        check(values.get(0).getValue() == 2500.0, "RPM scaling");
        check(Math.abs(values.get(1).getValue() - 13.194) < 0.000001, "Battery scaling");
        AtomicInteger reads = new AtomicInteger();
        reject(() -> new PortableLoggerCycle(plan).read(batch -> {
            reads.incrementAndGet(); return new byte[] {80};
        }, () -> reads.get() == 1));
        check(reads.get() == 1, "Cancellation stops before next PID");
        reject(() -> new PortableLoggerCycle(plan).read(batch -> new byte[0]));
        for (String bad : new String[] {"setpinvoltage=1,0", "conditionrpn=x", "type=ssm",
                "scalingrpn=x,unknown", "paramid=0x100", "priority=0", "paramname=RPM\nparamid=0x21"}) {
            reject(() -> read(config + bad + "\n"));
        }
        reject(() -> read("paramname=RPM\nparamid=0x21"));
        reject(() -> read("type=mut2\nparamname=RPM\nparamid=0x100"));
        reject(() -> read("type=mut2\nparamname=RPM\nparamid=0x21\nscalingrpn=x,+"));
        reject(() -> read(" ".repeat(PortableMut2LogConfigReader.MAX_CONFIG_CHARS + 1)));
        PortableLoggerDefinition negative = read("type=mut2\nparamname=Temperature\nparamid=18\nscalingrpn=x,-2.7,*,597.7,+\n");
        check(Math.abs(new PortableParameterConverter(negative.parameters().get(0).getConversions().get(0))
                .convert(new byte[] {100}) - 327.7) < 0.000001, "Negative RPN constant");

        ByteArrayOutputStream packets = new ByteArrayOutputStream();
        packets.write(new byte[] {0x61, 0x72, 0x33, 1, (byte) 0xA0});
        packets.write(new byte[] {0x61, 0x72, 0x33, 2, 0x20, 0x21});
        packets.write(new byte[] {0x61, 0x72, 0x33, 1, 0x60});
        packets.write(new byte[] {0x61, 0x72, 0x33, 1, (byte) 0x80});
        packets.write(new byte[] {0x61, 0x72, 0x33, 2, 0, 80});
        packets.write(new byte[] {0x61, 0x72, 0x33, 1, 0x40});
        OpenPortKLineFrameDecoder decoder = new OpenPortKLineFrameDecoder();
        List<byte[]> frames = new ArrayList<>();
        for (byte item : packets.toByteArray()) frames.addAll(decoder.accept(new byte[] {item}));
        check(frames.size() == 1 && Arrays.equals(frames.get(0), new byte[] {80}), "Byte-fragmented receive ignores TX loopback");

        File spool = Files.createTempFile("rr2-mut2-check-", ".part").toFile();
        PortableLogSession recording = PortableLogSession.streaming(spool, 1);
        try {
            recording.append(new PortableLogSample(0, "RPM", "RPM", 2500, "rpm"));
            recording.append(new PortableLogSample(1, "RPM", "RPM", 2600, "rpm"));
            recording.finish();
            check(Files.readString(spool.toPath()).contains("2500"), "Stop flushes even a short recording");
            reject(() -> recording.append(new PortableLogSample(2, "RPM", "RPM", 1, "rpm")));
            StringWriter csv = new StringWriter();
            recording.writeLongFormCsv(csv);
            check(csv.toString().contains("2500") && csv.toString().contains("2600"), "Export retains samples outside memory ring");
        } finally { recording.discard(); }
        if (args.length == 1) {
            // Optional private local compatibility probe: never copies the input.
            try (InputStream input = new FileInputStream(args[0])) {
                PortableLoggerDefinition local = PortableMut2LogConfigReader.read(input);
                for (PortableLoggerParameter parameter : local.parameters()) {
                    PortableParameterConverter converter = new PortableParameterConverter(parameter.getConversions().get(0));
                    for (int value = 0; value <= 255; value++) check(Double.isFinite(converter.convert(new byte[] {(byte) value})), "Private config scaling");
                }
                System.out.println("Local config validated: " + local.size() + " channels (input unchanged)");
            }
        }
        System.out.println("Portable MUT2 checks passed: " + assertions);
    }
    private static PortableLoggerDefinition read(String config) throws IOException {
        return PortableMut2LogConfigReader.read(new ByteArrayInputStream(bytes(config)));
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static String ascii(byte[] bytes) { return new String(bytes, StandardCharsets.US_ASCII); }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private interface Action { void run() throws Exception; }
    private static void reject(Action action) throws Exception {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException | IOException | IllegalStateException expected) { return; }
        throw new AssertionError("Invalid input was accepted");
    }
}
