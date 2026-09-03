/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Collection;

/** Applies one selection state to a group of Logger channels. */
@FunctionalInterface
public interface LoggerChannelSelectionCommand {
    void setSelected(Collection<String> parameterIds, boolean selected);
}
