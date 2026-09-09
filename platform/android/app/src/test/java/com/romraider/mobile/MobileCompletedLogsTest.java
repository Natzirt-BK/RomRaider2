/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.mobile;

import com.romraider.portable.PortableLogSample;
import com.romraider.portable.PortableLogSession;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobileCompletedLogsTest {
    private PortableLogSession log(double rpm) throws Exception {
        PortableLogSession log = new PortableLogSession();
        log.append(new PortableLogSample(0, "rpm", "Engine Speed", rpm, "rpm"));
        log.finish(); return log;
    }

    @Test public void promptIsOncePerNonemptyRecordingAndLaterKeepsData() throws Exception {
        MobileCompletedLogs store = new MobileCompletedLogs();
        assertNull(store.awaitingPrompt());
        store.remember(new PortableLogSession()); assertNull(store.awaitingPrompt());
        PortableLogSession first = log(900), second = log(950);
        store.remember(first); assertSame(first, store.awaitingPrompt());
        store.acknowledge(first); store.remember(first);
        assertNull(store.awaitingPrompt()); assertEquals(1, store.latest().size());
        store.remember(second); store.acknowledge(first);
        assertSame(second, store.awaitingPrompt());
        store.remember(null); store.remember(new PortableLogSession());
        assertSame(second, store.latest());
    }

    @Test public void exportRetainsExactSelectionWhenANewerRecordingCompletes() throws Exception {
        MobileCompletedLogs store = new MobileCompletedLogs();
        PortableLogSession first = log(900), second = log(950);
        store.remember(first); store.prepareExport(first); store.remember(second);
        assertSame(first, store.takeExport()); assertNull(store.takeExport());
        assertSame(second, store.latest());
    }

    @Test public void cancellingAnExportDoesNotDeleteOrAcknowledgeTheRecording() throws Exception {
        MobileCompletedLogs store = new MobileCompletedLogs();
        PortableLogSession log = log(900); store.remember(log); store.prepareExport(log);
        store.takeExport();
        assertSame(log, store.latest()); assertSame(log, store.awaitingPrompt());
        assertThrows(IllegalArgumentException.class, () -> store.prepareExport(new PortableLogSession()));
    }
}
