/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.romraider.maps.Table;

/** Selects a renderer without exposing its implementation to the editor. */
public final class MapVisualizationRegistry {
    private final List<MapVisualizationProvider> providers;

    public MapVisualizationRegistry(List<MapVisualizationProvider> providers) {
        this.providers = Collections.unmodifiableList(
                new ArrayList<MapVisualizationProvider>(providers));
    }

    public static MapVisualizationRegistry createDefault() {
        List<MapVisualizationProvider> providers =
                new ArrayList<MapVisualizationProvider>();
        providers.add(new Java2dSurfaceVisualizationProvider());
        return new MapVisualizationRegistry(providers);
    }

    public MapVisualizationProvider findProvider(Table table) {
        for (MapVisualizationProvider provider : providers) {
            try {
                if (provider != null && provider.supports(table)) return provider;
            } catch (RuntimeException ignored) {
                // A third-party provider must never prevent a map from opening.
            } catch (LinkageError ignored) {
                // A third-party provider must never prevent a map from opening.
            }
        }
        return null;
    }

    public List<MapVisualizationProvider> getProviders() {
        return providers;
    }
}
