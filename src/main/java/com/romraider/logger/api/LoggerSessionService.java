/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static com.romraider.util.ParamChecker.checkNotNull;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Toolkit-neutral command surface for the active Logger session.
 *
 * The transport and file implementations remain owned by the existing Logger
 * backend. Replacement views issue intent through this service and observe the
 * same state published to every other Logger surface.
 */
public final class LoggerSessionService implements LoggerLiveDataListener,
        AutoCloseable {
    private final LoggerLiveDataBus liveDataBus;
    private final Runnable connectAction;
    private final Runnable disconnectAction;
    private final Runnable startRecordingAction;
    private final Runnable stopRecordingAction;
    private final Consumer<RuntimeException> failureHandler;
    private final ExecutorService commands;
    private final CopyOnWriteArrayList<Consumer<LoggerSessionState>> listeners =
            new CopyOnWriteArrayList<Consumer<LoggerSessionState>>();
    private volatile LoggerSessionState state;
    private volatile boolean closed;

    public LoggerSessionService(LoggerLiveDataBus liveDataBus,
            Runnable connectAction, Runnable disconnectAction,
            Runnable startRecordingAction, Runnable stopRecordingAction,
            Consumer<RuntimeException> failureHandler) {
        this(liveDataBus, connectAction, disconnectAction,
                startRecordingAction, stopRecordingAction, failureHandler,
                Executors.newSingleThreadExecutor(new ThreadFactory() {
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable,
                                "Logger Session Commands");
                        thread.setDaemon(true);
                        return thread;
                    }
                }));
    }

    LoggerSessionService(LoggerLiveDataBus liveDataBus,
            Runnable connectAction, Runnable disconnectAction,
            Runnable startRecordingAction, Runnable stopRecordingAction,
            Consumer<RuntimeException> failureHandler,
            ExecutorService commands) {
        checkNotNull(liveDataBus, connectAction, disconnectAction,
                startRecordingAction, stopRecordingAction, failureHandler,
                commands);
        this.liveDataBus = liveDataBus;
        this.connectAction = connectAction;
        this.disconnectAction = disconnectAction;
        this.startRecordingAction = startRecordingAction;
        this.stopRecordingAction = stopRecordingAction;
        this.failureHandler = failureHandler;
        this.commands = commands;
        state = liveDataBus.getState();
        liveDataBus.addListener(this);
    }

    public LoggerSessionState getState() {
        return state;
    }

    public void addStateListener(Consumer<LoggerSessionState> listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        notifyListener(listener, state);
    }

    public void removeStateListener(Consumer<LoggerSessionState> listener) {
        listeners.remove(listener);
    }

    public void connect() {
        if (state != LoggerSessionState.STOPPED) return;
        submit(connectAction);
    }

    public void disconnect() {
        if (state == LoggerSessionState.STOPPED) return;
        submit(new Runnable() {
            public void run() {
                if (state == LoggerSessionState.RECORDING) {
                    stopRecordingAction.run();
                }
                disconnectAction.run();
            }
        });
    }

    public void startRecording() {
        if (state != LoggerSessionState.LIVE_ECU
                && state != LoggerSessionState.LIVE_EXTERNAL) return;
        submit(startRecordingAction);
    }

    public void stopRecording() {
        if (state != LoggerSessionState.RECORDING) return;
        submit(stopRecordingAction);
    }

    private void submit(final Runnable action) {
        if (closed) return;
        commands.execute(new Runnable() {
            public void run() {
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    failureHandler.accept(failure);
                }
            }
        });
    }

    public void sessionStateChanged(LoggerSessionState next) {
        if (next == null || next == state) return;
        state = next;
        for (Consumer<LoggerSessionState> listener : listeners) {
            notifyListener(listener, next);
        }
    }

    public void sampleUpdated(LiveDataSample sample) {
    }

    public void parameterRemoved(String parameterId) {
    }

    private void notifyListener(Consumer<LoggerSessionState> listener,
            LoggerSessionState next) {
        try {
            listener.accept(next);
        } catch (RuntimeException failure) {
            failureHandler.accept(failure);
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        liveDataBus.removeListener(this);
        listeners.clear();
        commands.shutdownNow();
    }
}
