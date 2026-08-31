/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/** Selects protocols and runs preflight/sessions away from the Swing EDT. */
public final class FlashManager implements AutoCloseable {
    private final List<FlashProtocol> protocols;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, FlashSession> activeSessions =
            new ConcurrentHashMap<UUID, FlashSession>();

    public FlashManager(List<FlashProtocol> protocols) {
        this(protocols, Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "RomRaider2 Flash Worker");
                thread.setDaemon(false);
                return thread;
            }
        }));
    }

    FlashManager(List<FlashProtocol> protocols, ExecutorService executor) {
        if (protocols == null) throw new IllegalArgumentException("protocols are required");
        if (executor == null) throw new IllegalArgumentException("executor is required");
        this.protocols = Collections.unmodifiableList(
                new ArrayList<FlashProtocol>(protocols));
        this.executor = executor;
    }

    public List<FlashProtocol> getProtocols() { return protocols; }

    public FlashProtocol findProtocol(FlashTarget target, FlashDevice device) {
        for (FlashProtocol protocol : protocols) {
            if (protocol != null && protocol.supports(target, device)) return protocol;
        }
        return null;
    }

    public FlashCapabilities getCapabilities(FlashTarget target,
            FlashDevice device) {
        FlashProtocol protocol = findProtocol(target, device);
        if (protocol == null) return FlashCapabilities.NONE;
        FlashCapabilities capabilities = protocol.getCapabilities(target, device);
        return capabilities == null ? FlashCapabilities.NONE : capabilities;
    }

    public Future<FlashResult> start(final FlashRequest request,
            final FlashProgressListener listener) {
        if (request == null) throw new IllegalArgumentException("request is required");
        return executor.submit(new Callable<FlashResult>() {
            public FlashResult call() {
                return execute(request, listener);
            }
        });
    }

    public List<FlashSession> getActiveSessions() {
        return Collections.unmodifiableList(
                new ArrayList<FlashSession>(activeSessions.values()));
    }

    public void close() {
        executor.shutdown();
    }

    private FlashResult execute(FlashRequest request,
            FlashProgressListener listener) {
        UUID preparationId = UUID.randomUUID();
        FlashProtocol protocol;
        try {
            protocol = findProtocol(request.getTarget(), request.getDevice());
        } catch (RuntimeException failure) {
            return failed(preparationId, request, "Protocol selection failed",
                    "", failure);
        } catch (LinkageError failure) {
            return failed(preparationId, request, "Protocol dependency unavailable",
                    "", failure);
        }
        if (protocol == null) {
            return failed(preparationId, request,
                    "No compatible flash protocol is registered", "", null);
        }

        FlashCapabilities capabilities;
        try {
            capabilities = protocol.getCapabilities(
                    request.getTarget(), request.getDevice());
        } catch (RuntimeException failure) {
            return failed(preparationId, request,
                    "Flash capability evaluation failed",
                    protocol.getId(), failure);
        } catch (LinkageError failure) {
            return failed(preparationId, request,
                    "Flash protocol dependency unavailable",
                    protocol.getId(), failure);
        }
        if (capabilities == null
                || !capabilities.supports(request.getOperation())) {
            return failed(preparationId, request,
                    protocol.getDisplayName() + " does not support "
                            + request.getOperation(), protocol.getId(), null);
        }

        if (listener != null) listener.progressChanged(
                FlashProgress.indeterminate(FlashState.PREFLIGHT,
                        "Running flash preflight"));
        FlashPreflight preflight;
        try {
            preflight = protocol.preflight(request);
        } catch (RuntimeException failure) {
            return failed(preparationId, request, "Flash preflight failed",
                    protocol.getId(), failure);
        } catch (LinkageError failure) {
            return failed(preparationId, request,
                    "Flash preflight dependency unavailable",
                    protocol.getId(), failure);
        }
        if (preflight == null || !preflight.canProceed()) {
            return failed(preparationId, request,
                    "Flash preflight did not pass", protocol.getId(), null);
        }

        FlashSession session;
        try {
            session = protocol.createSession(request, listener);
        } catch (RuntimeException failure) {
            return failed(preparationId, request,
                    "Flash session creation failed", protocol.getId(), failure);
        } catch (LinkageError failure) {
            return failed(preparationId, request,
                    "Flash session dependency unavailable",
                    protocol.getId(), failure);
        }
        if (session == null) {
            return failed(preparationId, request,
                    "Protocol did not create a flash session",
                    protocol.getId(), null);
        }
        activeSessions.put(session.getId(), session);
        try {
            FlashResult result = session.execute();
            if (result == null) {
                return failed(session.getId(), request,
                        "Flash session returned no result",
                        protocol.getId(), null);
            }
            return result;
        } catch (RuntimeException failure) {
            return failed(session.getId(), request, "Flash session failed",
                    protocol.getId(), failure);
        } catch (LinkageError failure) {
            return failed(session.getId(), request,
                    "Flash protocol dependency failed",
                    protocol.getId(), failure);
        } finally {
            activeSessions.remove(session.getId());
        }
    }

    private static FlashResult failed(UUID id, FlashRequest request,
            String message, String protocolId, Throwable failure) {
        return new FlashResult(id, request.getOperation(), FlashState.FAILED,
                message, protocolId, request.getDevice().getId(), "", failure);
    }
}
