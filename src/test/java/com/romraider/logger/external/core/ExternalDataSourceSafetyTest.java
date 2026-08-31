/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.external.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.Action;

import org.junit.Test;

import com.romraider.logger.ecu.EcuLogger;

public class ExternalDataSourceSafetyTest {
    @Test
    public void missingPortDoesNotStartAConnectorRetryLoop() {
        StubDataSource delegate = new StubDataSource();
        GenericDataSourceManager manager = new GenericDataSourceManager(delegate);

        manager.connect();

        assertEquals(0, delegate.connectCalls);
    }

    @Test
    public void selectingPortWhileActiveConnectsWithoutLoggerRestart() throws Exception {
        StubDataSource delegate = new StubDataSource();
        GenericDataSourceManager manager = new GenericDataSourceManager(delegate);
        manager.connect();

        manager.setPort(" ttyUSB0 ");

        assertTrue(delegate.connected.await(2, TimeUnit.SECONDS));
        assertEquals("ttyUSB0", delegate.port);
        manager.disconnect();
    }

    @Test
    public void pluginCanDeclareOperatingSystemCompatibility() {
        Properties unrestricted = new Properties();
        assertTrue(ExternalDataSourceLoaderImpl.supportsCurrentPlatform(unrestricted));

        Properties impossible = new Properties();
        impossible.setProperty("datasource.os", "DefinitelyNotAnOperatingSystem");
        assertFalse(ExternalDataSourceLoaderImpl.supportsCurrentPlatform(impossible));
    }

    private static final class StubDataSource implements ExternalDataSource {
        private final CountDownLatch connected = new CountDownLatch(1);
        private volatile int connectCalls;
        private String port;

        public String getId() { return "stub"; }
        public String getName() { return "Stub sensor"; }
        public String getVersion() { return "1"; }
        public List<? extends ExternalDataItem> getDataItems() {
            return Collections.emptyList();
        }
        public Action getMenuAction(EcuLogger logger) { return null; }
        public void setPort(String port) { this.port = port; }
        public String getPort() { return port; }
        public void setProperties(Properties properties) { }
        public void connect() {
            connectCalls++;
            connected.countDown();
        }
        public void disconnect() { }
    }
}
