/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

/** Evo MUT-II PID polling only. No reset, memory access or pin-voltage commands. */
public final class ReadOnlyMut2Protocol {
    public static final int PROBE_PID = 0x14;
    public static final String GENERIC_ECU_ID = "MUT2_GENERIC";
    private ReadOnlyMut2Protocol() { }
    public static byte[] request(int pid) {
        if (pid < 0 || pid > 255) throw new IllegalArgumentException("MUT-II PID must be one byte");
        return new byte[] {(byte) pid};
    }
    /** Transport must remove separately flagged TX loopback packets first. */
    public static byte value(int pid, byte[] response) {
        request(pid);
        if (response == null || !(response.length == 1
                || response.length == 2 && (response[0] & 255) == pid))
            throw new IllegalArgumentException("Invalid MUT-II PID response");
        return response[response.length - 1];
    }
    public static String probeIdentity(byte[] response) {
        int battery = value(PROBE_PID, response) & 255;
        if (battery < 80 || battery > 250)
            throw new IllegalArgumentException("MUT-II battery probe is not plausible");
        // A successful live probe is NOT identification of a particular ROM.
        return GENERIC_ECU_ID;
    }
}
