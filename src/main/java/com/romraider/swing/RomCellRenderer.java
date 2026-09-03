/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2022 RomRaider.com
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

import static javax.swing.BorderFactory.createEmptyBorder;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;

public class RomCellRenderer implements TreeCellRenderer {

    JLabel fileName;
    JLabel carInfo;
    DefaultTreeCellRenderer defaultRenderer = new DefaultTreeCellRenderer();
    
    static ImageIcon icon1D = imageIcon(Action.TABLE_1D);
    static ImageIcon icon2D = imageIcon(Action.TABLE_2D);
    static ImageIcon icon3D = imageIcon(Action.TABLE_3D);
    static ImageIcon iconSwitch = imageIcon(Action.SWITCH);
    static ImageIcon iconCategory = imageIcon(Action.CATEGORY);
    static ImageIcon iconRom = imageIcon(Action.DEFINITION);
    
    public RomCellRenderer() {
        fileName = new JLabel(" ");
        fileName.setFont(fileName.getFont().deriveFont(Font.BOLD));
        fileName.setHorizontalAlignment(JLabel.LEFT);

        carInfo = new JLabel(" ");
        carInfo.setFont(carInfo.getFont().deriveFont(Font.PLAIN,
                Math.max(10.0f, carInfo.getFont().getSize2D() - 1.0f)));
        carInfo.setHorizontalAlignment(JLabel.LEFT);
    }
    
    public static ImageIcon getIconForTable(Table t) {
        // display icon
        if (t.getType() == Table.TableType.TABLE_1D) {
        	return icon1D;
        } else if (t.getType() == Table.TableType.TABLE_2D) {
        	return icon2D;     
        } else if (t.getType() == Table.TableType.TABLE_3D) {
        	return icon3D;
        } else if (t.getType() == Table.TableType.SWITCH) {
        	return iconSwitch;
        }
        
        return null;
    }

    private static ImageIcon imageIcon(Action action) {
        javax.swing.Icon icon = ModernIconFactory.icon(action);
        BufferedImage image = new BufferedImage(icon.getIconWidth(),
                icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            JLabel colors = new JLabel();
            Color foreground = UIManager.getColor("Tree.foreground");
            colors.setForeground(foreground == null ? Color.LIGHT_GRAY : foreground);
            icon.paintIcon(colors, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return new ImageIcon(image);
    }
    
    private String buildCarInfoText(Rom rom) {
    	String carInfoText = "<html>";
        RomID id = rom.getRomID();
        
        if(id.getVersion() != null)
            carInfoText+= "<B>" + id.getVersion() + " </B>";
        	
        if(rom.getRomIDString() != null)
        	carInfoText+=rom.getRomIDString() + ", ";
        
        if(id.getCaseId() != null)
        	carInfoText+=id.getCaseId() + "; ";
        
        if(id.getYear() != null)
        	carInfoText+=id.getYear() + " ";
        
        if(id.getMake() != null)
        	carInfoText+=id.getMake() + " ";
        
        if(id.getModel() != null)
        	carInfoText+=id.getModel() + " "; 
        
        if(id.getSubModel() != null)
        	carInfoText+=id.getSubModel(); 
        
        if(id.getTransmission() != null)
        	carInfoText+=", " + id.getTransmission();
        
        if(carInfoText.endsWith(", ") || carInfoText.endsWith("; ")) 
        	carInfoText = carInfoText.substring(0, carInfoText.length() - 2);          
        
        if(id.getAuthor() != null)
        	carInfoText+=" by " + id.getAuthor();
        
        carInfoText+= "</html>";
        
        return carInfoText;
    }
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row,
            boolean hasFocus) {

        Component returnValue = null;

        if (value instanceof SwingRomTreeNode) {
            Rom rom = ((SwingRomTreeNode) value).getRom();
            Color background = color(selected ? "Tree.selectionBackground"
                    : "Tree.background", tree.getBackground());
            Color foreground = color(selected ? "Tree.selectionForeground"
                    : "Tree.foreground", tree.getForeground());

            fileName.setText(rom.getFileName());
            fileName.setIcon(iconRom);
            fileName.setIconTextGap(7);
            fileName.setToolTipText(rom.getFileName());
                   
            carInfo.setText(buildCarInfoText(rom));
            
            JPanel renderer = new JPanel(new GridLayout(2, 1, 0, 1));
            renderer.add(fileName);
            renderer.add(carInfo);

            fileName.setForeground(foreground);
            carInfo.setForeground(foreground);
            fileName.setOpaque(false);
            carInfo.setOpaque(false);
            renderer.setOpaque(true);
            renderer.setBackground(background);
            renderer.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(0,
                            selected ? 3 : 0, 0, 0,
                            color("RomRaider2.accent", foreground)),
                    createEmptyBorder(4, 6, 4, 6)));

            int width = tree.getParent() == null ? tree.getWidth()
                    : tree.getParent().getWidth();
            renderer.setPreferredSize(new Dimension(Math.max(180, width), 44));
            renderer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            renderer.setEnabled(tree.isEnabled());
            returnValue = renderer;
        } else if (value != null && value instanceof TableTreeNode) {

            Table table = (Table) (((TableTreeNode)(value)).getUserObject());
            JPanel renderer = new JPanel(new BorderLayout(5, 0));
            Color background = color(selected ? "Tree.selectionBackground"
                    : "Tree.background", tree.getBackground());
            Color foreground = color(selected ? "Tree.selectionForeground"
                    : "Tree.foreground", tree.getForeground());
            renderer.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(0,
                            selected ? 3 : 0, 0, 0,
                            color("RomRaider2.accent", foreground)),
                    createEmptyBorder(0, 1, 0, 1)));
            renderer.setBackground(background);
            renderer.setOpaque(true);

            JLabel tableName = new JLabel(table.getName() + " ",
                    getIconForTable(table), JLabel.LEFT);
            tableName.setIconTextGap(7);
            tableName.setBorder(createEmptyBorder(4, 5, 4, 5));
            tableName.setToolTipText(table.getDescription());
            tableName.setForeground(foreground);
            tableName.setOpaque(false);

            // set color
            renderer.add(tableName, BorderLayout.CENTER);
            tableName.setFont(tree.getFont().deriveFont(Font.PLAIN));

            int changedCells = RomChangeSummary.countChangedCells(table);
            if (changedCells > 0) {
                JLabel changed = new JLabel("● " + changedCells);
                changed.setName("CHANGED TABLE BADGE");
                changed.setForeground(color("RomRaider2.danger",
                        new Color(220, 80, 80)));
                changed.setBorder(createEmptyBorder(4, 4, 4, 7));
                changed.setToolTipText(changedCells + (changedCells == 1
                        ? " unsaved cell" : " unsaved cells"));
                renderer.add(changed, BorderLayout.EAST);
                String description = table.getDescription();
                tableName.setToolTipText((description == null ? "" : description)
                        + (description == null || description.trim().isEmpty()
                                ? "" : " • ")
                        + changed.getToolTipText());
            }

            if (table.getUserLevel() == 5) {
                tableName.setForeground(color("RomRaider2.danger",
                        new Color(220, 80, 80)));
                tableName.setFont(tree.getFont().deriveFont(Font.ITALIC));

            } else if (table.getUserLevel() > table.getSettings().getUserLevel()) {
                tableName.setForeground(color("Label.disabledForeground", foreground));
                tableName.setFont(tree.getFont().deriveFont(Font.ITALIC));

            }

            returnValue = renderer;
        } else if (value instanceof CategoryTreeNode) {
            Color background = color(selected ? "Tree.selectionBackground"
                    : "Tree.background", tree.getBackground());
            Color foreground = color(selected ? "Tree.selectionForeground"
                    : "Tree.foreground", tree.getForeground());
            JLabel category = new JLabel(String.valueOf(value), iconCategory,
                    JLabel.LEFT);
            category.setFont(tree.getFont().deriveFont(Font.BOLD));
            category.setForeground(foreground);
            category.setBackground(background);
            category.setOpaque(true);
            category.setIconTextGap(7);
            int tables = ((CategoryTreeNode) value).getChildCount();
            category.setToolTipText(tables + (tables == 1
                    ? " calibration entry" : " calibration entries"));
            category.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(0,
                            selected ? 3 : 0, 0, 0,
                            color("RomRaider2.accent", foreground)),
                    createEmptyBorder(4, 5, 4, 5)));
            returnValue = category;
        }

        if (returnValue == null) {
            defaultRenderer.setBackgroundNonSelectionColor(
                    color("Tree.background", tree.getBackground()));
            defaultRenderer.setTextNonSelectionColor(
                    color("Tree.foreground", tree.getForeground()));
            defaultRenderer.setBackgroundSelectionColor(
                    color("Tree.selectionBackground", tree.getBackground()));
            defaultRenderer.setTextSelectionColor(
                    color("Tree.selectionForeground", tree.getForeground()));
            defaultRenderer.setBorderSelectionColor(
                    color("Tree.selectionBorderColor", tree.getForeground()));
            returnValue = defaultRenderer.getTreeCellRendererComponent(tree,
                    value, selected, expanded, leaf, row, hasFocus);
        }

        return returnValue;

    }

    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }
}
