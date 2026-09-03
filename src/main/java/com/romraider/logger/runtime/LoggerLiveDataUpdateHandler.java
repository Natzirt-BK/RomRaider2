/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import static com.romraider.util.ParamChecker.checkNotNull;

import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.ecu.comms.query.Response;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.ui.handler.DataUpdateHandler;

/** Publishes transport responses without creating a Swing table model. */
public final class LoggerLiveDataUpdateHandler implements DataUpdateHandler {
    private final LoggerLiveDataBus liveData;

    public LoggerLiveDataUpdateHandler(LoggerLiveDataBus liveData) {
        checkNotNull(liveData, "liveData");
        this.liveData = liveData;
    }

    @Override
    public void registerData(LoggerData loggerData) {
        checkNotNull(loggerData, "loggerData");
    }

    @Override
    public void handleDataUpdate(Response response) {
        checkNotNull(response, "response");
        for (LoggerData data : response.getData()) {
            liveData.publish(data, response.getDataValue(data));
        }
    }

    @Override
    public void deregisterData(LoggerData loggerData) {
        liveData.remove(loggerData);
    }

    @Override
    public void cleanUp() {
    }

    @Override
    public void reset() {
        liveData.clearSamples();
    }
}
