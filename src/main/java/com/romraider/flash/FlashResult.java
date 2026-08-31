/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.UUID;

/** Terminal session evidence suitable for UI status and diagnostics. */
public final class FlashResult {
    private final UUID sessionId;
    private final FlashOperation operation;
    private final FlashState state;
    private final String message;
    private final String protocolId;
    private final String deviceId;
    private final String diagnosticReference;
    private final Throwable failure;

    public FlashResult(UUID sessionId, FlashOperation operation, FlashState state,
            String message, String protocolId, String deviceId,
            String diagnosticReference, Throwable failure) {
        if (sessionId == null) throw new IllegalArgumentException("session id is required");
        if (operation == null) throw new IllegalArgumentException("operation is required");
        if (state == null || !state.isTerminal()) {
            throw new IllegalArgumentException("result state must be terminal");
        }
        this.sessionId = sessionId;
        this.operation = operation;
        this.state = state;
        this.message = normalize(message);
        this.protocolId = normalize(protocolId);
        this.deviceId = normalize(deviceId);
        this.diagnosticReference = normalize(diagnosticReference);
        this.failure = failure;
    }

    public UUID getSessionId() { return sessionId; }
    public FlashOperation getOperation() { return operation; }
    public FlashState getState() { return state; }
    public String getMessage() { return message; }
    public String getProtocolId() { return protocolId; }
    public String getDeviceId() { return deviceId; }
    public String getDiagnosticReference() { return diagnosticReference; }
    public Throwable getFailure() { return failure; }
    public boolean isSuccessful() { return state == FlashState.COMPLETED; }
    public boolean isRecoveryRequired() {
        return state == FlashState.RECOVERY_REQUIRED;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
