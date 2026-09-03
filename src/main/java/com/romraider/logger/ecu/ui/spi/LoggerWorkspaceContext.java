/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.spi;

import static com.romraider.util.ParamChecker.checkNotNull;

import com.romraider.logger.api.LoggerChannelService;
import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.api.LoggerMessageService;
import com.romraider.logger.api.LoggerSessionService;
import com.romraider.logger.api.LoggerWorkspacePreferences;

/** Shared services made available to a replacement Logger workspace. */
public final class LoggerWorkspaceContext {
    private final LoggerLiveDataBus liveData;
    private final LoggerSessionService session;
    private final LoggerChannelService channels;
    private final LoggerWorkspacePreferences preferences;
    private final LoggerMessageService messages;
    private final boolean hostSessionControls;

    public LoggerWorkspaceContext(LoggerLiveDataBus liveData,
            LoggerSessionService session, LoggerChannelService channels,
            LoggerWorkspacePreferences preferences) {
        this(liveData, session, channels, preferences, false);
    }

    public LoggerWorkspaceContext(LoggerLiveDataBus liveData,
            LoggerSessionService session, LoggerChannelService channels,
            LoggerWorkspacePreferences preferences,
            boolean hostSessionControls) {
        this(liveData, session, channels, preferences,
                new LoggerMessageService(), hostSessionControls);
    }

    public LoggerWorkspaceContext(LoggerLiveDataBus liveData,
            LoggerSessionService session, LoggerChannelService channels,
            LoggerWorkspacePreferences preferences,
            LoggerMessageService messages, boolean hostSessionControls) {
        checkNotNull(liveData, session, channels, preferences, messages);
        this.liveData = liveData;
        this.session = session;
        this.channels = channels;
        this.preferences = preferences;
        this.messages = messages;
        this.hostSessionControls = hostSessionControls;
    }

    public LoggerLiveDataBus getLiveData() { return liveData; }
    public LoggerSessionService getSession() { return session; }
    public LoggerChannelService getChannels() { return channels; }
    public LoggerWorkspacePreferences getPreferences() { return preferences; }
    public LoggerMessageService getMessages() { return messages; }
    public boolean hasHostSessionControls() { return hostSessionControls; }
}
