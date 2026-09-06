/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerProfileReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Synthetic import security/encoding regressions through all three public readers. */
public final class PortableXmlSecurityCheck {
    private static int assertions;
    private static final String LABEL = "Température &amp; &#176;C";
    private static final String EXPECTED = "Température & °C";
    private static final String[] ROOTS = {"profile", "logger", "roms"};
    private static final String[] DOCUMENTS = {
        "<profile protocol='SSM'><parameters><parameter id='P8' units='" + LABEL
                + "' livedata='selected'/></parameters></profile>",
        "<logger version='370'><protocols><protocol id='SSM'><parameters>"
                + "<parameter id='P8' name='" + LABEL + "'><address>0x0E</address>"
                + "<conversions><conversion units='rpm' expr='x' format='0'/></conversions>"
                + "</parameter></parameters></protocol></protocols></logger>",
        "<roms><rom><romid><xmlid>TEST</xmlid><filesize>8</filesize>"
                + "<internalidaddress>0</internalidaddress><internalidstring>TEST</internalidstring>"
                + "<model>" + LABEL + "</model></romid>"
                + "<table type='2D' name='Fixture' storageaddress='4' storagetype='uint8' sizey='1'>"
                + "<scaling units='raw' expression='x' to_byte='x'/></table></rom></roms>"
    };

    public static void main(String[] args) throws Exception {
        assertions = 0;
        for (String encoding : new String[] {"UTF-8", "UTF-16", "UTF-16LE", "UTF-16BE",
                "UTF-32LE", "UTF-32BE", "ISO-8859-1", "windows-1252"}) {
            for (int reader = 0; reader < DOCUMENTS.length; reader++) {
                String declaration = "<?xml version='1.0' encoding='" + encoding + "'?>";
                String dtd = "<!DOCTYPE " + ROOTS[reader] + " [<!ELEMENT " + ROOTS[reader]
                        + " ANY><!ATTLIST " + ROOTS[reader] + " optional CDATA #IMPLIED>]>";
                byte[] valid = (declaration + dtd + DOCUMENTS[reader]).getBytes(encoding);
                check(EXPECTED.equals(read(reader, valid)), encoding + " valid label / DTD");
                for (String entity : new String[] {"<!ENTITY x 'expanded'>",
                        "<!ENTITY % x 'expanded'>", "<!ENTITY x SYSTEM 'file:///nonexistent-rr2-entity'>",
                        "<!ENTITY % x SYSTEM 'https://invalid.invalid/rr2-entity'>"}) {
                    reject(reader, (declaration + "<!DOCTYPE " + ROOTS[reader] + " [" + entity
                            + "]>" + DOCUMENTS[reader]).getBytes(encoding), "entity declarations");
                }
                if (encoding.equals("UTF-8") || encoding.equals("UTF-16LE") || encoding.equals("UTF-16BE")
                        || encoding.equals("UTF-32LE") || encoding.equals("UTF-32BE")) {
                    String bom = "\ufeff";
                    check(EXPECTED.equals(read(reader, (bom + declaration + DOCUMENTS[reader])
                            .getBytes(encoding))), encoding + " explicit BOM");
                    reject(reader, (bom + declaration + "<!DOCTYPE " + ROOTS[reader]
                            + " [<!ENTITY x 'bad'>]>" + DOCUMENTS[reader]).getBytes(encoding),
                            "entity declarations");
                    check(EXPECTED.equals(read(reader, (bom + DOCUMENTS[reader]).getBytes(encoding))),
                            encoding + " BOM without declaration");
                }
            }
        }
        for (int reader = 0; reader < DOCUMENTS.length; reader++) {
            check(EXPECTED.equals(read(reader, ("<?xml-stylesheet encoding='does-not-exist'?>"
                    + DOCUMENTS[reader]).getBytes(StandardCharsets.UTF_8))), "Ignore non-declaration PI");
            reject(reader, new byte[0], "empty");
            reject(reader, "<?xml version='1.0' encoding='does-not-exist'?><x/>".getBytes(StandardCharsets.UTF_8),
                    "Unsupported XML encoding");
            reject(reader, ("\ufeff<?xml version='1.0' encoding='UTF-8'?>" + DOCUMENTS[reader])
                    .getBytes(StandardCharsets.UTF_16LE), "conflicts");
            reject(reader, ("<?xml version='1.0' encoding='UTF-16'?>" + DOCUMENTS[reader])
                    .getBytes(StandardCharsets.UTF_8), "signature");
            byte[] valid = DOCUMENTS[reader].getBytes(StandardCharsets.UTF_8);
            byte[] malformed = Arrays.copyOf(valid, valid.length + 1);
            malformed[malformed.length - 1] = (byte) 0xc3;
            reject(reader, malformed, "Malformed XML text");
            byte[] utf16 = ("\ufeff" + DOCUMENTS[reader]).getBytes(StandardCharsets.UTF_16LE);
            reject(reader, Arrays.copyOf(utf16, utf16.length - 1), "Malformed XML text");
            reject(reader, new byte[16 * 1024 * 1024 + 1], "limit");
        }
        // If a parser resolves this external subset it supplies a default units
        // attribute. The empty resolver must keep that attribute absent.
        Path external = Files.createTempFile("rr2-xml-external-", ".dtd");
        try {
            Files.writeString(external, "<!ATTLIST parameter units CDATA 'EXTERNAL_SENTINEL'>");
            String xml = "<!DOCTYPE profile SYSTEM '" + external.toUri() + "'>"
                    + "<profile protocol='SSM'><parameters><parameter id='P8' livedata='selected'/>"
                    + "</parameters></profile>";
            check(read(0, xml.getBytes(StandardCharsets.UTF_8)).isEmpty(), "External DTD was ignored");
        } finally { Files.delete(external); }
        System.out.println("Portable XML security checks passed: " + assertions + " assertions");
    }

    private static String read(int reader, byte[] xml) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(xml);
        if (reader == 0) return PortableLoggerProfileReader.read(input).selections().get(0).getUnits();
        if (reader == 1) return PortableLoggerDefinitionReader.read(input, "SSM").parameter("P8").getName();
        return PortableEcuDefinitionReader.read(input, new PortableRomDocument("synthetic.bin",
                new byte[] {'T', 'E', 'S', 'T', 1, 0, 0, 0})).getModel();
    }

    private static void reject(int reader, byte[] xml, String message) throws IOException {
        try { read(reader, xml); }
        catch (IOException ex) {
            check(ex.getMessage().contains(message), "Wrong rejection: " + ex.getMessage());
            return;
        }
        throw new AssertionError("Reader " + reader + " accepted forbidden XML: " + message);
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        assertions++;
    }
}
