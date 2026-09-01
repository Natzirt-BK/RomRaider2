/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.editor;

import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.logger.PortableExpression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/** Secure reader for exact, definition-backed offline ROM table editing. */
public final class PortableEcuDefinitionReader {
    private static final int MAX_XML_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ROMS = 2000;
    private static final int MAX_TABLES = 10000;
    private static final int MAX_INHERITANCE_DEPTH = 16;
    private static final int MAX_TABLE_CELLS = 65536;

    private PortableEcuDefinitionReader() { }

    public static PortableEcuDefinition read(InputStream input,
            PortableRomDocument rom) throws IOException {
        if (input == null) throw new IllegalArgumentException(
                "An ECU definition input stream is required");
        if (rom == null) throw new IllegalArgumentException("Open a ROM first");
        byte[] xml = readBounded(input);
        rejectEntityDeclarations(xml);

        MetadataHandler metadata = new MetadataHandler();
        parse(xml, metadata);
        RomMetadata match = exactMatch(metadata.roms, rom.snapshot());
        List<String> chain = inheritanceChain(match, metadata.byId);

        TableHandler tableHandler = new TableHandler(new HashSet<String>(chain));
        parse(xml, tableHandler);
        LinkedHashMap<String, RawTable> merged = new LinkedHashMap<>();
        for (String xmlId : chain) {
            List<RawTable> definitions = tableHandler.tables.get(xmlId);
            if (definitions == null) continue;
            for (RawTable table : definitions) {
                RawTable inherited = merged.get(table.name());
                merged.put(table.name(), inherited == null
                        ? table : inherited.merge(table));
            }
        }

        List<PortableRomTable> tables = new ArrayList<>();
        for (RawTable raw : merged.values()) {
            PortableRomTable table = toPortable(raw, rom.size());
            if (table != null) tables.add(table);
        }
        if (tables.isEmpty()) {
            throw new IOException("The matching ECU definition has no supported numeric tables");
        }
        return new PortableEcuDefinition(match.xmlId, match.make, match.model,
                match.submodel, tables);
    }

    private static PortableRomTable toPortable(RawTable raw, int romSize) {
        try {
            String type = raw.attribute("type");
            int columns;
            int rows;
            if ("3d".equalsIgnoreCase(type)) {
                columns = positive(raw.attribute("sizex"));
                rows = positive(raw.attribute("sizey"));
            } else if ("2d".equalsIgnoreCase(type)) {
                columns = 1;
                rows = positive(first(raw.attribute("sizey"),
                        raw.attribute("sizex")));
            } else {
                return null;
            }
            int cells = Math.multiplyExact(columns, rows);
            if (cells < 1 || cells > MAX_TABLE_CELLS) return null;
            int address = address(raw.attribute("storageaddress"));
            PortableRomTable.Storage storage = PortableRomTable.Storage.parse(
                    raw.attribute("storagetype"));
            long end = (long) address + (long) cells * storage.bytes;
            if (address < 0 || end > romSize) return null;
            RawScale scale = raw.preferredScale();
            if (scale == null) return null;
            String expression = first(scale.attributes.get("expression"), "x");
            String toByte = first(scale.attributes.get("to_byte"), "x");
            PortableExpression fromBytes = PortableExpression.compile(expression);
            PortableExpression toBytes = PortableExpression.compile(toByte);
            return new PortableRomTable(raw.name(),
                    first(raw.attribute("category"), "Uncategorized"),
                    raw.description, type, address, columns, rows, storage,
                    "little".equalsIgnoreCase(raw.attribute("endian")),
                    first(scale.attributes.get("units"), "raw"),
                    scale.attributes.get("format"), fromBytes, toBytes);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static RomMetadata exactMatch(List<RomMetadata> definitions,
            byte[] rom) throws IOException {
        List<RomMetadata> matches = new ArrayList<>();
        for (RomMetadata definition : definitions) {
            if (definition.fileSize != rom.length || definition.idAddress < 0
                    || definition.idString.isEmpty()) continue;
            byte[] expected = definition.idString.regionMatches(true, 0, "0x", 0, 2)
                    ? hexBytes(definition.idString.substring(2))
                    : definition.idString.getBytes(StandardCharsets.US_ASCII);
            if (expected.length == 0 || definition.idAddress > rom.length - expected.length) {
                continue;
            }
            boolean equal = true;
            for (int index = 0; index < expected.length; index++) {
                int actual = rom[definition.idAddress + index] & 0xFF;
                int wanted = expected[index] & 0xFF;
                if (definition.idString.regionMatches(true, 0, "0x", 0, 2)) {
                    if (actual != wanted) equal = false;
                } else if (Character.toLowerCase((char) actual)
                        != Character.toLowerCase((char) wanted)) {
                    equal = false;
                }
                if (!equal) break;
            }
            if (equal) matches.add(definition);
        }
        if (matches.isEmpty()) {
            throw new IOException("No exact ECU definition matched this ROM's size and internal ID");
        }
        if (matches.size() > 1) {
            throw new IOException("More than one ECU definition matched this ROM; editing was not enabled");
        }
        return matches.get(0);
    }

    private static List<String> inheritanceChain(RomMetadata match,
            Map<String, RomMetadata> byId) throws IOException {
        List<String> reversed = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        RomMetadata current = match;
        while (current != null) {
            if (reversed.size() >= MAX_INHERITANCE_DEPTH) {
                throw new IOException("ECU definition inheritance is too deep");
            }
            String key = current.xmlId.toLowerCase(Locale.ROOT);
            if (!visited.add(key)) throw new IOException(
                    "ECU definition inheritance contains a cycle");
            reversed.add(current.xmlId);
            if (current.base.isEmpty()) break;
            current = byId.get(current.base.toLowerCase(Locale.ROOT));
            // Standalone custom definitions sometimes name an external base
            // while carrying complete tables of their own. Those tables are
            // still safe to expose; incomplete address-only tables are
            // filtered out later because they lack storage and scaling data.
            if (current == null) break;
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) continue;
            if (output.size() + count > MAX_XML_BYTES) {
                throw new IOException("ECU definition exceeds the portable size limit");
            }
            output.write(buffer, 0, count);
        }
        if (output.size() == 0) throw new IOException("ECU definition is empty");
        return output.toByteArray();
    }

    private static void rejectEntityDeclarations(byte[] xml) throws IOException {
        String text = new String(xml, StandardCharsets.ISO_8859_1)
                .toUpperCase(Locale.ROOT);
        if (text.contains("<!ENTITY")) {
            throw new IOException("XML entity declarations are not allowed");
        }
    }

    private static void parse(byte[] xml, DefaultHandler handler) throws IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            feature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            feature(factory, "http://xml.org/sax/features/external-general-entities", false);
            feature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            feature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setEntityResolver((publicId, systemId) ->
                    new InputSource(new java.io.StringReader("")));
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (Exception ex) {
            throw new IOException("ECU definition could not be parsed: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : ex.getMessage()), ex);
        }
    }

    private static void feature(SAXParserFactory factory, String name,
            boolean value) {
        try { factory.setFeature(name, value); } catch (Exception ignored) { }
    }

    private static int positive(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) throw new IllegalArgumentException("Positive size required");
        return parsed;
    }

    private static int address(String value) {
        if (value == null) throw new IllegalArgumentException("Address required");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) normalized = normalized.substring(2);
        // RomRaider definition addresses are hexadecimal even when the 0x
        // prefix is omitted (for example, internalidaddress="2000").
        long parsed = Long.parseLong(normalized, 16);
        if (parsed < 0 || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Address is outside the portable range");
        }
        return (int) parsed;
    }

    private static int fileSize(String value) {
        if (value == null || value.trim().isEmpty()) return -1;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1;
        if (normalized.endsWith("kb")) {
            multiplier = 1024;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("mb")) {
            multiplier = 1024 * 1024;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        try {
            long bytes = Math.multiplyExact(Long.parseLong(normalized), multiplier);
            return bytes > Integer.MAX_VALUE ? -1 : (int) bytes;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private static byte[] hexBytes(String text) {
        String compact = text.replaceAll("[^0-9A-Fa-f]", "");
        if (compact.isEmpty() || (compact.length() & 1) != 0) return new byte[0];
        byte[] result = new byte[compact.length() / 2];
        try {
            for (int index = 0; index < result.length; index++) {
                result[index] = (byte) Integer.parseInt(
                        compact.substring(index * 2, index * 2 + 2), 16);
            }
            return result;
        } catch (NumberFormatException ex) {
            return new byte[0];
        }
    }

    private static String first(String first, String fallback) {
        return first == null || first.trim().isEmpty() ? fallback : first.trim();
    }

    private static Map<String, String> attributes(Attributes source) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < source.getLength(); index++) {
            result.put(source.getQName(index).toLowerCase(Locale.ROOT),
                    source.getValue(index));
        }
        return result;
    }

    private static final class MetadataHandler extends DefaultHandler {
        private final List<RomMetadata> roms = new ArrayList<>();
        private final Map<String, RomMetadata> byId = new HashMap<>();
        private RomMetadata current;
        private boolean romId;
        private String field;
        private StringBuilder text;

        @Override public void startElement(String uri, String local, String name,
                Attributes attrs) throws org.xml.sax.SAXException {
            String tag = name.toLowerCase(Locale.ROOT);
            if ("rom".equals(tag)) {
                current = new RomMetadata(first(attrs.getValue("base"), ""));
            } else if (current != null && "romid".equals(tag)) {
                romId = true;
            } else if (current != null && romId) {
                field = tag;
                text = new StringBuilder();
            }
        }

        @Override public void characters(char[] chars, int start, int length) {
            if (text != null) text.append(chars, start, length);
        }

        @Override public void endElement(String uri, String local, String name)
                throws org.xml.sax.SAXException {
            String tag = name.toLowerCase(Locale.ROOT);
            if (current != null && field != null && field.equals(tag)) {
                current.set(field, text.toString().trim());
                field = null;
                text = null;
            } else if (current != null && "romid".equals(tag)) {
                romId = false;
            } else if (current != null && "rom".equals(tag)) {
                if (!current.xmlId.isEmpty()) {
                    if (roms.size() >= MAX_ROMS) throw new org.xml.sax.SAXException(
                            "ECU definition contains too many ROM entries");
                    roms.add(current);
                    byId.put(current.xmlId.toLowerCase(Locale.ROOT), current);
                }
                current = null;
            }
        }
    }

    private static final class RomMetadata {
        private final String base;
        private String xmlId = "";
        private String idString = "";
        private int idAddress = -1;
        private int fileSize = -1;
        private String make = "";
        private String model = "";
        private String submodel = "";

        private RomMetadata(String base) { this.base = base; }

        private void set(String key, String value) {
            switch (key) {
                case "xmlid": xmlId = value; break;
                case "internalidstring": idString = value; break;
                case "internalidaddress":
                    try { idAddress = address(value); } catch (RuntimeException ignored) { }
                    break;
                case "filesize": fileSize = fileSize(value); break;
                case "make": make = value; break;
                case "model": model = value; break;
                case "submodel": submodel = value; break;
                default: break;
            }
        }
    }

    private static final class TableHandler extends DefaultHandler {
        private final Set<String> wanted;
        private final Map<String, List<RawTable>> tables = new HashMap<>();
        private String currentRomId = "";
        private boolean romId;
        private String field;
        private StringBuilder text;
        private int tableDepth;
        private RawTable table;

        private TableHandler(Set<String> wanted) {
            this.wanted = new HashSet<>();
            for (String value : wanted) this.wanted.add(value.toLowerCase(Locale.ROOT));
        }

        @Override public void startElement(String uri, String local, String name,
                Attributes attrs) throws org.xml.sax.SAXException {
            String tag = name.toLowerCase(Locale.ROOT);
            if ("rom".equals(tag)) {
                currentRomId = "";
            } else if ("romid".equals(tag) && tableDepth == 0) {
                romId = true;
            } else if (romId && "xmlid".equals(tag)) {
                field = "xmlid";
                text = new StringBuilder();
            } else if ("table".equals(tag)) {
                tableDepth++;
                if (tableDepth == 1 && wanted.contains(
                        currentRomId.toLowerCase(Locale.ROOT))) {
                    table = new RawTable(attributes(attrs));
                }
            } else if (table != null && tableDepth == 1 && "scaling".equals(tag)) {
                table.scales.add(new RawScale(attributes(attrs)));
            } else if (table != null && tableDepth == 1 && "description".equals(tag)) {
                field = "description";
                text = new StringBuilder();
            }
        }

        @Override public void characters(char[] chars, int start, int length) {
            if (text != null) text.append(chars, start, length);
        }

        @Override public void endElement(String uri, String local, String name)
                throws org.xml.sax.SAXException {
            String tag = name.toLowerCase(Locale.ROOT);
            if ("xmlid".equals(field) && "xmlid".equals(tag)) {
                currentRomId = text.toString().trim();
                field = null;
                text = null;
            } else if ("description".equals(field) && "description".equals(tag)) {
                table.description = text.toString().trim();
                field = null;
                text = null;
            } else if ("romid".equals(tag)) {
                romId = false;
            } else if ("table".equals(tag)) {
                if (tableDepth == 1 && table != null) {
                    if (table.name() != null && !table.name().trim().isEmpty()) {
                        List<RawTable> list = tables.computeIfAbsent(currentRomId,
                                key -> new ArrayList<>());
                        if (list.size() >= MAX_TABLES) throw new org.xml.sax.SAXException(
                                "ECU definition contains too many tables");
                        list.add(table);
                    }
                    table = null;
                }
                tableDepth--;
            }
        }
    }

    private static final class RawTable {
        private final Map<String, String> attributes;
        private final List<RawScale> scales = new ArrayList<>();
        private String description = "";

        private RawTable(Map<String, String> attributes) {
            this.attributes = attributes;
        }

        private String name() { return attribute("name"); }
        private String attribute(String name) { return attributes.get(name); }

        private RawScale preferredScale() {
            for (RawScale scale : scales) {
                if ("standard".equalsIgnoreCase(scale.attributes.get("name"))) return scale;
            }
            return scales.isEmpty() ? null : scales.get(0);
        }

        private RawTable merge(RawTable override) {
            Map<String, String> combined = new HashMap<>(attributes);
            combined.putAll(override.attributes);
            RawTable result = new RawTable(combined);
            result.scales.addAll(override.scales.isEmpty() ? scales : override.scales);
            result.description = override.description.isEmpty()
                    ? description : override.description;
            return result;
        }
    }

    private static final class RawScale {
        private final Map<String, String> attributes;
        private RawScale(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }
}
