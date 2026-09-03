/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2025 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.swing;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.awt.*;
import java.util.Enumeration;
import java.util.List;

import com.romraider.maps.Rom;
import com.romraider.swing.RomTree;
import com.romraider.ui.swing.ModernSearchField;

public class RomFilterPanel extends JPanel {

	private static final long serialVersionUID = 1L;
    private final JTextField filterField = new ModernSearchField(
            "Search calibrations...", 20);

    public RomFilterPanel(final DefaultMutableTreeNode imageRoot, final RomTree imageList) {
        super(new BorderLayout());

        setBorder(BorderFactory.createEmptyBorder());
        filterField.setName("CALIBRATION SEARCH");
        filterField.setToolTipText(
                "Fuzzy search names, categories, and descriptions (Ctrl+P)");
        filterField.getAccessibleContext().setAccessibleName(
                "Search calibrations");
        add(filterField, BorderLayout.CENTER);

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filter(); }

            private void filter() {
                String text = filterField.getText().trim();

                final Enumeration<?> children = imageRoot.children();
                while (children.hasMoreElements()) {
                    Object child = children.nextElement();
                    if (child instanceof SwingRomTreeNode) {
                        SwingRomTreeNode romNode = (SwingRomTreeNode) child;
                        List<TreePath> pathsToExpand =
                                romNode.refreshDisplayedTables(text);

                        DefaultTreeModel model = (DefaultTreeModel) imageList.getModel();
                        model.reload(romNode);
                        
                        for (TreePath path : pathsToExpand) {
                            imageList.expandPath(path);
                        }
                        
                    }
                }

                imageList.repaint();
            }
        });
    }

    public void focusSearch() {
        filterField.requestFocusInWindow();
        filterField.selectAll();
    }
}
