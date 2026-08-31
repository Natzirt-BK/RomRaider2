/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JViewport;

import org.junit.Test;

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
