/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.api.LoggerChannelKind;
import com.romraider.logger.runtime.*;
import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.definition.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.stage.*;

/** Reviewed desktop/Android channel exchange; never transfers connection state. */
final class FxLoggerSetupTransfer implements AutoCloseable {
    private final Window owner;
    private final LoggerDesktopRuntime runtime;
    private final BiPredicate<String, String> review;
    private final Consumer<String> status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "rr2-logger-setup-transfer"); thread.setDaemon(true); return thread;
    });
    private Future<?> pending;
    private volatile long generation;
    private volatile boolean closed;

    FxLoggerSetupTransfer(Window owner, LoggerDesktopRuntime runtime, Consumer<String> status) {
        this(owner, runtime, status, (title, text) -> FxDialogs.confirmScrollable(owner, title, text, "Continue"));
    }
    FxLoggerSetupTransfer(Window owner, LoggerDesktopRuntime runtime, Consumer<String> status,
            BiPredicate<String, String> review) {
        this.owner = owner; this.runtime = runtime; this.status = status; this.review = review;
    }

    void showImport() {
        if (closed) return;
        cancelWork();
        try {
            runtime.captureChannelSetup();
            FileChooser picker = picker("Import channel setup");
            File selected = picker.showOpenDialog(owner);
            if (selected != null) load(selected);
        } catch (RuntimeException failure) { failure(failure); }
    }
    void showExport() { exportTo(null); }

    void load(File file) {
        if (closed || file == null) return;
        cancelWork(); long ticket = generation;
        final LoggerSetupSnapshot snapshot;
        try { snapshot = runtime.captureChannelSetup(); }
        catch (RuntimeException failure) { failure(failure); return; }
        status.accept("Reading channel setup; current selection is unchanged…");
        pending = worker.submit(() -> {
            try (InputStream input = Files.newInputStream(file.toPath())) {
                PortableLoggerSetup setup = PortableLoggerSetup.read(input);
                byte[] bytes = snapshot.definitionBytes();
                PortableLoggerProtocol protocol = PortableLoggerProtocol.fromId(snapshot.protocol());
                PortableLoggerDefinition definition = PortableLoggerDefinitionReader.read(new ByteArrayInputStream(bytes), protocol.name());
                setup.validateAgainst(protocol, bytes, definition);
                Platform.runLater(() -> {
                    if (!current(ticket)) return;
                    try {
                        runtime.requireCurrentChannelSetup(snapshot);
                        if (!review.test("Import channel setup", summary(setup)
                                + "\n\nReplace all current channel selections, including external selections?\n"
                                + "No connection starts. ECU-specific addresses and calculated inputs still require normal logger validation. Gauge appearance is unchanged.")) {
                            status.accept("Import cancelled; current selection unchanged."); return;
                        }
                        if (!current(ticket)) return;
                        runtime.requireCurrentChannelSetup(snapshot);
                        Map<String, String> ordered = new LinkedHashMap<>();
                        for (PortableLoggerProfile.Selection choice : setup.profile().selections()) ordered.put(choice.getId(), choice.getUnits());
                        pending = worker.submit(() -> {
                            if (!current(ticket) || Thread.currentThread().isInterrupted()) return;
                            try {
                                boolean saved = runtime.applyChannelSetup(snapshot, ordered);
                                Platform.runLater(() -> { if (current(ticket)) status.accept(saved
                                        ? "Channel setup imported. Logger remains disconnected; selection saved to the recovery profile."
                                        : "Channel setup imported for this session, but persistence failed. Export a setup copy before closing."); });
                            } catch (RuntimeException failure) { publishFailure(ticket, failure); }
                        });
                    } catch (RuntimeException failure) { failure(failure); }
                });
            } catch (IOException | RuntimeException failure) { publishFailure(ticket, failure); }
        });
    }

    /** An explicit destination is used by native tests; normal use opens a save picker after review. */
    void exportTo(File destination) {
        if (closed) return;
        cancelWork(); long ticket = generation;
        final LoggerSetupSnapshot snapshot;
        try { snapshot = runtime.captureChannelSetup(); }
        catch (RuntimeException failure) { failure(failure); return; }
        status.accept("Preparing a channel setup snapshot…");
        pending = worker.submit(() -> {
            try {
                byte[] definitionBytes = snapshot.definitionBytes();
                PortableLoggerProtocol protocol = PortableLoggerProtocol.fromId(snapshot.protocol());
                PortableLoggerDefinition definition = PortableLoggerDefinitionReader.read(new ByteArrayInputStream(definitionBytes), protocol.name());
                List<PortableLoggerProfile.Selection> selections = new ArrayList<>();
                for (var channel : snapshot.selectedChannels()) {
                    if (channel.getKind() == LoggerChannelKind.EXTERNAL) throw new IOException("External selections cannot be exported to a portable channel setup");
                    selections.add(new PortableLoggerProfile.Selection(channel.getParameterId(), channel.getUnits()));
                }
                PortableLoggerSetup setup = PortableLoggerSetup.capture(protocol, definitionBytes, definition,
                        new PortableLoggerProfile(protocol.name(), selections, Collections.emptyList()));
                byte[] encoded = setup.encode();
                Platform.runLater(() -> {
                    if (!current(ticket)) return;
                    try {
                        runtime.requireCurrentChannelSetup(snapshot);
                        if (!review.test("Export channel setup", summary(setup)
                                + "\n\nIncludes only protocol, ordered channel IDs/units and a definition fingerprint. The receiving app needs the exact same definition."
                                + " No definition contents, ROMs, recordings, private paths, gauge appearance or connection state are included.")) {
                            status.accept("Export cancelled; no file written."); return;
                        }
                        if (!current(ticket)) return;
                        runtime.requireCurrentChannelSetup(snapshot);
                        File file = destination;
                        if (file == null) {
                            FileChooser picker = picker("Export channel setup"); picker.setInitialFileName("RomRaider2-channels.rr2logger");
                            file = picker.showSaveDialog(owner);
                        }
                        if (file == null || !current(ticket)) { status.accept("Export cancelled; no file written."); return; }
                        runtime.requireCurrentChannelSetup(snapshot);
                        final Path target = file.toPath();
                        // The reviewed snapshot is frozen. Later edits cannot change the bytes written.
                        pending = worker.submit(() -> {
                            try {
                                save(encoded, target);
                                Platform.runLater(() -> { if (current(ticket)) status.accept("Reviewed channel setup exported."); });
                            } catch (IOException | RuntimeException failure) { publishFailure(ticket, failure); }
                        });
                    } catch (RuntimeException failure) { failure(failure); }
                });
            } catch (IOException | RuntimeException failure) { publishFailure(ticket, failure); }
        });
    }

    static void save(byte[] bytes, Path path) throws IOException {
        // Revalidate the frozen envelope before opening output, including the size bound.
        PortableLoggerSetup.read(new ByteArrayInputStream(bytes));
        Path target = path.toAbsolutePath().normalize();
        String name = target.getFileName().toString();
        if (!name.contains(".")) target = target.resolveSibling(name + ".rr2logger");
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".rr2logger"))
            throw new IOException("Choose a .rr2logger destination; CSV, XML and ROM files are not setup destinations");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Setup destination must be a regular file, not a directory or symbolic link");
        Path temporary = Files.createTempFile(target.getParent(), ".rr2-setup-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) { checkCancelled(); output.write(buffer); }
                output.force(true);
            }
            checkCancelled();
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Setup export cancelled");
    }
    private static FileChooser picker(String title) {
        FileChooser picker = new FileChooser(); picker.setTitle(title);
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("RomRaider2 channel setups", "*.rr2logger", "*.RR2LOGGER"));
        return picker;
    }
    private static String summary(PortableLoggerSetup setup) {
        StringBuilder summary = new StringBuilder("Protocol: ").append(setup.protocol()).append("\nExact definition fingerprint: ")
                .append(setup.definitionSha256()).append("\nChannels: ").append(setup.profile().size());
        for (var choice : setup.profile().selections()) summary.append("\n").append(choice.getId()).append(" — ").append(choice.getUnits());
        return summary.toString();
    }
    private void publishFailure(long ticket, Exception failure) {
        Platform.runLater(() -> { if (current(ticket)) failure(failure); });
    }
    private void failure(Exception failure) { status.accept("Channel setup transfer failed: " + FxDialogs.rootMessage(failure)); }
    private boolean current(long ticket) { return !closed && ticket == generation; }
    private void cancelWork() { generation++; if (pending != null) pending.cancel(true); }
    @Override public void close() { closed = true; cancelWork(); worker.shutdownNow(); }
}
