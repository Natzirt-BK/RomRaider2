/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.xml;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes bounded XML once; callers must parse the returned character stream. */
public final class PortableXmlInput {
    private static final Pattern ENCODING = Pattern.compile(
            "[\\t\\r\\n ]encoding[\\t\\r\\n ]*=[\\t\\r\\n ]*(['\"])([A-Za-z][A-Za-z0-9._-]*)\\1");

    private PortableXmlInput() { }

    public static String decodeWithoutEntities(byte[] bytes) throws IOException {
        String detected = null;
        int offset = 0;
        // Four-byte signatures must precede the overlapping UTF-16 BOMs.
        if (starts(bytes, 0, 0, 0xfe, 0xff)) { detected = "UTF-32BE"; offset = 4; }
        else if (starts(bytes, 0xff, 0xfe, 0, 0)) { detected = "UTF-32LE"; offset = 4; }
        else if (starts(bytes, 0xef, 0xbb, 0xbf)) { detected = "UTF-8"; offset = 3; }
        else if (starts(bytes, 0xfe, 0xff)) { detected = "UTF-16BE"; offset = 2; }
        else if (starts(bytes, 0xff, 0xfe)) { detected = "UTF-16LE"; offset = 2; }
        else if (starts(bytes, 0, 0, 0, '<')) detected = "UTF-32BE";
        else if (starts(bytes, '<', 0, 0, 0)) detected = "UTF-32LE";
        else if (starts(bytes, 0, '<', 0, '?')) detected = "UTF-16BE";
        else if (starts(bytes, '<', 0, '?', 0)) detected = "UTF-16LE";

        Charset charset;
        String text;
        if (detected != null) {
            charset = charset(detected);
            text = decode(bytes, offset, charset);
        } else {
            // Only the ASCII XML declaration is inspected before decoding. A
            // declared legacy charset (e.g. ISO-8859-1) still preserves labels.
            String declared = declaredEncoding(new String(bytes, StandardCharsets.ISO_8859_1));
            charset = declared == null ? StandardCharsets.UTF_8 : charset(declared);
            if (!new String("<?xml".getBytes(StandardCharsets.US_ASCII), charset).equals("<?xml")) {
                throw new IOException("XML encoding requires a supported byte-order signature");
            }
            text = decode(bytes, 0, charset);
        }
        String declared = declaredEncoding(text);
        if (declared != null) {
            String name = charset(declared).name();
            boolean genericUnicode = (name.equals("UTF-16") && charset.name().startsWith("UTF-16"))
                    || (name.equals("UTF-32") && charset.name().startsWith("UTF-32"));
            if (!charset.name().equals(name) && !genericUnicode) {
                throw new IOException("XML encoding declaration conflicts with its byte-order signature");
            }
        }
        if (text.contains("<!ENTITY")) {
            throw new IOException("XML entity declarations are not allowed");
        }
        return text;
    }

    private static String declaredEncoding(String text) {
        if (!text.startsWith("<?xml") || text.length() <= 5
                || " \t\r\n".indexOf(text.charAt(5)) < 0) return null;
        int end = text.indexOf("?>");
        if (end < 0) return null; // SAX rejects the incomplete declaration.
        Matcher matcher = ENCODING.matcher(text).region(0, end);
        return matcher.find() ? matcher.group(2) : null;
    }

    private static Charset charset(String name) throws IOException {
        try { return Charset.forName(name); }
        catch (IllegalArgumentException ex) {
            throw new IOException("Unsupported XML encoding: " + name, ex);
        }
    }

    private static String decode(byte[] bytes, int offset, Charset charset) throws IOException {
        try {
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("Malformed XML text for " + charset.name(), ex);
        }
    }

    private static boolean starts(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xff) != prefix[i]) return false;
        }
        return true;
    }
}
