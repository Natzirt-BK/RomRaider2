/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.junit.Test;

import com.romraider.editor.ecu.EditorTabbedWorkspace;
import com.romraider.swing.TableFrame;

/** Guards responsive fitting of legacy calibration grids in editor tabs. */
public final class TableViewResponsiveTest {
    @Test
    public void embeddedTableFitsReadableViewportAndScrollsWhenTooNarrow() {
        TestTableView view = new TestTableView(16, 12);
        JViewport viewport = new JViewport();
        viewport.setView(view);
        view.setEmbeddedDocumentMode(true);

        viewport.setExtentSize(new Dimension(900, 520));
        assertTrue(view.getScrollableTracksViewportWidth());
        assertTrue(view.getScrollableTracksViewportHeight());

        viewport.setExtentSize(new Dimension(420, 180));
        assertFalse(view.getScrollableTracksViewportWidth());
        assertFalse(view.getScrollableTracksViewportHeight());
    }

    @Test
    public void standaloneTableKeepsLegacyPreferredCanvas() {
        TestTableView view = new TestTableView(4, 3);
        JViewport viewport = new JViewport();
        viewport.setView(view);
        viewport.setExtentSize(new Dimension(1200, 800));

        assertFalse(view.getScrollableTracksViewportWidth());
        assertFalse(view.getScrollableTracksViewportHeight());
    }

    @Test
    public void workspaceShrinkReachesTableViewportAndEnablesScrolling() {
        TestTableView view = new TestTableView(16, 12);
        TableFrame frame = new TableFrame("Fuel Map | test.bin", view);
        EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(
                new EditorTabbedWorkspace.Listener() {
                    public void tableActivated(TableFrame selected) { }
                    public void closeRequested(TableFrame selected) { }
                });
        workspace.open(frame);

        JScrollPane outer = new JScrollPane(workspace,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        outer.setSize(900, 500);
        layoutTree(outer);
        layoutTree(outer);

        JScrollPane tableScroll = findNamed(workspace, JScrollPane.class,
                "CALIBRATION TABLE SCROLL");
        assertNotNull(tableScroll);
        assertFalse(tableScroll.getHorizontalScrollBar().isVisible());
        assertEquals(tableScroll.getViewport().getExtentSize().width,
                view.getWidth());

        outer.setSize(430, 500);
        layoutTree(outer);
        layoutTree(outer);

        assertEquals(outer.getViewport().getExtentSize().width,
                workspace.getWidth());
        assertTrue(tableScroll.getHorizontalScrollBar().isVisible());
        assertTrue(view.getWidth() > tableScroll.getViewport()
                .getExtentSize().width);

        outer.setSize(900, 500);
        layoutTree(outer);
        layoutTree(outer);
        assertFalse(tableScroll.getHorizontalScrollBar().isVisible());
        assertEquals(tableScroll.getViewport().getExtentSize().width,
                view.getWidth());
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) layoutTree((Container) child);
        }
    }

    private static <T extends Component> T findNamed(Container root,
            Class<T> type, String name) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component) && name.equals(component.getName())) {
                return type.cast(component);
            }
            if (component instanceof Container) {
                T found = findNamed((Container) component, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class TestTableView extends TableView {
        private static final long serialVersionUID = 1L;

        TestTableView(int columns, int rows) {
            super(new Table1D());
            centerLayout.setColumns(columns);
            centerLayout.setRows(rows);
            for (int index = 0; index < columns * rows; index++) {
                JLabel cell = new JLabel("14.70");
                cell.setPreferredSize(getSettings().getCellSize());
                centerPanel.add(cell);
            }
        }

        public void populateTableVisual() { }
        public void updateTableLabel() { }
        public void cursorUp() { }
        public void cursorDown() { }
        public void cursorLeft() { }
        public void cursorRight() { }
        public void shiftCursorUp() { }
        public void shiftCursorDown() { }
        public void shiftCursorLeft() { }
        public void shiftCursorRight() { }
    }
}
