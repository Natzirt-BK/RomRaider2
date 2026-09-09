/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.definition.EcuSwitch;
import com.romraider.logger.ecu.definition.Module;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/** One read-only diagnostic request. No UI calls and no live settings lookups. */
public final class DiagnosticReadRequest {
    private final DtcReadPlan plan;
    private final DmRuntimeReadRequest identity;
    private final Module module;
    private final Supplier<LoggerConnection> connections;
    private boolean attempted;

    public DiagnosticReadRequest(List<EcuSwitch> codes, int initializationLength, DmRuntimeReadRequest identity,
            Module module, Supplier<LoggerConnection> connections) {
        plan = DtcReadPlan.prepare(codes, initializationLength);
        this.identity = Objects.requireNonNull(identity);
        this.module = new Module(module.getName(), module.getAddress().clone(), module.getDescription(),
                module.getTester().clone(), module.getFastPoll());
        this.connections = Objects.requireNonNull(connections);
    }

    public String target() { return module.getName().toUpperCase(java.util.Locale.ROOT); }
    public void requireCurrent() { identity.requireCurrent(); }

    public Result read() throws InterruptedException {
        synchronized (this) {
            if (attempted) throw new IllegalStateException("Diagnostic request already used");
            attempted = true;
        }
        checkCurrent();
        LoggerConnection connection = Objects.requireNonNull(connections.get(), "Diagnostic connection");
        Throwable failure = null;
        try {
            checkCurrent();
            DmInit dime = identity.read(connection, module);
            ArrayList<EcuQuery> codes = plan.read(connection, module, identity::requireCurrent);
            checkCurrent();
            return new Result(codes, dime == null ? Set.of() : dime.decodeDmCurrentErrors(),
                    dime == null ? Set.of() : dime.decodeDmMemorizedErrors());
        } catch (InterruptedException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            // Cleanup owns the connection. Cancellation must not skip its close
            // or publish completion while a native close is still outstanding.
            boolean interrupted = Thread.interrupted();
            try { connection.close(); }
            catch (RuntimeException | Error closing) {
                if (failure != null) failure.addSuppressed(closing);
                else throw closing;
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
    }

    private void checkCurrent() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Diagnostic read cancelled");
        requireCurrent();
    }

    public record Result(List<EcuQuery> codes, Set<String> currentDimeCodes, Set<String> memorizedDimeCodes) {
        public Result {
            codes = List.copyOf(codes);
            currentDimeCodes = Set.copyOf(currentDimeCodes);
            memorizedDimeCodes = Set.copyOf(memorizedDimeCodes);
        }
        public boolean isEmpty() { return codes.isEmpty() && currentDimeCodes.isEmpty() && memorizedDimeCodes.isEmpty(); }
    }
}
