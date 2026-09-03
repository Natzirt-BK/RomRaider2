/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import com.romraider.Settings;
import com.romraider.editor.search.TableSearchService;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

/** Swing-only tree projection of a ROM's neutral calibration catalog. */
public final class SwingRomTreeNode extends DefaultMutableTreeNode {
    private static final long serialVersionUID = 1L;
    private static final ResourceBundle RB = new ResourceUtil().getBundle(
            Rom.class.getName());

    private final Rom rom;
    private final LinkedHashMap<String, TableTreeNode> tableNodes =
            new LinkedHashMap<String, TableTreeNode>();

    SwingRomTreeNode(Rom rom) {
        super(rom);
        if (rom == null) throw new IllegalArgumentException("ROM is required");
        this.rom = rom;
        syncCatalog();
    }

    public Rom getRom() { return rom; }

    public TableTreeNode getTableNodeByName(String tableName) {
        if (tableName == null) return null;
        syncCatalog();
        return tableNodes.get(tableName.toLowerCase());
    }

    public Map<String, TableTreeNode> getTableNodes() {
        syncCatalog();
        return java.util.Collections.unmodifiableMap(tableNodes);
    }

    public List<TreePath> refreshDisplayedTables() {
        return refreshDisplayedTables(null);
    }

    public List<TreePath> refreshDisplayedTables(String filterText) {
        syncCatalog();
        removeAllChildren();
        Settings settings = SettingsManager.getSettings();
        boolean shouldFilter = filterText != null && !filterText.isEmpty();
        boolean anyTablesAdded = false;
        List<TreePath> pathsToExpand = new ArrayList<TreePath>();

        for (TableTreeNode tableTreeNode : tableNodes.values()) {
            Table table = tableTreeNode.getTable();
            if (shouldFilter && !TableSearchService.matches(table, filterText)) {
                continue;
            }
            anyTablesAdded = true;
            String[] categories = table.getCategory().split("//");
            if (!settings.isDisplayHighTables()
                    && settings.getUserLevel() < table.getUserLevel()) {
                continue;
            }
            DefaultMutableTreeNode currentParent = this;
            for (int i = 0; i < categories.length; i++) {
                DefaultMutableTreeNode existing = childNamed(currentParent,
                        categories[i]);
                if (existing == null) {
                    existing = new CategoryTreeNode(categories[i]);
                    sortedAdd(currentParent, existing, settings);
                }
                currentParent = existing;
                if (shouldFilter) {
                    pathsToExpand.add(new TreePath(currentParent.getPath()));
                }
                if (i == categories.length - 1) {
                    sortedAdd(currentParent, tableTreeNode, settings);
                }
            }
        }
        if (!anyTablesAdded && shouldFilter) {
            sortedAdd(this, new DefaultMutableTreeNode(
                    RB.getString("NOMATCHES")), settings);
        }
        return pathsToExpand;
    }

    private void syncCatalog() {
        LinkedHashMap<String, TableTreeNode> next =
                new LinkedHashMap<String, TableTreeNode>();
        for (Table table : rom.getTableCatalog()) {
            String key = table.getName().toLowerCase();
            TableTreeNode node = tableNodes.get(key);
            if (node == null || node.getTable() != table) {
                node = new TableTreeNode(table);
            }
            next.put(key, node);
        }
        tableNodes.clear();
        tableNodes.putAll(next);
    }

    private static DefaultMutableTreeNode childNamed(
            DefaultMutableTreeNode parent, String name) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            TreeNode child = parent.getChildAt(index);
            if (child.toString().equalsIgnoreCase(name)) {
                return (DefaultMutableTreeNode) child;
            }
        }
        return null;
    }

    private static void sortedAdd(DefaultMutableTreeNode parent,
            DefaultMutableTreeNode node, Settings settings) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            TreeNode existing = parent.getChildAt(index);
            boolean before = node instanceof CategoryTreeNode
                    && !(existing instanceof CategoryTreeNode);
            if (!(node instanceof CategoryTreeNode)
                    && existing instanceof CategoryTreeNode) {
                continue;
            }
            if (before || (settings.isTableTreeSorted()
                    && existing.toString().compareToIgnoreCase(
                            node.toString()) >= 0)) {
                parent.insert(node, index);
                return;
            }
        }
        parent.add(node);
    }
}
