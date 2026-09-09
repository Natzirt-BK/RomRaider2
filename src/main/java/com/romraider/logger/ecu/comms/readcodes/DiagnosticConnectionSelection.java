/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.Settings;
import com.romraider.io.connection.*;
import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.io.connection.LoggerConnectionFactory;
import java.util.List;
import java.util.Arrays;

/** Captured adapter settings. Opening never consults the global Settings object. */
public final class DiagnosticConnectionSelection {
    private final String protocol, port, device, transport;
    private final boolean elm;
    private final ConnectionProperties properties;

    public DiagnosticConnectionSelection(Settings settings) {
        protocol = settings.getLoggerProtocol();
        port = settings.getLoggerPort();
        device = settings.getJ2534Device();
        transport = settings.getTransportProtocol();
        elm = settings.getElm327Enabled();
        properties = snapshot(settings.getLoggerConnectionProperties());
    }

    public LoggerConnection open() {
        return LoggerConnectionFactory.getConnection(protocol, port, properties, device, transport, elm);
    }

    public static List<Object> fingerprint(Settings settings) {
        ConnectionProperties p = settings.getLoggerConnectionProperties();
        return Arrays.asList(settings.getLoggerProtocol(), settings.getLoggerPort(), settings.getJ2534Device(),
                settings.getTransportProtocol(), settings.getElm327Enabled(),
                p == null ? null : List.of(p.getBaudRate(), p.getDataBits(), p.getStopBits(), p.getParity(), p.getConnectTimeout(), p.getSendTimeout()),
                p instanceof KwpConnectionProperties k ? List.of(k.getP1Max(), k.getP3Min(), k.getP4Min()) : null);
    }

    static ConnectionProperties snapshot(ConnectionProperties p) {
        if (p == null) throw new IllegalStateException("Load logger connection settings before reading codes");
        if (p instanceof KwpConnectionProperties k)
            return new KwpSerialConnectionProperties(p.getBaudRate(), p.getDataBits(), p.getStopBits(), p.getParity(),
                    p.getConnectTimeout(), p.getSendTimeout(), k.getP1Max(), k.getP3Min(), k.getP4Min());
        return new SerialConnectionProperties(p.getBaudRate(), p.getDataBits(), p.getStopBits(), p.getParity(),
                p.getConnectTimeout(), p.getSendTimeout());
    }
}
