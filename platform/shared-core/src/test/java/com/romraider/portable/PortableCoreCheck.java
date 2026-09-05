/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.romraider.portable.openport.OpenPortWireProtocol;
import com.romraider.portable.openport.OpenPortKLineFrameDecoder;
import com.romraider.portable.editor.PortableEcuDefinition;
import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.editor.PortableRomTable;
import com.romraider.portable.logger.PortableExpression;
import com.romraider.portable.logger.PortableLoggerCycle;
import com.romraider.portable.logger.PortableLoggerQueryBatch;
import com.romraider.portable.logger.PortableLoggerQueryPlan;
import com.romraider.portable.logger.PortableLoggerValue;
import com.romraider.portable.logger.PortableParameterConverter;
import com.romraider.portable.logger.ReadOnlySsmProtocol;
import com.romraider.portable.logger.definition.PortableLoggerConversion;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import com.romraider.portable.logger.definition.PortableLoggerProfileReader;
import com.romraider.portable.logger.definition.PortableLoggerSelection;
import com.romraider.portable.logger.definition.PortableLoggerSelectionService;

public final class PortableCoreCheck {
    public static void main(String[] args) throws Exception {
        PortableMut2Check.main(new String[0]);
        PortableRomDocument rom = new PortableRomDocument("sample.bin",
                new byte[] {1, 2, 3, 4});
        rom.replace(1, new byte[] {8, 9});
        require(rom.hasChanges(), "ROM edit was not retained");
        require(rom.changes().size() == 1, "ROM changes were not grouped");
        require(rom.changes().get(0).getOffset() == 1,
                "ROM change offset is wrong");
        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        rom.write(binary);
        require(Arrays.equals(new byte[] {1, 8, 9, 4},
                binary.toByteArray()), "ROM output is wrong");
        byte[] written = rom.snapshot();
        rom.replace(0, new byte[] {7});
        require(!rom.markSavedIfCurrent(written),
                "An asynchronous save hid a newer ROM edit");
        require(rom.hasChanges(), "The newer ROM edit was not left dirty");

        byte[] calibrationBytes = new byte[] {'T', 'E', 'S', 'T',
                0, 100, 0, (byte) 200, 1, 44, 1, (byte) 144,
                0, 0, 0, 0};
        PortableRomDocument calibration = new PortableRomDocument(
                "calibration.bin", calibrationBytes);
        String ecuDefinitions = "<roms>"
                + "<rom><romid><xmlid>BASE</xmlid><filesize>16</filesize>"
                + "</romid><table type=\"3D\" name=\"Fuel Map\""
                + " category=\"Fuel\" storagetype=\"uint16\" endian=\"big\""
                + " sizex=\"2\" sizey=\"2\"><scaling name=\"Standard\""
                + " units=\"ms\" expression=\"x/10\" to_byte=\"x*10\""
                + " format=\"0.0\"/><description>Fixture table</description>"
                + "</table></rom>"
                + "<rom base=\"BASE\"><romid><xmlid>TEST</xmlid>"
                + "<internalidaddress>0</internalidaddress>"
                + "<internalidstring>TEST</internalidstring>"
                + "<filesize>16</filesize><make>Subaru</make>"
                + "<model>Fixture</model></romid>"
                + "<table name=\"Fuel Map\" storageaddress=\"0x4\"/>"
                + "</rom></roms>";
        PortableEcuDefinition ecuDefinition = PortableEcuDefinitionReader.read(
                new ByteArrayInputStream(ecuDefinitions.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)), calibration);
        require("TEST".equals(ecuDefinition.getXmlId())
                        && ecuDefinition.getTables().size() == 1,
                "Exact portable ECU definition did not match");
        PortableRomTable fuelMap = ecuDefinition.getTables().get(0);
        require(fuelMap.getRows() == 2 && fuelMap.getColumns() == 2,
                "Inherited table dimensions were not retained");
        require(fuelMap.valueAt(calibration, 0, 1) == 20.0,
                "Definition-backed table value decoded incorrectly");
        fuelMap.replaceValue(calibration, 1, 0, 45.5);
        require((calibration.byteAt(8) & 0xFF) == 1
                        && (calibration.byteAt(9) & 0xFF) == 199,
                "Definition-backed table edit encoded incorrectly");
        require("45.5".equals(fuelMap.formattedValueAt(calibration, 1, 0)),
                "Definition-backed table display did not refresh");
        try {
            PortableEcuDefinitionReader.read(new ByteArrayInputStream(
                    ecuDefinitions.replace("TEST</internalidstring>",
                            "MISS</internalidstring>").getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)), calibration);
            throw new AssertionError("Mismatched ECU definition was accepted");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("No exact ECU definition"),
                    "ECU definition mismatch was unclear");
        }

        PortableLogSession log = new PortableLogSession();
        log.append(new PortableLogSample(100, "P8", "Engine Speed",
                2500.0, "RPM"));
        log.append(new PortableLogSample(110, "P9", "Boost, Manifold",
                4.5, "psi"));
        StringWriter csv = new StringWriter();
        log.writeLongFormCsv(csv);
        require(csv.toString().contains("2500.0"),
                "Logger CSV omitted a sample");
        Path spool = Files.createTempFile("romraider2-portable-log", ".part");
        PortableLogSession streaming = PortableLogSession.streaming(
                spool.toFile(), 2);
        streaming.append(new PortableLogSample(1, "A", "First", 1, ""));
        streaming.append(new PortableLogSample(2, "B", "Second", 2, ""));
        streaming.append(new PortableLogSample(3, "C", "Third", 3, ""));
        require(streaming.size() == 3 && streaming.snapshot().size() == 2,
                "Streaming log did not bound only its recent memory");
        StringWriter streamedCsv = new StringWriter();
        streaming.writeLongFormCsv(streamedCsv);
        require(streamedCsv.toString().contains("First")
                        && streamedCsv.toString().contains("Third"),
                "Streaming log did not preserve its complete CSV");
        streaming.discard();
        require(!Files.exists(spool), "Streaming log spool was not removed");
        require(csv.toString().contains("\"Boost, Manifold\""),
                "Logger CSV did not quote a channel name");
        PortableLogSession imported = PortableLogCsvReader.read(
                new StringReader(csv.toString()));
        require(imported.size() == 2, "Logger CSV did not round-trip");
        require("Boost, Manifold".equals(imported.snapshot().get(1)
                .getChannelName()), "Quoted logger field did not round-trip");

        String wide = "\ufeffTime (msec),Engine Speed (rpm),"
                + "\"Fuel, Trim (%)\"\r\n"
                + "0,1496,0.00\r\n"
                + "102,1524,\r\n";
        PortableLogSession wideImported = PortableLogCsvReader.read(
                new StringReader(wide));
        require(wideImported.size() == 4,
                "Traditional RomRaider CSV did not import");
        require("Engine Speed".equals(wideImported.snapshot().get(0)
                .getChannelName()), "Wide CSV channel name is wrong");
        require("rpm".equals(wideImported.snapshot().get(0).getUnits()),
                "Wide CSV channel units are wrong");
        require("Fuel, Trim".equals(wideImported.snapshot().get(1)
                .getChannelName()), "Quoted wide CSV channel is wrong");
        require(wideImported.snapshot().get(2).getTimestampMillis() == 102,
                "Wide CSV timestamp is wrong");
        require(Double.isNaN(wideImported.snapshot().get(3).getValue()),
                "Missing wide CSV value was not retained");

        PortableLogSession absoluteTime = PortableLogCsvReader.read(
                new StringReader("time,RPM (rpm)\n23:59:59.900,900\n"
                        + "00:00:00.100,1000\n"));
        require(absoluteTime.snapshot().get(0).getTimestampMillis() == 0,
                "Absolute logger time did not start at zero");
        require(absoluteTime.snapshot().get(1).getTimestampMillis() == 200,
                "Absolute logger time did not handle midnight");

        byte[] firmware = "noise\r\nari p 1.17.4877\r\n"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        require(OpenPortWireProtocol.contains(firmware, firmware.length,
                "ari "), "OpenPort response marker was not found");
        require("p 1.17.4877".equals(OpenPortWireProtocol
                .parseFirmwareVersion(firmware, firmware.length)),
                "OpenPort firmware response was not parsed");
        byte[] voltage = "arr 16 12480\r\n"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        require(OpenPortWireProtocol.parseBatteryMillivolts(
                voltage, voltage.length) == 12480,
                "OpenPort battery voltage was not parsed");
        require(Arrays.equals("ato3 512 4800 0\r\n".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII),
                OpenPortWireProtocol.openSsmKLineRequest()),
                "OpenPort SSM K-line setup request is wrong");
        byte[] wrappedSsm = OpenPortWireProtocol.transmitSsmKLineRequest(
                ReadOnlySsmProtocol.ecuInitRequest());
        require(Arrays.equals("att3 6 0\r\n".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII),
                Arrays.copyOf(wrappedSsm, 10)),
                "OpenPort SSM transmit header is wrong");

        require(Arrays.equals(new byte[] {(byte) 0x80, 0x10, (byte) 0xF0,
                        0x01, (byte) 0xBF, 0x40},
                ReadOnlySsmProtocol.ecuInitRequest()),
                "Read-only SSM init request is wrong");
        require(Arrays.equals(new byte[] {(byte) 0x80, 0x10, (byte) 0xF0,
                        0x05, (byte) 0xA8, 0x00, 0x00, 0x00, 0x0C, 0x39},
                ReadOnlySsmProtocol.readAddressesRequest(0x00000C)),
                "Read-only SSM address request is wrong");
        byte[] ecuInit = new byte[] {(byte) 0x80, (byte) 0xF0, 0x10, 0x09,
                (byte) 0xFF, (byte) 0xA2, 0x10, 0x11, 0x31, 0x52, 0x58,
                0x40, 0x06, 0x6C};
        require("3152584006".equals(ReadOnlySsmProtocol.ecuId(ecuInit)),
                "SSM ECU ID was not decoded");

        byte[] ssmResponse = new byte[] {(byte) 0x80, (byte) 0xF0, 0x10,
                0x02, (byte) 0xE8, 0x64, (byte) 0xCE};
        byte[] startPacket = new byte[] {0x61, 0x72, 0x33, 0x05,
                (byte) 0x80, 0, 0, 0, 1};
        byte[] dataPacket = new byte[5 + ssmResponse.length];
        dataPacket[0] = 0x61;
        dataPacket[1] = 0x72;
        dataPacket[2] = 0x33;
        dataPacket[3] = (byte) (ssmResponse.length + 1);
        dataPacket[4] = 0;
        System.arraycopy(ssmResponse, 0, dataPacket, 5, ssmResponse.length);
        byte[] endPacket = new byte[] {0x61, 0x72, 0x33, 0x05,
                0x40, 0, 0, 0, 2};
        byte[] packets = new byte[startPacket.length + dataPacket.length
                + endPacket.length];
        System.arraycopy(startPacket, 0, packets, 0, startPacket.length);
        System.arraycopy(dataPacket, 0, packets, startPacket.length,
                dataPacket.length);
        System.arraycopy(endPacket, 0, packets,
                startPacket.length + dataPacket.length, endPacket.length);
        OpenPortKLineFrameDecoder decoder = new OpenPortKLineFrameDecoder();
        require(decoder.accept(Arrays.copyOfRange(packets, 0, 11)).isEmpty(),
                "Fragmented OpenPort packet completed too soon");
        java.util.List<byte[]> decoded = decoder.accept(
                Arrays.copyOfRange(packets, 11, packets.length));
        require(decoded.size() == 1 && Arrays.equals(ssmResponse, decoded.get(0)),
                "OpenPort K-line frame did not decode");
        require(Arrays.equals(new byte[] {0x64},
                ReadOnlySsmProtocol.readAddressValues(decoded.get(0), 1)),
                "Read-only SSM value did not decode");

        require(PortableExpression.compile("x/4").evaluate(10000) == 2500.0,
                "Basic logger expression evaluated incorrectly");
        require(PortableExpression.compile(
                        "if(x%256==255,0,((x%256)-128)*100/128)")
                        .evaluate(255) == 0.0,
                "Conditional logger expression evaluated incorrectly");
        require(PortableExpression.compile("BitWise(3,x,1)")
                        .evaluate(6) == 2.0,
                "BitWise logger expression evaluated incorrectly");
        require(PortableExpression.compile("!BitWise(1,x,1)")
                        .evaluate(2) == 1.0,
                "Logical logger expression evaluated incorrectly");
        PortableParameterConverter signed = new PortableParameterConverter(
                new PortableLoggerConversion("raw", "x", "0", "int16", ""));
        require(signed.convert(new byte[] {(byte) 0xFF, (byte) 0xFE}) == -2.0,
                "Signed logger value decoded incorrectly");
        PortableParameterConverter littleFloat = new PortableParameterConverter(
                new PortableLoggerConversion("raw", "x", "0.0", "float",
                        "little"));
        require(littleFloat.convert(new byte[] {0, 0, 32, 64}) == 2.5,
                "Little-endian floating logger value decoded incorrectly");

        String definitionXml = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE logger [<!ELEMENT logger ANY>]>"
                + "<logger version=\"370\"><protocols>"
                + "<protocol id=\"SSM\"><parameters>"
                + "<parameter id=\"P8\" name=\"Engine Speed\" desc=\"RPM\">"
                + "<address length=\"2\">0x00000E</address><conversions>"
                + "<conversion units=\"rpm\" expr=\"x/4\" format=\"0\"/>"
                + "</conversions></parameter>"
                + "<parameter id=\"T1\" name=\"ATF Temperature\" target=\"2\">"
                + "<address>0x000020</address><conversions>"
                + "<conversion units=\"C\" expr=\"x\" format=\"0\"/>"
                + "</conversions></parameter></parameters><ecuparams>"
                + "<ecuparam id=\"E1\" name=\"Extended\" desc=\"Extended\">"
                + "<ecu id=\"ABC,DEF\"><address length=\"4\">0xFF1000</address></ecu>"
                + "<conversions><conversion units=\"raw\" expr=\"x\" format=\"0\"/>"
                + "</conversions></ecuparam></ecuparams><switches>"
                + "<switch id=\"S1\" name=\"Starter Switch\" byte=\"0x61\""
                + " bit=\"6\" target=\"1\"/>"
                + "</switches></protocol>"
                + "</protocols></logger>";
        PortableLoggerDefinition definition = PortableLoggerDefinitionReader.read(
                new java.io.ByteArrayInputStream(definitionXml.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)), "SSM");
        require("370".equals(definition.getVersion()) && definition.size() == 4,
                "Portable logger definition did not parse");
        require(definition.parameter("T1").getTarget() == 2,
                "Logger module target did not parse");
        String profileXml = "<profile protocol=\"SSM\"><parameters>"
                + "<parameter id=\"P8\" livedata=\"selected\" units=\"rpm\"/>"
                + "<parameter id=\"E1\" graph=\"selected\" units=\"raw\"/>"
                + "<parameter id=\"MISSING\" dash=\"selected\" units=\"x\"/>"
                + "</parameters></profile>";
        PortableLoggerProfile profile = PortableLoggerProfileReader.read(
                new java.io.ByteArrayInputStream(profileXml.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
        PortableLoggerSelection beforeEcu = PortableLoggerSelectionService.resolve(
                definition, profile, null);
        require(beforeEcu.ready().size() == 1
                        && beforeEcu.unavailable().size() == 2,
                "Logger profile resolved ECU-specific addresses too early");
        PortableLoggerSelection afterEcu = PortableLoggerSelectionService.resolve(
                definition, profile, "ABC");
        require(afterEcu.ready().size() == 2
                        && afterEcu.ready().get(1).getAddresses().length == 4,
                "Logger profile did not resolve the identified ECU");
        PortableLoggerProfile targetProfile = PortableLoggerProfileReader.read(
                new java.io.ByteArrayInputStream(
                        ("<profile protocol=\"SSM\"><parameters>"
                        + "<parameter id=\"T1\" livedata=\"selected\" units=\"C\"/>"
                        + "</parameters></profile>").getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
        require(PortableLoggerSelectionService.resolve(definition,
                        targetProfile, null, 1).ready().isEmpty(),
                "Transmission parameter was exposed to an engine session");
        require(PortableLoggerSelectionService.resolve(definition,
                        targetProfile, null, 2).ready().size() == 1,
                "Transmission parameter was not exposed to a transmission session");
        PortableLoggerProfile switchProfile = PortableLoggerProfileReader.read(
                new java.io.ByteArrayInputStream(
                        ("<profile protocol=\"SSM\"><switches>"
                        + "<switch id=\"S1\" livedata=\"selected\"/>"
                        + "</switches></profile>").getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
        PortableLoggerSelection switchSelection =
                PortableLoggerSelectionService.resolve(definition,
                        switchProfile, null, 1);
        require(switchSelection.ready().size() == 1,
                "Selected logger switch did not resolve");
        PortableLoggerQueryPlan switchPlan = PortableLoggerQueryPlan.create(
                switchSelection.ready());
        require(switchPlan.decode(java.util.Collections.singletonList(
                        new byte[] {0x40})).get(0).getValue() == 1.0,
                "Logger switch bit did not decode");
        PortableLoggerProfile externalProfile = PortableLoggerProfileReader.read(
                new java.io.ByteArrayInputStream(
                        ("<profile protocol=\"SSM\"><externals>"
                        + "<external id=\"X_AEM\" graph=\"selected\"/>"
                        + "</externals></profile>").getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
        require(externalProfile.size() == 1
                        && externalProfile.unsupported().size() == 1
                        && externalProfile.unsupported().get(0)
                                .contains("external input transport unavailable"),
                "Selected external input was silently omitted");
        PortableLoggerQueryPlan queryPlan = PortableLoggerQueryPlan.create(
                afterEcu.ready());
        require(queryPlan.batches().size() == 1
                        && queryPlan.batches().get(0).getAddresses().length == 6,
                "Logger query plan did not deduplicate and batch addresses");
        PortableLoggerQueryBatch queryBatch = queryPlan.batches().get(0);
        byte[] queryValues = new byte[queryBatch.getAddresses().length];
        int[] queryAddresses = queryBatch.getAddresses();
        for (int index = 0; index < queryAddresses.length; index++) {
            if (queryAddresses[index] == 0x00000E) queryValues[index] = 0x27;
            else if (queryAddresses[index] == 0x00000F) queryValues[index] = 0x10;
            else if (queryAddresses[index] == 0xFF1003) queryValues[index] = 42;
        }
        java.util.List<PortableLoggerValue> queryResult = queryPlan.decode(
                java.util.Collections.singletonList(queryValues));
        require(queryResult.get(0).getValue() == 2500.0
                        && queryResult.get(1).getValue() == 42.0,
                "Logger query response did not map and convert values");
        java.util.List<PortableLoggerValue> cycleResult =
                new PortableLoggerCycle(queryPlan).read(batch ->
                        Arrays.copyOf(queryValues, queryValues.length));
        require(cycleResult.size() == 2
                        && cycleResult.get(0).getValue() == 2500.0,
                "Portable logger cycle did not execute the query plan");

        try {
            PortableLoggerProfileReader.read(new java.io.ByteArrayInputStream(
                    ("<!DOCTYPE profile [<!ENTITY x \"bad\">]>"
                            + "<profile protocol=\"SSM\"/>").getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)));
            throw new AssertionError("Logger XML entity declaration was accepted");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("entity declarations"),
                    "Logger XML entity rejection was unclear");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
