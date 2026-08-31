/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Truthful user-facing states emitted by a flash session. */
public enum FlashState {
    CREATED(false, true),
    PREFLIGHT(false, true),
    CONNECTING(false, true),
    IDENTIFYING_ECU(false, true),
    ENTERING_PROGRAMMING_MODE(false, true),
    UPLOADING_KERNEL(false, false),
    READING(false, true),
    ERASING(false, false),
    PROGRAMMING(false, false),
    VERIFYING(false, false),
    RESETTING_ECU(false, false),
    RECONNECTING(false, true),
    COMPLETED(true, false),
    FAILED(true, false),
    RECOVERY_REQUIRED(true, false),
    CANCELLED(true, false);

    private final boolean terminal;
    private final boolean cancellationSafe;

    FlashState(boolean terminal, boolean cancellationSafe) {
        this.terminal = terminal;
        this.cancellationSafe = cancellationSafe;
    }

    public boolean isTerminal() { return terminal; }
    public boolean isCancellationSafe() { return cancellationSafe; }
}
