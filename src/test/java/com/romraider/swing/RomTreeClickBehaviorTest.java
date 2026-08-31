/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;

import org.junit.Test;

import com.romraider.maps.DataCell;
import com.romraider.maps.Table1D;

public class RomTreeClickBehaviorTest {
    @Test
    public void singleClickModeDoesNotTreatSecondDoubleClickEventAsAnotherOpen() {
        assertTrue(RomTree.shouldOpenTable(1, 1));
        assertFalse(RomTree.shouldOpenTable(2, 1));
    }

    @Test
    public void doubleClickModeWaitsForTheSecondClick() {
        assertFalse(RomTree.shouldOpenTable(1, 2));
        assertTrue(RomTree.shouldOpenTable(2, 2));
    }

    @Test
    public void calibrationCategoriesUseModernCountAwareRendering() {
        CategoryTreeNode category = new CategoryTreeNode("Fueling");
        category.add(new javax.swing.tree.DefaultMutableTreeNode("A"));
        category.add(new javax.swing.tree.DefaultMutableTreeNode("B"));

        Component rendered = new RomCellRenderer().getTreeCellRendererComponent(
                new JTree(), category, false, false, false, 0, false);

        assertTrue(rendered instanceof JLabel);
        JLabel label = (JLabel) rendered;
        assertEquals("Fueling", label.getText());
        assertEquals("2 calibration entries", label.getToolTipText());
        assertNotNull(label.getIcon());
    }

    @Test
    public void changedCalibrationRowsShowUnsavedCellBadge() {
        Table1D table = new Table1D();
        table.setName("Fuel Target");
        DataCell cell = new DataCell(table, (com.romraider.maps.Rom) null);
        cell.setOriginalValue(1.0);
        table.setData(new DataCell[] {cell});

        Component rendered = new RomCellRenderer().getTreeCellRendererComponent(
                new JTree(), new TableTreeNode(table), false, false, true,
                0, false);

        assertTrue(rendered instanceof JPanel);
        JLabel badge = null;
        for (Component child : ((JPanel) rendered).getComponents()) {
            if (child instanceof JLabel
                    && "CHANGED TABLE BADGE".equals(child.getName())) {
                badge = (JLabel) child;
            }
        }
        assertNotNull(badge);
        assertEquals("● 1", badge.getText());
        assertEquals("1 unsaved cell", badge.getToolTipText());
    }
}
