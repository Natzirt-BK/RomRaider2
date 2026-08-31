/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;

import com.romraider.Settings;
import com.romraider.xml.DOMSettingsUnmarshaller;

public class EditorWorkspaceSettingsMigrationTest {
    @Test
    public void restoresFavoritesRecentsAndPerRomOpenTables() throws Exception {
        Settings settings = load("<settings><editor-workspace schema=\"1\">"
                + "<favorites><table rom=\"EVO8\" name=\"Boost Limit\"/></favorites>"
                + "<recent><table rom=\"EVO8\" name=\"Timing\"/>"
                + "<table rom=\"EVO8\" name=\"Fuel\"/></recent>"
                + "<open rom=\"EVO8\"><table name=\"Fuel\"/>"
                + "<table name=\"Timing\"/></open>"
                + "</editor-workspace></settings>");
        EditorWorkspacePreferences preferences = settings.getEditorWorkspacePreferences();
        assertTrue(preferences.isFavorite(new TableLocation("EVO8", "Boost Limit")));
        assertEquals("Timing", preferences.getRecent().get(0).getTableName());
        assertEquals("Fuel", preferences.getRecent().get(1).getTableName());
        assertEquals(2, preferences.getOpenTables("EVO8").size());
    }

    @Test
    public void futureSchemaLeavesSafeEmptyWorkspace() throws Exception {
        Settings settings = load("<settings><editor-workspace schema=\"9\">"
                + "<favorites><table rom=\"EVO8\" name=\"Boost\"/></favorites>"
                + "</editor-workspace></settings>");
        assertTrue(settings.getEditorWorkspacePreferences().getFavorites().isEmpty());
        assertTrue(settings.getEditorWorkspacePreferences().getRecent().isEmpty());
    }

    @Test
    public void restoresVersionTwoActiveTableWithoutChangingTabOrder()
            throws Exception {
        Settings settings = load("<settings><editor-workspace schema=\"2\">"
                + "<open rom=\"EVO8\" active=\"Timing\">"
                + "<table name=\"Fuel\"/><table name=\"Timing\"/>"
                + "<table name=\"Boost\"/></open>"
                + "</editor-workspace></settings>");
        EditorWorkspacePreferences preferences =
                settings.getEditorWorkspacePreferences();
        assertEquals("Fuel", preferences.getOpenTables("EVO8").get(0));
        assertEquals("Timing", preferences.getOpenTables("EVO8").get(1));
        assertEquals("Timing", preferences.getActiveTable("EVO8"));
    }

    @Test
    public void restoresVersionThreeMapNotes() throws Exception {
        Settings settings = load("<settings><editor-workspace schema=\"3\">"
                + "<notes><table rom=\"EVO8\" name=\"Fuel\" "
                + "text=\"Adjusted for E85 blend.\"/></notes>"
                + "</editor-workspace></settings>");
        assertEquals("Adjusted for E85 blend.",
                settings.getEditorWorkspacePreferences().getTableNote(
                        new TableLocation("EVO8", "Fuel")));
    }

    private Settings load(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return new DOMSettingsUnmarshaller().unmarshallSettings(document.getDocumentElement());
    }
}
