/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

/** Invoked only by the isolated D-Bus fixture, never part of a runtime package. */
public final class LinuxDisplayAwakeProbe {
    public static void main(String[] args) throws Exception {
        if (!"1".equals(System.getenv("RR2_PRIVATE_AWAKE_TEST"))) throw new IllegalStateException("Private test bus required");
        String mode = args[0];
        long started = System.nanoTime();
        if (mode.equals("missing") || mode.equals("timeout") || mode.equals("wrong-type") || mode.equals("handshake")) {
            try (DesktopDisplayAwake.Lease unexpected = LinuxDisplayAwake.acquire()) {
                throw new AssertionError("Unexpected inhibition in " + mode);
            } catch (IllegalStateException expected) {
                long elapsed = System.nanoTime() - started;
                if (elapsed > 6_000_000_000L) throw new AssertionError("Unbounded bus operation", expected);
                if ((mode.equals("timeout") || mode.equals("handshake")) && elapsed < 1_000_000_000L)
                    throw new AssertionError("Fixture did not exercise the native timeout", expected);
            }
        } else {
            DesktopDisplayAwake.Lease lease = LinuxDisplayAwake.acquire();
            if (!lease.isValid()) throw new AssertionError("Inhibition connection was not retained");
            if (mode.equals("process-exit")) Runtime.getRuntime().halt(0);
            try {
                Thread.sleep(800);
                if (mode.equals("service-loss")) {
                    boolean valid;
                    try { valid = lease.isValid(); } catch (IllegalStateException expected) { valid = false; }
                    if (valid) throw new AssertionError("Lost service still reported active");
                } else if (!lease.isValid()) throw new AssertionError("Live inhibition was lost");
            } finally { lease.close(); lease.close(); }
        }
        System.out.println("PASS: " + mode);
    }
}
