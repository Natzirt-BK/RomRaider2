/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.table.DefaultTableModel;

import org.junit.Test;

import com.romraider.activity.ApplicationActivityService;
import com.romraider.maps.Table1D;
import com.romraider.maps.Table1DView;
import com.romraider.maps.Table1DView.Table1DType;
import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.Table3D;
import com.romraider.flash.FlashBackendRegistry;
import com.romraider.editor.search.UnifiedSearchPanel;
import com.romraider.editor.recovery.RecoveryState;
import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.search.SearchEntry;
import com.romraider.search.SearchKind;
import com.romraider.search.UnifiedSearchIndex;
import com.romraider.swing.TableFrame;
import com.romraider.swing.TableToolBar;
import com.romraider.swing.JProgressPane;
import com.romraider.ui.DisplayMode;
import com.romraider.ui.TouchTargetService;
import com.romraider.ui.swing.ApplicationControlsPanel;
import com.romraider.swing.ECUEditorToolBar;

public class EditorWorkbenchComponentsTest {
    @Test
    public void editorWindowStaysInsideTheUsableDesktop() {
        assertEquals(new Point(180, 60), ECUEditor.clampWindowLocation(
                new Point(180, 70), new Dimension(1100, 700),
                new Rectangle(0, 0, 1280, 800)));
        assertEquals(new Point(0, 0), ECUEditor.clampWindowLocation(
                new Point(-20, -10), new Dimension(1400, 900),
                new Rectangle(0, 0, 1280, 764)));
    }

    @Test
    public void unifiedSearchPaletteIsKeyboardReadyAndShowsTypedKinds() {
        String source = "test:search-palette";
        UnifiedSearchIndex index = UnifiedSearchIndex.getInstance();
        index.replaceSource(source, Arrays.asList(
                new SearchEntry(SearchKind.TABLE, "ROM", "Boost Target",
                        "Boost Target", "Boost", "Main target", null),
                new SearchEntry(SearchKind.COMMAND, source, "open-logger",
                        "Open Logger", "Logging", "Launch logger", null)));
        try {
            UnifiedSearchPanel panel = new UnifiedSearchPanel(index, null);
            assertNotNull(findNamed(panel, JTextField.class,
                    "UNIFIED SEARCH QUERY"));
            assertNotNull(findNamed(panel, javax.swing.JList.class,
                    "UNIFIED SEARCH RESULTS"));
            assertTrue(panel.getPreferredSize().width >= 600);
        } finally {
            index.removeSource(source);
        }
    }

    @Test
    public void inspectorExposesTableRomAndLiveSections() {
        EditorInspectorPanel inspector = new EditorInspectorPanel();
        assertEquals(1, count(inspector, JTabbedPane.class));
        JTabbedPane tabs = findNamed(inspector, JTabbedPane.class,
                "INSPECTOR TABS");
        assertEquals(4, tabs.getTabCount());
        assertEquals("INFO", tabs.getTitleAt(0));
        assertEquals("LIVE", tabs.getTitleAt(1));
        assertEquals("NOTES", tabs.getTitleAt(2));
        assertEquals("CHANGES", tabs.getTitleAt(3));
        assertEquals("Map information", tabs.getToolTipTextAt(0));
        assertEquals("ROM changes", tabs.getToolTipTextAt(3));
        assertNotNull(findNamed(inspector, JTextArea.class,
                "MAP NOTES EDITOR"));
        assertNotNull(findNamed(inspector, JTable.class,
                "ROM CHANGE TABLE"));
        assertNotNull(findNamed(inspector, JList.class,
                "EDIT HISTORY LIST"));
        assertNotNull(findNamed(inspector, JButton.class,
                "UNDO ROM HISTORY"));
        assertNotNull(findNamed(inspector, JButton.class,
                "REDO ROM HISTORY"));
        assertNotNull(findNamed(inspector, JButton.class,
                "OPEN CHANGED TABLE"));
        assertEquals("No unsaved cell changes",
                findNamed(inspector, JLabel.class, "ROM CHANGE TOTAL").getText());
        assertNotNull(findNamed(inspector, JPanel.class, "TABLE DETAILS"));
        assertNotNull(findNamed(inspector, JPanel.class, "ROM INFORMATION"));
        assertNotNull(findNamed(inspector, JPanel.class, "WORKSPACE CONTEXT"));
        JTable liveParameters = findNamed(inspector, JTable.class,
                "LIVE PARAMETERS");
        assertNotNull(liveParameters);
        assertEquals(3, liveParameters.getColumnCount());
        assertEquals(JTable.AUTO_RESIZE_OFF,
                liveParameters.getAutoResizeMode());
        int compactLiveWidth = 0;
        for (int column = 0; column < liveParameters.getColumnCount(); column++) {
            compactLiveWidth += liveParameters.getColumnModel().getColumn(column)
                    .getPreferredWidth();
        }
        assertTrue(compactLiveWidth <= 210);
        assertTrue(liveParameters.getColumnModel().getColumn(0).getResizable());
        assertTrue(liveParameters.getColumnModel().getColumn(1).getMaxWidth()
                > 1000);
        assertFalse(liveParameters.getTableHeader().getReorderingAllowed());
        assertNotNull(liveParameters.getRowSorter());
        JTextField liveFilter = findNamed(inspector, JTextField.class,
                "LIVE PARAMETER FILTER");
        assertNotNull(liveFilter);
        assertNotNull(findNamed(inspector, JPanel.class, "LIVE VALUE CARDS"));
        JSplitPane liveSplit = findNamed(inspector, JSplitPane.class,
                "LIVE DATA RAIL SPLIT");
        assertNotNull(liveSplit);
        assertTrue(liveSplit.getTopComponent().getMinimumSize().height >= 100);
        assertTrue(liveSplit.getBottomComponent().getMinimumSize().height >= 120);
        assertNotNull(findNamed(inspector, JButton.class,
                "OPEN LIVE PARAMETER IN LOGGER"));
        DefaultTableModel liveModel = (DefaultTableModel)
                liveParameters.getModel();
        liveModel.addRow(new Object[] {"Engine Speed", "4523", "RPM"});
        liveModel.addRow(new Object[] {"Boost Pressure", "14.3", "psi"});
        liveFilter.setText("boost");
        assertEquals(1, liveParameters.getRowCount());

        Table1D table = new Table1D();
        table.setName("Fuel Target");
        inspector.showSelection(null, table);
        assertEquals("1 values",
                findNamed(inspector, JLabel.class, "Dimensions").getText());
        assertEquals("Editable",
                findNamed(inspector, JLabel.class, "Access").getText());
    }

    @Test
    public void statusBarKeepsSafePrimaryActionsExplicit() {
        ApplicationActivityService.getInstance().ready("Ready");
        final boolean[] resetRequested = {false};
        EditorStatusBar bar = new EditorStatusBar(new EditorStatusBar.Actions() {
            public void resetChanges() { resetRequested[0] = true; }
        });
        assertNull(findButton(bar, "Save ROM"));
        assertNull(findButton(bar, "Logger"));
        assertNull(findButton(bar, "Live Data"));
        JButton reset = findNamed(bar, JButton.class, "RESET ROM CHANGES");
        assertNotNull(reset);
        assertTrue(reset.getPreferredSize().height <= 22);
        assertFalse(reset.isEnabled());
        assertFalse(resetRequested[0]);
        JButton dimeMod = findNamed(bar, JButton.class,
                "DIMEMOD STATUS CHIP");
        assertNotNull(dimeMod);
        assertTrue(dimeMod.isVisible());
        JLabel recovery = findNamed(bar, JLabel.class, "ROM RECOVERY STATUS");
        assertNotNull(recovery);
        assertFalse(recovery.isVisible());
        JProgressPane activity = findNamed(bar, JProgressPane.class,
                "EDITOR PROGRESS STATUS");
        assertNotNull(activity);
        assertSame(activity, bar.getComponent(0));
        JLabel activityText = findNamed(activity, JLabel.class,
                "APPLICATION ACTIVITY STATUS");
        assertNotNull(activityText);
        assertFalse(findNamed(activity, JProgressBar.class,
                "EDITOR TASK PROGRESS").isVisible());
        JLabel romState = findNamed(bar, JLabel.class, "ROM LOAD STATE");
        JLabel romIdentity = findNamed(bar, JLabel.class, "ROM IDENTITY");
        assertNotNull(romState);
        assertNotNull(romIdentity);
        assertSame(activity, romState.getParent().getParent());
        assertSame(romState.getParent(), romIdentity.getParent());
        assertTrue(romState.getParent().getLayout() instanceof BoxLayout);
        activity.setSize(500, 26);
        activity.doLayout();
        romState.getParent().doLayout();
        assertEquals(componentBaseline(activityText),
                componentBaseline(romState));
        assertEquals(componentBaseline(romState),
                componentBaseline(romIdentity));
        JLabel vehicleContext = findNamed(bar, JLabel.class,
                "VEHICLE AND ROM CONTEXT");
        assertNotNull(vehicleContext);
        assertSame(vehicleContext.getParent(), bar.getComponent(1));
        assertEquals(JLabel.CENTER, vehicleContext.getHorizontalAlignment());
        assertTrue(bar.getPreferredSize().height >= 28);
        assertTrue(bar.getPreferredSize().height <= 34);

        Rom rom = new Rom(new com.romraider.maps.RomID());
        rom.setFileName("test.bin");
        rom.populateTables(new byte[] {1, 2},
                new com.romraider.swing.JProgressPane());
        bar.showRom(rom);
        bar.showRecoveryState(rom, RecoveryState.SCHEDULED);
        assertTrue(recovery.isVisible());
        assertEquals("○ RECOVERY QUEUED", recovery.getText());

        rom.setFileName("Tristan_05FXT_BCP500R_TMIC_40EWG_"
                + "ID1300s_built_CobbSF_omni4R.bin");
        bar.showRom(rom);
        bar.setSize(1166, 30);
        bar.doLayout();
        bar.updateResponsiveText();
        assertTrue(romIdentity.getText().endsWith("…"));
        assertEquals("ROM: " + rom.getFileName(),
                romIdentity.getToolTipText());
        assertTrue(bar.getPreferredSize().height <= 34);
    }

    @Test
    public void inspectorChangedMapsOpenDirectlyFromTheChangesTab() {
        EditorInspectorPanel inspector = new EditorInspectorPanel();
        Rom rom = new Rom(new com.romraider.maps.RomID());
        rom.setFileName("tuned.bin");
        rom.populateTables(new byte[] {1, 2},
                new com.romraider.swing.JProgressPane());
        Table1D table = new Table1D();
        table.setName("Boost Target");
        ChangedDataCell cell = new ChangedDataCell(table, 10.0);
        table.setData(new DataCell[] {cell});
        rom.addTableByName(table);
        cell.setCurrent(12.0);
        final Table[] opened = {null};
        inspector.setOpenTableAction(selected -> opened[0] = selected);
        inspector.showRom(rom);
        findNamed(inspector, JTabbedPane.class, "INSPECTOR TABS")
                .setSelectedIndex(3);

        JTable changes = findNamed(inspector, JTable.class, "ROM CHANGE TABLE");
        JButton open = findNamed(inspector, JButton.class,
                "OPEN CHANGED TABLE");
        assertEquals(1, changes.getRowCount());
        assertEquals("Cells", changes.getColumnName(0));
        assertEquals(1, changes.getValueAt(0, 0));
        assertTrue(changes.getColumnModel().getColumn(0).getMinWidth() >= 48);
        assertTrue(changes.getColumnModel().getColumn(0).getMaxWidth() <= 64);
        JSplitPane changeSplit = findNamed(inspector, JSplitPane.class,
                "CHANGES AND HISTORY SPLIT");
        assertEquals(0, changeSplit.getTopComponent().getMinimumSize().height);
        assertEquals(0,
                changeSplit.getBottomComponent().getMinimumSize().height);
        assertFalse(open.isEnabled());
        changes.setRowSelectionInterval(0, 0);
        assertTrue(open.isEnabled());
        open.doClick();
        assertSame(table, opened[0]);
    }

    @Test
    public void connectionCenterIsExplicitAboutUnavailableBackends() {
        EcuConnectionPanel panel = new EcuConnectionPanel(
                FlashBackendRegistry.getInstance());

        assertEquals("ECU CONNECTION CENTER", panel.getName());
        assertNotNull(findLabelContaining(panel, "No ECU commands will be sent"));
        assertNotNull(findLabelContaining(panel, "Connect interface"));
        assertTrue(panel.getPreferredSize().width >= 500);
    }

    @Test
    public void inspectorConsumesLiveDataWithoutReadingLoggerSwingTables() {
        EditorInspectorPanel inspector = new EditorInspectorPanel();
        JTable parameters = findNamed(inspector, JTable.class,
                "LIVE PARAMETERS");
        JLabel status = findNamed(inspector, JLabel.class,
                "LIVE DATA STATUS");

        inspector.showLoggerState(LoggerSessionState.LIVE_ECU);
        assertNotNull(findLabelContaining(inspector, "Logger connected"));
        inspector.showLiveSample(new LiveDataSample("P8", "Engine Speed",
                4523.0, "4523", "RPM", 100L));
        inspector.showLiveSample(new LiveDataSample("P8", "Engine Speed",
                4600.0, "4600", "RPM", 200L));

        assertEquals("● ECU LIVE", status.getText());
        assertEquals(1, parameters.getModel().getRowCount());
        assertEquals("4600", parameters.getModel().getValueAt(0, 1));
        JPanel cards = findNamed(inspector, JPanel.class, "LIVE VALUE CARDS");
        assertEquals(1, cards.getComponentCount());
        assertNotNull(findNamed(cards, JLabel.class,
                "COMPACT LIVE VALUE READING"));
        JButton nextPage = findNamed(inspector, JButton.class,
                "NEXT LIVE VALUE PAGE");
        assertFalse(nextPage.isVisible());
        inspector.showLiveSample(new LiveDataSample("P9", "Boost Pressure",
                14.3, "14.3", "psi", 200L));
        inspector.showLiveSample(new LiveDataSample("P10", "AFR",
                11.8, "11.8", ":1", 200L));
        inspector.showLiveSample(new LiveDataSample("P11", "Throttle Position",
                78.6, "78.6", "%", 200L));
        inspector.showLiveSample(new LiveDataSample("P12", "Coolant Temp",
                192.0, "192", "°F", 200L));
        assertTrue(nextPage.isVisible());
        assertTrue(nextPage.isEnabled());
        nextPage.doClick();
        assertEquals(1, cards.getComponentCount());
        assertEquals("Coolant Temp", findNamed(cards, JLabel.class,
                "COMPACT LIVE VALUE NAME").getText());
        assertEquals("2 / 2", findNamed(inspector, JLabel.class,
                "LIVE VALUE PAGE").getText());
        final String[] focused = {null};
        inspector.setFocusLiveParameterAction(id -> focused[0] = id);
        parameters.setRowSelectionInterval(0, 0);
        JButton open = findNamed(inspector, JButton.class,
                "OPEN LIVE PARAMETER IN LOGGER");
        assertTrue(open.isEnabled());
        open.doClick();
        assertEquals("P8", focused[0]);
        inspector.removeLiveParameter("P8");
        inspector.removeLiveParameter("P9");
        inspector.removeLiveParameter("P10");
        inspector.removeLiveParameter("P11");
        inspector.removeLiveParameter("P12");
        assertEquals(0, parameters.getModel().getRowCount());
        assertEquals(0, cards.getComponentCount());
    }

    @Test
    public void editorAppBarOmitsManualPlatformSelection() {
        ECUEditorManager.clearECUEditor();
        ECUEditorToolBar editorToolbar = new ECUEditorToolBar("Editor");
        ApplicationControlsPanel full = new ApplicationControlsPanel();

        assertNull(ECUEditorManager.getECUEditorWithoutCreation());
        assertEquals(0, count(editorToolbar, JComboBox.class));
        assertEquals(5, count(full, JComboBox.class));
    }

    @Test
    public void tableToolbarUsesReadableModernActions() {
        TableToolBar toolbar = new TableToolBar();

        assertFalse(toolbar.isFloatable());
        assertFalse(toolbar.isVisible());
        assertEquals(0, toolbar.getMinimumSize().width);
        assertTrue(toolbar.getMinimumSize().height > 0);
        assertNotNull(findButton(toolbar, "Set"));
        assertNotNull(findButton(toolbar, "Multiply"));
        JButton more = findNamed(toolbar, JButton.class,
                "TABLE TOOLBAR MORE ACTIONS");
        assertNotNull(more);
        JPopupMenu overflow = (JPopupMenu) more.getClientProperty(
                "TABLE_TOOLBAR_MORE_POPUP");
        assertNotNull(overflow);
        assertNotNull(findButton(overflow, "Color").getIcon());
        assertNotNull(findButton(overflow, "Refresh Diff").getIcon());
        assertNull(findButton(toolbar, "3D View"));
        assertNotNull(findButton(overflow, "Clear trace"));
        assertFalse(toolbar.getBorder() instanceof javax.swing.border.TitledBorder);
        assertEquals("Increase by fine step",
                findButton(toolbar, "+").getAccessibleContext()
                        .getAccessibleName());
    }

    @Test
    public void mapContextMenusExposeCompareAtTheTopLevel() {
        JMenuBar menu = tableFrame("Fuel Map").getTableMenuBar();

        assertEquals(4, menu.getMenuCount());
        assertEquals("Map", menu.getMenu(0).getText());
        assertEquals("Compare", menu.getMenu(3).getText());
    }

    @Test
    public void calibrationWorkspaceOpensSelectsAndClosesDocumentsAsTabs() {
        final TableFrame[] closeRequest = {null};
        EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(
                new EditorTabbedWorkspace.Listener() {
                    public void tableActivated(TableFrame frame) { }
                    public void closeRequested(TableFrame frame) { closeRequest[0] = frame; }
                });
        TableFrame fuel = tableFrame("Fuel Map");
        TableFrame ignition = tableFrame("Ignition Map");

        workspace.open(fuel);
        workspace.open(ignition);

        assertEquals(2, workspace.getOpenCount());
        assertSame(ignition, workspace.getActiveFrame());
        JPanel fuelTab = (JPanel) workspace.getTabsForTesting()
                .getTabComponentAt(0);
        JPanel ignitionTab = (JPanel) workspace.getTabsForTesting()
                .getTabComponentAt(1);
        assertFalse(fuelTab.getBackground().equals(
                ignitionTab.getBackground()));
        JLabel fuelTabLabel = find(fuelTab, JLabel.class);
        fuelTabLabel.dispatchEvent(new java.awt.event.MouseEvent(fuelTabLabel,
                java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 8, 8, 1, false,
                java.awt.event.MouseEvent.BUTTON1));
        assertSame(fuel, workspace.getActiveFrame());
        workspace.select(ignition);
        TuningDocumentPanel document = (TuningDocumentPanel) workspace
                .getTabsForTesting().getSelectedComponent();
        assertTrue(SwingUtilities.isDescendingFrom(
                ignition.getTableView(), document));
        assertEquals(JSplitPane.HORIZONTAL_SPLIT,
                document.getMapSplitForTesting().getOrientation());
        assertEquals(JSplitPane.VERTICAL_SPLIT,
                document.getDocumentSplitForTesting().getOrientation());
        assertTrue(document.getDocumentSplitForTesting().getMinimumSize().width
                <= 420);
        assertTrue(document.getMapSplitForTesting().getLeftComponent()
                .getMinimumSize().width <= 220);
        assertNotNull(findNamed(document, JPanel.class, "MAP VISUALIZATION"));
        assertNotNull(findNamed(document, JPanel.class,
                "MAP DOCUMENT HEADER"));
        assertNotNull(findNamed(document, JLabel.class, "MAP TITLE"));
        assertNotNull(findNamed(document, JButton.class, "MAP PROPERTIES"));
        JToggleButton view3d = findNamed(document, JToggleButton.class,
                "OPEN 3D VIEW");
        assertNotNull(view3d);
        assertFalse(view3d.isEnabled());
        JButton options = findNamed(document, JButton.class, "MAP OPTIONS");
        assertNotNull(options);
        assertNotNull(findNamed(document, JButton.class, "UNDO EDIT"));
        assertNotNull(findNamed(document, JButton.class, "REDO EDIT"));
        assertFalse(findNamed(document, JButton.class, "UNDO EDIT").isEnabled());
        javax.swing.JPopupMenu optionsPopup = (javax.swing.JPopupMenu)
                options.getClientProperty("MAP_OPTIONS_POPUP");
        assertNotNull(optionsPopup);
        assertEquals(4, optionsPopup.getComponentCount());
        assertNotNull(findNamed(document, JPanel.class, "DATALOG VIEWER"));
        assertNotNull(findNamed(document, JScrollPane.class,
                "CALIBRATION TABLE SCROLL"));
        JPanel legacyHeader = findNamed(ignition.getTableView(), JPanel.class,
                "TABLE VIEW HEADER");
        if (legacyHeader != null) assertFalse(legacyHeader.isVisible());

        workspace.select(fuel);
        assertSame(fuel, workspace.getActiveFrame());
        workspace.moveTab(0, 1);
        assertSame(ignition, workspace.getOpenFrames().get(0));
        assertSame(fuel, workspace.getOpenFrames().get(1));
        assertSame(fuel, workspace.getActiveFrame());
        JButton close = find((Container) workspace.getTabsForTesting().getTabComponentAt(0),
                JButton.class);
        assertNotNull(close);
        close.doClick();
        assertSame(ignition, closeRequest[0]);

        workspace.close(fuel);
        assertEquals(1, workspace.getOpenCount());
        assertSame(ignition, workspace.getActiveFrame());
        workspace.close(ignition);
        assertEquals(0, workspace.getOpenCount());
    }

    @Test
    public void workspaceHostsAndClosesNonMapDocuments() {
        EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(null);
        JPanel comparison = new JPanel();
        comparison.setName("TEST COMPARISON");

        workspace.openUtility("compare", "Compare ROMs", null, comparison);
        assertEquals(1, workspace.getTabsForTesting().getTabCount());
        assertEquals(0, workspace.getOpenCount());
        assertSame(comparison,
                workspace.getTabsForTesting().getSelectedComponent());
        assertEquals(null, workspace.getActiveFrame());

        workspace.requestCloseActiveTab();
        assertEquals(0, workspace.getTabsForTesting().getTabCount());
    }

    @Test
    public void mapTabsExposeScopedCloseActionsWithoutClosingUtilities() {
        final java.util.List<TableFrame> closeRequests =
                new java.util.ArrayList<TableFrame>();
        final Table[] reopened = {null};
        EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(
                new EditorTabbedWorkspace.Listener() {
                    public void tableActivated(TableFrame frame) { }
                    public void closeRequested(TableFrame frame) {
                        closeRequests.add(frame);
                    }
                    public void reopenRequested(Table table) {
                        reopened[0] = table;
                    }
                });
        TableFrame fuel = tableFrame("Fuel Map");
        TableFrame ignition = tableFrame("Ignition Map");
        workspace.open(fuel);
        workspace.open(ignition);
        workspace.openUtility("live", "Live Data", null, new JPanel());

        JPanel fuelTab = (JPanel) workspace.getTabsForTesting()
                .getTabComponentAt(0);
        JPopupMenu menu = (JPopupMenu) fuelTab.getClientProperty(
                "TAB_CONTEXT_MENU");
        assertNotNull(menu);
        assertEquals("Close Fuel Map",
                ((JMenuItem) menu.getComponent(0)).getText());
        assertEquals("Close Other Maps",
                ((JMenuItem) menu.getComponent(1)).getText());
        assertEquals("Close All Maps",
                ((JMenuItem) menu.getComponent(2)).getText());

        ((JMenuItem) menu.getComponent(1)).doClick();
        assertEquals(1, closeRequests.size());
        assertSame(ignition, closeRequests.get(0));
        JMenuItem reopen = (JMenuItem) menu.getComponent(4);
        assertEquals("Reopen Last Closed Map", reopen.getText());
        assertTrue(reopen.isEnabled());
        reopen.doClick();
        assertSame(ignition.getTable(), reopened[0]);

        closeRequests.clear();
        ((JMenuItem) menu.getComponent(2)).doClick();
        assertEquals(2, closeRequests.size());
        assertTrue(closeRequests.contains(fuel));
        assertTrue(closeRequests.contains(ignition));
        assertEquals(3, workspace.getTabsForTesting().getTabCount());
    }

    @Test
    public void mapTabsShowRealUnsavedCellStateAndClearIt() {
        EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(null);
        TableFrame frame = tableFrame("Fuel Target");
        Table1D table = (Table1D) frame.getTable();
        ChangedDataCell cell = new ChangedDataCell(table, 10.0);
        table.setData(new DataCell[] {cell});
        workspace.open(frame);

        JPanel header = (JPanel) workspace.getTabsForTesting()
                .getTabComponentAt(0);
        JLabel indicator = findNamed(header, JLabel.class,
                "UNSAVED MAP INDICATOR");
        assertNotNull(indicator);
        assertFalse(indicator.isVisible());
        assertTrue(workspace.getTabsForTesting().getToolTipTextAt(0)
                .contains("No unsaved changes"));

        cell.setCurrent(12.5);
        workspace.refreshTabStates();
        assertTrue(indicator.isVisible());
        assertEquals("1 unsaved cell", indicator.getToolTipText());
        assertTrue(workspace.getTabsForTesting().getToolTipTextAt(0)
                .contains("1 unsaved cell"));

        cell.setCurrent(10.0);
        workspace.refreshTabStates();
        assertFalse(indicator.isVisible());
    }

    @Test
    public void romComparisonUsesResponsiveWorkspaceControls() {
        Rom left = new Rom(new com.romraider.maps.RomID());
        Rom right = new Rom(new com.romraider.maps.RomID());
        left.setFileName("stock.bin");
        right.setFileName("tuned.bin");
        RomComparePanel panel = new RomComparePanel(
                Arrays.asList(left, right), null);

        assertEquals("ROM COMPARE WORKSPACE", panel.getName());
        assertEquals(2, count(panel, JComboBox.class));
        assertNotNull(findNamed(panel, JTable.class,
                "ROM COMPARISON RESULTS"));
        assertNotNull(findNamed(panel, JButton.class,
                "COMPARE SELECTED ROMS"));
        assertNotNull(findNamed(panel, JButton.class,
                "OPEN SELECTED ROM COMPARISON"));
    }

    @Test
    public void touchModeMakesTabCloseTargetsFingerSized() {
        EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(null);
        workspace.open(tableFrame("Boost Control"));
        TouchTargetService.apply(workspace, DisplayMode.TOUCH);

        JButton close = find((Container) workspace.getTabsForTesting().getTabComponentAt(0),
                JButton.class);
        assertNotNull(close);
        assertTrue(close.getPreferredSize().width >= 48);
        assertTrue(close.getPreferredSize().height >= 48);
    }

    @Test
    public void tuningDocumentDisposesItsVisualizationProvider() {
        final boolean[] disposed = {false};
        TableFrame frame = tableFrame("Fuel Map");
        MapVisualizationProvider provider = new MapVisualizationProvider() {
            public String getName() { return "Test renderer"; }
            public boolean supports(com.romraider.maps.Table table) { return true; }
            public javax.swing.JComponent createVisualization(
                    com.romraider.maps.Table table) {
                return new JPanel();
            }
            public void dispose(javax.swing.JComponent visualization) {
                disposed[0] = true;
            }
        };
        TuningDocumentPanel document = new TuningDocumentPanel(
                frame.getTableMenuBar(), frame.getTableView(),
                frame.getTable(), provider);

        document.disposeDocument();

        assertTrue(disposed[0]);
    }

    @Test
    public void tuningDocumentStacksVisualizationAtNarrowWidths() {
        TableFrame frame = tableFrame("Fuel Map");
        TuningDocumentPanel document = new TuningDocumentPanel(
                frame.getTableMenuBar(), frame.getTableView(), surfaceTable(),
                new Java2dSurfaceVisualizationProvider());
        JSplitPane mapSplit = document.getMapSplitForTesting();

        mapSplit.setSize(640, 460);
        mapSplit.dispatchEvent(new java.awt.event.ComponentEvent(mapSplit,
                java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        assertEquals(JSplitPane.VERTICAL_SPLIT, mapSplit.getOrientation());

        mapSplit.setSize(900, 460);
        mapSplit.dispatchEvent(new java.awt.event.ComponentEvent(mapSplit,
                java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, mapSplit.getOrientation());
        document.disposeDocument();
    }

    @Test
    public void defaultVisualizationRegistryProvidesSafeInteractiveSurface() {
        Table3D table = surfaceTable();
        MapVisualizationRegistry registry =
                MapVisualizationRegistry.createDefault();
        MapVisualizationProvider provider = registry.findProvider(table);

        assertNotNull(provider);
        assertEquals("Java2D surface", provider.getName());
        assertTrue(provider.supports(table));
        assertFalse(provider.supports(new Table1D()));

        Java2dSurfacePanel surface = (Java2dSurfacePanel)
                provider.createVisualization(table);
        assertEquals("BUILT-IN MAP SURFACE", surface.getName());
        assertTrue(surface.getInteractionHint().contains("Click to select"));
        assertTrue(surface.cellDescriptionForTesting(3, 2)
                .contains("Value: 16.45"));
        assertTrue(surface.selectCellForTesting(3, 2));
        assertTrue(table.get3dData()[3][2].isSelected());
        surface.setSize(420, 300);
        BufferedImage image = new BufferedImage(420, 300,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            surface.paint(graphics);
        } finally {
            graphics.dispose();
        }
        assertTrue(countDistinctPixels(image) > 4);
        provider.dispose(surface);
        assertFalse(surface.isMonitoringForTesting());
    }

    @Test
    public void threeDimensionalSurfaceFollowsPointerDragDirection() {
        Java2dSurfacePanel surface = new Java2dSurfacePanel(surfaceTable());
        double initialYaw = surface.yawForTesting();
        double initialPitch = surface.pitchForTesting();

        surface.dispatchEvent(new java.awt.event.MouseEvent(surface,
                java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 100, 100, 1, false,
                java.awt.event.MouseEvent.BUTTON1));
        surface.dispatchEvent(new java.awt.event.MouseEvent(surface,
                java.awt.event.MouseEvent.MOUSE_DRAGGED,
                System.currentTimeMillis(),
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                120, 120, 0, false, java.awt.event.MouseEvent.NOBUTTON));

        assertTrue(surface.yawForTesting() < initialYaw);
        assertTrue(surface.pitchForTesting() > initialPitch);
        surface.disposeSurface();
    }

    @Test
    public void visualizationProviderFailureStaysInsideMapSurface() {
        MapVisualizationProvider failing = new MapVisualizationProvider() {
            public String getName() { return "Broken renderer"; }
            public boolean supports(com.romraider.maps.Table table) { return true; }
            public javax.swing.JComponent createVisualization(
                    com.romraider.maps.Table table) {
                throw new UnsatisfiedLinkError("native backend missing");
            }
            public void dispose(javax.swing.JComponent visualization) { }
        };

        MapVisualizationHost host = new MapVisualizationHost(surfaceTable(), failing);

        JLabel message = findLabelContaining(host, "Surface renderer unavailable");
        assertNotNull(message);
        assertTrue(message.getText().contains("native backend missing"));
    }

    @Test
    public void registrySkipsBrokenProviderBeforeUsingFallback() {
        MapVisualizationProvider broken = new MapVisualizationProvider() {
            public String getName() { return "Broken"; }
            public boolean supports(com.romraider.maps.Table table) {
                throw new UnsatisfiedLinkError("missing");
            }
            public javax.swing.JComponent createVisualization(
                    com.romraider.maps.Table table) { return null; }
            public void dispose(javax.swing.JComponent visualization) { }
        };
        MapVisualizationRegistry registry = new MapVisualizationRegistry(
                Arrays.asList(broken, new Java2dSurfaceVisualizationProvider()));

        assertEquals("Java2D surface",
                registry.findProvider(surfaceTable()).getName());
    }

    @Test
    public void threeDimensionalViewButtonTogglesIntegratedSurface() {
        TableFrame frame = tableFrame("Fuel Map");
        TuningDocumentPanel document = new TuningDocumentPanel(
                frame.getTableMenuBar(), frame.getTableView(), surfaceTable(),
                new Java2dSurfaceVisualizationProvider());
        JToggleButton toggle = findNamed(document, JToggleButton.class,
                "OPEN 3D VIEW");
        MapVisualizationHost host = find(document, MapVisualizationHost.class);

        assertNotNull(toggle);
        assertEquals("Show 3D", toggle.getText());
        assertFalse(toggle.isSelected());
        assertFalse(host.isVisible());
        assertEquals(0, document.getMapSplitForTesting().getDividerSize());
        assertTrue(toggle.getToolTipText().contains("Show"));

        toggle.doClick();
        assertTrue(toggle.isSelected());
        assertEquals("Hide 3D", toggle.getText());
        assertTrue(host.isVisible());
        assertEquals(5, document.getMapSplitForTesting().getDividerSize());
        assertTrue(toggle.getToolTipText().contains("Hide"));

        toggle.doClick();
        assertFalse(toggle.isSelected());
        assertEquals("Show 3D", toggle.getText());
        assertFalse(host.isVisible());
        assertEquals(0, document.getMapSplitForTesting().getDividerSize());
        document.disposeDocument();
    }

    @Test
    public void datalogViewerCanCollapseAndRestoreItsWorkspace() {
        TableFrame frame = tableFrame("Fuel Map");
        TuningDocumentPanel document = new TuningDocumentPanel(
                frame.getTableMenuBar(), frame.getTableView(), frame.getTable(),
                null, new Runnable() { public void run() { } });
        DatalogDockPanel dock = find(document, DatalogDockPanel.class);
        JToggleButton toggle = findNamed(dock, JToggleButton.class,
                "TOGGLE DATALOG VIEWER");

        assertNotNull(toggle);
        assertTrue(toggle.isSelected());
        toggle.doClick();
        assertFalse(toggle.isSelected());
        assertEquals(34, dock.getMinimumSize().height);
        assertTrue(toggle.getToolTipText().contains("Expand"));
        toggle.doClick();
        assertTrue(toggle.isSelected());
        assertEquals(110, dock.getMinimumSize().height);
        assertTrue(toggle.getToolTipText().contains("Collapse"));
    }

    @Test
    public void datalogDockCanOpenTheExistingLogger() {
        final boolean[] opened = {false};
        DatalogDockPanel dock = new DatalogDockPanel(new Runnable() {
            public void run() { opened[0] = true; }
        });

        JButton open = findButton(dock, "Configure Logger");
        assertNotNull(open);
        assertTrue(open.isEnabled());
        open.doClick();

        assertTrue(opened[0]);
    }

    @Test
    public void liveDataWorkspaceFiltersResizableValuesAndOpensConfiguration() {
        final boolean[] configured = {false};
        final String[] focusedParameter = {null};
        LiveDataWorkspacePanel workspace = new LiveDataWorkspacePanel(
                new Runnable() {
                    public void run() { configured[0] = true; }
                }, new java.util.function.Consumer<String>() {
                    public void accept(String parameterId) {
                        focusedParameter[0] = parameterId;
                    }
                });
        JTable parameters = findNamed(workspace, JTable.class,
                "LIVE WORKSPACE PARAMETERS");
        JTextField filter = findNamed(workspace, JTextField.class,
                "LIVE WORKSPACE FILTER");
        JLabel status = findNamed(workspace, JLabel.class,
                "LIVE WORKSPACE SESSION STATUS");
        JLabel traceSummary = findNamed(workspace, JLabel.class,
                "LIVE TRACE SUMMARY");
        JButton configure = findNamed(workspace, JButton.class,
                "CONFIGURE LOGGER");
        JButton findInLogger = findNamed(workspace, JButton.class,
                "FIND LIVE PARAMETER IN LOGGER");

        assertEquals("LIVE DATA WORKSPACE", workspace.getName());
        assertNotNull(findNamed(workspace, JSplitPane.class,
                "LIVE DATA SPLIT"));
        assertNotNull(findNamed(workspace, JPanel.class,
                "LIVE TRACE PLOT"));
        assertEquals(4, parameters.getColumnCount());
        assertEquals(JTable.AUTO_RESIZE_OFF, parameters.getAutoResizeMode());
        int preferredWidth = 0;
        for (int column = 0; column < parameters.getColumnCount(); column++) {
            assertTrue(parameters.getColumnModel().getColumn(column)
                    .getResizable());
            preferredWidth += parameters.getColumnModel().getColumn(column)
                    .getPreferredWidth();
        }
        assertTrue(preferredWidth <= 400);
        assertNotNull(findNamed(workspace, JPanel.class,
                "LIVE PARAMETER EMPTY STATE"));
        assertNotNull(findInLogger);
        assertFalse(findInLogger.isEnabled());

        workspace.updateSessionState(LoggerSessionState.LIVE_ECU);
        workspace.updateSample(new LiveDataSample("RPM", "Engine Speed",
                4523.0, "4523", "RPM", 100L));
        workspace.updateSample(new LiveDataSample("RPM", "Engine Speed",
                4600.0, "4600", "RPM", 200L));
        workspace.updateSample(new LiveDataSample("BOOST", "Boost Pressure",
                14.3, "14.3", "psi", 300L));

        assertEquals("● ECU LIVE", status.getText());
        assertEquals(2, parameters.getModel().getRowCount());
        assertEquals("4600", parameters.getModel().getValueAt(0, 1));
        parameters.setRowSelectionInterval(0, 0);
        assertEquals("1 selected trace", traceSummary.getText());
        assertTrue(findInLogger.isEnabled());
        findInLogger.doClick();
        assertEquals("RPM", focusedParameter[0]);
        parameters.clearSelection();
        assertFalse(findInLogger.isEnabled());
        assertTrue(traceSummary.getText().contains("up to 5 traces"));
        filter.setText("boost");
        assertEquals(1, parameters.getRowCount());
        configure.doClick();
        assertTrue(configured[0]);
        workspace.removeSample("BOOST");
        assertEquals(1, parameters.getModel().getRowCount());
    }

    @Test
    public void liveDashboardUsesActualSamplesAndCapsTheOverview() {
        final boolean[] configured = {false};
        LiveDashboardPanel dashboard = new LiveDashboardPanel(new Runnable() {
            public void run() { configured[0] = true; }
        });
        dashboard.updateSessionState(LoggerSessionState.LIVE_ECU);
        for (int parameter = 0; parameter < 7; parameter++) {
            for (int sample = 0; sample < 8; sample++) {
                dashboard.updateSample(new LiveDataSample("P" + parameter,
                        "Parameter " + parameter, parameter * 10.0 + sample,
                        Integer.toString(parameter * 10 + sample), "unit",
                        100L + sample));
            }
        }

        assertEquals("LIVE DASHBOARD WORKSPACE", dashboard.getName());
        assertEquals("● ECU LIVE", findNamed(dashboard, JLabel.class,
                "DASHBOARD SESSION STATUS").getText());
        assertEquals(6, countNamed(dashboard, "LIVE DASHBOARD CARD"));
        assertTrue(findNamed(dashboard, JLabel.class,
                "LIVE DASHBOARD SUMMARY").getText().contains("6 of 7"));
        JPanel trace = findNamed(dashboard, JPanel.class,
                "LIVE DASHBOARD MINI TRACE");
        trace.setSize(260, 80);
        BufferedImage image = new BufferedImage(260, 80,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            trace.paint(graphics);
        } finally {
            graphics.dispose();
        }
        assertTrue(countDistinctPixels(image) > 4);

        findNamed(dashboard, JButton.class,
                "DASHBOARD CONFIGURE LOGGER").doClick();
        assertTrue(configured[0]);
        dashboard.removeSample("P0");
        assertEquals(6, countNamed(dashboard, "LIVE DASHBOARD CARD"));
    }

    @Test
    public void datalogDockDrawsRealSampleHistory() {
        DatalogDockPanel dock = new DatalogDockPanel();
        dock.updateSessionState(LoggerSessionState.LIVE_ECU);
        for (int index = 0; index < 24; index++) {
            dock.updateSample(new LiveDataSample("RPM", "Engine Speed",
                    1800.0 + Math.sin(index * 0.45) * 500.0,
                    Integer.toString(1800 + index), "RPM", index));
        }
        JPanel plot = findNamed(dock, JPanel.class, "LIVE TRACE PLOT");
        JLabel status = findNamed(dock, JLabel.class,
                "DATALOG SESSION STATUS");
        plot.setSize(640, 130);
        BufferedImage image = new BufferedImage(640, 130,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            plot.paint(graphics);
        } finally {
            graphics.dispose();
        }

        assertTrue(status.getText().contains("1 PARAMETERS"));
        assertTrue(countDistinctPixels(image) > 4);
    }

    @Test
    public void mapHeaderCanFocusTheSharedCalibrationSearch() {
        final boolean[] focused = {false};
        TableFrame frame = tableFrame("Fuel Map");
        TuningDocumentPanel document = new TuningDocumentPanel(
                frame.getTableMenuBar(), frame.getTableView(), frame.getTable(),
                null, null, new Runnable() {
                    public void run() { focused[0] = true; }
                });
        JButton search = findNamed(document, JButton.class, "SEARCH MAPS");

        assertNotNull(search);
        assertTrue(search.isEnabled());
        search.doClick();
        assertTrue(focused[0]);
    }

    @Test
    public void mapHeaderExposesDirectFavoriteAndChangeContext() {
        final boolean[] favorite = {false};
        EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(
                new EditorTabbedWorkspace.Listener() {
                    public void tableActivated(TableFrame frame) { }
                    public void closeRequested(TableFrame frame) { }
                    public boolean isFavorite(Table table) {
                        return favorite[0];
                    }
                    public void toggleFavorite(Table table) {
                        favorite[0] = !favorite[0];
                    }
                });
        TableFrame frame = tableFrame("Fuel Map");
        workspace.open(frame);

        JToggleButton toggle = findNamed(workspace, JToggleButton.class,
                "TOGGLE MAP FAVORITE");
        JLabel changes = findNamed(workspace, JLabel.class,
                "MAP CHANGE STATUS");
        JLabel metadata = findNamed(workspace, JLabel.class, "MAP METADATA");

        assertNotNull(toggle);
        assertFalse(toggle.isSelected());
        toggle.doClick();
        assertTrue(toggle.isSelected());
        assertTrue(favorite[0]);
        assertFalse(changes.isVisible());
        assertTrue(metadata.getText().contains("Other"));
        assertTrue(metadata.getToolTipText().contains("Address 0x"));
    }

    private static TableFrame tableFrame(String name) {
        Table1D table = new Table1D();
        table.setName(name);
        Table1DView view = new Table1DView(table, Table1DType.NO_AXIS);
        table.setTableView(view);
        return new TableFrame(name + " | test.bin", view);
    }

    private static Table3D surfaceTable() {
        Table3D table = new Table3D();
        table.setName("Primary Open Loop Fueling");
        table.setSizeX(4);
        table.setSizeY(3);
        DataCell[][] cells = table.get3dData();
        for (int x = 0; x < cells.length; x++) {
            for (int y = 0; y < cells[x].length; y++) {
                cells[x][y] = new FixedDataCell(table,
                        8.0 + x * 1.75 + y * y * 0.8);
            }
        }
        return table;
    }

    private static int countDistinctPixels(BufferedImage image) {
        java.util.HashSet<Integer> colors = new java.util.HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y += 5) {
            for (int x = 0; x < image.getWidth(); x += 5) {
                colors.add(image.getRGB(x, y));
            }
        }
        return colors.size();
    }

    private static int componentBaseline(Component component) {
        return component.getY() + component.getBaseline(
                component.getWidth(), component.getHeight());
    }

    private static JLabel findLabelContaining(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel
                    && ((JLabel) component).getText() != null
                    && ((JLabel) component).getText().contains(text)) {
                return (JLabel) component;
            }
            if (component instanceof Container) {
                JLabel found = findLabelContaining((Container) component, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class FixedDataCell extends DataCell {
        private static final long serialVersionUID = 1L;
        private final double value;

        private FixedDataCell(Table3D table, double value) {
            super(table, (Rom) null);
            this.value = value;
        }

        @Override
        public double getRealValue() {
            return value;
        }
    }

    private static final class ChangedDataCell extends DataCell {
        private final double original;
        private double current;

        private ChangedDataCell(Table1D table, double value) {
            super(table, (Rom) null);
            original = value;
            current = value;
        }

        private void setCurrent(double value) { current = value; }
        @Override public double getBinValue() { return current; }
        @Override public double getOriginalValue() { return original; }
    }

    private static JButton findButton(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton
                    && text.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
            if (component instanceof Container) {
                JButton found = findButton((Container) component, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container) {
                T found = find((Container) component, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T findNamed(Container root,
            Class<T> type, String name) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component) && name.equals(component.getName())) {
                return type.cast(component);
            }
            if (component instanceof Container) {
                T found = findNamed((Container) component, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int count(Container root, Class<?> type) {
        int result = 0;
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) result++;
            if (component instanceof Container) result += count((Container) component, type);
        }
        return result;
    }

    private static int countNamed(Container root, String name) {
        int result = 0;
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) result++;
            if (component instanceof Container) {
                result += countNamed((Container) component, name);
            }
        }
        return result;
    }
}
