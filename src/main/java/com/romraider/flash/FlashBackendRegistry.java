/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Process registry for independently supplied device and protocol modules. */
public final class FlashBackendRegistry {
    private static final FlashBackendRegistry INSTANCE = new FlashBackendRegistry();
    private final Map<String, FlashDeviceProvider> deviceProviders =
            new LinkedHashMap<String, FlashDeviceProvider>();
    private final Map<String, FlashProtocol> protocols =
            new LinkedHashMap<String, FlashProtocol>();

    private FlashBackendRegistry() {
    }

    public static FlashBackendRegistry getInstance() { return INSTANCE; }

    public synchronized void registerDeviceProvider(FlashDeviceProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        deviceProviders.put(required(provider.getId(), "provider id"), provider);
    }

    public synchronized void registerProtocol(FlashProtocol protocol) {
        if (protocol == null) throw new IllegalArgumentException("protocol is required");
        protocols.put(required(protocol.getId(), "protocol id"), protocol);
    }

    public synchronized List<FlashDeviceProvider> getDeviceProviders() {
        return Collections.unmodifiableList(
                new ArrayList<FlashDeviceProvider>(deviceProviders.values()));
    }

    public synchronized List<FlashProtocol> getProtocols() {
        return Collections.unmodifiableList(
                new ArrayList<FlashProtocol>(protocols.values()));
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
