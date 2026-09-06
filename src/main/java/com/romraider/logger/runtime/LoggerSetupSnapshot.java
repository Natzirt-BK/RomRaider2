/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import com.romraider.logger.api.LoggerChannel;
import java.util.*;

/** Immutable transfer context. The owning runtime validates its revision before use. */
public final class LoggerSetupSnapshot {
    final Object owner;
    final long revision;
    final LoggerDefinitionSource source;
    private final String protocol;
    private final List<LoggerChannel> selected;
    LoggerSetupSnapshot(Object owner, long revision, LoggerDefinitionSource source,
            String protocol, List<LoggerChannel> selected) {
        this.owner = owner; this.revision = revision; this.source = source; this.protocol = protocol;
        this.selected = Collections.unmodifiableList(new ArrayList<>(selected));
    }
    public byte[] definitionBytes() { return source.bytes(); }
    public String protocol() { return protocol; }
    public List<LoggerChannel> selectedChannels() { return selected; }
}
