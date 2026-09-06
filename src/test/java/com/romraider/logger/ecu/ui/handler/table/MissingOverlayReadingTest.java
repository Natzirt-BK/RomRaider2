/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.handler.table;

import com.romraider.logger.ecu.comms.query.ResponseImpl;
import com.romraider.logger.ecu.definition.*;
import com.romraider.maps.*;
import com.romraider.swing.TableToolBar;
import javax.swing.SwingUtilities;
import org.junit.*;
import static org.junit.Assert.*;

public class MissingOverlayReadingTest {
    private final TableUpdateHandler handler = TableUpdateHandler.getInstance();
    private FixtureView root, axis, other;
    private EcuParameterImpl x, y, unrelated;

    @Before public void setup() throws Exception {
        handler.cleanUp();
        SwingUtilities.invokeAndWait(() -> {
            root = new FixtureView("y");
            axis = new FixtureView("x");
            axis.setAxisParent(root);
            other = new FixtureView("other");
            handler.registerTable(root.model);
            handler.registerTable(axis.model);
            handler.registerTable(other.model);
        });
        x = parameter("x"); y = parameter("y"); unrelated = parameter("other");
    }

    @After public void cleanup() throws Exception {
        handler.cleanUp();
        SwingUtilities.invokeAndWait(() -> {
            root.setTable(null); axis.setTable(null); other.setTable(null);
        });
    }

    @Test public void invalidAxisClearsItsWholeTableUntilRecoveryWithoutBlockingOtherTables() throws Exception {
        send(x, 12); send(y, 13);
        assertEquals(1, root.highlights);
        send(x, Double.NaN);
        assertEquals(1, root.clears);
        assertEquals("NO VALID DATA", root.toolbar.last);
        send(y, 14);
        assertEquals(1, root.highlights); // A valid sibling cannot reactivate the bad axis.
        send(unrelated, 20);
        assertEquals(1, other.highlights);
        send(x, 0);
        assertEquals("0", axis.last);
        send(y, 15);
        assertEquals(2, root.highlights);
    }

    @Test public void queuedReadingsCannotUpdateClosedRegistrations() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ResponseImpl response = new ResponseImpl();
            response.setDataValue(x, 12);
            handler.handleDataUpdate(response);
            handler.deregisterTable(axis.model);
        });
        SwingUtilities.invokeAndWait(() -> assertEquals(0, axis.highlights));
    }

    @Test public void badUnrelatedChannelIsNotTreatedAsAnOverlayFailureAndResetClearsTraces() throws Exception {
        send(parameter("not-bound"), Double.NaN);
        assertEquals(0, root.clears);
        send(x, Double.NEGATIVE_INFINITY);
        handler.reset();
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(root.clears > 0);
        send(x, 0); send(y, 1);
        assertEquals(1, root.highlights);
    }

    private void send(EcuParameterImpl data, double value) throws Exception {
        ResponseImpl response = new ResponseImpl();
        response.setDataValue(data, value);
        handler.handleDataUpdate(response);
        SwingUtilities.invokeAndWait(() -> { });
    }
    private EcuParameterImpl parameter(String id) {
        return new EcuParameterImpl(id, id, "Synthetic", new EcuAddressImpl("000001", 1, -1),
                null, null, null, new EcuDataConvertor[] {new EcuParameterConvertorImpl()});
    }
    private static class FixtureToolbar extends TableToolBar {
        String last;
        @Override public void setLiveDataValue(String value) { last = value; }
    }
    private static class FixtureView extends Table1DView {
        final Table1D model;
        final FixtureToolbar toolbar = new FixtureToolbar();
        int highlights, clears;
        String last;
        FixtureView(String id) { this(new Table1D(), id); }
        private FixtureView(Table1D table, String id) {
            super(table, Table1DType.NO_AXIS);
            model = table;
            table.setLogParam(id);
            setOverlayLog(true);
        }
        @Override public void highlightLiveData(String value) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            highlights++; last = value;
        }
        @Override public void clearLiveDataTrace() {
            assertTrue(SwingUtilities.isEventDispatchThread());
            clears++;
        }
        @Override public TableToolBar getToolbar() { return toolbar; }
    }
}
