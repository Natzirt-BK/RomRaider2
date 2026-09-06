/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.definition.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free checks shared by desktop and Android builds. */
public final class PortableLoggerSetupCheck {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        byte[] bytes = xml("SSM", "°C").getBytes(StandardCharsets.UTF_8);
        PortableLoggerDefinition definition = parse(bytes, "SSM");
        PortableLoggerProfile profile = profile("SSM", "P2", "", "P1", "°C");
        PortableLoggerSetup setup = PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, definition, profile);
        byte[] encoded = setup.encode();
        PortableLoggerSetup read = read(encoded);
        read.validateAgainst(PortableLoggerProtocol.SSM, bytes, definition);
        require(read.profile().selections().get(0).getId().equals("P2"), "Order changed");
        require(read.profile().selections().get(0).getUnits().equals("rpm"), "Default units were not explicit");
        require(read.profile().selections().get(1).getUnits().equals("°C"), "Unicode units lost");
        require(Arrays.equals(read.encode(), encoded), "Round trip was not canonical");
        String text = new String(encoded, StandardCharsets.UTF_8);
        require(!text.contains("<logger") && !text.contains("Fixture") && !text.contains("0x"), "Definition details leaked");
        require(read(text.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)).profile().size() == 2, "CRLF rejected");
        reject(() -> read.profile().selections().clear());
        reject(() -> read.validateAgainst(PortableLoggerProtocol.MUT2, bytes, definition));
        reject(() -> read.validateAgainst(PortableLoggerProtocol.SSM, Arrays.copyOf(bytes, bytes.length + 1), definition));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, definition, profile("SSM", "missing", "rpm")));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, definition, profile("SSM", "P1", "°c")));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, definition, profile("SSM", "P1", "°C", "P1", "°C")));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, definition,
                new PortableLoggerProfile("SSM", Collections.emptyList(), Collections.singletonList("external"))));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.MUT2, bytes, definition, profile));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, new byte[0], definition, profile));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, null, profile));
        String ambiguous = xml("SSM", "mV").replace("units='mV' expr='x' format='0'/>",
                "units='mV' expr='x' format='0'/><conversion units='MV' expr='x*1000' format='0'/>");
        byte[] ambiguousBytes = ambiguous.getBytes(StandardCharsets.UTF_8);
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, ambiguousBytes,
                parse(ambiguousBytes, "SSM"), profile("SSM", "P1", "mV")));
        PortableLoggerSetup empty = PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, definition, profile("SSM"));
        require(read(empty.encode()).profile().size() == 0, "Explicit empty selection was lost");
        byte[] mut = xml("MUT2", "°C").getBytes(StandardCharsets.UTF_8);
        PortableLoggerSetup mutSetup = PortableLoggerSetup.capture(PortableLoggerProtocol.MUT2, mut,
                parse(mut, "MUT2"), profile("MUT2", "P1", "°C"));
        require(read(mutSetup.encode()).protocol() == PortableLoggerProtocol.MUT2, "MUT-II protocol lost");
        List<PortableLoggerParameter> manyParameters = new ArrayList<>();
        List<PortableLoggerProfile.Selection> manyChoices = new ArrayList<>();
        for (int i = 0; i < PortableLoggerSetup.MAX_CHANNELS; i++) {
            String id = "P" + i;
            manyParameters.add(new PortableLoggerParameter(id, "Fixture " + i, "", 1,
                    Collections.emptyMap(), Collections.emptyList(), definition.parameter("P2").getConversions()));
            manyChoices.add(new PortableLoggerProfile.Selection(id, "rpm"));
        }
        PortableLoggerDefinition many = new PortableLoggerDefinition("", "SSM", manyParameters);
        PortableLoggerSetup largestSelection = PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, many,
                new PortableLoggerProfile("SSM", manyChoices, Collections.emptyList()));
        require(read(largestSelection.encode()).profile().size() == 256, "256-channel boundary did not round trip");
        manyChoices.add(new PortableLoggerProfile.Selection("P256", "rpm"));
        reject(() -> PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, bytes, many,
                new PortableLoggerProfile("SSM", manyChoices, Collections.emptyList())));
        for (String invalid : new String[] {
                text.replace("setup 1", "setup 2"), text + "unknown=1\n", text + "protocol=SSM\n",
                text.replace("channels=2", "channels=257"), text.replace("channels=2", "channels=02"),
                text.replace("protocol=SSM", "protocol=MUT-II"), text.replace("channel.0.id=UDI=", "channel.0.id=/w=="),
                text.replace("channel.0.id=UDI=", "channel.0.id=UDI"),
                text.replace("channel.0.id=UDI=", "channel.0.id=UDE="),
                text.replace("channel.0.id=UDI=", "channel.0.id=IA=="),
                text.replace("channel.0.id=UDI=", "channel.0.id=UAo="),
                text.substring(0, text.length() - 1), text.replace("channels=2\n", "")}) {
            require(!invalid.equals(text), "Invalid fixture did not change");
            reject(() -> read(invalid.getBytes(StandardCharsets.UTF_8)));
        }
        reject(() -> read(new byte[] {(byte) 0xff}));
        reject(() -> PortableLoggerSetup.read(null));
        reject(() -> read(new byte[PortableLoggerSetup.MAX_BYTES + 1]));
        InputStream endless = new InputStream() {
            int count;
            @Override public int read() {
                if (++count > PortableLoggerSetup.MAX_BYTES + 8192) throw new AssertionError("Read beyond bounded input");
                return 'a';
            }
        };
        reject(() -> PortableLoggerSetup.read(endless));
        Thread.currentThread().interrupt();
        try { reject(() -> read(encoded)); }
        finally { Thread.interrupted(); }
        require(profile.selections().get(0).getUnits().isEmpty(), "Export mutated original profile");
        ByteArrayInputStream owned = new ByteArrayInputStream(encoded) {
            @Override public void close() { throw new AssertionError("Reader closed caller's stream"); }
        };
        require(PortableLoggerSetup.read(owned).profile().size() == 2, "Caller-owned stream did not read");
        System.out.println("Portable logger setup checks passed (" + assertions + " assertions)");
    }
    private static PortableLoggerSetup read(byte[] bytes) throws IOException {
        return PortableLoggerSetup.read(new ByteArrayInputStream(bytes));
    }
    private static PortableLoggerDefinition parse(byte[] bytes, String protocol) throws IOException {
        return PortableLoggerDefinitionReader.read(new ByteArrayInputStream(bytes), protocol);
    }
    private static PortableLoggerProfile profile(String protocol, String... choices) {
        List<PortableLoggerProfile.Selection> selections = new ArrayList<>();
        for (int i = 0; i < choices.length; i += 2) selections.add(new PortableLoggerProfile.Selection(choices[i], choices[i + 1]));
        return new PortableLoggerProfile(protocol, selections, Collections.emptyList());
    }
    private static String xml(String protocol, String units) {
        return "<logger><protocols><protocol id='" + protocol + "'><parameters>"
                + "<parameter id='P1' name='Fixture' target='1'><address>0x01</address><conversions>"
                + "<conversion units='" + units + "' expr='x' format='0'/></conversions></parameter>"
                + "<parameter id='P2' name='Other' target='1'><address>0x02</address><conversions>"
                + "<conversion units='rpm' expr='x' format='0'/></conversions></parameter>"
                + "</parameters></protocol></protocols></logger>";
    }
    private interface Action { void run() throws Exception; }
    private static void reject(Action action) throws Exception {
        try { action.run(); }
        catch (IOException | IllegalArgumentException | UnsupportedOperationException expected) { assertions++; return; }
        throw new AssertionError("Invalid setup was accepted");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
    }
}
