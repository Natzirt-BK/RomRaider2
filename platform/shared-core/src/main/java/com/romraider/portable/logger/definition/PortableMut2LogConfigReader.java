/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Imports only the read-only MUT2 parameter subset of an OpenPort logcfg.txt.
 * Hardware commands, conditions, external sensors and unknown keys fail closed.
 * Priority is validated and retained as documentation; polling is one full cycle.
 */
public final class PortableMut2LogConfigReader {
    public static final int MAX_CONFIG_CHARS = 256 * 1024;

    private PortableMut2LogConfigReader() { }

    public static PortableLoggerDefinition read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Logger configuration is missing");
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (text.length() + count > MAX_CONFIG_CHARS) {
                throw new IOException("MUT2 configuration is too large");
            }
            text.append(buffer, 0, count);
        }
        List<PortableLoggerParameter> parameters = new ArrayList<>();
        Map<String, String> fields = new LinkedHashMap<>();
        boolean typeSeen = false;
        int lineNumber = 0;
        try {
            for (String raw : text.toString().replace("\uFEFF", "").split("\r?\n")) {
                lineNumber++;
                String line = raw.split("[;#]", 2)[0].trim();
                if (line.isEmpty()) continue;
                int separator = line.indexOf('=');
                if (separator < 1) throw new IllegalArgumentException("Expected key=value");
                String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                if (key.equals("type")) {
                    if (typeSeen || !fields.isEmpty() || !value.equalsIgnoreCase("mut2")) {
                        throw new IllegalArgumentException("Exactly one type=mut2 is required first");
                    }
                    typeSeen = true;
                    continue;
                }
                if (!typeSeen) throw new IllegalArgumentException("Select type=mut2 first");
                if (key.equals("paramname")) {
                    if (!fields.isEmpty()) parameters.add(parameter(fields));
                    fields.clear();
                    if (parameters.size() >= 256) throw new IllegalArgumentException("Too many parameters");
                } else if (!key.equals("paramid") && !key.equals("scalingrpn")
                        && !key.equals("priority")) {
                    throw new IllegalArgumentException("Unsupported logcfg option: " + key);
                } else if (!fields.containsKey("paramname")) {
                    throw new IllegalArgumentException("Parameter name is required first");
                }
                if (value.isEmpty() || fields.put(key, value) != null) {
                    throw new IllegalArgumentException("Empty or duplicate option: " + key);
                }
            }
            if (!fields.isEmpty()) parameters.add(parameter(fields));
            if (!typeSeen || parameters.isEmpty()) throw new IllegalArgumentException("No MUT2 parameters found");
            return new PortableLoggerDefinition("OpenPort logcfg", "MUT2", parameters);
        } catch (IllegalArgumentException ex) {
            throw new IOException("MUT2 configuration near line " + lineNumber + ": " + ex.getMessage(), ex);
        }
    }

    private static PortableLoggerParameter parameter(Map<String, String> fields) {
        String name = fields.get("paramname");
        if (name.length() > 128) throw new IllegalArgumentException("Parameter name is too long");
        String rawId = fields.get("paramid");
        if (rawId == null) throw new IllegalArgumentException("Missing paramid for " + name);
        int pid = rawId.toLowerCase(Locale.ROOT).startsWith("0x")
                ? Integer.parseInt(rawId.substring(2), 16) : Integer.parseInt(rawId);
        if (pid < 0 || pid > 255) throw new IllegalArgumentException("MUT2 PID must fit in one byte");
        int priority = Integer.parseInt(fields.getOrDefault("priority", "1"));
        if (priority < 1 || priority > 255) throw new IllegalArgumentException("Invalid priority");
        String expression = expression(fields.getOrDefault("scalingrpn", "x"));
        com.romraider.portable.logger.PortableExpression compiled =
                com.romraider.portable.logger.PortableExpression.compile(expression);
        for (int value = 0; value <= 255; value++) {
            if (!Double.isFinite(compiled.evaluate(value))) {
                throw new IllegalArgumentException("Scaling produces an invalid value for " + name);
            }
        }
        String units = expression.equals("x") ? "raw" : "scaled";
        return new PortableLoggerParameter(name, name,
                "Imported OpenPort PID; priority " + priority
                        + " (Android polls all selected channels each cycle). Units are as named in the configuration.",
                1, Collections.singletonMap(PortableLoggerParameter.ALL_ECUS,
                        Collections.singletonList(new PortableLoggerAddress(pid, 1))), Collections.emptyList(),
                Collections.singletonList(new PortableLoggerConversion(units, expression, "0.000", "uint8", "big")));
    }

    private static String expression(String rpn) {
        if (rpn.length() > 2048) throw new IllegalArgumentException("Scaling is too long");
        String[] tokens = rpn.split(",", -1);
        if (tokens.length > 64) throw new IllegalArgumentException("Too many scaling operations");
        Deque<String> stack = new ArrayDeque<>();
        for (String raw : tokens) {
            String token = raw.trim();
            if (token.length() == 1 && "+-*/".contains(token)) {
                if (stack.size() < 2) throw new IllegalArgumentException("Invalid RPN operands");
                String right = stack.pop();
                String left = stack.pop();
                stack.push("(" + left + token + right + ")");
            } else if (token.equals("x")) {
                stack.push(token);
            } else {
                if (!token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
                        || !Double.isFinite(Double.parseDouble(token))) {
                    throw new IllegalArgumentException("Unsupported scaling token: " + token);
                }
                stack.push("(" + token + ")");
            }
        }
        if (stack.size() != 1) throw new IllegalArgumentException("Invalid RPN result");
        return stack.pop();
    }
}
