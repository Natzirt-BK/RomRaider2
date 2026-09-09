/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Current logger selections in RomRaider XML; not a copy of desktop graph/dashboard preferences. */
public final class PortableLoggerProfileWriter {
    private PortableLoggerProfileWriter() { }

    public static byte[] encode(PortableLoggerProfile profile, PortableLoggerDefinition definition) throws IOException {
        if (profile == null || definition == null) throw new IOException("Load a definition and choose logger channels first");
        if (!profile.getProtocol().isEmpty() && !profile.getProtocol().equalsIgnoreCase(definition.getProtocol())) {
            throw new IOException("Profile protocol does not match the definition");
        }
        if (profile.size() > 256) throw new IOException("Profile exceeds 256 selections");
        StringBuilder parameters = new StringBuilder(), switches = new StringBuilder(), externals = new StringBuilder();
        Set<String> ids = new HashSet<>();
        int order = 0;
        for (PortableLoggerProfile.Selection choice : profile.selections()) {
            if (!ids.add(choice.getId())) throw new IOException("Duplicate profile channel ID");
            PortableLoggerParameter parameter = definition.parameter(choice.getId());
            boolean isSwitch = parameter != null && parameter.isSwitch();
            String units = choice.getUnits();
            if (units.isEmpty() && parameter != null && parameter.conversionFor("") != null) {
                units = parameter.conversionFor("").getUnits();
            }
            append(isSwitch ? switches : parameters, isSwitch ? "switch" : "parameter", choice.getId(), units, order++);
        }
        // The reader retains external IDs as unavailable diagnostics, but not their original units/preferences.
        // Preserve those selections rather than silently dropping them. Unknown diagnostics cannot be serialized.
        String suffix = ": external input transport unavailable";
        for (String unsupported : profile.unsupported()) {
            if (!unsupported.endsWith(suffix)) throw new IOException("This profile contains an entry that cannot be saved without loss");
            String id = unsupported.substring(0, unsupported.length() - suffix.length());
            if (id.isEmpty() || !ids.add(id)) throw new IOException("Invalid or duplicate external profile ID");
            append(externals, "external", id, "", order++);
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<profile protocol=\"" + attribute(definition.getProtocol()) + "\">\n"
                + "  <parameters>\n" + parameters + "  </parameters>\n"
                + "  <switches>\n" + switches + "  </switches>\n"
                + "  <externals>\n" + externals + "  </externals>\n</profile>\n";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024 * 1024) throw new IOException("Profile exceeds 1 MiB");
        return bytes;
    }

    private static void append(StringBuilder out, String kind, String id, String units, int order) throws IOException {
        out.append("    <").append(kind).append(" id=\"").append(attribute(id))
                .append("\" livedata=\"selected\" rr2-order=\"").append(order)
                .append("\" units=\"").append(attribute(units)).append("\"/>\n");
    }

    private static String attribute(String value) throws IOException {
        for (int i = 0; i < value.length();) {
            int c = value.codePointAt(i); i += Character.charCount(c);
            if (!(c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF
                    || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF)) {
                throw new IOException("Profile text contains an invalid XML character");
            }
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;")
                .replace("\t", "&#9;").replace("\n", "&#10;").replace("\r", "&#13;");
    }
}
