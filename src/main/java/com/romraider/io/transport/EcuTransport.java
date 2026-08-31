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

public interface EcuTransport {
    void connect(EcuIdentity expectedIdentity) throws TransportException;

    void disconnect();

    boolean isConnected();

    EcuIdentity getConnectedIdentity();

    byte[] readMemory(long address, int length) throws TransportException;

    void writeMemory(long address, byte[] data) throws TransportException;
}
