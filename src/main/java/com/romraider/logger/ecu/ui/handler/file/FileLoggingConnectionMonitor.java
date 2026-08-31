/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.romraider.logger.ecu.ui.handler.file;

import static com.romraider.util.ParamChecker.checkNotNull;

import com.romraider.logger.ecu.ui.StatusChangeListener;

/** Stops and resets file capture whenever the ECU connection stops. */
public final class FileLoggingConnectionMonitor implements StatusChangeListener {
    private final FileUpdateHandler fileUpdateHandler;
    private final Runnable stoppedCallback;

    public FileLoggingConnectionMonitor(FileUpdateHandler fileUpdateHandler,
            Runnable stoppedCallback) {
        checkNotNull(fileUpdateHandler, "fileUpdateHandler");
        checkNotNull(stoppedCallback, "stoppedCallback");
        this.fileUpdateHandler = fileUpdateHandler;
        this.stoppedCallback = stoppedCallback;
    }

    @Override public void connecting() { }
    @Override public void readingData() { }
    @Override public void readingDataExternal() { }
    @Override public void loggingData() { }

    @Override
    public void stopped() {
        fileUpdateHandler.stop();
        stoppedCallback.run();
    }
}
