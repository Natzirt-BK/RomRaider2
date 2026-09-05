/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.definition.PortableLoggerProfile;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoggerImportStateTest {
    @Test public void newDefinitionDoesNotSelectCatalog() {
        assertEquals(0, LoggerImportState.afterDefinition(null, "SSM").size());
    }
    @Test public void definitionReloadRetainsExactProfileAndUnits() {
        PortableLoggerProfile profile = new PortableLoggerProfile("SSM", List.of(
                new PortableLoggerProfile.Selection("P8", "rpm"),
                new PortableLoggerProfile.Selection("P1", "V")), Collections.emptyList());
        assertSame(profile, LoggerImportState.afterDefinition(profile, "SSM"));
        assertEquals(0, LoggerImportState.afterDefinition(profile, "MUT2").size());
    }
    @Test public void profileDoesNotCancelPendingDefinition() {
        LoggerImportState state = new LoggerImportState();
        int definition = state.beginDefinition();
        int profile = state.beginProfile();
        assertTrue(state.finishProfile(profile));
        assertTrue(state.isLoading());
        assertTrue(state.finishDefinition(definition));
        assertFalse(state.isLoading());
    }
    @Test public void definitionDoesNotCancelPendingProfile() {
        LoggerImportState state = new LoggerImportState();
        int profile = state.beginProfile();
        int definition = state.beginDefinition();
        assertTrue(state.finishDefinition(definition));
        assertTrue(state.isLoading());
        assertTrue(state.finishProfile(profile));
        assertFalse(state.isLoading());
    }
    @Test public void newerImportRejectsOldSuccessOrFailure() {
        LoggerImportState state = new LoggerImportState();
        int old = state.beginProfile();
        int current = state.beginProfile();
        assertFalse(state.finishProfile(old));
        assertTrue(state.isLoading());
        assertTrue(state.finishProfile(current));
        assertFalse(state.finishProfile(current));
        int oldDefinition = state.beginDefinition();
        int newDefinition = state.beginDefinition();
        assertFalse(state.finishDefinition(oldDefinition));
        assertTrue(state.finishDefinition(newDefinition));
    }
    @Test public void protocolChangeInvalidatesBothImports() {
        LoggerImportState state = new LoggerImportState();
        int definition = state.beginDefinition();
        int profile = state.beginProfile();
        state.reset();
        assertFalse(state.finishDefinition(definition));
        assertFalse(state.finishProfile(profile));
        assertFalse(state.isLoading());
    }
}
