/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApplicationActivityServiceTest {
    private final ApplicationActivityService service =
            ApplicationActivityService.getInstance();

    @Before
    public void reset() { service.resetForTesting(); }

    @After
    public void leaveIdle() { service.resetForTesting(); }

    @Test
    public void measuredWorkCompletesWithTruthfulProgressAndHistory() {
        service.update("Opening ROM", 12);
        ActivitySnapshot running = service.getCurrent();
        assertEquals(ActivityState.RUNNING, running.getState());
        assertEquals(12, running.getProgressPercent());

        service.complete("ROM opened");
        ActivitySnapshot complete = service.getCurrent();
        assertEquals(ActivityState.SUCCEEDED, complete.getState());
        assertEquals(100, complete.getProgressPercent());
        assertEquals(1, service.getHistory().size());
        assertEquals("ROM opened", service.getHistory().get(0).getMessage());
    }

    @Test
    public void indeterminateWorkAndIdleStateDoNotInventPercentages() {
        service.updateIndeterminate("Identifying ECU");
        assertFalse(service.getCurrent().hasMeasuredProgress());
        service.ready("Ready");
        assertEquals(ActivityState.IDLE, service.getCurrent().getState());
        assertFalse(service.getCurrent().hasMeasuredProgress());
        assertEquals(1, service.getHistory().size());
        assertEquals(ActivityState.SUCCEEDED,
                service.getHistory().get(0).getState());
    }

    @Test
    public void failuresRemainVisibleAndBoundedInRecentActivity() {
        service.update("Reading ECU", 30);
        service.fail("Interface disconnected");
        assertEquals(ActivityState.FAILED, service.getCurrent().getState());
        assertTrue(service.getCurrent().hasMeasuredProgress());
        for (int index = 0; index < 20; index++) {
            service.update("Task " + index, index);
            service.complete("Task " + index + " complete");
        }
        assertEquals(12, service.getHistory().size());
    }
}
