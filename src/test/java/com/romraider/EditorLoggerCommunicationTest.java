package com.romraider;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public class EditorLoggerCommunicationTest {
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
