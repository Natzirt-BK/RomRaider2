/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.definition.PortableLoggerProfile;
import java.util.Collections;

/** UI-thread import guards: loading a profile must not cancel its definition. */
public final class LoggerImportState {
    private int definitionRevision;
    private int profileRevision;
    private boolean definitionPending;
    private boolean profilePending;

    public int beginDefinition() { definitionPending = true; return ++definitionRevision; }
    public int beginProfile() { profilePending = true; return ++profileRevision; }
    public boolean finishDefinition(int revision) {
        if (revision != definitionRevision || !definitionPending) return false;
        definitionPending = false;
        return true;
    }
    public boolean finishProfile(int revision) {
        if (revision != profileRevision || !profilePending) return false;
        profilePending = false;
        return true;
    }
    public boolean isLoading() { return definitionPending || profilePending; }
    public void reset() {
        definitionRevision++;
        profileRevision++;
        definitionPending = false;
        profilePending = false;
    }

    /** A definition is a catalog, never an instruction to select every channel. */
    public static PortableLoggerProfile afterDefinition(PortableLoggerProfile current,
            String protocol) {
        if (current != null && (current.getProtocol().isEmpty()
                || current.getProtocol().equalsIgnoreCase(protocol))) return current;
        return new PortableLoggerProfile(protocol, Collections.emptyList(), Collections.emptyList());
    }
}
