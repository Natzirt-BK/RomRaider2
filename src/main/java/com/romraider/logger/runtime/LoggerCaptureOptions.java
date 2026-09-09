/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import com.romraider.Settings;

/** Capture preferences, applied together with connection setup while disconnected. */
public record LoggerCaptureOptions(boolean fastPolling, boolean switchRecording,
        boolean absoluteTimestamp, boolean usNumbers, String logName) {
    public LoggerCaptureOptions {
        logName = logName == null ? "" : logName.trim();
        if (logName.length() > 80 || logName.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0)
                || logName.equals(".") || logName.equals("..") || logName.endsWith("."))
            throw new IllegalArgumentException("Log name must be a short filename prefix without path separators or special filename characters.");
    }

    public static LoggerCaptureOptions from(Settings settings) {
        return new LoggerCaptureOptions(settings.isFastPoll(), settings.isFileLoggingControllerSwitchActive(),
                settings.isFileLoggingAbsoluteTimestamp(), settings.isUsNumberFormat(), settings.getLogfileNameText());
    }

    void apply(Settings settings) {
        settings.setFastPoll(fastPolling);
        settings.setFileLoggingControllerSwitchActive(switchRecording);
        settings.setFileLoggingAbsoluteTimestamp(absoluteTimestamp);
        if (settings.isUsNumberFormat() != usNumbers) settings.setLocale(usNumbers ? "en_US" : "system");
        settings.setLogfileNameText(logName);
    }
}
