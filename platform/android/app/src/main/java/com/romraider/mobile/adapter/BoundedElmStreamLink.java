/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.mobile.adapter;

import com.romraider.portable.logger.Elm327Session;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded blocking-stream bridge for a single Elm327Session owner. */
public final class BoundedElmStreamLink implements Elm327Session.Link {
    /** close must promptly abort connect and stream I/O from another thread. */
    public interface Endpoint extends Closeable {
        void connect() throws IOException;
        InputStream input() throws IOException;
        OutputStream output() throws IOException;
    }

    private static final int CAPACITY = 4096;
    private final Endpoint endpoint;
    private final Object lock = new Object();
    private final byte[] received = new byte[CAPACITY];
    private final AtomicBoolean operation = new AtomicBoolean();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RR2 adapter output");
        thread.setDaemon(true); return thread;
    });
    private boolean started, ready, closed;
    private int head, count;
    private IOException failure;
    private InputStream input;
    private OutputStream output;
    private FutureTask<Void> pending;

    public BoundedElmStreamLink(Endpoint endpoint) { this.endpoint = Objects.requireNonNull(endpoint); }

    /** Call off the UI thread. Publish this object before opening so Stop can close it. */
    public void open(int timeoutMillis) throws IOException {
        validateTimeout(timeoutMillis);
        if (!operation.compareAndSet(false, true)) throw new IOException("Adapter operation already running");
        try {
            synchronized (lock) {
                if (started || closed) throw new IOException("Create a new adapter link to connect");
                started = true;
            }
            bounded(() -> {
                endpoint.connect();
                InputStream nextInput = Objects.requireNonNull(endpoint.input());
                OutputStream nextOutput = Objects.requireNonNull(endpoint.output());
                synchronized (lock) { requireOpen(); input = nextInput; output = nextOutput; }
            }, timeoutMillis);
            synchronized (lock) {
                requireOpen(); ready = true;
                Thread reader = new Thread(this::receive, "RR2 adapter input");
                reader.setDaemon(true); reader.start();
            }
        } finally { operation.set(false); }
    }

    @Override public void write(byte[] command, int timeoutMillis) throws IOException {
        Objects.requireNonNull(command); validateTimeout(timeoutMillis);
        if (command.length == 0 || command.length > CAPACITY) throw new IllegalArgumentException("Invalid command size");
        if (!operation.compareAndSet(false, true)) throw new IOException("Adapter operation already running");
        try {
            synchronized (lock) { requireReady(); }
            byte[] copy = command.clone();
            bounded(() -> { output.write(copy); output.flush(); }, timeoutMillis);
        } finally { operation.set(false); }
    }

    @Override public int read(byte[] buffer, int timeoutMillis) throws IOException {
        Objects.requireNonNull(buffer); validateTimeout(timeoutMillis);
        if (buffer.length == 0) throw new IllegalArgumentException("Empty read buffer");
        long start = System.nanoTime(), duration = timeoutMillis * 1_000_000L;
        try {
            synchronized (lock) {
                while (true) {
                    if (failure != null) throw failure;
                    if (closed) return -1;
                    requireReady();
                    if (count > 0) {
                        int length = Math.min(count, buffer.length);
                        for (int i = 0; i < length; i++) buffer[i] = received[(head + i) % CAPACITY];
                        head = (head + length) % CAPACITY; count -= length;
                        return length;
                    }
                    long remaining = duration - (System.nanoTime() - start);
                    if (remaining <= 0) return 0;
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            IOException problem = new IOException("Adapter read cancelled", interrupted);
            terminate(problem); throw problem;
        }
    }

    private void receive() {
        byte[] buffer = new byte[512];
        try {
            while (true) {
                int length = input.read(buffer);
                if (length == -1) { terminate(null); return; }
                if (length < 1 || length > buffer.length) throw new IOException("Invalid adapter stream read");
                synchronized (lock) {
                    if (closed) return;
                    if (length > CAPACITY - count) throw new IOException("Adapter input overflow; reconnect required");
                    for (int i = 0; i < length; i++) received[(head + count + i) % CAPACITY] = buffer[i];
                    count += length; lock.notifyAll();
                }
            }
        } catch (IOException problem) { terminate(problem); }
        catch (RuntimeException problem) { terminate(new IOException("Adapter input failed", problem)); }
    }

    private interface IoAction { void run() throws IOException; }

    private void bounded(IoAction action, int timeoutMillis) throws IOException {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        synchronized (lock) {
            requireOpen(); pending = task;
            writer.execute(task);
        }
        try {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS);
            synchronized (lock) { requireOpen(); }
        } catch (TimeoutException timeout) {
            IOException problem = new IOException("Adapter operation timed out; reconnect required", timeout);
            terminate(problem); throw problem;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            IOException problem = new IOException("Adapter operation cancelled", interrupted);
            terminate(problem); throw problem;
        } catch (ExecutionException failed) {
            IOException problem = new IOException("Adapter operation failed", failed.getCause());
            terminate(problem); throw problem;
        } catch (CancellationException cancelled) {
            throw new IOException("Adapter link closed", cancelled);
        } finally {
            synchronized (lock) { if (pending == task) pending = null; }
        }
    }

    private void requireOpen() throws IOException {
        if (failure != null) throw failure;
        if (closed) throw new IOException("Adapter link closed");
    }
    private void requireReady() throws IOException {
        requireOpen(); if (!ready) throw new IOException("Adapter link is not connected");
    }
    private static void validateTimeout(int timeoutMillis) {
        if (timeoutMillis < 1 || timeoutMillis > 120_000) throw new IllegalArgumentException("Deadline must be 1–120000 ms");
    }
    private void terminate(IOException problem) {
        synchronized (lock) {
            if (closed) return;
            closed = true; ready = false; failure = problem; count = 0;
            if (pending != null) pending.cancel(true);
            lock.notifyAll();
        }
        // Never hold the queue lock while aborting platform I/O.
        try { endpoint.close(); }
        catch (IOException closeFailure) {
            synchronized (lock) {
                if (failure == null) failure = closeFailure;
                else if (failure != closeFailure) failure.addSuppressed(closeFailure);
            }
        } finally { writer.shutdownNow(); }
    }
    @Override public void close() { terminate(null); }
}
