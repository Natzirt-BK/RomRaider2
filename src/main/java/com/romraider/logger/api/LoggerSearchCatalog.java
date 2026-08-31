/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.romraider.logger.ecu.definition.EcuData;
import com.romraider.logger.ecu.definition.EcuParameter;
import com.romraider.logger.ecu.definition.EcuSwitch;
import com.romraider.search.SearchEntry;
import com.romraider.search.SearchKind;
import com.romraider.search.UnifiedSearchIndex;

/** Publishes logger definitions without exposing logger Swing components. */
public final class LoggerSearchCatalog {
    public static final String SOURCE_ID = "logger:definitions";

    private LoggerSearchCatalog() {
    }

    public static void publish(List<EcuParameter> parameters,
            List<EcuSwitch> diagnosticCodes) {
        List<SearchEntry> entries = new ArrayList<SearchEntry>();
        if (parameters != null) {
            for (EcuParameter parameter : parameters) {
                entries.add(entry(parameter, SearchKind.LOGGER_PARAMETER,
                        "Available in Logger"));
            }
        }
        if (diagnosticCodes != null) {
            for (EcuSwitch code : diagnosticCodes) {
                entries.add(entry(code, SearchKind.DTC,
                        "Diagnostic trouble code"));
            }
        }
        UnifiedSearchIndex.getInstance().replaceSource(SOURCE_ID, entries);
    }

    private static SearchEntry entry(EcuData data, SearchKind kind,
            String context) {
        String group = data.getGroup();
        if (group == null || group.trim().isEmpty()) group = context;
        List<String> aliases = data.getId() == null
                ? Collections.<String>emptyList()
                : Arrays.asList(data.getId());
        return new SearchEntry(kind, SOURCE_ID, data.getId(), data.getName(),
                group, data.getDescription(), aliases);
    }
}
