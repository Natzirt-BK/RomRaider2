/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2020 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.logger.ecu.ui.handler.file;

import static com.romraider.util.ParamChecker.checkNotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;

import com.romraider.Settings;
import com.romraider.logger.ecu.exception.FileLoggerException;
import com.romraider.logger.analysis.RecentLogCaptureService;
import com.romraider.logger.ecu.ui.EcuRelatedMessageListener;
import com.romraider.logger.ecu.ui.MessageListener;
import com.romraider.util.FormatFilename;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public final class FileLoggerImpl implements FileLogger {
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            FileLoggerImpl.class.getName());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private final EcuRelatedMessageListener messageListener;
    private final java.util.function.Supplier<Date> clock;
    private boolean started;
    private OutputStream os;
    private File activeFile;
    private long startTimestamp;
    private boolean timestampInitialized;
    //private boolean zero;

    public FileLoggerImpl(EcuRelatedMessageListener messageListener) {
        this(messageListener, Date::new);
    }

    FileLoggerImpl(EcuRelatedMessageListener messageListener, java.util.function.Supplier<Date> clock) {
        checkNotNull(messageListener);
        this.messageListener = messageListener;
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    public synchronized void start() {
        if (!started) {
            stop();
            try {
                String filePath = buildFilePath();
                File requested = new File(filePath).getAbsoluteFile();
                for (int suffix = 0; ; suffix++) {
                    File candidate = suffix == 0 ? requested : new File(requested.getParentFile(),
                            requested.getName().replaceFirst("\\.csv$", "_" + suffix + ".csv"));
                    try {
                        os = new BufferedOutputStream(java.nio.file.Files.newOutputStream(candidate.toPath(),
                                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE));
                        activeFile = candidate;
                        break;
                    } catch (java.nio.file.FileAlreadyExistsException collision) { /* Never overwrite another capture. */ }
                }
                messageListener.reportMessageInTitleBar(MessageFormat.format(
                        rb.getString("STARTLOG"),
                        FormatFilename.getShortName(activeFile.getPath())));
            } catch (Exception e) {
                try { stop(); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
                throw new FileLoggerException(e);
            }
            
            started = true;
            startTimestamp = 0;
            timestampInitialized = false;
        }
    }

    @Override
    public synchronized void stop() {
        File completed = activeFile;
        OutputStream closing = os;
        os = null; started = false; activeFile = null;
        if (closing != null) {
            try {
                closing.close();
                messageListener.reportMessageInTitleBar(rb.getString("STOPLOG"));
            } catch (Exception e) {
                throw new FileLoggerException(e);
            }
        }
        RecentLogCaptureService.getInstance().completed(completed);
    }

    @Override
    public synchronized boolean isStarted() {
        return started;
    }

    @Override
    public synchronized void writeHeaders(String headers) {
        String timeHeader = "Time";
        if (!SettingsManager.getSettings().isFileLoggingAbsoluteTimestamp()) {
            timeHeader = timeHeader  + " (msec)";
        }
        writeText(timeHeader + headers);
    }

    @Override
    public synchronized void writeLine(String line, long timestamp) {
        writeText(prependTimestamp(line, timestamp));
    }

    private void writeText(String text) {
        try {
            os.write(text.getBytes());
            if (!text.endsWith(NEW_LINE)) {
                os.write(NEW_LINE.getBytes());
            }
        } catch (Exception e) {
            try { stop(); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw new FileLoggerException(e);
        }
    }

    private String prependTimestamp(String line, long timestamp) {
        String formattedTimestamp;
        if (SettingsManager.getSettings().isFileLoggingAbsoluteTimestamp()) {
            formattedTimestamp = timestampFormat.format(new Date(timestamp));
        } else {
            if (!timestampInitialized) { startTimestamp = timestamp; timestampInitialized = true; }
        	formattedTimestamp = String.valueOf(timestamp - startTimestamp);          
        }
        return new StringBuilder(formattedTimestamp).append(line).toString();
    }

    private String buildFilePath() {
        String logDir = SettingsManager.getSettings().getLoggerOutputDirPath();
        if (!logDir.endsWith(File.separator)) {
            logDir += File.separator;
        }
        Settings settings = SettingsManager.getSettings();
        if (settings.getLogfileNameText() != null
                && !settings.getLogfileNameText().isEmpty()) {
            logDir += settings.getLogfileNameText() + "_";
        }
        String ecuId = messageListener.getEcuInit() == null ? "EXTERNAL"
                : messageListener.getEcuInit().getEcuId();
        logDir += dateFormat.format(clock.get()) + "_[" + ecuId + "]" + ".csv";
        return logDir;
    }

}
