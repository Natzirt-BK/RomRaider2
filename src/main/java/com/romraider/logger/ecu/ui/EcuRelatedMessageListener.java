package com.romraider.logger.ecu.ui;

import com.romraider.logger.ecu.comms.query.EcuInit;

public interface EcuRelatedMessageListener extends MessageListener {
    EcuInit getEcuInit();
}
