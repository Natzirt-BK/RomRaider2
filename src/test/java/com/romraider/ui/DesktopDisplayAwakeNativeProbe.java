/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

/** Opt-in transient native API acceptance check for disposable Windows/macOS CI runners. */
public final class DesktopDisplayAwakeNativeProbe {
    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name", "");
        if (!"1".equals(System.getenv("RR2_NATIVE_AWAKE_TEST")) ||
                !(os.startsWith("Windows") || os.startsWith("Mac")))
            throw new IllegalStateException("This probe requires an opted-in Windows/macOS runner");
        try (DesktopDisplayAwake awake = new DesktopDisplayAwake()) {
            for (int i = 0; i < 2; i++) {
                awake.setActive(true);
                await(awake, DesktopDisplayAwake.Status.ACTIVE);
                awake.setActive(false);
                await(awake, DesktopDisplayAwake.Status.OFF);
            }
        }
        System.out.println("PASS: " + os + " native display-awake acquisition/release accepted twice");
    }
    private static void await(DesktopDisplayAwake awake, DesktopDisplayAwake.Status expected) throws Exception {
        long deadline = System.nanoTime() + 6_000_000_000L;
        while (awake.getStatus() != expected) {
            if (awake.getStatus() == DesktopDisplayAwake.Status.UNAVAILABLE || System.nanoTime() > deadline)
                throw new AssertionError("Expected " + expected + ": " + awake.getFailure());
            Thread.sleep(10);
        }
    }
}
