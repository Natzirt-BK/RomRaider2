/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.io.j2534.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON codec for the private, newline-delimited bridge protocol. */
final class BridgeJson {
    private BridgeJson() {
    }

    static String write(Object value) {
        StringBuilder json = new StringBuilder();
        append(json, value);
        return json.toString();
    }

    static Object read(String json) {
        Parser parser = new Parser(json);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.atEnd()) throw parser.error("Unexpected trailing content");
        return value;
    }

    private static void append(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof String) {
            appendString(json, (String) value);
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Map) {
            json.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) json.append(',');
                first = false;
                appendString(json, String.valueOf(entry.getKey()));
                json.append(':');
                append(json, entry.getValue());
            }
            json.append('}');
        } else if (value instanceof Iterable) {
            json.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) json.append(',');
                first = false;
                append(json, item);
            }
            json.append(']');
        } else if (value.getClass().isArray()) {
            json.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) json.append(',');
                append(json, java.lang.reflect.Array.get(value, i));
            }
            json.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\b': json.append("\\b"); break;
                case '\f': json.append("\\f"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
            }
        }
        json.append('"');
    }

    private static final class Parser {
        private final String json;
        private int offset;

        private Parser(String json) {
            this.json = json;
        }

        private Object value() {
            whitespace();
            if (atEnd()) throw error("Expected a value");
            char c = json.charAt(offset);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') return literal("true", Boolean.TRUE);
            if (c == 'f') return literal("false", Boolean.FALSE);
            if (c == 'n') return literal("null", null);
            if (c == '-' || Character.isDigit(c)) return number();
            throw error("Unexpected character '" + c + "'");
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            whitespace();
            if (take('}')) return result;
            do {
                whitespace();
                if (atEnd() || json.charAt(offset) != '"') {
                    throw error("Expected an object key");
                }
                String key = string();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<Object>();
            whitespace();
            if (take(']')) return result;
            do {
                result.add(value());
                whitespace();
            } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char c = json.charAt(offset++);
                if (c == '"') return result.toString();
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                if (atEnd()) throw error("Unterminated escape");
                char escaped = json.charAt(offset++);
                switch (escaped) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        if (offset + 4 > json.length()) {
                            throw error("Incomplete Unicode escape");
                        }
                        result.append((char) Integer.parseInt(
                                json.substring(offset, offset + 4), 16));
                        offset += 4;
                        break;
                    default: throw error("Unknown escape '" + escaped + "'");
                }
            }
            throw error("Unterminated string");
        }

        private Number number() {
            int start = offset;
            if (json.charAt(offset) == '-') offset++;
            while (!atEnd() && Character.isDigit(json.charAt(offset))) offset++;
            boolean decimal = false;
            if (!atEnd() && json.charAt(offset) == '.') {
                decimal = true;
                offset++;
                while (!atEnd() && Character.isDigit(json.charAt(offset))) offset++;
            }
            if (!atEnd() && (json.charAt(offset) == 'e'
                    || json.charAt(offset) == 'E')) {
                decimal = true;
                offset++;
                if (!atEnd() && (json.charAt(offset) == '+'
                        || json.charAt(offset) == '-')) offset++;
                while (!atEnd() && Character.isDigit(json.charAt(offset))) offset++;
            }
            String value = json.substring(start, offset);
            try {
                if (decimal) return Double.valueOf(value);
                return Long.valueOf(value);
            } catch (NumberFormatException e) {
                throw error("Invalid number '" + value + "'");
            }
        }

        private Object literal(String literal, Object value) {
            if (!json.startsWith(literal, offset)) {
                throw error("Expected " + literal);
            }
            offset += literal.length();
            return value;
        }

        private void whitespace() {
            while (!atEnd() && Character.isWhitespace(json.charAt(offset))) offset++;
        }

        private boolean take(char c) {
            if (!atEnd() && json.charAt(offset) == c) {
                offset++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            whitespace();
            if (!take(c)) throw error("Expected '" + c + "'");
        }

        private boolean atEnd() {
            return offset >= json.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + offset);
        }
    }
}
