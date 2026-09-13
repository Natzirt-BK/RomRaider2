package com.romraider;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public class EditorLoggerCommunicationTest {
    @Test public void launchEndpointDoesNotCollideWithOriginalRomRaider() {
        assertNotEquals("Original RomRaider must retain its own startup port",
                23272, EditorLoggerCommunication.PORT);
    }

    @Test public void originalRomRaiderListenerDoesNotCountAsRunningRr2() throws Exception {
        org.junit.Assume.assumeFalse("RR2 is already running",
                EditorLoggerCommunication.isRunning());
        java.net.ServerSocket legacy;
        try {
            legacy = new java.net.ServerSocket(23272);
        } catch (java.net.BindException inUse) {
            org.junit.Assume.assumeNoException("Legacy endpoint already in use", inUse);
            return;
        }
        try (legacy) {
            assertFalse("Original RomRaider must not block RR2 startup",
                    EditorLoggerCommunication.isRunning());
            try (var rr2 = new java.net.ServerSocket(EditorLoggerCommunication.PORT, 1,
                    java.net.InetAddress.getLoopbackAddress())) {
                assertTrue(EditorLoggerCommunication.isRunning());
                assertFalse(legacy.isClosed());
            }
        }
    }

    @Test public void productionEndpointDetectsAndForwardsOnlyToRr2() throws Exception {
        // Never send a test launch to a real running RR2 instance.
        java.net.ServerSocket server;
        try {
            server = new java.net.ServerSocket(EditorLoggerCommunication.PORT, 1,
                    java.net.InetAddress.getLoopbackAddress());
        } catch (java.net.BindException inUse) {
            org.junit.Assume.assumeNoException("RR2 endpoint already in use", inUse);
            return;
        }
        var previousType = EditorLoggerCommunication.getExecutableType();
        var previousArgs = EditorLoggerCommunication.getExecutableArgs();
        String[] args = {"/tmp/original calibration.bin", "-logger"};
        try (server) {
            server.setSoTimeout(3000);
            assertTrue(EditorLoggerCommunication.isRunning());
            EditorLoggerCommunication.setExectable(
                    EditorLoggerCommunication.Exec_type.LOGGER, args);
            EditorLoggerCommunication.sendTypeToOtherExec(args);
            var received = EditorLoggerCommunication.receive(server);
            assertEquals(EditorLoggerCommunication.Exec_type.LOGGER, received.execType);
            assertArrayEquals(args, received.currentArgs);
        } finally {
            EditorLoggerCommunication.setExectable(previousType, previousArgs);
        }
    }

    @Test public void launchArgumentsRoundTripWithoutPathSplitting() throws Exception {
        String[] arguments = {"review copy.bin", "C:\\Users\\Test User\\桌面\\calibration.bin",
                "/tmp/café/測定.bin", "", "line\nbreak\t.bin", "-logger.touch"};
        String encoded = EditorLoggerCommunication.encodeArguments(arguments);
        assertFalse(encoded.contains("\n"));
        assertArrayEquals(arguments, EditorLoggerCommunication.decodeArguments(encoded));
        assertArrayEquals(new String[0], EditorLoggerCommunication.decodeArguments(
                EditorLoggerCommunication.encodeArguments(new String[0])));
    }

    @Test public void legacyArgumentsRemainReadable() throws Exception {
        assertArrayEquals(new String[] {"-logger", "sample.bin"},
                EditorLoggerCommunication.decodeArguments("-logger sample.bin "));
        assertEquals(0, EditorLoggerCommunication.decodeArguments("").length);
    }

    @Test public void socketTransportPreservesPathsOnAnIsolatedPort() throws Exception {
        String[] args = {"/tmp/review copy.bin", "C:\\Test User\\é.bin", "-logger.touch"};
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
                java.net.Socket client = new java.net.Socket(server.getInetAddress(), server.getLocalPort())) {
            server.setSoTimeout(3000);
            java.io.PrintWriter writer = new java.io.PrintWriter(client.getOutputStream(), true,
                    java.nio.charset.StandardCharsets.UTF_8);
            writer.println("EDITOR");
            writer.println(EditorLoggerCommunication.encodeArguments(args));
            var received = EditorLoggerCommunication.receive(server);
            assertEquals(EditorLoggerCommunication.Exec_type.EDITOR, received.execType);
            assertArrayEquals(args, received.currentArgs);
        }
    }

    @Test public void malformedFramesAreRejected() {
        for (String message : new String[] {"RR2ARGS1:!", "RR2ARGS1:",
                "RR2ARGS1://///w==", "RR2ARGS1:AAAAAQ==", "RR2ARGS1:AAAAAAA="}) {
            try { EditorLoggerCommunication.decodeArguments(message); fail(message); }
            catch (IOException expected) { }
        }
    }
}
