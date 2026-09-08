package com.romraider.logger.api;

/** File capture is an overlay on connection state, not an independent ECU connection. */
public final class LoggerRecordingStatusListener implements LoggerStatusListener {
    private final LoggerLiveDataBus bus;
    private LoggerSessionState beforeRecording = LoggerSessionState.LIVE_ECU;
    public LoggerRecordingStatusListener(LoggerLiveDataBus bus) { this.bus = java.util.Objects.requireNonNull(bus); }
    public void connecting() { }
    public void readingDataExternal() { readingData(); }
    public void readingData() {
        if (bus.getState() != LoggerSessionState.RECORDING) return;
        if (beforeRecording == LoggerSessionState.LIVE_EXTERNAL) bus.readingDataExternal();
        else bus.readingData();
    }
    public void loggingData() {
        if (bus.getState() != LoggerSessionState.RECORDING) beforeRecording = bus.getState();
        bus.loggingData();
    }
    public void stopped() { readingData(); }
}
