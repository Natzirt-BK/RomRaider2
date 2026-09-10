/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.profile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Locale;

/** Complete serialization and synced temporary output before replacing a profile. */
public final class UserProfileWriter {
    private UserProfileWriter() { }

    public static void save(UserProfile profile, Path destination) throws IOException {
        save(profile, destination, (temporary, target) -> Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    @FunctionalInterface public interface ReplacementCheck { void check() throws IOException; }

    /** Check a reviewed destination again immediately before atomic replacement. */
    public static void saveChecked(UserProfile profile, Path destination, ReplacementCheck check) throws IOException {
        save(profile, destination, (temporary, target) -> {
            check.check();
            checkCancelled();
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
    }

    @FunctionalInterface interface Move { void replace(Path temporary, Path target) throws IOException; }

    static void save(UserProfile profile, Path destination, Move move) throws IOException {
        checkCancelled();
        if (profile == null || destination == null) throw new IllegalArgumentException("Profile and destination are required");
        // getBytes can reject invalid XML text. Never open/truncate a target first.
        byte[] bytes = profile.getBytes();
        if (bytes == null || bytes.length == 0) throw new IOException("Profile serialization is empty");
        Path target = destination.toAbsolutePath().normalize();
        if (target.getFileName() == null || !target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
            throw new IOException("Logger profiles require an .xml destination");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Profile destination must be a regular file, not a directory or symbolic link");
        Path temporary = Files.createTempFile(target.getParent(), ".rr2-profile-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) { checkCancelled(); output.write(buffer); }
                output.force(true);
            }
            // No non-atomic fallback: failed replacement preserves the previous profile.
            checkCancelled();
            move.replace(temporary, target);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void checkCancelled() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Profile save cancelled");
    }
}
