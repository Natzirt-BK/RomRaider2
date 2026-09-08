package com.romraider2.javafx;

import com.romraider.portable.logger.Elm327ReadOnlyRecorder;
import com.romraider.portable.logger.Elm327Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Offline injected link: never loads a native serial device. */
final class ElmAdapterTestFixture implements Elm327Session.Link {
    long nanos;
    int closes;
    int rpmQueries;
    String mask = "08 18 00 00";
    String rpmReply = "41 0C 1A F9>";
    boolean intermittent;
    boolean noData;
    boolean closeFails;
    final ArrayDeque<Byte> input = new ArrayDeque<>();
    final List<String> commands = new ArrayList<>();
    public void write(byte[] data, int timeout) {
        String command = new String(data, StandardCharsets.US_ASCII).trim(); commands.add(command);
        String reply = switch (command) {
            case "AT WS" -> "ELM327 v2.3>";
            case "0100" -> "41 00 " + mask + ">";
            case "010C" -> { rpmQueries++; yield noData || (intermittent && rpmQueries == 2) ? "NO DATA>" : rpmReply; }
            case "0105" -> noData ? "7F 01 12>" : "41 05 7B>";
            case "010D" -> noData ? "NO DATA>" : "41 0D 00>";
            default -> command.startsWith("AT ") ? "OK>" : "?>";
        };
        for (byte b : reply.getBytes(StandardCharsets.US_ASCII)) input.add(b);
    }
    public int read(byte[] bytes, int timeout) {
        if (input.isEmpty()) { nanos += timeout * 1_000_000L; return 0; }
        int count = Math.min(bytes.length, input.size());
        for (int i = 0; i < count; i++) bytes[i] = input.remove(); return count;
    }
    public void close() throws IOException { closes++; if (closeFails) throw new IOException("close fixture failed"); }
    Elm327Session session() { return new Elm327Session(this, () -> nanos); }
    Elm327ReadOnlyRecorder recorder() { return new Elm327ReadOnlyRecorder(() -> nanos, millis -> nanos += millis * 1_000_000L); }
}
