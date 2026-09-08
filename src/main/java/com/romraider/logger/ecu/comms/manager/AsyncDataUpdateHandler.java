/* RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2021 RomRaider.com. GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.manager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;
import com.romraider.logger.ecu.comms.query.Response;
import com.romraider.logger.ecu.ui.handler.DataUpdateHandler;

/** Ordered, bounded dispatch. Failures are surfaced to the polling owner, never silently lost. */
public class AsyncDataUpdateHandler extends Thread {
    private static final Logger LOGGER = Logger.getLogger(AsyncDataUpdateHandler.class);
    private final LinkedBlockingQueue<Response> responses;
    private final DataUpdateHandler[] handlers;
    private volatile boolean stop;
    private volatile boolean running;
    private volatile RuntimeException failure;

    public AsyncDataUpdateHandler(DataUpdateHandler[] handlers) { this(handlers, 2048); }
    AsyncDataUpdateHandler(DataUpdateHandler[] handlers, int capacity) {
        this.handlers = handlers.clone();
        responses = new LinkedBlockingQueue<>(capacity);
        setName("AsyncDataUpdater");
        setDaemon(true);
    }

    @Override public void run() {
        running = true;
        try {
            while (!stop) {
                Response response = responses.poll(100, TimeUnit.MILLISECONDS);
                if (response == null) continue;
                for (DataUpdateHandler handler : handlers) {
                    if (stop) break;
                    handler.handleDataUpdate(response);
                }
            }
        } catch (InterruptedException interrupted) {
            if (!stop) failure = new IllegalStateException("Logger update worker was interrupted", interrupted);
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            failure = error;
            LOGGER.error("Logger update handler failed", error);
        } finally {
            stop = true;
            responses.clear();
            running = false;
        }
    }

    public void requireHealthy() {
        RuntimeException error = failure;
        if (error != null) throw new IllegalStateException("Logger data delivery failed; capture stopped", error);
        if (stop) throw new IllegalStateException("Logger update worker has stopped");
    }

    public void stopUpdater() {
        stop = true; responses.clear(); interrupt();
    }

    public boolean isRunning() { return running; }

    public void addResponse(Response response) {
        requireHealthy();
        if (stop) throw new IllegalStateException("Logger update worker has stopped");
        if (!responses.offer(java.util.Objects.requireNonNull(response))) {
            failure = new IllegalStateException("Logger update queue is full; refusing to silently drop samples");
            stopUpdater();
            requireHealthy();
        }
    }
}
