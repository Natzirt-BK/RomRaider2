/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** One bounded, atomic, app-private setup snapshot; contains no connection state. */
public final class LoggerSetupStore {
    public static final int MAX_DEFINITION_BYTES = 32 * 1024 * 1024;
    private static final int MAX_SELECTIONS = 20_000;
    private static final long MAX_SNAPSHOT_BYTES = MAX_DEFINITION_BYTES + 4L * 1024 * 1024;
    private static final int MAGIC = 0x5252324c;
    private static final int VERSION = 1;
    private static final String FILE_NAME = "logger-setup.workspace";
    private LoggerSetupStore() { }

    public static final class Setup {
        public final PortableLoggerProtocol protocol;
        public final String definitionName;
        public final String profileName;
        public final PortableLoggerProfile profile;
        private final byte[] definition;

        public Setup(PortableLoggerProtocol protocol, String definitionName,
                byte[] definition, String profileName, PortableLoggerProfile profile) {
            if (protocol == null || definitionName == null || profileName == null
                    || definition == null || definition.length > MAX_DEFINITION_BYTES) {
                throw new IllegalArgumentException("Invalid saved logger setup");
            }
            if (profile != null && ((!profile.getProtocol().isEmpty()
                    && !profile.getProtocol().equalsIgnoreCase(protocol.name()))
                    || profile.size() > MAX_SELECTIONS)) {
                throw new IllegalArgumentException("Invalid saved logger profile");
            }
            this.protocol = protocol;
            this.definitionName = definitionName;
            this.definition = definition.clone();
            this.profileName = profileName;
            this.profile = profile;
        }
        public byte[] definitionBytes() { return definition.clone(); }
    }

    public static byte[] readDefinition(InputStream input) throws IOException {
        if (input == null) throw new IOException("Logger definition is unavailable");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() > MAX_DEFINITION_BYTES - count) {
                throw new IOException("Logger definition exceeds the 32 MiB limit");
            }
            output.write(buffer, 0, count);
        }
        if (output.size() == 0) throw new IOException("Logger definition is empty");
        return output.toByteArray();
    }

    public static void save(File directory, Setup setup) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Logger setup storage is unavailable");
        }
        File target = new File(directory, FILE_NAME);
        File temporary = File.createTempFile("logger-setup-", ".tmp", directory);
        try {
            try (FileOutputStream file = new FileOutputStream(temporary);
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(file))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeUTF(setup.protocol.name());
                output.writeUTF(setup.definitionName);
                output.writeInt(setup.definition.length);
                output.write(setup.definition);
                output.writeUTF(setup.profileName);
                output.writeBoolean(setup.profile != null);
                if (setup.profile != null) {
                    output.writeInt(setup.profile.selections().size());
                    for (PortableLoggerProfile.Selection selected : setup.profile.selections()) {
                        output.writeUTF(selected.getId());
                        output.writeUTF(selected.getUnits());
                    }
                    output.writeInt(setup.profile.unsupported().size());
                    for (String unsupported : setup.profile.unsupported()) output.writeUTF(unsupported);
                }
                output.flush();
                file.getFD().sync();
            }
            if (temporary.length() > MAX_SNAPSHOT_BYTES) throw new IOException("Logger setup is too large");
            // Fail without replacing the last valid snapshot if atomic rename is unavailable.
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    public static Setup restore(File directory) throws IOException {
        File source = new File(directory, FILE_NAME);
        if (!source.exists()) return null;
        if (source.length() > MAX_SNAPSHOT_BYTES) throw new IOException("Saved logger setup is too large");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(source)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported saved logger setup");
            }
            PortableLoggerProtocol protocol = PortableLoggerProtocol.valueOf(input.readUTF());
            String definitionName = input.readUTF();
            int length = bounded(input.readInt(), MAX_DEFINITION_BYTES);
            byte[] definition = new byte[length];
            input.readFully(definition);
            String profileName = input.readUTF();
            PortableLoggerProfile profile = null;
            if (input.readBoolean()) {
                int count = bounded(input.readInt(), MAX_SELECTIONS);
                List<PortableLoggerProfile.Selection> selections = new ArrayList<>();
                for (int i = 0; i < count; i++) selections.add(
                        new PortableLoggerProfile.Selection(input.readUTF(), input.readUTF()));
                int unsupportedCount = bounded(input.readInt(), MAX_SELECTIONS - count);
                List<String> unsupported = new ArrayList<>();
                for (int i = 0; i < unsupportedCount; i++) unsupported.add(input.readUTF());
                profile = new PortableLoggerProfile(protocol.name(), selections, unsupported);
            }
            if (input.read() != -1) throw new IOException("Saved logger setup has extra data");
            return new Setup(protocol, definitionName, definition, profileName, profile);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Saved logger setup is invalid", ex);
        }
    }

    private static int bounded(int value, int maximum) throws IOException {
        if (value < 0 || value > maximum) throw new IOException("Saved logger setup size is invalid");
        return value;
    }
}
