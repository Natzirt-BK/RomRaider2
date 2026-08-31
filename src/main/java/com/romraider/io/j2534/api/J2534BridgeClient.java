/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.io.j2534.api;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

/** Owns one architecture-matched bridge process and its private named pipe. */
final class J2534BridgeClient implements J2534BridgeChannel {
    private static final Logger LOGGER = Logger.getLogger(J2534BridgeClient.class);
    private static final long PIPE_CONNECT_TIMEOUT_MILLIS = 10_000L;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final Process process;
    private final RandomAccessFile pipe;
    private long nextRequestId = 1L;
    private boolean shutdown;

    J2534BridgeClient(int bridgeBits) {
        File executable = findExecutable(bridgeBits);
        String pipeName = "\\\\.\\pipe\\romraider2-j2534-"
                + ProcessHandle.current().pid() + "-"
                + UUID.randomUUID().toString().replace("-", "");
        Process started = null;
        RandomAccessFile connected = null;
        try {
            started = new ProcessBuilder(executable.getAbsolutePath(), pipeName)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            consumeDiagnostics(started.getErrorStream(), bridgeBits);
            connected = connect(pipeName, started);
        } catch (IOException e) {
            if (started != null && started.isAlive()) started.destroy();
            if (connected != null) {
                try {
                    connected.close();
                } catch (IOException ignored) {
                }
            }
            throw new J2534Exception("Unable to start the " + bridgeBits
                    + "-bit J2534 bridge. The vendor driver remains installed and unchanged. "
                    + e.getMessage(), e);
        }
        process = started;
        pipe = connected;
    }

    public synchronized Object request(String method, Map<String, Object> parameters) {
        if (shutdown) throw new J2534Exception("The J2534 bridge is closed");
        long requestId = nextRequestId++;
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("id", Long.valueOf(requestId));
        request.put("method", method);
        if (parameters != null && !parameters.isEmpty()) request.put("params", parameters);
        try {
            byte[] bytes = BridgeJson.write(request).getBytes(UTF_8);
            pipe.write(bytes);
            pipe.write('\n');
            String line = readUtf8Line(pipe);
            if (line == null) {
                throw new IOException("bridge closed its named pipe");
            }
            Object parsed = BridgeJson.read(line);
            if (!(parsed instanceof Map)) {
                throw new IOException("bridge returned a non-object response");
            }
            Map<?, ?> response = (Map<?, ?>) parsed;
            if (number(response.get("id")) != requestId) {
                throw new IOException("bridge response ID did not match request " + requestId);
            }
            if ("error".equals(response.get("status"))) {
                throw new J2534Exception("J2534 bridge " + method + " failed ["
                        + number(response.get("code")) + "]: " + response.get("message"));
            }
            if (!"ok".equals(response.get("status"))) {
                throw new IOException("bridge response has no valid status");
            }
            return response.get("data");
        } catch (J2534Exception e) {
            throw e;
        } catch (Exception e) {
            throw new J2534Exception("J2534 bridge communication failed during "
                    + method + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void close() {
        if (shutdown) return;
        try {
            request("Shutdown", null);
        } catch (RuntimeException e) {
            LOGGER.warn("J2534 bridge did not acknowledge shutdown: " + e.getMessage());
        } finally {
            shutdown = true;
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
            if (process.isAlive()) process.destroy();
        }
    }

    private static File findExecutable(int bits) {
        String name = "j2534-bridge-" + bits + ".exe";
        String explicit = System.getProperty("romraider2.j2534.bridge." + bits, "").trim();
        if (!explicit.isEmpty()) {
            File file = new File(explicit);
            if (file.isFile()) return file;
            throw missingBridge(bits, file.getAbsolutePath());
        }

        String directory = System.getProperty("romraider2.j2534.bridge.dir", "").trim();
        if (!directory.isEmpty()) {
            File file = new File(directory, name);
            if (file.isFile()) return file;
        }

        String libraryPath = System.getProperty("java.library.path", "");
        for (String entry : libraryPath.split(java.util.regex.Pattern.quote(
                File.pathSeparator))) {
            if (entry.trim().isEmpty()) continue;
            File direct = new File(entry, name);
            if (direct.isFile()) return direct;
            File child = new File(new File(entry, "j2534"), name);
            if (child.isFile()) return child;
        }
        throw missingBridge(bits, directory.isEmpty() ? name : new File(directory, name).getPath());
    }

    private static J2534Exception missingBridge(int bits, String searched) {
        return new J2534Exception("The installed J2534 DLL requires the bundled " + bits
                + "-bit bridge, but it was not found at " + searched
                + ". Reinstall or extract the complete RomRaider2 package; do not disable "
                + "Windows driver signing or replace the vendor driver.");
    }

    private static RandomAccessFile connect(String pipeName, Process process)
            throws IOException {
        long deadline = System.currentTimeMillis() + PIPE_CONNECT_TIMEOUT_MILLIS;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("bridge process exited with code " + process.exitValue());
            }
            try {
                return new RandomAccessFile(pipeName, "rw");
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting to the bridge", interrupted);
                }
            }
        }
        throw new IOException("timed out opening the bridge named pipe", last);
    }

    private static String readUtf8Line(RandomAccessFile input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() <= MAX_RESPONSE_BYTES) {
            int value = input.read();
            if (value == -1) return line.size() == 0 ? null : line.toString(UTF_8);
            if (value == '\n') return line.toString(UTF_8);
            if (value != '\r') line.write(value);
        }
        throw new IOException("bridge response exceeded " + MAX_RESPONSE_BYTES + " bytes");
    }

    private static long number(Object value) throws IOException {
        if (!(value instanceof Number)) throw new IOException("expected a numeric bridge field");
        return ((Number) value).longValue();
    }

    private static void consumeDiagnostics(final InputStream input, int bits) {
        Thread reader = new Thread(() -> {
            try (InputStream stream = input) {
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                int value;
                while ((value = stream.read()) != -1) {
                    if (value == '\n') {
                        if (line.size() > 0 && LOGGER.isDebugEnabled()) {
                            LOGGER.debug("J2534 bridge: " + line.toString(UTF_8));
                        }
                        line.reset();
                    } else if (value != '\r') {
                        line.write(value);
                    }
                }
            } catch (IOException e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("J2534 bridge diagnostic stream closed: " + e.getMessage());
                }
            }
        }, "J2534-bridge-" + bits + "-diagnostics");
        reader.setDaemon(true);
        reader.start();
    }
}
