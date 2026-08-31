/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.romraider.io.transport;

public final class TransportException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        TIMEOUT,
        REJECTED,
        IDENTITY_MISMATCH,
        DISCONNECTED,
        INVALID_REQUEST
    }

    private final Reason reason;

    public TransportException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
