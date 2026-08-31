/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.swing.TableTreeNode;

/** UI-independent table comparison using indexed, case-insensitive names. */
public final class RomComparisonService {
    private RomComparisonService() {
    }

    public static RomComparisonResult compare(Rom left, Rom right) {
        if (left == null || right == null) {
            return new RomComparisonResult(
                    Collections.<TableComparison>emptyList(), 0, 0, 0);
        }
        Map<String, Table> leftTables = tablesByName(left);
        Map<String, Table> rightTables = tablesByName(right);
        List<TableComparison> comparisons = new ArrayList<TableComparison>();
        int equal = 0;
        int different = 0;
        int missing = 0;

        for (Map.Entry<String, Table> entry : leftTables.entrySet()) {
            Table leftTable = entry.getValue();
            Table rightTable = rightTables.remove(entry.getKey());
            if (rightTable == null) {
                comparisons.add(new TableComparison(leftTable.getName(),
                        TableComparisonStatus.ONLY_LEFT));
                missing++;
            } else if (leftTable.equals(rightTable)) {
                comparisons.add(new TableComparison(leftTable.getName(),
                        TableComparisonStatus.EQUAL));
                equal++;
            } else {
                comparisons.add(new TableComparison(leftTable.getName(),
                        TableComparisonStatus.DIFFERENT));
                different++;
            }
        }
        for (Table rightTable : rightTables.values()) {
            comparisons.add(new TableComparison(rightTable.getName(),
                    TableComparisonStatus.ONLY_RIGHT));
            missing++;
        }
        Collections.sort(comparisons);
        return new RomComparisonResult(comparisons, equal, different, missing);
    }

    private static Map<String, Table> tablesByName(Rom rom) {
        Map<String, Table> tables = new LinkedHashMap<String, Table>();
        for (TableTreeNode node : rom.getTableNodes().values()) {
            Table table = node.getTable();
            tables.put(table.getName().toLowerCase(Locale.ENGLISH), table);
        }
        return tables;
    }
}
