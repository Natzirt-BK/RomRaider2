/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

public interface LoggerLiveDataListener {
    void sessionStateChanged(LoggerSessionState state);
    void sampleUpdated(LiveDataSample sample);
    void parameterRemoved(String parameterId);
}
