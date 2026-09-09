/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.mobile;

import com.romraider.portable.PortableLogSession;

/** Completed data only: no Activity, Service, adapter or permission ownership. */
final class MobileCompletedLogs {
    static final MobileCompletedLogs INSTANCE = new MobileCompletedLogs();
    private PortableLogSession latest, pendingExport;
    private boolean acknowledged;

    synchronized void remember(PortableLogSession completed) {
        if (completed == null || completed.size() == 0 || completed == latest) return;
        latest = completed; acknowledged = false;
    }
    synchronized PortableLogSession latest() { return latest; }
    synchronized PortableLogSession awaitingPrompt() { return acknowledged ? null : latest; }
    synchronized void acknowledge(PortableLogSession completed) {
        if (completed == latest) acknowledged = true;
    }
    synchronized void prepareExport(PortableLogSession completed) {
        if (completed == null || completed.size() == 0) throw new IllegalArgumentException("No completed recording selected");
        pendingExport = completed;
    }
    synchronized PortableLogSession takeExport() {
        PortableLogSession result = pendingExport; pendingExport = null; return result;
    }
}
