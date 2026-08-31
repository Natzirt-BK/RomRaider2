/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.recovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.romraider.editor.workspace.RomChangeService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.maps.Rom;

/** UI-independent, bounded crash-recovery storage for unsaved ROM data. */
public final class RomRecoveryService {
    public interface Listener {
        void recoveryStateChanged(Rom rom, RecoveryState state,
                RecoverySnapshot snapshot, Throwable failure);
    }

    private static final int DEFAULT_LIMIT = 5;
    private static final long DEFAULT_DELAY_MILLIS = 1800L;
    private static final RomRecoveryService INSTANCE = new RomRecoveryService(
            Paths.get(System.getProperty("user.home"), ".RomRaider",
                    "romraider2-recovery"), DEFAULT_LIMIT,
            DEFAULT_DELAY_MILLIS);

    private final Path root;
    private final int snapshotLimit;
    private final long delayMillis;
    private final ScheduledExecutorService executor;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Rom, ScheduledFuture<?>> pending =
            new WeakHashMap<Rom, ScheduledFuture<?>>();
    private final Map<Rom, RecoveryState> states =
            new WeakHashMap<Rom, RecoveryState>();
    private final Map<Rom, String> keys = new WeakHashMap<Rom, String>();
    private final Map<Rom, Long> generations = new WeakHashMap<Rom, Long>();
    private final List<Listener> listeners =
            new CopyOnWriteArrayList<Listener>();

    public static RomRecoveryService getInstance() { return INSTANCE; }

    public RomRecoveryService(Path root, int snapshotLimit, long delayMillis) {
        if (root == null) throw new IllegalArgumentException("root is required");
        if (snapshotLimit < 1) {
            throw new IllegalArgumentException("snapshot limit must be positive");
        }
        this.root = root.toAbsolutePath().normalize();
        this.snapshotLimit = snapshotLimit;
        this.delayMillis = Math.max(0L, delayMillis);
        executor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    public Thread newThread(Runnable work) {
                        Thread thread = new Thread(work,
                                "RomRaider2 ROM recovery");
                        thread.setDaemon(true);
                        return thread;
                    }
                });
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized RecoveryState getState(Rom rom) {
        RecoveryState state = states.get(rom);
        return state == null ? RecoveryState.IDLE : state;
    }

    /** Debounces rapid cell edits and captures bytes before leaving the caller. */
    public void schedule(Rom rom) {
        if (rom == null) return;
        if (!hasUnsavedChanges(rom)) {
            markResolved(rom);
            return;
        }
        final SnapshotRequest request;
        final long generation;
        synchronized (this) {
            ScheduledFuture<?> previous = pending.remove(rom);
            if (previous != null) previous.cancel(false);
            generation = nextGeneration(rom);
            request = capture(rom);
            states.put(rom, RecoveryState.SCHEDULED);
            pending.put(rom, executor.schedule(new Runnable() {
                public void run() { writeScheduled(rom, request, generation); }
            }, delayMillis, TimeUnit.MILLISECONDS));
        }
        notifyListeners(rom, RecoveryState.SCHEDULED, null, null);
    }

    public RecoverySnapshot snapshotNow(Rom rom) throws IOException {
        if (rom == null || rom.getBinary() == null) {
            throw new IllegalArgumentException("loaded ROM is required");
        }
        final SnapshotRequest request;
        synchronized (this) {
            ScheduledFuture<?> previous = pending.remove(rom);
            if (previous != null) previous.cancel(false);
            nextGeneration(rom);
            request = capture(rom);
        }
        RecoverySnapshot snapshot = write(request);
        synchronized (this) { states.put(rom, RecoveryState.SAVED); }
        notifyListeners(rom, RecoveryState.SAVED, snapshot, null);
        return snapshot;
    }

    /** Clears only recovery artifacts owned by this service for the ROM. */
    public void markResolved(Rom rom) {
        if (rom == null) return;
        final String key;
        synchronized (this) {
            ScheduledFuture<?> future = pending.remove(rom);
            if (future != null) future.cancel(false);
            nextGeneration(rom);
            states.put(rom, RecoveryState.IDLE);
            key = keyFor(rom);
        }
        try {
            deleteOwnedDirectory(ownedDirectory(key));
            synchronized (this) { keys.remove(rom); }
            notifyListeners(rom, RecoveryState.IDLE, null, null);
        } catch (IOException failure) {
            synchronized (this) { states.put(rom, RecoveryState.FAILED); }
            notifyListeners(rom, RecoveryState.FAILED, null, failure);
        }
    }

    public List<RecoverySnapshot> listSnapshots(Rom rom) throws IOException {
        if (rom == null) return Collections.emptyList();
        Path directory = directoryFor(rom);
        return listSnapshots(directory, safe(rom.getFileName(), "Untitled ROM"));
    }

    /** Returns every valid crash snapshot, including snapshots from prior runs. */
    public List<RecoverySnapshot> discoverSnapshots() throws IOException {
        if (!Files.isDirectory(root)) return Collections.emptyList();
        List<RecoverySnapshot> snapshots = new ArrayList<RecoverySnapshot>();
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root)) {
            for (Path directory : directories) {
                if (!Files.isDirectory(directory)) continue;
                snapshots.addAll(listSnapshots(directory, "Untitled ROM"));
            }
        }
        sortNewestFirst(snapshots);
        return Collections.unmodifiableList(snapshots);
    }

    /** Returns only the newest valid snapshot for each recovered ROM. */
    public List<RecoverySnapshot> discoverLatestSnapshots() throws IOException {
        if (!Files.isDirectory(root)) return Collections.emptyList();
        List<RecoverySnapshot> snapshots = new ArrayList<RecoverySnapshot>();
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root)) {
            for (Path directory : directories) {
                if (!Files.isDirectory(directory)) continue;
                List<RecoverySnapshot> versions = listSnapshots(directory,
                        "Untitled ROM");
                if (!versions.isEmpty()) snapshots.add(versions.get(0));
            }
        }
        sortNewestFirst(snapshots);
        return Collections.unmodifiableList(snapshots);
    }

    /** Removes one validated recovery pair without touching a source ROM. */
    public void discard(RecoverySnapshot snapshot) throws IOException {
        if (snapshot == null) return;
        Path binary = snapshot.getBinaryPath().toAbsolutePath().normalize();
        Path metadata = snapshot.getMetadataPath().toAbsolutePath().normalize();
        if (!binary.startsWith(root) || !metadata.startsWith(root)
                || binary.getParent() == null
                || !binary.getParent().equals(metadata.getParent())) {
            throw new IOException("Recovery snapshot is outside the managed root");
        }
        deleteSnapshot(snapshot);
    }

    /** Removes every recovery version belonging to the selected source. */
    public void discardAll(RecoverySnapshot snapshot) throws IOException {
        Path directory = managedDirectory(snapshot);
        deleteOwnedDirectory(directory);
    }

    private Path managedDirectory(RecoverySnapshot snapshot) throws IOException {
        if (snapshot == null) throw new IOException("Recovery snapshot is required");
        Path binary = snapshot.getBinaryPath().toAbsolutePath().normalize();
        Path metadata = snapshot.getMetadataPath().toAbsolutePath().normalize();
        Path directory = binary.getParent();
        if (!binary.startsWith(root) || !metadata.startsWith(root)
                || directory == null || !directory.equals(metadata.getParent())
                || root.equals(directory)) {
            throw new IOException("Recovery snapshot is outside the managed root");
        }
        return directory;
    }

    private List<RecoverySnapshot> listSnapshots(Path directory,
            String fallbackName) throws IOException {
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        List<RecoverySnapshot> snapshots = new ArrayList<RecoverySnapshot>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory,
                "*.bin")) {
            for (Path binary : files) {
                String base = binary.getFileName().toString();
                base = base.substring(0, base.length() - 4);
                Path metadata = directory.resolve(base + ".properties");
                if (!Files.isRegularFile(metadata)) continue;
                long created = parseCreated(base, binary);
                Properties values = new Properties();
                try (InputStream input = Files.newInputStream(metadata)) {
                    values.load(input);
                }
                if (!isValid(binary, values)) continue;
                snapshots.add(new RecoverySnapshot(binary, metadata,
                        parseLong(values.getProperty("created.epoch"), created),
                        safe(values.getProperty("source.name"), fallbackName),
                        safe(values.getProperty("source.path"), ""),
                        safe(values.getProperty("rom.id"), "unknown"),
                        parseInt(values.getProperty("changed.cells"), 0)));
            }
        }
        sortNewestFirst(snapshots);
        return Collections.unmodifiableList(snapshots);
    }

    public Path getRoot() { return root; }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void writeScheduled(Rom rom, SnapshotRequest request,
            long generation) {
        try {
            RecoverySnapshot snapshot = write(request);
            boolean stale;
            synchronized (this) {
                stale = currentGeneration(rom) != generation;
                if (!stale) {
                    pending.remove(rom);
                    states.put(rom, RecoveryState.SAVED);
                }
            }
            if (stale) {
                deleteSnapshot(snapshot);
                return;
            }
            notifyListeners(rom, RecoveryState.SAVED, snapshot, null);
        } catch (Throwable failure) {
            synchronized (this) {
                if (currentGeneration(rom) != generation) return;
                pending.remove(rom);
                states.put(rom, RecoveryState.FAILED);
            }
            notifyListeners(rom, RecoveryState.FAILED, null, failure);
        }
    }

    private RecoverySnapshot write(SnapshotRequest request) throws IOException {
        Path directory = ownedDirectory(request.key);
        Files.createDirectories(directory);
        long created = System.currentTimeMillis();
        String base = created + "-" + sequence.incrementAndGet();
        Path binary = directory.resolve(base + ".bin");
        Path metadata = directory.resolve(base + ".properties");
        atomicWrite(binary, request.binary);
        atomicWrite(metadata, metadata(request, created)
                .getBytes(StandardCharsets.UTF_8));
        prune(directory);
        return new RecoverySnapshot(binary, metadata, created,
                request.sourceName, request.sourcePath, request.romId,
                request.changedCells);
    }

    private static String metadata(SnapshotRequest request, long created)
            throws IOException {
        Properties values = new Properties();
        values.setProperty("format.version", "1");
        values.setProperty("created.epoch", Long.toString(created));
        values.setProperty("source.name", request.sourceName);
        values.setProperty("source.path", request.sourcePath);
        values.setProperty("rom.id", request.romId);
        values.setProperty("binary.size", Integer.toString(request.binary.length));
        values.setProperty("binary.sha256", sha256Hex(request.binary));
        values.setProperty("changed.cells",
                Integer.toString(request.changedCells));
        StringWriter writer = new StringWriter();
        values.store(writer, "RomRaider2 crash recovery snapshot");
        return writer.toString();
    }

    private void prune(Path directory) throws IOException {
        List<Path> binaries = new ArrayList<Path>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory,
                "*.bin")) {
            for (Path file : files) binaries.add(file);
        }
        Collections.sort(binaries, new Comparator<Path>() {
            public int compare(Path first, Path second) {
                return compareSnapshotNames(first.getFileName().toString(),
                        second.getFileName().toString());
            }
        });
        while (binaries.size() > snapshotLimit) {
            Path old = binaries.remove(0);
            String name = old.getFileName().toString();
            String base = name.substring(0, name.length() - 4);
            Files.deleteIfExists(old);
            Files.deleteIfExists(directory.resolve(base + ".properties"));
        }
    }

    private static void atomicWrite(Path target, byte[] data)
            throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, data);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private SnapshotRequest capture(Rom rom) {
        byte[] binary = rom.getBinary();
        if (binary == null) binary = new byte[0];
        String sourceName = safe(rom.getFileName(), "Untitled ROM");
        String sourcePath = rom.getFullFileName() == null ? ""
                : rom.getFullFileName().getAbsolutePath();
        String romId;
        try { romId = safe(rom.getRomIDString(), "unknown"); }
        catch (RuntimeException ignored) { romId = "unknown"; }
        return new SnapshotRequest(keyFor(rom), binary.clone(), sourceName,
                sourcePath, romId, RomChangeSummary.countChangedCells(rom));
    }

    private boolean hasUnsavedChanges(Rom rom) {
        return rom.getBinary() != null && (RomChangeSummary.countChangedCells(rom) > 0
                || RomChangeService.hasBinaryChanges(rom));
    }

    private Path directoryFor(Rom rom) {
        return ownedDirectory(keyFor(rom));
    }

    /** Keeps a ROM's recovery location stable across Save As path changes. */
    private synchronized String keyFor(Rom rom) {
        String key = keys.get(rom);
        if (key != null) return key;
        String sourceName = safe(rom.getFileName(), "Untitled ROM");
        String sourcePath = rom.getFullFileName() == null ? ""
                : rom.getFullFileName().getAbsolutePath();
        String romId;
        try { romId = safe(rom.getRomIDString(), "unknown"); }
        catch (RuntimeException ignored) { romId = "unknown"; }
        key = sha256Hex((sourcePath + "\n" + sourceName + "\n" + romId)
                .getBytes(StandardCharsets.UTF_8)).substring(0, 24);
        keys.put(rom, key);
        return key;
    }

    private long nextGeneration(Rom rom) {
        long next = currentGeneration(rom) + 1L;
        generations.put(rom, next);
        return next;
    }

    private long currentGeneration(Rom rom) {
        Long generation = generations.get(rom);
        return generation == null ? 0L : generation.longValue();
    }

    private Path ownedDirectory(String key) {
        Path directory = root.resolve(key).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalStateException("Recovery path escaped its root");
        }
        return directory;
    }

    private void deleteOwnedDirectory(Path directory) throws IOException {
        if (!directory.startsWith(root) || !Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(directory);
    }

    private void deleteSnapshot(RecoverySnapshot snapshot) throws IOException {
        Files.deleteIfExists(snapshot.getBinaryPath());
        Files.deleteIfExists(snapshot.getMetadataPath());
        Path directory = snapshot.getBinaryPath().getParent();
        if (directory == null || !directory.startsWith(root)
                || !Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> remaining = Files.newDirectoryStream(directory)) {
            if (!remaining.iterator().hasNext()) Files.deleteIfExists(directory);
        }
    }

    private void notifyListeners(Rom rom, RecoveryState state,
            RecoverySnapshot snapshot, Throwable failure) {
        for (Listener listener : listeners) {
            listener.recoveryStateChanged(rom, state, snapshot, failure);
        }
    }

    private static void sortNewestFirst(List<RecoverySnapshot> snapshots) {
        Collections.sort(snapshots, new Comparator<RecoverySnapshot>() {
            public int compare(RecoverySnapshot first, RecoverySnapshot second) {
                int createdOrder = Long.compare(second.getCreatedAt(),
                        first.getCreatedAt());
                if (createdOrder != 0) return createdOrder;
                return compareSnapshotNames(
                        second.getBinaryPath().getFileName().toString(),
                        first.getBinaryPath().getFileName().toString());
            }
        });
    }

    private static long parseCreated(String base, Path file) throws IOException {
        int separator = base.indexOf('-');
        try {
            return Long.parseLong(separator < 0 ? base
                    : base.substring(0, separator));
        } catch (NumberFormatException ignored) {
            return Files.getLastModifiedTime(file).toMillis();
        }
    }

    private static int compareSnapshotNames(String first, String second) {
        long firstCreated = namePart(first, 0);
        long secondCreated = namePart(second, 0);
        int createdOrder = Long.compare(firstCreated, secondCreated);
        if (createdOrder != 0) return createdOrder;
        return Long.compare(namePart(first, 1), namePart(second, 1));
    }

    private static long namePart(String name, int part) {
        String base = name.endsWith(".bin")
                ? name.substring(0, name.length() - 4) : name;
        int separator = base.indexOf('-');
        String value = part == 0
                ? (separator < 0 ? base : base.substring(0, separator))
                : (separator < 0 ? "0" : base.substring(separator + 1));
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private static boolean isValid(Path binary, Properties metadata)
            throws IOException {
        long expectedSize = parseLong(metadata.getProperty("binary.size"), -1L);
        String expectedHash = metadata.getProperty("binary.sha256");
        if (expectedSize < 0L || expectedSize != Files.size(binary)
                || expectedHash == null || expectedHash.trim().isEmpty()) {
            return false;
        }
        return expectedHash.equalsIgnoreCase(
                sha256Hex(Files.readAllBytes(binary)));
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value);
            StringBuilder text = new StringBuilder();
            for (byte item : digest) {
                text.append(String.format("%02x", item & 0xff));
            }
            return text.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static final class SnapshotRequest {
        private final String key;
        private final byte[] binary;
        private final String sourceName;
        private final String sourcePath;
        private final String romId;
        private final int changedCells;

        private SnapshotRequest(String key, byte[] binary, String sourceName,
                String sourcePath, String romId, int changedCells) {
            this.key = key;
            this.binary = binary;
            this.sourceName = sourceName;
            this.sourcePath = sourcePath;
            this.romId = romId;
            this.changedCells = changedCells;
        }
    }
}
