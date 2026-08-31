/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import org.junit.Test;

public class TableVisualComponentsTest {
    @Test
    public void colorScaleLegendCanBeCreatedOnJava8() {
        TableColorScaleLegend legend = new TableColorScaleLegend(new Table1D());

        assertEquals("Table color scale", legend.getName());
        assertNotNull(legend.getToolTipText());
        assertEquals(40, legend.getPreferredSize().height);
    }

    @Test
    public void tableCellChangeDirectionIsDeterministic() {
        assertEquals(-1, DataCellView.compareChange(12.0, 11.5));
        assertEquals(0, DataCellView.compareChange(12.0, 12.0));
        assertEquals(1, DataCellView.compareChange(12.0, 12.5));
    }

    @Test
    public void selectionTintPreservesHeatmapInformation() {
        Color heatmap = new Color(20, 80, 140);
        Color selection = new Color(60, 140, 220);

        assertEquals(heatmap, DataCellView.blend(heatmap, selection, 0.0));
        assertEquals(selection, DataCellView.blend(heatmap, selection, 1.0));
        assertEquals(new Color(30, 95, 160),
                DataCellView.blend(heatmap, selection, 0.25));
    }

    @Test
    public void selectionTintClampsInvalidWeights() {
        Color heatmap = new Color(20, 80, 140);
        Color selection = new Color(60, 140, 220);

        assertEquals(heatmap, DataCellView.blend(heatmap, selection, -1.0));
        assertEquals(selection, DataCellView.blend(heatmap, selection, 2.0));
    }

    @Test
    public void selectionSummaryReportsCountAndRange() {
        Table1D table = new Table1D();
        table.setData(new DataCell[] {
                new SelectionCell(table, 14.5, true),
                new SelectionCell(table, 10.0, false),
                new SelectionCell(table, 12.0, true)
        });

        TableSelectionSummary summary = TableSelectionSummary.of(table);

        assertEquals(2, summary.getSelectedCells());
        assertEquals(2, summary.getChangedCells());
        assertEquals(12.0, summary.getMinimum(), 0.0);
        assertEquals(14.5, summary.getMaximum(), 0.0);
    }

    @Test
    public void switchStateControlsUseACenteredWorkspaceCard() {
        TableSwitch table = new TableSwitch();
        table.setName("Test switch");
        table.setData(new DataCell[] {
                new SelectionCell(table, 0.0, false)
        });
        table.setPresetValues("Disabled", "00");
        table.setPresetValues("Enabled", "01");
        TableSwitchView view = new TableSwitchView(table);

        view.populateTableVisual();

        Component center = ((BorderLayout) view.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        assertNotSame(view.centerPanel, center);
        assertEquals("SWITCH STATE CARD HOST", center.getName());
    }

    private static final class SelectionCell extends DataCell {
        private static final long serialVersionUID = 1L;
        private final double value;
        private final boolean selected;

        private SelectionCell(Table table, double value, boolean selected) {
            super(table, (Rom) null);
            this.value = value;
            this.selected = selected;
        }

        @Override public double getRealValue() { return value; }
        @Override public double getBinValue() { return value; }
        @Override public double getOriginalValue() { return 0.0; }
        @Override public boolean isSelected() { return selected; }
    }
}
