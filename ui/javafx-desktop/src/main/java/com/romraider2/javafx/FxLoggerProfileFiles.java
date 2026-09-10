/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.io.BinaryFileIO;
import com.romraider.logger.ecu.profile.UserProfile;
import com.romraider.logger.ecu.profile.xml.UserProfileHandler;
import com.romraider.util.SaxParserFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded XML and destination checks shared by named-profile actions. */
final class FxLoggerProfileFiles {
    static final long MAX_BYTES = 4L * 1024 * 1024;
    private FxLoggerProfileFiles() { }

    static UserProfile parse(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Logger profile exceeds 4 MiB");
        try {
            var handler = new UserProfileHandler();
            SaxParserFactory.getSaxParser().parse(new ByteArrayInputStream(bytes), handler);
            return handler.getUserProfile();
        } catch (Exception failure) { throw new IOException("Not a valid logger XML profile", failure); }
    }

    static Path destination(File file) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        if (path.getFileName() == null) throw new IOException("Choose an .xml profile destination");
        String name = path.getFileName().toString();
        if (!name.contains(".")) path = path.resolveSibling(name + ".xml");
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
            throw new IOException("Logger profiles require an .xml destination");
        return path;
    }

    static byte[] existing(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Profile destination must be a regular file, not a directory or symbolic link");
        byte[] bytes = BinaryFileIO.read(path.toFile(), MAX_BYTES);
        parse(bytes); // An XML definition, settings file or unrelated document is not a profile.
        return bytes;
    }

    static void requireUnchanged(Path path, byte[] expected) throws IOException {
        if (!Arrays.equals(expected, existing(path)))
            throw new IOException("Profile file changed outside this window. Reload it or use Save Profile As with a new filename.");
    }
}
