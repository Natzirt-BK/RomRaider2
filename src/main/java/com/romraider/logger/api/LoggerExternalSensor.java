/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Immutable configuration row for one external Logger sensor plugin. */
public final class LoggerExternalSensor {
    private final String id;
    private final String name;
    private final String port;

    public LoggerExternalSensor(String id, String name, String port) {
        this.id = id == null ? "" : id;
        this.name = name == null ? this.id : name;
        this.port = port == null ? "" : port;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPort() { return port; }
}
