/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.junit.Test;

import com.romraider.logger.ecu.definition.EcuDataConvertor;
import com.romraider.logger.ecu.definition.EcuDataType;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.ui.handler.dash.GaugeMinMax;
import com.romraider.logger.ecu.ui.handler.graph.GraphUpdateHandler;
import com.romraider.logger.ecu.ui.handler.livedata.LiveDataTableModel;
import com.romraider.logger.ecu.ui.paramlist.ParameterListTable;
import com.romraider.logger.ecu.ui.paramlist.ParameterListTableModel;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

public class LoggerWorkspaceComponentsTest {
    @Test
    public void parameterBrowserSearchesAndSummarizesEverySection()
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                DataRegistrationBroker broker = new NoOpBroker();
                ParameterListTableModel parameters = model(broker, "Parameters");
                ParameterListTableModel switches = model(broker, "Switches");
                ParameterListTableModel external = model(broker, "External");
                parameters.addParam(data("P-BOOST", "Boost Pressure", "psi"),
                        false);
                parameters.addParam(data("P-RPM", "Engine Speed", "rpm"),
                        false);
                external.addParam(data("E-AFR", "Wideband AFR", "lambda"),
                        false);

                ParameterListTable parameterTable =
                        new ParameterListTable(parameters);
                ParameterListTable switchTable = new ParameterListTable(switches);
                ParameterListTable externalTable =
                        new ParameterListTable(external);
                LoggerParameterBrowser browser = new LoggerParameterBrowser(
                        new ParameterListTableModel[] {parameters, switches,
                                external},
                        new ParameterListTable[] {parameterTable, switchTable,
                                externalTable},
                        new String[] {"Parameters", "Switches", "External"});

                assertEquals("0 / 3 selected",
                        browser.getSummaryLabel().getText());
                assertTrue(parameterTable.getRowHeight() >= 27);
                assertEquals(30, parameterTable.getTableHeader()
                        .getPreferredSize().height);
                Component parameterName = parameterTable.getCellRenderer(0, 1)
                        .getTableCellRendererComponent(parameterTable,
                                "Boost Pressure", false, false, 0, 1);
                assertEquals(UiThemeService.getInstance().color(
                        ThemeToken.LIVE_TRACE), parameterName.getForeground());
                browser.getSearchField().setText("boost");
                assertEquals(1, parameterTable.getRowCount());
                assertEquals(0, externalTable.getRowCount());

                parameters.setValueAt(Boolean.TRUE, 0, 0);
                assertTrue(browser.getSummaryLabel().getText().startsWith(
                        "1 / 3 selected"));
                assertEquals(Boolean.class, switches.getColumnClass(0));
                assertEquals(JTabbedPane.TOP,
                        browser.getSectionTabs().getTabPlacement());
            }
        });
    }

    @Test
    public void liveDataUsesInstructionalEmptyStateAndEnablesReset()
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                LiveDataTableModel model = new LiveDataTableModel();
                AtomicInteger resets = new AtomicInteger();
                LoggerLiveDataPanel panel = new LoggerLiveDataPanel(model,
                        new Runnable() {
                            public void run() {
                                resets.incrementAndGet();
                                model.reset();
                            }
                        });
                JComponent empty = findNamed(panel,
                        "LOGGER LIVE DATA EMPTY STATE");
                assertTrue(empty.isVisible());
                assertFalse(panel.getResetButton().isEnabled());
                assertTrue(panel.getTable().getAutoCreateRowSorter());
                assertTrue(panel.getTable().getRowHeight() >= 27);
                assertEquals(30, panel.getTable().getTableHeader()
                        .getPreferredSize().height);

                model.addParam(data("P-RPM", "Engine Speed", "rpm"));
                assertFalse(empty.isVisible());
                assertTrue(panel.getResetButton().isEnabled());
                panel.getResetButton().doClick();
                assertEquals(1, resets.get());
            }
        });
    }

    @Test
    public void connectionStatusUsesReadableNamedStates() throws Exception {
        StatusIndicator indicator = new StatusIndicator();
        flushEvents();
        assertEquals("STOPPED", indicator.getStatusText());
        indicator.connecting();
        flushEvents();
        assertEquals("CONNECTING", indicator.getStatusText());
        indicator.reconnecting();
        flushEvents();
        assertEquals("RECONNECTING", indicator.getStatusText());
        indicator.loggingData();
        flushEvents();
        assertEquals("LOGGING TO FILE", indicator.getStatusText());
    }

    @Test
    public void graphAndDashboardExplainHowToPopulateTheirViews()
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                JPanel graph = new JPanel(new BorderLayout());
                GraphUpdateHandler graphHandler = new GraphUpdateHandler(graph);
                JComponent graphEmpty = findNamed(graph,
                        "LOGGER GRAPH EMPTY STATE");
                assertTrue(graphEmpty.isVisible());
                LoggerData rpm = data("P-RPM", "Engine Speed", "rpm");
                graphHandler.registerData(rpm);
                assertFalse(graphEmpty.isVisible());
                graphHandler.deregisterData(rpm);
                assertTrue(graphEmpty.isVisible());

                JPanel gauges = new JPanel();
                LoggerDashboardPanel dashboard = new LoggerDashboardPanel(
                        gauges, new Runnable() { public void run() { } },
                        new Runnable() { public void run() { } });
                JComponent dashboardEmpty = findNamed(dashboard,
                        "LOGGER DASHBOARD EMPTY STATE");
                assertTrue(dashboardEmpty.isVisible());
                assertFalse(dashboard.getStyleButton().isEnabled());
                gauges.add(new JLabel("Gauge"));
                assertFalse(dashboardEmpty.isVisible());
                assertTrue(dashboard.getStyleButton().isEnabled());
            }
        });
    }

    @Test
    public void numberedShortcutsSwitchLoggerWorkspaces() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                JTabbedPane tabs = new JTabbedPane();
                tabs.addTab("Data", new JPanel());
                tabs.addTab("Graph", new JPanel());
                tabs.addTab("Dashboard", new JPanel());
                LoggerWorkspaceShortcuts.install(tabs);

                KeyStroke ctrlThree = KeyStroke.getKeyStroke(KeyEvent.VK_3,
                        InputEvent.CTRL_DOWN_MASK);
                Object actionKey = tabs.getInputMap(
                        JComponent.WHEN_IN_FOCUSED_WINDOW).get(ctrlThree);
                Action action = tabs.getActionMap().get(actionKey);
                action.actionPerformed(new ActionEvent(tabs,
                        ActionEvent.ACTION_PERFORMED, "Ctrl+3"));

                assertEquals(2, tabs.getSelectedIndex());
            }
        });
    }

    private static void flushEvents() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() { }
        });
    }

    private static ParameterListTableModel model(DataRegistrationBroker broker,
            String name) {
        return new ParameterListTableModel(broker, name);
    }

    private static LoggerData data(final String id, final String name,
            final String units) {
        return new LoggerData() {
            private boolean selected;
            private final EcuDataConvertor convertor = new EcuDataConvertor() {
                public double convert(byte[] bytes) { return 0.0; }
                public String format(double value) { return String.valueOf(value); }
                public String getUnits() { return units; }
                public GaugeMinMax getGaugeMinMax() { return null; }
                public String getFormat() { return "0.0"; }
                public String getExpression() { return "x"; }
                public String getDataType() { return "float"; }
            };
            public String getId() { return id; }
            public String getName() { return name; }
            public String getDescription() { return name + " description"; }
            public EcuDataConvertor getSelectedConvertor() { return convertor; }
            public EcuDataConvertor[] getConvertors() {
                return new EcuDataConvertor[] {convertor};
            }
            public void selectConvertor(EcuDataConvertor value) { }
            public EcuDataType getDataType() { return null; }
            public boolean isSelected() { return selected; }
            public void setSelected(boolean value) { selected = value; }
        };
    }

    private static JComponent findNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent
                    && name.equals(((JComponent) child).getName())) {
                return (JComponent) child;
            }
            if (child instanceof Container) {
                JComponent nested = findNamed((Container) child, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static final class NoOpBroker implements DataRegistrationBroker {
        public void registerLoggerDataForLogging(LoggerData loggerData) { }
        public void deregisterLoggerDataFromLogging(LoggerData loggerData) { }
        public void clear() { }
        public void connecting() { }
        public void readingData() { }
        public void readingDataExternal() { }
        public void loggingData() { }
        public void stopped() { }
    }
}
