/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import com.romraider.portable.logger.PortableLoggerProtocol;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Selection-only exchange. No definitions, paths, ECU identity or running state. */
public final class PortableLoggerSetup {
    public static final int MAX_BYTES = 128 * 1024;
    public static final int MAX_CHANNELS = 256;
    public static final int MAX_DEFINITION_BYTES = 32 * 1024 * 1024;
    private static final String HEADER = "RomRaider2 logger setup 1";
    private final PortableLoggerProtocol protocol;
    private final String definitionSha256;
    private final PortableLoggerProfile profile;

    private PortableLoggerSetup(PortableLoggerProtocol protocol, String hash,
            List<PortableLoggerProfile.Selection> selections) throws IOException {
        if (protocol == null || hash == null || !hash.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid setup protocol or definition fingerprint");
        if (selections.size() > MAX_CHANNELS) throw new IOException("Setup exceeds 256 channels");
        Set<String> ids = new HashSet<>();
        for (PortableLoggerProfile.Selection selection : selections) {
            checkedText(selection.getId(), false);
            checkedText(selection.getUnits(), true);
            if (!ids.add(selection.getId())) throw new IOException("Duplicate setup channel ID");
        }
        this.protocol = protocol;
        this.definitionSha256 = hash;
        this.profile = new PortableLoggerProfile(protocol.name(), selections, Collections.emptyList());
    }

    public PortableLoggerProtocol protocol() { return protocol; }
    public String definitionSha256() { return definitionSha256; }
    public PortableLoggerProfile profile() { return profile; }

    public static PortableLoggerSetup capture(PortableLoggerProtocol protocol, byte[] definitionBytes,
            PortableLoggerDefinition definition, PortableLoggerProfile profile) throws IOException {
        if (protocol == null || definition == null) throw new IOException("Load a logger definition first");
        if (profile == null || !profile.unsupported().isEmpty())
            throw new IOException("Resolve unsupported profile entries before exporting a setup");
        if (profile.selections().size() > MAX_CHANNELS) throw new IOException("Setup exceeds 256 channels");
        if (!profile.getProtocol().isEmpty() && !profile.getProtocol().equals(protocol.name()))
            throw new IOException("Profile protocol does not match the setup");
        // Materialize legacy empty-unit defaults before transfer, never on import.
        List<PortableLoggerProfile.Selection> explicit = new ArrayList<>();
        for (PortableLoggerProfile.Selection selection : profile.selections()) {
            PortableLoggerParameter parameter = definition.parameter(selection.getId());
            if (parameter == null) throw new IOException("A selected channel is missing from the definition");
            String units = selection.getUnits();
            if (units.isEmpty() && !parameter.getConversions().isEmpty())
                units = parameter.getConversions().get(0).getUnits();
            explicit.add(new PortableLoggerProfile.Selection(selection.getId(), units));
        }
        PortableLoggerSetup setup = new PortableLoggerSetup(protocol, fingerprint(definitionBytes), explicit);
        setup.validateAgainst(protocol, definitionBytes, definition);
        setup.encode(); // Fail size validation before any destination is opened.
        return setup;
    }

    /** Catalog validation only. ECU-specific addresses still require live identification. */
    public void validateAgainst(PortableLoggerProtocol selectedProtocol, byte[] definitionBytes,
            PortableLoggerDefinition definition) throws IOException {
        if (protocol != selectedProtocol || definition == null
                || !protocol.name().equals(definition.getProtocol()))
            throw new IOException("Setup protocol does not match the loaded definition");
        if (!definitionSha256.equals(fingerprint(definitionBytes)))
            throw new IOException("Load the exact same logger definition before importing this setup");
        for (PortableLoggerProfile.Selection selection : profile.selections()) {
            PortableLoggerParameter parameter = definition.parameter(selection.getId());
            if (parameter == null) throw new IOException("A setup channel is missing from the definition");
            int matching = 0;
            boolean exact = false;
            for (PortableLoggerConversion conversion : parameter.getConversions()) {
                if (selection.getUnits().equalsIgnoreCase(conversion.getUnits())) matching++;
                if (selection.getUnits().equals(conversion.getUnits())) exact = true;
            }
            if (!exact || matching != 1)
                throw new IOException("Setup units are missing or ambiguous for " + selection.getId());
        }
    }

    public byte[] encode() throws IOException {
        StringBuilder text = new StringBuilder(HEADER).append('\n')
                .append("protocol=").append(protocol.name()).append('\n')
                .append("definition-sha256=").append(definitionSha256).append('\n')
                .append("channels=").append(profile.selections().size()).append('\n');
        int index = 0;
        for (PortableLoggerProfile.Selection selection : profile.selections()) {
            text.append("channel.").append(index).append(".id=").append(encoded(selection.getId())).append('\n');
            text.append("channel.").append(index++).append(".units=").append(encoded(selection.getUnits())).append('\n');
        }
        byte[] bytes = text.toString().getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_BYTES) throw new IOException("Logger setup exceeds 128 KiB");
        return bytes;
    }

    /** Caller owns the stream. Rejects unknown, duplicate, oversized and trailing fields. */
    public static PortableLoggerSetup read(InputStream input) throws IOException {
        interrupted();
        if (input == null) throw new IOException("Logger setup is unavailable");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            interrupted();
            if (bytes.size() > MAX_BYTES - count) throw new IOException("Logger setup exceeds 128 KiB");
            bytes.write(buffer, 0, count);
        }
        String text = strictUtf8(bytes.toByteArray());
        String[] lines = text.split("\\r?\\n", -1);
        if (lines.length < 5 || !HEADER.equals(lines[0]) || !lines[lines.length - 1].isEmpty())
            throw new IOException("Unsupported or incomplete logger setup");
        Map<String, String> fields = new HashMap<>();
        for (int i = 1; i < lines.length - 1; i++) {
            int equals = lines[i].indexOf('=');
            if (equals <= 0 || fields.put(lines[i].substring(0, equals), lines[i].substring(equals + 1)) != null)
                throw new IOException("Malformed or duplicate logger setup field");
        }
        try {
            PortableLoggerProtocol protocol = PortableLoggerProtocol.valueOf(take(fields, "protocol"));
            String hash = take(fields, "definition-sha256");
            String size = take(fields, "channels");
            if (!size.matches("0|[1-9][0-9]{0,2}")) throw new IOException("Invalid setup channel count");
            int length = Integer.parseInt(size);
            if (length > MAX_CHANNELS) throw new IOException("Setup exceeds 256 channels");
            List<PortableLoggerProfile.Selection> selections = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                String id = decoded(take(fields, "channel." + i + ".id"), false);
                String units = decoded(take(fields, "channel." + i + ".units"), true);
                selections.add(new PortableLoggerProfile.Selection(id, units));
            }
            if (!fields.isEmpty()) throw new IOException("Unknown logger setup field");
            return new PortableLoggerSetup(protocol, hash, selections);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid logger setup value", failure);
        }
    }

    private static String take(Map<String, String> fields, String key) throws IOException {
        String value = fields.remove(key);
        if (value == null) throw new IOException("Missing logger setup field: " + key);
        return value;
    }
    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decoded(String value, boolean empty) throws IOException {
        if (value.length() > 1024) throw new IOException("Setup text field is too long");
        String decoded = strictUtf8(Base64.getDecoder().decode(value));
        checkedText(decoded, empty);
        if (!encoded(decoded).equals(value)) throw new IOException("Noncanonical setup text encoding");
        return decoded;
    }
    private static void checkedText(String value, boolean empty) throws IOException {
        if (value == null || value.length() > 128 || (!empty && value.isEmpty()) || !value.equals(value.trim()))
            throw new IOException("Invalid setup ID or units");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            int type = Character.getType(character);
            if (Character.isISOControl(character) || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR)
                throw new IOException("Control characters are not allowed in setup fields");
            if (Character.isHighSurrogate(character)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new IOException("Invalid Unicode in setup field");
            } else if (Character.isLowSurrogate(character)) throw new IOException("Invalid Unicode in setup field");
        }
    }
    private static String strictUtf8(byte[] value) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
    }
    private static String fingerprint(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_DEFINITION_BYTES)
            throw new IOException("Load a logger definition of at most 32 MiB first");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int offset = 0; offset < bytes.length; offset += 8192) {
                interrupted();
                digest.update(bytes, offset, Math.min(8192, bytes.length - offset));
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            return hash.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void interrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Setup transfer cancelled");
    }
}
