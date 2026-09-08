/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.portable.logger.Elm327ReadOnlyRecorder;
import com.romraider.portable.logger.Elm327Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/** One worker owns both adapter and CSV; the UI reads bounded snapshots. */
final class ElmAdapterTestRun implements Runnable {
    interface Factory { Elm327Session open(String port, int baud) throws IOException; }
    record Configuration(String port, int baud, Elm327Session.Protocol protocol, int seconds, Path output) {
        Configuration {
            if (port == null || port.isBlank() || port.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Enter the ELM adapter's serial port");
            if (baud < 1200 || baud > 2_000_000 || protocol == null || seconds < 1 || seconds > 300 || output == null)
                throw new IllegalArgumentException("Invalid adapter test configuration");
        }
    }
    private final Configuration config;
    private final Factory factory;
    private final Elm327ReadOnlyRecorder recorder;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Thread worker;
    private volatile String status = "Ready";
    private volatile Elm327ReadOnlyRecorder.Progress progress;
    private volatile boolean finished;
    private volatile boolean created;
    private volatile boolean successful;
    ElmAdapterTestRun(Configuration config) {
        this(config, (port, baud) -> new Elm327Session(ElmSerialLink.open(port, baud)), new Elm327ReadOnlyRecorder());
    }
    ElmAdapterTestRun(Configuration config, Factory factory, Elm327ReadOnlyRecorder recorder) {
        this.config = config; this.factory = factory; this.recorder = recorder;
    }
    void start() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Test already started");
        Thread thread = new Thread(this, "rr2-read-only-adapter-test"); thread.setDaemon(true); thread.start();
    }
    @Override public void run() {
        worker = Thread.currentThread();
        try {
            checkCancelled();
            // Refuse overwrites before opening a device, including a symbolic-link target.
            try (var writer = Files.newBufferedWriter(config.output(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                checkCancelled(); status = "Opening selected serial port…";
                try (Elm327Session session = factory.open(config.port(), config.baud())) {
                    checkCancelled(); status = "Checking adapter settings and supported standard OBD-II channels…";
                    session.initialize(config.protocol(), 30_000, cancelled::get);
                    checkCancelled(); status = "Recording read-only standard OBD-II data…";
                    progress = recorder.record(session, writer, config.seconds(), cancelled::get, update -> progress = update);
                }
            }
            successful = !cancelled.get() && progress != null && progress.validValues > 0;
            status = cancelled.get() ? "Stopped. Completed rows retained."
                    : successful ? "Recording complete. Review the CSV and compare readings with the vehicle."
                    : "No valid values recorded. This is not a successful compatibility test.";
        } catch (Exception | LinkageError failure) {
            String message = failure instanceof java.nio.file.FileAlreadyExistsException
                    ? "CSV already exists; choose a new filename."
                    : failure instanceof java.nio.file.AccessDeniedException
                    ? "CSV location is not writable; choose a writable folder."
                    : failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            status = cancelled.get() ? "Stopped. " + message : "Test stopped: " + message;
            if (failure.getSuppressed().length > 0) status += " Cleanup also reported an error; unplug the adapter before retrying.";
        } finally {
            worker = null; finished = true;
        }
    }
    private void checkCancelled() throws IOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new IOException("Adapter test cancelled");
    }
    void cancel() {
        cancelled.set(true);
        if (!finished) status = "Stopping and closing the adapter…";
        Thread thread = worker; if (thread != null) thread.interrupt();
    }
    boolean isFinished() { return finished; }
    boolean isSuccessful() { return successful; }
    boolean isCreated() { return created; }
    String status() { return status; }
    Elm327ReadOnlyRecorder.Progress progress() { return progress; }
}
