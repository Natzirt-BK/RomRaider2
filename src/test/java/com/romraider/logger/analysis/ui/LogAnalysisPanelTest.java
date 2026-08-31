/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;

import org.junit.Test;

public class LogAnalysisPanelTest {
    @Test
    public void exposesOfflinePlaybackAndGraphControls() {
        LogAnalysisPanel panel = new LogAnalysisPanel();

        assertNotNull(findNamed(panel, JButton.class,
                "LOAD LOG FOR ANALYSIS"));
        assertNotNull(findNamed(panel, JButton.class,
                "LOG PLAYBACK PLAY PAUSE"));
        assertNotNull(findNamed(panel, LogTimeGraphPanel.class,
                "OFFLINE LOG TIME GRAPH"));
        assertNotNull(findNamed(panel, LogXyGraphPanel.class,
                "OFFLINE LOG XY GRAPH"));
        assertNotNull(findNamed(panel, JComboBox.class,
                "LOG XY X AXIS"));
        assertNotNull(findNamed(panel, JComboBox.class,
                "LOG XY Y AXIS"));
        assertNotNull(findNamed(panel, JButton.class,
                "REPLAY LAST LOG CAPTURE"));
        assertNotNull(findNamed(panel, JButton.class,
                "ADD LOG MARKER"));
        assertNotNull(findNamed(panel, JButton.class,
                "PREVIOUS LOG MARKER"));
        assertNotNull(findNamed(panel, JButton.class,
                "NEXT LOG MARKER"));
        assertNotNull(findNamed(panel, JComboBox.class,
                "LOG MARKER TYPE"));
        JTable statistics = findNamed(panel, JTable.class,
                "LOG ANALYSIS STATISTICS");
        assertNotNull(statistics);
        assertEquals(JTable.AUTO_RESIZE_ALL_COLUMNS,
                statistics.getAutoResizeMode());
        assertEquals(180, statistics.getColumnModel().getColumn(0).getMinWidth());
        assertEquals(65, statistics.getColumnModel().getColumn(2).getMinWidth());
    }

    private static <T extends Component> T findNamed(Container root,
            Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) {
                return type.cast(child);
            }
            if (child instanceof Container) {
                T nested = findNamed((Container) child, type, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
