/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui;

import com.romraider.logger.api.LoggerStatusListener;
import com.romraider.logger.ecu.comms.controller.LoggerController;
import com.romraider.logger.ecu.comms.query.Response;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.ui.handler.*;
import com.romraider.logger.ecu.ui.handler.file.FileLoggerControllerSwitchMonitor;
import com.romraider.logger.ecu.ui.paramlist.ParameterListTableModel;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real brokers and row models, fake controller/handlers; never starts a connection. */
public class DataRegistrationBrokerTest {
    @Test public void clearingOneTablePreservesOtherTablesSharingItsBroker() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            ParameterListTableModel parameters = new ParameterListTableModel(f.broker, "Parameters");
            ParameterListTableModel switches = new ParameterListTableModel(f.broker, "Switches");
            ParameterListTableModel externals = new ParameterListTableModel(f.broker, "Externals");
            LoggerData parameter = data("P1", EcuDataType.PARAMETER);
            LoggerData ecuSwitch = data("S1", EcuDataType.SWITCH);
            LoggerData external = data("E1", EcuDataType.EXTERNAL);
            parameters.addParam(parameter, true);
            switches.addParam(ecuSwitch, true);
            externals.addParam(external, true);
            parameters.clear();
            assertEquals(Set.of(ecuSwitch, external), f.queries);
            assertEquals(f.queries, f.handlers);
            assertEquals(0, parameters.getRowCount());
            assertEquals(1, switches.getSelectedCount());
            assertEquals(1, externals.getSelectedCount());
            parameters.clear(); // Idempotent and still scoped to this table.
            switches.clear();
            assertEquals(Set.of(external), f.queries);
            assertEquals(f.queries, f.handlers);
            externals.clear();
            assertTrue(f.queries.isEmpty());
            assertTrue(f.handlers.isEmpty());
        });
    }

    @Test public void definitionInvalidationRemovesEcuRegistrationsFromEveryBrokerOnly() {
        // Live, graph, gauges, MAF, injector and dyno each own a broker.
        for (int owner = 0; owner < 6; owner++) {
            Fixture f = new Fixture();
            LoggerData external = data("E1", EcuDataType.EXTERNAL);
            f.broker.registerLoggerDataForLogging(data("P1", EcuDataType.PARAMETER));
            f.broker.registerLoggerDataForLogging(data("S1", EcuDataType.SWITCH));
            f.broker.registerLoggerDataForLogging(external);
            f.broker.clearEcuData();
            f.broker.clearEcuData();
            assertEquals(Set.of(external), f.queries);
            assertEquals(f.queries, f.handlers);
            f.broker.registerLoggerDataForLogging(external);
            assertEquals(1, f.queries.size());
            f.broker.clear();
            assertTrue(f.queries.isEmpty());
            assertTrue(f.handlers.isEmpty());
        }
    }

    private static LoggerData data(String id, EcuDataType type) {
        return new LoggerData() {
            private boolean selected;
            private final EcuDataConvertor converter = new EcuParameterConvertorImpl();
            public String getId() { return id; }
            public String getName() { return id; }
            public String getDescription() { return "Synthetic"; }
            public EcuDataConvertor getSelectedConvertor() { return converter; }
            public EcuDataConvertor[] getConvertors() { return new EcuDataConvertor[] {converter}; }
            public void selectConvertor(EcuDataConvertor next) { }
            public EcuDataType getDataType() { return type; }
            public boolean isSelected() { return selected; }
            public void setSelected(boolean value) { selected = value; }
        };
    }

    private static final class Fixture {
        final Set<LoggerData> queries = new LinkedHashSet<>();
        final Set<LoggerData> handlers = new LinkedHashSet<>();
        final DataRegistrationBrokerImpl broker;
        Fixture() {
            DataUpdateHandlerManagerImpl manager = new DataUpdateHandlerManagerImpl();
            manager.addHandler(new DataUpdateHandler() {
                public void registerData(LoggerData data) { handlers.add(data); }
                public void deregisterData(LoggerData data) { handlers.remove(data); }
                public void handleDataUpdate(Response response) { fail("No samples expected"); }
                public void cleanUp() { }
                public void reset() { }
            });
            broker = new DataRegistrationBrokerImpl(new LoggerController() {
                public void addLogger(String caller, LoggerData data) { queries.add(data); }
                public void removeLogger(String caller, LoggerData data) { queries.remove(data); }
                public void setFileLoggerSwitchMonitor(FileLoggerControllerSwitchMonitor monitor) { }
                public boolean isStarted() { return false; }
                public void start() { fail("No controller start permitted"); }
                public void stop() { fail("No controller stop needed"); }
                public void addListener(LoggerStatusListener listener) { }
            }, manager);
        }
    }
}
