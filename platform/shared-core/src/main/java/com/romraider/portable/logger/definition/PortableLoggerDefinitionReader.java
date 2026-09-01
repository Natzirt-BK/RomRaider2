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

/** Secure, bounded reader for one protocol in a RomRaider logger definition. */
public final class PortableLoggerDefinitionReader {
    private static final int MAX_PARAMETERS = 20_000;
    private static final int MAX_ADDRESS_MAPPINGS = 250_000;

    private PortableLoggerDefinitionReader() { }

    public static PortableLoggerDefinition read(InputStream input,
            String protocol) throws IOException {
        if (protocol == null || protocol.trim().isEmpty()) {
            throw new IllegalArgumentException("Logger protocol is required");
        }
        Handler handler = new Handler(protocol.trim());
        PortableXmlReaderSupport.parse(input, handler);
        if (!handler.foundProtocol) {
            throw new IOException("Logger definition has no " + protocol
                    + " protocol");
        }
        if (handler.parameters.isEmpty()) {
            throw new IOException("Logger definition contains no parameters");
        }
        try {
            return new PortableLoggerDefinition(handler.version,
                    protocol, handler.parameters);
        } catch (IllegalArgumentException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private static final class Handler extends DefaultHandler {
        private final String requestedProtocol;
        private final List<PortableLoggerParameter> parameters = new ArrayList<>();
        private String version = "";
        private boolean foundProtocol;
        private boolean inProtocol;
        private ParameterBuilder parameter;
        private List<String> ecuIds = new ArrayList<>();
        private StringBuilder addressText;
        private int addressLength;
        private int addressMappings;

        private Handler(String requestedProtocol) {
            this.requestedProtocol = requestedProtocol;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            if ("logger".equals(qName)) version = value(attributes, "version");
            if ("protocol".equals(qName)) {
                inProtocol = requestedProtocol.equalsIgnoreCase(
                        value(attributes, "id"));
                if (inProtocol) foundProtocol = true;
                return;
            }
            if (!inProtocol) return;
            if ("parameter".equals(qName) || "ecuparam".equals(qName)) {
                if (parameter != null) throw new SAXException(
                        "Nested logger parameters are invalid");
                parameter = new ParameterBuilder(value(attributes, "id"),
                        value(attributes, "name"), value(attributes, "desc"),
                        parseTarget(value(attributes, "target")));
            } else if ("switch".equals(qName)) {
                if (parameter != null) throw new SAXException(
                        "Nested logger parameters are invalid");
                parameter = new ParameterBuilder(value(attributes, "id"),
                        value(attributes, "name"), value(attributes, "desc"),
                        parseTarget(value(attributes, "target")));
                int address = parseAddress(value(attributes, "byte"));
                int bit = parseBit(value(attributes, "bit"));
                parameter.addresses.computeIfAbsent(
                        PortableLoggerParameter.ALL_ECUS,
                        ignored -> new ArrayList<>()).add(
                                new PortableLoggerAddress(address, 1));
                parameter.conversions.add(new PortableLoggerConversion(
                        "On/Off", "BitWise(" + (1 << bit) + ",x,1)>0",
                        "0", "uint8", ""));
                if (++addressMappings > MAX_ADDRESS_MAPPINGS) {
                    throw new SAXException("Logger address mapping limit reached");
                }
            } else if (parameter != null && "ecu".equals(qName)) {
                ecuIds = splitIds(value(attributes, "id"));
            } else if (parameter != null && "address".equals(qName)) {
                addressText = new StringBuilder();
                String length = value(attributes, "length");
                addressLength = length.isEmpty() ? 1 : parsePositive(length,
                        "Logger address length");
            } else if (parameter != null && "conversion".equals(qName)) {
                parameter.conversions.add(new PortableLoggerConversion(
                        value(attributes, "units"), value(attributes, "expr"),
                        value(attributes, "format"),
                        value(attributes, "storagetype"),
                        value(attributes, "endian")));
            } else if (parameter != null && "ref".equals(qName)) {
                String dependency = value(attributes, "parameter");
                if (dependency.isEmpty()) dependency = value(attributes, "ecuparam");
                if (!dependency.isEmpty()) parameter.dependencies.add(dependency);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (addressText != null) addressText.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (!inProtocol && !"protocol".equals(qName)) return;
            if ("address".equals(qName) && parameter != null
                    && addressText != null) {
                int address = parseAddress(addressText.toString());
                PortableLoggerAddress range;
                try {
                    range = new PortableLoggerAddress(address, addressLength);
                } catch (IllegalArgumentException ex) {
                    throw new SAXException(ex.getMessage(), ex);
                }
                List<String> targets = ecuIds.isEmpty()
                        ? java.util.Collections.singletonList(
                                PortableLoggerParameter.ALL_ECUS) : ecuIds;
                for (String target : targets) {
                    parameter.addresses.computeIfAbsent(target,
                            ignored -> new ArrayList<>()).add(range);
                    if (++addressMappings > MAX_ADDRESS_MAPPINGS) {
                        throw new SAXException("Logger address mapping limit reached");
                    }
                }
                addressText = null;
            } else if ("ecu".equals(qName)) {
                ecuIds = new ArrayList<>();
            } else if (("parameter".equals(qName) || "ecuparam".equals(qName)
                    || "switch".equals(qName))
                    && parameter != null) {
                try {
                    parameters.add(parameter.build());
                } catch (IllegalArgumentException ex) {
                    throw new SAXException(ex.getMessage(), ex);
                }
                if (parameters.size() > MAX_PARAMETERS) {
                    throw new SAXException("Logger parameter limit reached");
                }
                parameter = null;
                ecuIds = new ArrayList<>();
                addressText = null;
            } else if ("protocol".equals(qName)) {
                inProtocol = false;
            }
        }

        private static String value(Attributes attributes, String name) {
            String value = attributes.getValue(name);
            return value == null ? "" : value.trim();
        }

        private static List<String> splitIds(String ids) {
            List<String> values = new ArrayList<>();
            for (String id : ids.split(",")) {
                if (!id.trim().isEmpty()) values.add(id.trim());
            }
            return values;
        }

        private static int parsePositive(String value, String label)
                throws SAXException {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 1) throw new NumberFormatException();
                return parsed;
            } catch (NumberFormatException ex) {
                throw new SAXException(label + " is invalid", ex);
            }
        }

        private static int parseTarget(String value) throws SAXException {
            if (value.isEmpty()) return 1;
            int target = parsePositive(value, "Logger parameter target");
            if (target > 3) {
                throw new SAXException("Logger parameter target is invalid");
            }
            return target;
        }

        private static int parseBit(String value) throws SAXException {
            try {
                int bit = Integer.parseInt(value);
                if (bit < 0 || bit > 7) throw new NumberFormatException();
                return bit;
            } catch (NumberFormatException ex) {
                throw new SAXException("Logger switch bit is invalid", ex);
            }
        }

        private static int parseAddress(String value) throws SAXException {
            try {
                String trimmed = value.trim();
                long parsed = trimmed.startsWith("0x") || trimmed.startsWith("0X")
                        ? Long.parseLong(trimmed.substring(2), 16)
                        : Long.parseLong(trimmed);
                if (parsed < 0 || parsed > 0xFFFFFF) throw new NumberFormatException();
                return (int) parsed;
            } catch (NumberFormatException ex) {
                throw new SAXException("Logger address is invalid", ex);
            }
        }
    }

    private static final class ParameterBuilder {
        private final String id;
        private final String name;
        private final String description;
        private final int target;
        private final Map<String, List<PortableLoggerAddress>> addresses =
                new LinkedHashMap<>();
        private final List<String> dependencies = new ArrayList<>();
        private final List<PortableLoggerConversion> conversions = new ArrayList<>();

        private ParameterBuilder(String id, String name, String description,
                int target) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.target = target;
        }

        private PortableLoggerParameter build() {
            return new PortableLoggerParameter(id, name, description, target,
                    addresses, dependencies, conversions);
        }
    }
}
