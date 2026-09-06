/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import static org.junit.Assert.*;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;

import org.junit.Test;

public class LogAnalysisPanelTest {
    @org.junit.Rule public org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();
    @Test public void malformedOrExternallyChangedMarkersCannotBeOverwrittenFromSwing() throws Exception {
        var source = temporary.newFile("synthetic.csv");
        java.nio.file.Files.writeString(source.toPath(), "Value\n1\n2\n");
        var data = new com.romraider.logger.analysis.RomRaiderCsvLogParser().parse(source);
        var store = new com.romraider.logger.analysis.LogMarkerStore();
        java.nio.file.Files.writeString(store.sidecar(source), "format.version=99\nmarker.count=0\n");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                LogAnalysisPanel panel = new LogAnalysisPanel();
                var install = LogAnalysisPanel.class.getDeclaredMethod("setDataset", com.romraider.logger.analysis.LogDataset.class, java.io.File.class);
                install.setAccessible(true); install.invoke(panel, data, source);
                JButton add = findNamed(panel, JButton.class, "ADD LOG MARKER"); assertFalse(add.isEnabled());
                assertTrue(add.getToolTipText().contains("Reload the log"));
                assertTrue(java.nio.file.Files.readString(store.sidecar(source)).contains("99"));
                java.nio.file.Files.delete(store.sidecar(source)); install.invoke(panel, data, source); assertTrue(add.isEnabled());
                store.save(source, java.util.List.of(new com.romraider.logger.analysis.LogMarker(1, com.romraider.logger.analysis.LogMarkerType.CUSTOM, "external")));
                add.doClick(); assertFalse(add.isEnabled());
                assertEquals("external", store.load(source, 2).get(0).getLabel());
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }
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
