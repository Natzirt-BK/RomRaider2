/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public final class XmlSecurityTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void domParserDoesNotExpandExternalEntities() throws Exception {
        Path secret = temporaryFolder.newFile("secret.txt").toPath();
        Files.writeString(secret, "ROMRAIDER2_XML_SECRET", UTF_8);
        String xml = externalEntityDocument(secret);

        Document document = XmlSecurity.newDocumentBuilderFactory()
                .newDocumentBuilder().parse(
                        new ByteArrayInputStream(xml.getBytes(UTF_8)));

        assertFalse(document.getDocumentElement().getTextContent()
                .contains("ROMRAIDER2_XML_SECRET"));
    }

    @Test
    public void saxParserDoesNotExpandExternalEntities() throws Exception {
        Path secret = temporaryFolder.newFile("sax-secret.txt").toPath();
        Files.writeString(secret, "ROMRAIDER2_SAX_SECRET", UTF_8);
        String xml = externalEntityDocument(secret);
        final StringBuilder content = new StringBuilder();

        SaxParserFactory.getSaxParser().parse(
                new ByteArrayInputStream(xml.getBytes(UTF_8)),
                new DefaultHandler() {
                    @Override
                    public void startElement(String uri, String localName,
                            String qName, Attributes attributes) { }

                    @Override
                    public void characters(char[] characters, int start,
                            int length) {
                        content.append(characters, start, length);
                    }
                });

        assertFalse(content.toString().contains("ROMRAIDER2_SAX_SECRET"));
    }

    private static String externalEntityDocument(Path secret) {
        return "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE root [<!ENTITY xxe SYSTEM \""
                + secret.toUri() + "\">]>"
                + "<root>&xxe;</root>";
    }
}
