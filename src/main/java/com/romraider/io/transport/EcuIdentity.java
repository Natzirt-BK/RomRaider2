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

public final class EcuIdentity {
    private final String ecuId;
    private final String romId;

    public EcuIdentity(String ecuId, String romId) {
        if (ecuId == null || romId == null) {
            throw new IllegalArgumentException("ECU ID and ROM ID are required");
        }
        this.ecuId = ecuId;
        this.romId = romId;
    }

    public String getEcuId() {
        return ecuId;
    }

    public String getRomId() {
        return romId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EcuIdentity)) return false;
        EcuIdentity identity = (EcuIdentity) other;
        return ecuId.equals(identity.ecuId) && romId.equals(identity.romId);
    }

    @Override
    public int hashCode() {
        return 31 * ecuId.hashCode() + romId.hashCode();
    }

    @Override
    public String toString() {
        return ecuId + "/" + romId;
    }
}
