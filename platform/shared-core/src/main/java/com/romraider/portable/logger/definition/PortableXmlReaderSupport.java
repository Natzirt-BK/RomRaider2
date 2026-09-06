/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import com.romraider.portable.xml.PortableXmlInput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

final class PortableXmlReaderSupport {
    private static final int MAX_XML_BYTES = 16 * 1024 * 1024;

    private PortableXmlReaderSupport() { }

    static void parse(InputStream input, DefaultHandler handler)
            throws IOException {
        if (input == null) throw new IllegalArgumentException("XML input is required");
        String xml = PortableXmlInput.decodeWithoutEntities(readBounded(input));
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            setFeatureIfSupported(factory,
                    XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeatureIfSupported(factory,
                    "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory,
                    "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureIfSupported(factory,
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setEntityResolver((publicId, systemId) ->
                    new InputSource(new StringReader("")));
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IOException("Logger XML could not be parsed: "
                    + safeMessage(ex), ex);
        }
    }

    private static void setFeatureIfSupported(SAXParserFactory factory,
            String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
            // The bounded input, entity-declaration rejection, and empty
            // external resolver remain enforced on parsers lacking a feature.
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) continue;
            if (bytes.size() + count > MAX_XML_BYTES) {
                throw new IOException("Logger XML size limit reached");
            }
            bytes.write(buffer, 0, count);
        }
        if (bytes.size() == 0) throw new IOException("Logger XML is empty");
        return bytes.toByteArray();
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty()
                ? ex.getClass().getSimpleName() : message;
    }
}
