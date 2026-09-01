/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Secure, bounded reader for parameter selections in a logger profile. */
public final class PortableLoggerProfileReader {
    private static final int MAX_SELECTIONS = 256;

    private PortableLoggerProfileReader() { }

    public static PortableLoggerProfile read(InputStream input) throws IOException {
        Handler handler = new Handler();
        PortableXmlReaderSupport.parse(input, handler);
        if (!handler.foundProfile) throw new IOException("Logger profile is missing");
        return new PortableLoggerProfile(handler.protocol,
                new ArrayList<PortableLoggerProfile.Selection>(
                        handler.selections.values()),
                new ArrayList<String>(handler.unsupported));
    }

    private static final class Handler extends DefaultHandler {
        private final Map<String, PortableLoggerProfile.Selection> selections =
                new LinkedHashMap<>();
        private final List<String> unsupported = new ArrayList<>();
        private String protocol = "";
        private boolean foundProfile;
        private boolean inParameters;
        private boolean inSwitches;
        private boolean inExternals;

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            if ("profile".equals(qName)) {
                foundProfile = true;
                protocol = value(attributes, "protocol");
            } else if ("parameters".equals(qName)) {
                inParameters = true;
            } else if ("switches".equals(qName)) {
                inSwitches = true;
            } else if ("externals".equals(qName)) {
                inExternals = true;
            } else if ((inParameters && "parameter".equals(qName))
                    || (inSwitches && "switch".equals(qName))) {
                boolean selected = "selected".equals(value(attributes, "livedata"))
                        || "selected".equals(value(attributes, "graph"))
                        || "selected".equals(value(attributes, "dash"));
                if (!selected) return;
                PortableLoggerProfile.Selection selection =
                        new PortableLoggerProfile.Selection(
                                value(attributes, "id"),
                                value(attributes, "units"));
                selections.put(selection.getId(), selection);
                if (selections.size() > MAX_SELECTIONS) {
                    throw new SAXException("Logger profile selection limit reached");
                }
            } else if (inExternals && "external".equals(qName)) {
                boolean selected = "selected".equals(value(attributes, "livedata"))
                        || "selected".equals(value(attributes, "graph"))
                        || "selected".equals(value(attributes, "dash"));
                if (!selected) return;
                String id = value(attributes, "id");
                if (id.isEmpty()) id = "external input";
                unsupported.add(id + ": external input transport unavailable");
                if (selections.size() + unsupported.size() > MAX_SELECTIONS) {
                    throw new SAXException("Logger profile selection limit reached");
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("parameters".equals(qName)) inParameters = false;
            if ("switches".equals(qName)) inSwitches = false;
            if ("externals".equals(qName)) inExternals = false;
        }

        private static String value(Attributes attributes, String name) {
            String value = attributes.getValue(name);
            return value == null ? "" : value.trim();
        }
    }
}
