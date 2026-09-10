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
    private final Consumer<String> showError;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "rr2-logger-setup-transfer"); thread.setDaemon(true); return thread;
    });
    private Future<?> pending;
    private volatile long generation;
    private volatile boolean closed;
    // Explicit file ownership is separate from the runtime's automatic recovery path.
    private Path profilePath;
    private byte[] profileBytes;
    private List<String> unavailableProfileIds = List.of();

    FxLoggerSetupTransfer(Window owner, LoggerDesktopRuntime runtime, Consumer<String> status) {
        this(owner, runtime, status, (title, text) -> FxDialogs.confirmScrollable(owner, title, text, "Continue"),
                message -> FxDialogs.error(owner, "Logger setup", message));
    }
    FxLoggerSetupTransfer(Window owner, LoggerDesktopRuntime runtime, Consumer<String> status,
            BiPredicate<String, String> review) {
        this(owner, runtime, status, review, message -> {});
    }
    FxLoggerSetupTransfer(Window owner, LoggerDesktopRuntime runtime, Consumer<String> status,
            BiPredicate<String, String> review, Consumer<String> showError) {
        this.owner = owner; this.runtime = runtime; this.status = status; this.review = review;
        this.showError = showError;
    }

    void showProfileLoad() {
        if (closed) return;
        cancelWork();
        try {
            runtime.captureChannelSetup();
            FileChooser picker = new FileChooser();
            picker.setTitle("Load Logger Profile");
            picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Logger profiles", "*.xml", "*.XML"));
            File selected = picker.showOpenDialog(owner);
            if (selected != null) loadProfile(selected);
        } catch (RuntimeException failure) { failure(failure); }
    }

    Path profilePath() { return profilePath; }
    void showProfileSave() { saveProfileTo(profilePath == null ? null : profilePath.toFile()); }
    void showProfileSaveAs() { saveProfileTo(null); }
    void reloadProfile() {
        if (closed) return;
        if (profilePath == null) {
            failure(new IllegalStateException("Load or save a logger profile in this window before reloading it."));
            return;
        }
        loadProfile(profilePath.toFile());
    }

    /** Normal Save As opens a picker; explicit destinations support isolated native tests. */
    void saveProfileTo(File destination) {
        if (closed) return;
        cancelWork(); long ticket = generation;
        try {
            LoggerSetupSnapshot snapshot = runtime.captureChannelSetup();
            var profile = runtime.captureLoggerProfile(snapshot);
            File chosen = destination;
            if (chosen == null) {
                FileChooser picker = new FileChooser();
                picker.setTitle("Save Logger Profile As");
                picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Logger profiles", "*.xml", "*.XML"));
                picker.setInitialFileName(profilePath == null ? "RomRaider2-profile.xml" : profilePath.getFileName().toString());
                if (profilePath != null && Files.isDirectory(profilePath.getParent()))
                    picker.setInitialDirectory(profilePath.getParent().toFile());
                chosen = picker.showSaveDialog(owner);
            }
            if (!current(ticket)) return;
            if (chosen == null) { status.accept("Profile save cancelled; no file written."); return; }
            runtime.requireCurrentChannelSetup(snapshot);
            Path target = FxLoggerProfileFiles.destination(chosen);
            boolean sameFile = target.equals(profilePath);
            byte[] loadedBytes = sameFile ? profileBytes : null;
            List<String> omitted = unavailableProfileIds.stream()
                    .filter(id -> !profile.getSelectedIds().contains(id)).toList();
            status.accept("Preparing logger XML profile…");
            pending = worker.submit(() -> {
                try {
                    byte[] encoded = profile.getBytes();
                    FxLoggerProfileFiles.parse(encoded);
                    byte[] previous = FxLoggerProfileFiles.existing(target);
                    if (sameFile && !Arrays.equals(loadedBytes, previous))
                        throw new IOException("Profile file changed outside this window. Reload it or use Save Profile As with a new filename.");
                    if (previous != null) {
                        String protocol = FxLoggerProfileFiles.parse(previous).getProtocol();
                        if (!protocol.isEmpty() && !protocol.equalsIgnoreCase(profile.getProtocol()))
                            throw new IOException("Destination belongs to another logger protocol. Use Save Profile As with a new filename.");
                    }
                    Platform.runLater(() -> {
                        if (!current(ticket)) return;
                        try {
                            runtime.requireCurrentChannelSetup(snapshot);
                            String detail = "File: " + target + "\nProtocol: " + profile.getProtocol()
                                    + "\nSelected channels: " + profile.getSelectedIds().size()
                                    + "\n" + String.join(", ", profile.getSelectedIds())
                                    + (omitted.isEmpty() ? "" : "\n\nUnavailable selections from the loaded profile will not be saved: "
                                            + String.join(", ", omitted))
                                    + "\n\n" + (previous == null ? "Create this profile?" : "Replace this profile with the current selections and units?")
                                    + "\nGauge layout, recordings, definitions and connection settings are not included."
                                    + " The automatic recovery profile is separate.";
                            if (!review.test("Save Logger Profile", detail)) {
                                status.accept("Profile save cancelled; no file written."); return;
                            }
                            if (!current(ticket)) return;
                            runtime.requireCurrentChannelSetup(snapshot);
                            pending = worker.submit(() -> {
                                try {
                                    com.romraider.logger.ecu.profile.UserProfileWriter.saveChecked(profile, target, () -> {
                                        if (!current(ticket)) throw new InterruptedIOException("Profile save cancelled");
                                        runtime.requireCurrentChannelSetup(snapshot);
                                        FxLoggerProfileFiles.requireUnchanged(target, previous);
                                    });
                                    Platform.runLater(() -> {
                                        if (!current(ticket)) return;
                                        profilePath = target; profileBytes = encoded; unavailableProfileIds = List.of();
                                        status.accept("Logger profile saved: " + target + ". Logger remains disconnected.");
                                    });
                                } catch (IOException | RuntimeException failure) { publishFailure(ticket, failure); }
                            });
                        } catch (RuntimeException failure) { failure(failure); }
                    });
                } catch (IOException | RuntimeException failure) { publishFailure(ticket, failure); }
            });
        } catch (IOException | RuntimeException failure) { failure(failure); }
    }

    void loadProfile(File file) {
        if (closed || file == null) return;
        cancelWork(); long ticket = generation;
        final LoggerSetupSnapshot snapshot;
        try { snapshot = runtime.captureChannelSetup(); }
        catch (RuntimeException failure) { failure(failure); return; }
        status.accept("Reading logger profile; current selection is unchanged…");
        pending = worker.submit(() -> {
            try {
                byte[] bytes = com.romraider.io.BinaryFileIO.read(file, FxLoggerProfileFiles.MAX_BYTES);
                var profile = FxLoggerProfileFiles.parse(bytes);
                Platform.runLater(() -> {
                    if (!current(ticket)) return;
                    try {
                        var preview = runtime.previewLoggerProfile(snapshot, profile);
                        String detail = "Profile: " + file.getName() + "\nAvailable selected channels: " + preview.available().size()
                                + "\n" + String.join(", ", preview.available())
                                + (preview.unavailable().isEmpty() ? "" : "\n\nUnavailable selections (will not be loaded): "
                                        + String.join(", ", preview.unavailable())
                                        + "\nECU identification or DimeMod discovery may make additional channels available; load the profile again afterward.")
                                + "\n\nReplace current channel selections? You can add or remove channels afterward. "
                                + "The source XML is unchanged. The logger stays disconnected; gauge layout is unchanged.";
                        if (!review.test("Load Logger Profile", detail)) {
                            status.accept("Profile load cancelled; current selection unchanged."); return;
                        }
                        if (!current(ticket)) return;
                        runtime.requireCurrentChannelSetup(snapshot);
                        pending = worker.submit(() -> {
                            if (!current(ticket) || Thread.currentThread().isInterrupted()) return;
                            try {
                                boolean saved = runtime.applyLoggerProfile(snapshot, profile);
                                Platform.runLater(() -> { if (current(ticket)) {
                                    profilePath = file.toPath().toAbsolutePath().normalize();
                                    profileBytes = bytes; unavailableProfileIds = preview.unavailable();
                                    status.accept(saved
                                        ? "Logger profile loaded: " + preview.available().size() + " channels selected; "
                                                + preview.unavailable().size() + " unavailable. Logger remains disconnected."
                                        : "Profile loaded for this session, but its recovery copy could not be saved."); } });
                            } catch (RuntimeException failure) { publishFailure(ticket, failure); }
                        });
                    } catch (RuntimeException failure) { failure(failure); }
                });
            } catch (Exception failure) { publishFailure(ticket, failure); }
        });
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
    private void failure(Exception failure) {
        String message = FxDialogs.rootMessage(failure);
        status.accept("Logger setup action failed: " + message);
        showError.accept(message);
    }
    private boolean current(long ticket) { return !closed && ticket == generation; }
    private void cancelWork() { generation++; if (pending != null) pending.cancel(true); }
    @Override public void close() { closed = true; cancelWork(); worker.shutdownNow(); }
}
