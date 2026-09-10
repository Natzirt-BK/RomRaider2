/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.query.dimemod;

import com.romraider.Settings;
import com.romraider.io.connection.ConnectionProperties;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.definition.Module;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Frozen observed identity and discovery provenance, not authentication of a vehicle. */
public final class DmCacheBinding {
    private final List<String> route;
    private final String ecuId, moduleName;
    private final byte[] initialization, moduleAddress, tester;
    private final int address, length;

    public DmCacheBinding(List<String> route, EcuInit ecu, Module module, int address, int length) {
        if (ecu == null || ecu.getEcuId() == null || ecu.getEcuId().isBlank()
                || ecu.getEcuInitBytes() == null || ecu.getEcuInitBytes().length == 0 || module == null
                || address < 0 || length < 4 || length > 0xffff || (long) address + length > 0x1000000L)
            throw new IllegalArgumentException("DimeMod cache requires identification and bounded discovery provenance");
        this.route = List.copyOf(route);
        ecuId = ecu.getEcuId(); initialization = ecu.getEcuInitBytes().clone();
        moduleName = module.getName(); moduleAddress = module.getAddress().clone(); tester = module.getTester().clone();
        this.address = address; this.length = length;
    }

    public int address() { return address; }
    public int length() { return length; }
    public boolean matchesRoute(List<String> current, Module module) {
        return route.equals(current) && module != null && Objects.equals(moduleName, module.getName())
                && Arrays.equals(moduleAddress, module.getAddress()) && Arrays.equals(tester, module.getTester());
    }
    public boolean matchesEcu(EcuInit ecu) {
        return ecu != null && ecuId.equals(ecu.getEcuId()) && Arrays.equals(initialization, ecu.getEcuInitBytes());
    }

    /** Logical adapter/port and wire configuration; no claim of a hardware serial number. */
    public static List<String> route(Settings settings, String wireProtocol) {
        ConnectionProperties properties = settings.getLoggerConnectionProperties();
        String serial = properties == null ? "" : properties.getBaudRate() + ":" + properties.getDataBits()
                + ":" + properties.getStopBits() + ":" + properties.getParity()
                + ":" + properties.getConnectTimeout() + ":" + properties.getSendTimeout();
        return List.of(Objects.toString(settings.getLoggerProtocol(), ""),
                Objects.toString(settings.getTransportProtocol(), ""), Objects.toString(settings.getLoggerPort(), ""),
                Objects.toString(settings.getJ2534Device(), ""), Boolean.toString(settings.getElm327Enabled()), serial, wireProtocol);
    }
}
