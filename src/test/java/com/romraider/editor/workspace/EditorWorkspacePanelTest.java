/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.Point;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import org.junit.Test;

public class EditorWorkspacePanelTest {
    @Test
    public void savedSectionsUseWeightedLayoutInsteadOfEqualCrampedRows() {
        JPanel panel = EditorWorkspacePanel.savedSections(new JPanel(),
                new JPanel(), new JPanel());
        assertEquals(GridBagLayout.class, panel.getLayout().getClass());
        assertEquals(300, panel.getPreferredSize().height);
        assertEquals(240, panel.getMinimumSize().height);
        java.awt.GridBagConstraints recent = ((GridBagLayout) panel.getLayout())
                .getConstraints(panel.getComponent(2));
        assertEquals(0.42, recent.weighty, 0.001);
    }

    @Test
    public void savedTableEntriesUseCompactNamesAndKeepRomIdentityAsHelp() {
        TableLocation location = new TableLocation("TEST_ROM",
                "High Octane Fuel Map");
        EditorWorkspacePanel.TableLocationRenderer renderer =
                new EditorWorkspacePanel.TableLocationRenderer(true);

        JLabel label = (JLabel) renderer.getListCellRendererComponent(
                new JList<TableLocation>(), location, 0, false, false);

        assertEquals("High Octane Fuel Map", label.getText());
        assertEquals("ROM: TEST_ROM", label.getToolTipText());
        assertNotNull(label.getIcon());
        assertEquals(36, new EditorWorkspacePanel.PlaceholderList<TableLocation>(
                new javax.swing.DefaultListModel<TableLocation>(), "Empty")
                .getFixedCellHeight());
    }

    @Test
    public void singleClickResolvesTheRowUnderThePointer() {
        javax.swing.DefaultListModel<TableLocation> model =
                new javax.swing.DefaultListModel<TableLocation>();
        TableLocation first = new TableLocation("ROM1", "Fuel Map");
        TableLocation second = new TableLocation("ROM1", "Ignition Map");
        model.addElement(first);
        model.addElement(second);
        JList<TableLocation> list = new JList<TableLocation>(model);
        list.setFixedCellHeight(30);
        list.setSize(200, 60);

        assertEquals(second,
                EditorWorkspacePanel.locationAt(list, new Point(10, 45)));
        assertEquals(1, list.getSelectedIndex());
        assertNull(EditorWorkspacePanel.locationAt(list, new Point(10, 80)));
    }

}
