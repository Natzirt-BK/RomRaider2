/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.InitializationAttempt;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * A diagnostic read uses paired owner metadata, a fresh identification reply,
 * and connection-local verification of the advertised metadata block.
 * This never negotiates discovery, clears the owner cache, or publishes runtime state.
 */
public final class DmRuntimeReadRequest {
    private final String ecuId;
    private final byte[] initializationBytes;
    private final DmInit metadata;
    private final BooleanSupplier ownerCurrent;

    public DmRuntimeReadRequest(EcuInit ecu, DmInit cached, BooleanSupplier ownerCurrent) {
        this.ownerCurrent = Objects.requireNonNull(ownerCurrent, "ownerCurrent");
        ecuId = ecu == null ? null : ecu.getEcuId();
        byte[] bytes = ecu == null ? null : ecu.getEcuInitBytes();
        initializationBytes = bytes == null ? null : bytes.clone();
        metadata = cached == null ? null : cached.metadataSnapshot();
    }

    public void requireCurrent() {
        if (!ownerCurrent.getAsBoolean())
            throw new IllegalStateException("Logger identity or setup changed during the diagnostic read");
    }

    public DmInit read(LoggerConnection connection, Module module) throws InterruptedException {
        requireCurrent();
        if (metadata == null) return null;
        if (ecuId == null || ecuId.isBlank() || initializationBytes == null || initializationBytes.length == 0 || module == null)
            throw new IllegalStateException("DimeMod diagnostic read requires the original ECU identity and module");
        DmInit snapshot = metadata.metadataSnapshot();
        if (snapshot.getMajorVer() != 2)
            throw new IllegalStateException("Unsupported DimeMod diagnostic metadata");
        checkInterrupted();
        AtomicReference<byte[]> observedBytes = new AtomicReference<>();
        AtomicReference<String> observedId = new AtomicReference<>();
        AtomicInteger replies = new AtomicInteger();
        try (InitializationAttempt attempt = new InitializationAttempt()) {
            connection.ecuInit(attempt.bind(next -> {
                replies.incrementAndGet();
                if (next == null) return;
                observedId.set(next.getEcuId());
                byte[] bytes = next.getEcuInitBytes();
                observedBytes.set(bytes == null ? null : bytes.clone());
            }), module);
            checkInterrupted();
            requireCurrent();
            if (replies.get() != 1 || !ecuId.equals(observedId.get())
                    || !Arrays.equals(initializationBytes, observedBytes.get()))
                throw new InvalidResponseException("The ECU identification reply does not match the cached DimeMod definition");
        }
        requireCurrent();
        connection.verifyDmSession(snapshot, module);
        checkInterrupted();
        requireCurrent();
        DmInit result = connection.readDmRuntime(snapshot, module);
        checkInterrupted();
        requireCurrent();
        if (result == null) throw new InvalidResponseException("DimeMod diagnostic read returned no runtime data");
        return result;
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("DimeMod diagnostic read cancelled");
    }
}
