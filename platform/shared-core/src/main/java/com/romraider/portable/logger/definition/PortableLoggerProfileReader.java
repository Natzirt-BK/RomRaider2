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
        List<PortableLoggerProfile.Selection> selected = new ArrayList<>(handler.selections.values());
        if (handler.anyOrder) {
            int size = selected.size() + handler.unsupported.size();
            if (handler.missingOrder || handler.usedRanks.size() != size)
                throw new IOException("Profile selection order is incomplete");
            for (int i = 0; i < size; i++) if (!handler.usedRanks.contains(i))
                throw new IOException("Profile selection order has gaps");
            selected.sort(java.util.Comparator.comparingInt(choice -> handler.ranks.get(choice.getId())));
        }
        return new PortableLoggerProfile(handler.protocol,
                selected,
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
        private boolean anyOrder, missingOrder;
        private final Map<String, Integer> ranks = new LinkedHashMap<>();
        private final java.util.Set<Integer> usedRanks = new java.util.HashSet<>();

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
                if (!selected) { rejectUnselectedRank(attributes); return; }
                PortableLoggerProfile.Selection selection =
                        new PortableLoggerProfile.Selection(
                                value(attributes, "id"),
                                value(attributes, "units"));
                selections.put(selection.getId(), selection);
                ranks.put(selection.getId(), rank(attributes));
                if (selections.size() + unsupported.size() > MAX_SELECTIONS) {
                    throw new SAXException("Logger profile selection limit reached");
                }
            } else if (inExternals && "external".equals(qName)) {
                boolean selected = "selected".equals(value(attributes, "livedata"))
                        || "selected".equals(value(attributes, "graph"))
                        || "selected".equals(value(attributes, "dash"));
                if (!selected) { rejectUnselectedRank(attributes); return; }
                rank(attributes);
                String id = value(attributes, "id");
                if (id.isEmpty()) id = "external input";
                unsupported.add(id + ": external input transport unavailable");
                if (selections.size() + unsupported.size() > MAX_SELECTIONS) {
                    throw new SAXException("Logger profile selection limit reached");
                }
            }
        }

        private int rank(Attributes attributes) throws SAXException {
            String value = attributes.getValue("rr2-order");
            if (value == null) { missingOrder = true; return -1; }
            anyOrder = true;
            if (!value.matches("0|[1-9][0-9]{0,8}")) throw new SAXException("Invalid profile selection order");
            int rank = Integer.parseInt(value);
            if (!usedRanks.add(rank)) throw new SAXException("Duplicate profile selection order");
            return rank;
        }
        private void rejectUnselectedRank(Attributes attributes) throws SAXException {
            if (attributes.getValue("rr2-order") != null) throw new SAXException("Unselected channel has a profile order");
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
