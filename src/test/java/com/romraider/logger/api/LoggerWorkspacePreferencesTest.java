/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;

import com.romraider.Settings;
import com.romraider.xml.DOMSettingsUnmarshaller;

public class LoggerWorkspacePreferencesTest {
    @Test
    public void publishesOnlyChangedWorkspacePreferences() {
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<LoggerWorkspaceView> view =
                new AtomicReference<LoggerWorkspaceView>();
        AtomicReference<Boolean> dark = new AtomicReference<Boolean>();
        LoggerWorkspacePreferences preferences =
                new LoggerWorkspacePreferences(LoggerWorkspaceView.DATA,
                        false, (nextView, nextDark) -> {
                            updates.incrementAndGet();
                            view.set(nextView);
                            dark.set(nextDark);
                        });

        preferences.setView(LoggerWorkspaceView.DATA);
        preferences.setDarkTheme(false);
        assertEquals(0, updates.get());

        preferences.setView(LoggerWorkspaceView.GRAPH);
        preferences.setDarkTheme(true);

        assertEquals(2, updates.get());
        assertEquals(LoggerWorkspaceView.GRAPH, view.get());
        assertTrue(dark.get().booleanValue());
        assertEquals(LoggerWorkspaceView.GRAPH, preferences.getView());
        assertTrue(preferences.isDarkTheme());
    }

    @Test
    public void restoresWorkspaceViewAndOptionalTheme() throws Exception {
        Settings saved = load("<settings><logger><tabs workspace=\"DASHBOARD\" "
                + "workspace-dark=\"true\"/></logger></settings>");
        assertEquals(LoggerWorkspaceView.DASHBOARD,
                saved.getLoggerWorkspaceView());
        assertTrue(saved.getLoggerWorkspaceDarkTheme().booleanValue());

        Settings legacy = load("<settings><logger><tabs workspace=\"future\"/>"
                + "</logger></settings>");
        assertEquals(LoggerWorkspaceView.OVERVIEW,
                legacy.getLoggerWorkspaceView());
        assertNull(legacy.getLoggerWorkspaceDarkTheme());
    }

    @Test
    public void viewNamesHaveSafeDefaults() {
        assertEquals(LoggerWorkspaceView.OVERVIEW,
                LoggerWorkspaceView.fromName(null));
        assertEquals(LoggerWorkspaceView.OVERVIEW,
                LoggerWorkspaceView.fromName("unknown"));
        assertEquals(LoggerWorkspaceView.GRAPH,
                LoggerWorkspaceView.fromName(" graph "));
        assertFalse(LoggerWorkspaceView.DATA.getDisplayName().isEmpty());
    }

    private Settings load(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8)));
        return new DOMSettingsUnmarshaller().unmarshallSettings(
                document.getDocumentElement());
    }
}
