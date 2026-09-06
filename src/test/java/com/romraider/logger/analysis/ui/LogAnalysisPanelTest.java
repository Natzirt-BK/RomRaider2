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
    @Test public void publicLoadMarshalsToSwingAndDetachedPanelRejectsQueuedResultsBeforeReattach() throws Exception {
        var first = temporary.newFile("first.csv"); var second = temporary.newFile("second.csv");
        java.nio.file.Files.writeString(first.toPath(), "Value\n1\n2\n");
        java.nio.file.Files.writeString(second.toPath(), "Value\n8\n9\n");
        LogAnalysisPanel[] panel = {null}; java.util.concurrent.Future<?>[] pending = {null};
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> panel[0] = new LogAnalysisPanel());
            panel[0].load(first); // Exercise a caller that is not the Swing event thread.
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { pending[0] = ((SwingLogLoadTask) field(panel[0], "logLoads")).pending(); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
            pending[0].get(5, java.util.concurrent.TimeUnit.SECONDS);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    assertEquals(first, field(panel[0], "datasetFile"));
                    assertTrue(findNamed(panel[0], JButton.class, "LOAD LOG FOR ANALYSIS").isEnabled());
                    panel[0].load(second);
                    ((SwingLogLoadTask) field(panel[0], "logLoads")).pending().get(5, java.util.concurrent.TimeUnit.SECONDS);
                    // Parsing completed, but its event-thread callback is still queued.
                    panel[0].removeNotify(); assertNull(field(panel[0], "logLoads")); panel[0].load(second);
                    assertNull(field(panel[0], "logLoads"));
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    assertEquals(first, field(panel[0], "datasetFile"));
                    panel[0].addNotify(); panel[0].load(second); pending[0] = ((SwingLogLoadTask) field(panel[0], "logLoads")).pending();
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            pending[0].get(5, java.util.concurrent.TimeUnit.SECONDS);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    assertEquals(second, field(panel[0], "datasetFile"));
                    var table = findNamed(panel[0], JTable.class, "LOG ANALYSIS STATISTICS"); assertEquals(1, table.getRowCount());
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        } finally { javax.swing.SwingUtilities.invokeAndWait(() -> { if (panel[0] != null) panel[0].removeNotify(); }); }
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    @Test public void malformedOrExternallyChangedMarkersCannotBeOverwrittenFromSwing() throws Exception {
        var source = temporary.newFile("synthetic.csv");
        java.nio.file.Files.writeString(source.toPath(), "Value\n1\n2\n");
        var store = new com.romraider.logger.analysis.LogMarkerStore();
        java.nio.file.Files.writeString(store.sidecar(source), "format.version=99\nmarker.count=0\n");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                LogAnalysisPanel panel = new LogAnalysisPanel();
                var install = LogAnalysisPanel.class.getDeclaredMethod("installLog", SwingLogLoadTask.PreparedLog.class);
                install.setAccessible(true); install.invoke(panel, SwingLogLoadTask.prepare(source));
                JButton add = findNamed(panel, JButton.class, "ADD LOG MARKER"); assertFalse(add.isEnabled());
                assertTrue(add.getToolTipText().contains("Reload the log"));
                assertTrue(java.nio.file.Files.readString(store.sidecar(source)).contains("99"));
                java.nio.file.Files.delete(store.sidecar(source)); install.invoke(panel, SwingLogLoadTask.prepare(source)); assertTrue(add.isEnabled());
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
