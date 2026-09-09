/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.ui.swing.tools.tablemodels.ReadCodesTableModel;
import com.romraider.logger.ecu.ui.swing.tools.tablemodels.DmReadCodesTableModel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTable;
import javax.swing.table.TableModel;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReadCodesTableModelTest {
    @Test public void emptyModelsHaveStableColumnTypesAndCanBeSorted() {
        for (TableModel model : List.of(new ReadCodesTableModel(), new DmReadCodesTableModel(Set.of(), Set.of()))) {
            assertEquals(String.class, model.getColumnClass(0));
            assertEquals(Boolean.class, model.getColumnClass(1));
            assertEquals(Boolean.class, model.getColumnClass(2));
            JTable table = new JTable(model);
            table.setAutoCreateRowSorter(true);
            table.getRowSorter().toggleSortOrder(0);
            assertEquals(0, table.getRowCount());
        }
    }

    @Test public void standardModelNotifiesWhenCleared() {
        ReadCodesTableModel model = new ReadCodesTableModel();
        AtomicInteger changes = new AtomicInteger();
        model.addTableModelListener(event -> changes.incrementAndGet());
        model.setDtcList(new ArrayList<>());
        model.setDtcList(null);
        assertEquals(2, changes.get());
        assertEquals(0, model.getRowCount());
    }

    @Test public void dimeModelKeepsCapturedStatusWhenSourceSetsChange() {
        Set<String> current = new HashSet<>(Set.of("B"));
        Set<String> memorized = new HashSet<>(Set.of("A"));
        DmReadCodesTableModel model = new DmReadCodesTableModel(current, memorized);
        current.clear();
        memorized.clear();
        assertEquals(" A", model.getValueAt(0, 0));
        assertEquals(false, model.getValueAt(0, 1));
        assertEquals(true, model.getValueAt(0, 2));
        assertEquals(" B", model.getValueAt(1, 0));
        assertEquals(true, model.getValueAt(1, 1));
        assertEquals(false, model.getValueAt(1, 2));
    }
}
