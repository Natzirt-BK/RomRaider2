/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.ScrollPaneConstants;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.romraider.maps.Rom;
import com.romraider.maps.Scale;
import com.romraider.maps.Table;
import com.romraider.maps.Table3D;
import com.romraider.maps.TableView;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.EditHistoryEntry;
import com.romraider.maps.history.EditHistoryListener;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.editor.workspace.EditorWorkspaceService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.editor.workspace.TableChangeSummary;
import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.api.LoggerLiveDataListener;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.PlatformContextListener;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.swing.ModernSearchField;

/** Context-sensitive information rail for the modern Editor workbench. */
public final class EditorInspectorPanel extends JPanel implements PlatformContextListener {
    private static final long serialVersionUID = 1L;
    private static final int LIVE_VALUE_PAGE_SIZE = 4;
    private final JLabel selectionTitle = new JLabel("No table selected");
    private final JLabel tableName = valueLabel();
    private final JLabel category = valueLabel();
    private final JLabel address = valueLabel();
    private final JLabel tableType = valueLabel();
    private final JLabel dimensions = valueLabel();
    private final JLabel scale = valueLabel();
    private final JLabel valueRange = valueLabel();
    private final JLabel access = valueLabel();
    private final JLabel liveSupport = valueLabel();
    private final JTextArea description = new JTextArea();
    private final JLabel romFile = valueLabel();
    private final JLabel romId = valueLabel();
    private final JLabel romSize = valueLabel();
    private final JLabel tableCount = valueLabel();
    private final JLabel platform = valueLabel();
    private final JLabel module = valueLabel();
    private final JLabel realtime = valueLabel();
    private final JTextArea notes = new JTextArea();
    private Rom noteRom;
    private Table noteTable;
    private boolean loadingNote;
    private final JLabel liveDataStatus = new JLabel("● ECU OFFLINE");
    private final JLabel liveDataSummary = new JLabel("No parameters");
    private final JLabel liveDataEmpty = new JLabel(
            "<html><div style='text-align:center'>No live data session<br>"
                    + "Open Logger to connect and select parameters.</div></html>",
            JLabel.CENTER);
    private final DefaultTableModel liveDataModel = new DefaultTableModel(
            new Object[] {"Parameter", "Value", "Units"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final Map<String, Integer> liveRows =
            new LinkedHashMap<String, Integer>();
    private final List<String> liveRowIds = new ArrayList<String>();
    private final Map<String, LiveDataSample> liveLatest =
            new LinkedHashMap<String, LiveDataSample>();
    private final Map<String, CompactLiveValueTile> liveValueTiles =
            new LinkedHashMap<String, CompactLiveValueTile>();
    private final JTable liveParameters = new JTable(liveDataModel);
    private final JPanel liveValueGrid = new JPanel(new GridLayout(0, 2, 6, 6));
    private final JPanel liveValueContent = new JPanel(new CardLayout());
    private final JButton previousLiveValues = new JButton(
            ModernIconFactory.icon(Action.BACK));
    private final JButton nextLiveValues = new JButton(
            ModernIconFactory.icon(Action.FORWARD));
    private final JLabel liveValuePageLabel = new JLabel("1 / 1");
    private final JButton openLiveParameter = new JButton("Open in Logger",
            ModernIconFactory.icon(Action.SEARCH));
    private final LoggerLiveDataListener liveDataListener =
            new LoggerLiveDataListener() {
        public void sessionStateChanged(final LoggerSessionState state) {
            onEventThread(() -> showLoggerState(state));
        }
        public void sampleUpdated(final LiveDataSample sample) {
            onEventThread(() -> showLiveSample(sample));
        }
        public void parameterRemoved(final String parameterId) {
            onEventThread(() -> removeLiveParameter(parameterId));
        }
    };
    private boolean liveDataAttached;
    private LoggerSessionState liveSessionState = LoggerSessionState.STOPPED;
    private int liveValuePage;
    private final JTabbedPane inspectorTabs = new JTabbedPane();
    private final JLabel changeTotal = new JLabel("No unsaved cell changes");
    private final DefaultTableModel changeModel = new DefaultTableModel(
            new Object[] {"Cells", "Table"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable changeTable = new JTable(changeModel);
    private final DefaultListModel<EditHistoryEntry> historyModel =
            new DefaultListModel<EditHistoryEntry>();
    private final JList<EditHistoryEntry> historyList =
            new JList<EditHistoryEntry>(historyModel);
    private final JLabel historySummary = new JLabel("No edit history");
    private final JButton openChangedTable = new JButton("Open",
            ModernIconFactory.icon(Action.OPEN));
    private final JButton undoHistory = new JButton("Undo",
            ModernIconFactory.icon(Action.UNDO));
    private final JButton redoHistory = new JButton("Redo",
            ModernIconFactory.icon(Action.REDO));
    private final RomEditHistory editHistory = RomEditHistory.getInstance();
    private Rom currentRom;
    private final EditHistoryListener editHistoryListener = rom -> {
        if (rom == currentRom) onEventThread(() -> refreshChanges());
    };
    private boolean editHistoryAttached;
    private boolean platformContextAttached;
    private Consumer<Table> openTableAction;
    private Consumer<String> focusLiveParameterAction;

    public EditorInspectorPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel heading = new JLabel("INSPECTOR");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        selectionTitle.setFont(selectionTitle.getFont().deriveFont(Font.BOLD,
                selectionTitle.getFont().getSize2D() + 1.0f));
        JPanel header = new JPanel(new BorderLayout());
        header.add(heading, BorderLayout.NORTH);
        header.add(selectionTitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setRows(3);
        description.setColumns(12);
        description.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        sections.add(section("TABLE DETAILS", buildTablePanel()));
        sections.add(section("ROM INFORMATION", buildRomPanel()));
        sections.add(section("WORKSPACE CONTEXT", buildLivePanel()));
        JScrollPane sectionScroll = new JScrollPane(sections,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sectionScroll.setBorder(BorderFactory.createEmptyBorder());
        sectionScroll.getViewport().setOpaque(false);
        sectionScroll.setOpaque(false);

        inspectorTabs.setName("INSPECTOR TABS");
        inspectorTabs.addTab("INFO", sectionScroll);
        inspectorTabs.addTab("LIVE", buildLiveDataPanel());
        inspectorTabs.addTab("NOTES", buildNotesPanel());
        inspectorTabs.addTab("CHANGES", buildChangesPanel());
        inspectorTabs.setToolTipTextAt(0, "Map information");
        inspectorTabs.setToolTipTextAt(1, "Live data");
        inspectorTabs.setToolTipTextAt(2, "Map notes");
        inspectorTabs.setToolTipTextAt(3, "ROM changes");
        inspectorTabs.getAccessibleContext().setAccessibleDescription(
                "Map information, live data, notes, and ROM changes");
        inspectorTabs.addChangeListener(event -> {
            if (inspectorTabs.getSelectedIndex() == 3) refreshChanges();
        });
        add(inspectorTabs, BorderLayout.CENTER);
        clear();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!platformContextAttached) {
            platformContextAttached = true;
            PlatformContext.getInstance().addListener(this);
        }
        if (!liveDataAttached) {
            liveDataAttached = true;
            LoggerLiveDataBus.getInstance().addListener(liveDataListener);
        }
        if (!editHistoryAttached) {
            editHistoryAttached = true;
            editHistory.addListener(editHistoryListener);
        }
    }

    @Override
    public void removeNotify() {
        if (platformContextAttached) {
            PlatformContext.getInstance().removeListener(this);
            platformContextAttached = false;
        }
        if (liveDataAttached) {
            LoggerLiveDataBus.getInstance().removeListener(liveDataListener);
            liveDataAttached = false;
        }
        if (editHistoryAttached) {
            editHistory.removeListener(editHistoryListener);
            editHistoryAttached = false;
        }
        super.removeNotify();
    }

    public void setOpenTableAction(Consumer<Table> action) {
        openTableAction = action;
        updateChangeActions();
    }

    public void setFocusLiveParameterAction(Consumer<String> action) {
        focusLiveParameterAction = action;
        updateLiveParameterAction();
    }

    public void showSelection(Rom rom, Table table) {
        showRom(rom);
        if (table == null) return;
        selectionTitle.setText(safe(table.getName(), "Selected table"));
        selectionTitle.setToolTipText(selectionTitle.getText());
        tableName.setText(safe(table.getName(), "—"));
        category.setText(safe(table.getCategory(), "Other").replace("//", " › "));
        address.setText(String.format("0x%08X", table.getStorageAddress()));
        tableType.setText(String.valueOf(table.getType()));
        dimensions.setText(tableDimensions(table));
        scale.setText(tableScale(table));
        valueRange.setText(tableRange(table));
        access.setText(table.isLocked() ? "Locked" : "Editable");
        liveSupport.setText(table.isLiveDataSupported()
                ? "Live trace available" : "Not defined for this map");
        description.setText(safe(table.getDescription(), "No description available."));
        description.setCaretPosition(0);
        tableName.setToolTipText(tableName.getText());
        category.setToolTipText(category.getText());
        scale.setToolTipText(scale.getText());
        description.setToolTipText(description.getText());
        loadNote(rom, table);
    }

    public void showRom(Rom rom) {
        currentRom = rom;
        PlatformContext context = PlatformContext.getInstance();
        updatePlatform(context);
        if (rom == null) {
            romFile.setText("No ROM open");
            romId.setText("—");
            romFile.setToolTipText(null);
            romId.setToolTipText(null);
            romSize.setText("—");
            tableCount.setText("0");
            refreshChanges();
            return;
        }
        romFile.setText(safe(rom.getFileName(), "Unnamed ROM"));
        romId.setText(safe(rom.getRomIDString(), "Unknown"));
        romFile.setToolTipText(romFile.getText());
        romId.setToolTipText(romId.getText());
        romSize.setText(formatSize(rom.getRealFileSize()));
        tableCount.setText(String.valueOf(rom.getTables().size()));
        if (inspectorTabs.getSelectedIndex() == 3) refreshChanges();
    }

    public void clear() {
        clearTableSelection();
        showRom(null);
    }

    public void clearTableSelection() {
        noteRom = null;
        noteTable = null;
        loadingNote = true;
        notes.setText("");
        notes.setEnabled(false);
        loadingNote = false;
        selectionTitle.setText("No table selected");
        selectionTitle.setToolTipText(null);
        tableName.setText("—");
        tableName.setToolTipText(null);
        category.setText("—");
        category.setToolTipText(null);
        address.setText("—");
        tableType.setText("—");
        dimensions.setText("—");
        scale.setText("—");
        scale.setToolTipText(null);
        valueRange.setText("—");
        access.setText("—");
        liveSupport.setText("—");
        description.setText("Select a table to view details.");
        description.setToolTipText(null);
    }

    private JPanel buildNotesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setName("MAP NOTES");
        panel.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        JLabel help = new JLabel("Notes for the selected ROM and table");
        help.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        panel.add(help, BorderLayout.NORTH);
        notes.setName("MAP NOTES EDITOR");
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setRows(8);
        notes.getAccessibleContext().setAccessibleName("Map notes");
        notes.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { saveNote(); }
            public void removeUpdate(DocumentEvent event) { saveNote(); }
            public void changedUpdate(DocumentEvent event) { saveNote(); }
        });
        panel.add(new JScrollPane(notes), BorderLayout.CENTER);
        JLabel saved = new JLabel("Saved automatically with workspace settings");
        saved.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        panel.add(saved, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildChangesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setName("ROM CHANGES");
        panel.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        JPanel header = new JPanel(new BorderLayout(6, 0));
        changeTotal.setName("ROM CHANGE TOTAL");
        changeTotal.setFont(changeTotal.getFont().deriveFont(Font.BOLD));
        header.add(changeTotal, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh",
                ModernIconFactory.icon(Action.REFRESH));
        refresh.setName("REFRESH ROM CHANGES");
        refresh.setToolTipText("Recalculate changed cells in the open ROM");
        refresh.addActionListener(event -> refreshChanges());
        header.add(refresh, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        changeTable.setName("ROM CHANGE TABLE");
        changeTable.setFillsViewportHeight(true);
        changeTable.setRowSelectionAllowed(true);
        changeTable.setColumnSelectionAllowed(false);
        changeTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        changeTable.setShowVerticalLines(false);
        changeTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        changeTable.getTableHeader().setReorderingAllowed(false);
        changeTable.getTableHeader().setToolTipText(
                "Table name and number of changed cells");
        changeTable.getColumnModel().getColumn(0).setMinWidth(48);
        changeTable.getColumnModel().getColumn(0).setPreferredWidth(56);
        changeTable.getColumnModel().getColumn(0).setMaxWidth(64);
        changeTable.getColumnModel().getColumn(1).setMinWidth(70);
        changeTable.getColumnModel().getColumn(1).setPreferredWidth(165);
        DefaultTableCellRenderer countRenderer = new DefaultTableCellRenderer();
        countRenderer.setHorizontalAlignment(JLabel.RIGHT);
        changeTable.getColumnModel().getColumn(0).setCellRenderer(countRenderer);
        changeTable.getSelectionModel().addListSelectionListener(
                event -> updateChangeActions());
        changeTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) openSelectedChangedTable();
            }
        });
        changeTable.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"),
                "openChangedTable");
        changeTable.getActionMap().put("openChangedTable",
                new javax.swing.AbstractAction() {
                    private static final long serialVersionUID = 1L;
                    public void actionPerformed(java.awt.event.ActionEvent event) {
                        openSelectedChangedTable();
                    }
        });
        JPanel changedMaps = new JPanel(new BorderLayout(0, 5));
        changedMaps.setMinimumSize(new Dimension(0, 0));
        JScrollPane changedScroll = new JScrollPane(changeTable);
        changedScroll.setMinimumSize(new Dimension(0, 0));
        changedMaps.add(changedScroll, BorderLayout.CENTER);
        openChangedTable.setName("OPEN CHANGED TABLE");
        openChangedTable.setToolTipText(
                "Open the selected affected map (double-click or Enter)");
        openChangedTable.addActionListener(event -> openSelectedChangedTable());
        JPanel mapActions = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 0, 0));
        mapActions.add(openChangedTable);
        changedMaps.add(mapActions, BorderLayout.SOUTH);

        JPanel history = new JPanel(new BorderLayout(0, 5));
        history.setName("EDIT HISTORY");
        history.setMinimumSize(new Dimension(0, 0));
        historySummary.setName("EDIT HISTORY SUMMARY");
        historySummary.setFont(historySummary.getFont().deriveFont(Font.BOLD));
        history.add(historySummary, BorderLayout.NORTH);
        historyList.setName("EDIT HISTORY LIST");
        historyList.setToolTipText("Newest undoable operation first");
        historyList.setVisibleRowCount(5);
        historyList.setSelectionMode(
                javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setMinimumSize(new Dimension(0, 0));
        history.add(historyScroll, BorderLayout.CENTER);
        undoHistory.setName("UNDO ROM HISTORY");
        redoHistory.setName("REDO ROM HISTORY");
        undoHistory.addActionListener(event -> applyHistory(false));
        redoHistory.addActionListener(event -> applyHistory(true));
        JPanel historyActions = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 6, 0));
        historyActions.add(undoHistory);
        historyActions.add(redoHistory);
        history.add(historyActions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                changedMaps, history);
        split.setName("CHANGES AND HISTORY SPLIT");
        split.setResizeWeight(0.54);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(5);
        split.setMinimumSize(new Dimension(0, 0));
        split.setBorder(BorderFactory.createEmptyBorder());
        panel.add(split, BorderLayout.CENTER);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { split.setDividerLocation(0.54); }
        });
        return panel;
    }

    void refreshChanges() {
        List<TableChangeSummary> summaries = RomChangeSummary.summarize(currentRom);
        changeModel.setRowCount(0);
        int total = 0;
        for (TableChangeSummary summary : summaries) {
            changeModel.addRow(new Object[] {summary.getChangedCells(),
                    summary.getTableName()});
            total += summary.getChangedCells();
        }
        changeTotal.setText(total == 0 ? "No unsaved cell changes"
                : total + " changed cells in " + summaries.size()
                        + (summaries.size() == 1 ? " table" : " tables"));
        if (total == 0 && currentRom != null
                && com.romraider.editor.workspace.RomChangeService
                        .hasBinaryChanges(currentRom)) {
            changeTotal.setText("Unsaved recovered ROM data");
        }
        refreshHistory();
        updateChangeActions();
    }

    private void refreshHistory() {
        List<EditHistoryEntry> undoEntries = editHistory.undoHistory(currentRom);
        List<EditHistoryEntry> redoEntries = editHistory.redoHistory(currentRom);
        historyModel.clear();
        int shown = Math.min(12, undoEntries.size());
        for (int index = 0; index < shown; index++) {
            historyModel.addElement(undoEntries.get(index));
        }
        historySummary.setText(undoEntries.isEmpty() && redoEntries.isEmpty()
                ? "No edit history"
                : undoEntries.size() + " undo step"
                        + (undoEntries.size() == 1 ? "" : "s") + "  ·  "
                        + redoEntries.size() + " redo");
        undoHistory.setEnabled(!undoEntries.isEmpty());
        redoHistory.setEnabled(!redoEntries.isEmpty());
        undoHistory.setToolTipText(undoEntries.isEmpty() ? "Nothing to undo"
                : "Undo: " + undoEntries.get(0).getDescription());
        redoHistory.setToolTipText(redoEntries.isEmpty() ? "Nothing to redo"
                : "Redo: " + redoEntries.get(0).getDescription());
    }

    private void updateChangeActions() {
        openChangedTable.setEnabled(currentRom != null
                && changeTable.getSelectedRow() >= 0
                && openTableAction != null);
    }

    private void openSelectedChangedTable() {
        int selected = changeTable.getSelectedRow();
        if (selected < 0 || currentRom == null || openTableAction == null) return;
        int modelRow = changeTable.convertRowIndexToModel(selected);
        Object name = changeModel.getValueAt(modelRow, 1);
        Table table = name == null ? null
                : currentRom.getTableByName(String.valueOf(name));
        if (table != null) openTableAction.accept(table);
    }

    private void applyHistory(boolean redo) {
        if (currentRom == null) return;
        try {
            if (redo) editHistory.redo(currentRom);
            else editHistory.undo(currentRom);
        } catch (UserLevelException failure) {
            TableView.showInvalidUserLevelPopup(failure);
        }
    }

    private void loadNote(Rom rom, Table table) {
        noteRom = rom;
        noteTable = table;
        loadingNote = true;
        notes.setText(rom == null || table == null ? ""
                : EditorWorkspaceService.getInstance().getTableNote(rom, table));
        notes.setCaretPosition(0);
        loadingNote = false;
        notes.setEnabled(rom != null && table != null);
    }

    private void saveNote() {
        if (loadingNote || noteRom == null || noteTable == null) return;
        EditorWorkspaceService.getInstance().setTableNote(
                noteRom, noteTable, notes.getText());
    }

    public void platformContextChanged(final PlatformContext context) {
        Runnable update = new Runnable() {
            public void run() { updatePlatform(context); }
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    private void updatePlatform(PlatformContext context) {
        platform.setText(String.valueOf(context.getPlatform()));
        module.setText(String.valueOf(context.getModule()));
        realtime.setText("DimeMod: " + context.getDimeModState().getDisplayName());
    }

    private JPanel buildTablePanel() {
        JPanel panel = detailsPanel();
        GridBagConstraints c = constraints();
        addRow(panel, c, 0, "Name", tableName);
        addRow(panel, c, 1, "Category", category);
        addRow(panel, c, 2, "Address", address);
        addRow(panel, c, 3, "Type", tableType);
        addRow(panel, c, 4, "Dimensions", dimensions);
        addRow(panel, c, 5, "Scale", scale);
        addRow(panel, c, 6, "Value range", valueRange);
        addRow(panel, c, 7, "Access", access);
        addRow(panel, c, 8, "Live data", liveSupport);
        c.gridx = 0; c.gridy = 9; c.gridwidth = 2; c.weighty = 0;
        panel.add(new JLabel("Description"), c);
        c.gridy = 10; c.weighty = 0; c.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(description,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), c);
        return panel;
    }

    private JPanel buildRomPanel() {
        JPanel panel = detailsPanel();
        GridBagConstraints c = constraints();
        addRow(panel, c, 0, "File", romFile);
        addRow(panel, c, 1, "ROM ID", romId);
        addRow(panel, c, 2, "Size", romSize);
        addRow(panel, c, 3, "Tables", tableCount);
        return panel;
    }

    private JPanel buildLivePanel() {
        JPanel panel = detailsPanel();
        GridBagConstraints c = constraints();
        addRow(panel, c, 0, "Platform", platform);
        addRow(panel, c, 1, "Module", module);
        addRow(panel, c, 2, "Realtime", realtime);
        return panel;
    }

    private JPanel buildLiveDataPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setName("LIVE DATA INSPECTOR");
        panel.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));

        liveDataStatus.setName("LIVE DATA STATUS");
        liveDataStatus.setFont(liveDataStatus.getFont().deriveFont(Font.BOLD));
        liveDataStatus.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        liveDataSummary.setName("LIVE DATA SUMMARY");
        liveDataSummary.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel status = new JPanel(new BorderLayout(8, 0));
        status.add(liveDataStatus, BorderLayout.WEST);
        status.add(liveDataSummary, BorderLayout.EAST);
        panel.add(status, BorderLayout.NORTH);

        liveParameters.setName("LIVE PARAMETERS");
        liveParameters.setFillsViewportHeight(true);
        liveParameters.setRowSelectionAllowed(true);
        liveParameters.setSelectionMode(
                javax.swing.ListSelectionModel.SINGLE_SELECTION);
        liveParameters.setShowVerticalLines(false);
        liveParameters.setRowHeight(Math.max(24, liveParameters.getRowHeight()));
        liveParameters.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        liveParameters.getTableHeader().setReorderingAllowed(false);
        TableColumnModel columns = liveParameters.getColumnModel();
        configureColumn(columns, 0, 75, 100);
        configureColumn(columns, 1, 48, 60);
        configureColumn(columns, 2, 40, 48);

        DefaultTableCellRenderer valueRenderer = new DefaultTableCellRenderer();
        valueRenderer.setHorizontalAlignment(JLabel.RIGHT);
        columns.getColumn(1).setCellRenderer(valueRenderer);
        DefaultTableCellRenderer unitRenderer = new DefaultTableCellRenderer();
        unitRenderer.setHorizontalAlignment(JLabel.CENTER);
        columns.getColumn(2).setCellRenderer(unitRenderer);

        final TableRowSorter<DefaultTableModel> sorter =
                new TableRowSorter<DefaultTableModel>(liveDataModel);
        liveParameters.setRowSorter(sorter);
        liveParameters.getSelectionModel().addListSelectionListener(
                event -> updateLiveParameterAction());
        liveParameters.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) openSelectedLiveParameter();
            }
        });
        liveParameters.getInputMap().put(
                javax.swing.KeyStroke.getKeyStroke("ENTER"),
                "openLiveParameter");
        liveParameters.getActionMap().put("openLiveParameter",
                new javax.swing.AbstractAction() {
                    private static final long serialVersionUID = 1L;
                    public void actionPerformed(java.awt.event.ActionEvent event) {
                        openSelectedLiveParameter();
                    }
                });
        final JTextField filter = new ModernSearchField(
                "Search parameters...");
        filter.setName("LIVE PARAMETER FILTER");
        filter.getAccessibleContext().setAccessibleName(
                "Search live parameters");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { update(); }
            public void removeUpdate(DocumentEvent event) { update(); }
            public void changedUpdate(DocumentEvent event) { update(); }

            private void update() {
                String query = filter.getText().trim();
                sorter.setRowFilter(query.isEmpty() ? null
                        : RowFilter.regexFilter("(?i)" +
                                java.util.regex.Pattern.quote(query), 0));
            }
        });

        JPanel parameterArea = new JPanel(new BorderLayout(0, 6));
        parameterArea.setMinimumSize(new Dimension(180, 120));
        JLabel parameterHeading = new JLabel("PARAMETERS");
        parameterHeading.setFont(parameterHeading.getFont().deriveFont(Font.BOLD));
        JPanel filterHeader = new JPanel(new BorderLayout(0, 5));
        filterHeader.add(parameterHeading, BorderLayout.NORTH);
        filterHeader.add(filter, BorderLayout.CENTER);
        parameterArea.add(filterHeader, BorderLayout.NORTH);
        JScrollPane parameterScroll = new JScrollPane(liveParameters,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        parameterScroll.setBorder(BorderFactory.createEmptyBorder());
        parameterArea.add(parameterScroll, BorderLayout.CENTER);
        openLiveParameter.setName("OPEN LIVE PARAMETER IN LOGGER");
        openLiveParameter.setEnabled(false);
        openLiveParameter.setToolTipText(
                "Open Logger and reveal the selected parameter");
        openLiveParameter.addActionListener(event -> openSelectedLiveParameter());
        JPanel parameterActions = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 0, 0));
        parameterActions.add(openLiveParameter);
        parameterArea.add(parameterActions, BorderLayout.SOUTH);

        liveDataEmpty.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        liveDataEmpty.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        JPanel emptyValues = new JPanel(new BorderLayout());
        emptyValues.add(liveDataEmpty, BorderLayout.CENTER);
        liveValueGrid.setName("LIVE VALUE CARDS");
        liveValueGrid.setMinimumSize(new Dimension(190, 110));
        liveValueGrid.setPreferredSize(new Dimension(230, 170));
        liveValueContent.setName("LIVE VALUE CONTENT");
        liveValueContent.add(emptyValues, "empty");
        liveValueContent.add(liveValueGrid, "values");
        JPanel liveValues = new JPanel(new BorderLayout(0, 6));
        liveValues.setMinimumSize(new Dimension(180, 100));
        JLabel liveHeading = new JLabel("LIVE VALUES");
        liveHeading.setFont(liveHeading.getFont().deriveFont(Font.BOLD));
        configureLiveValuePager();
        JPanel liveValueHeader = new JPanel(new BorderLayout(6, 0));
        liveValueHeader.add(liveHeading, BorderLayout.WEST);
        JPanel pager = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 3, 0));
        pager.setName("LIVE VALUE PAGER");
        pager.add(previousLiveValues);
        pager.add(liveValuePageLabel);
        pager.add(nextLiveValues);
        liveValueHeader.add(pager, BorderLayout.EAST);
        liveValues.add(liveValueHeader, BorderLayout.NORTH);
        liveValues.add(liveValueContent, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                liveValues, parameterArea);
        split.setName("LIVE DATA RAIL SPLIT");
        split.setResizeWeight(0.38);
        split.setContinuousLayout(true);
        split.setDividerSize(5);
        split.setBorder(BorderFactory.createEmptyBorder());
        panel.add(split, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.38));
        refreshLiveDataPresentation();
        return panel;
    }

    void showLoggerState(LoggerSessionState state) {
        LoggerSessionState current = state == null
                ? LoggerSessionState.STOPPED : state;
        liveSessionState = current;
        liveDataStatus.setText("● " + current.getDisplayName());
        liveDataStatus.setForeground(UiThemeService.getInstance().color(
                current.isLive() ? ThemeToken.SUCCESS : ThemeToken.SECONDARY_TEXT));
        refreshLiveDataPresentation();
    }

    void showLiveSample(LiveDataSample sample) {
        if (sample == null) return;
        liveLatest.put(sample.getParameterId(), sample);
        CompactLiveValueTile tile = liveValueTiles.get(sample.getParameterId());
        boolean newParameter = tile == null;
        if (tile == null) {
            tile = new CompactLiveValueTile();
            liveValueTiles.put(sample.getParameterId(), tile);
        }
        tile.addSample(sample);
        Integer row = liveRows.get(sample.getParameterId());
        if (row == null) {
            row = liveDataModel.getRowCount();
            liveRows.put(sample.getParameterId(), row);
            liveRowIds.add(sample.getParameterId());
            liveDataModel.addRow(new Object[] {sample.getName(),
                    sample.getDisplayValue(), sample.getUnits()});
        } else {
            liveDataModel.setValueAt(sample.getName(), row, 0);
            liveDataModel.setValueAt(sample.getDisplayValue(), row, 1);
            liveDataModel.setValueAt(sample.getUnits(), row, 2);
        }
        if (newParameter) {
            renderLiveValueTiles();
            refreshLiveDataPresentation();
        }
    }

    void removeLiveParameter(String parameterId) {
        Integer row = liveRows.remove(parameterId);
        if (row == null) return;
        liveLatest.remove(parameterId);
        liveValueTiles.remove(parameterId);
        liveDataModel.removeRow(row);
        liveRowIds.remove(row.intValue());
        liveRows.clear();
        for (int index = 0; index < liveRowIds.size(); index++) {
            liveRows.put(liveRowIds.get(index), index);
        }
        renderLiveValueTiles();
        refreshLiveDataPresentation();
    }

    private void renderLiveValueTiles() {
        liveValueGrid.removeAll();
        int pageCount = liveValuePageCount();
        liveValuePage = Math.max(0, Math.min(liveValuePage, pageCount - 1));
        int first = liveValuePage * LIVE_VALUE_PAGE_SIZE;
        int index = 0;
        int shown = 0;
        for (String parameterId : liveLatest.keySet()) {
            if (index++ < first) continue;
            CompactLiveValueTile tile = liveValueTiles.get(parameterId);
            if (tile == null) continue;
            liveValueGrid.add(tile);
            if (++shown == LIVE_VALUE_PAGE_SIZE) break;
        }
        liveValuePageLabel.setText((liveValuePage + 1) + " / " + pageCount);
        boolean multiplePages = pageCount > 1;
        previousLiveValues.setVisible(multiplePages);
        nextLiveValues.setVisible(multiplePages);
        liveValuePageLabel.setVisible(multiplePages);
        previousLiveValues.setEnabled(liveValuePage > 0);
        nextLiveValues.setEnabled(liveValuePage + 1 < pageCount);
        liveValueGrid.revalidate();
        liveValueGrid.repaint();
    }

    private void configureLiveValuePager() {
        previousLiveValues.setName("PREVIOUS LIVE VALUE PAGE");
        nextLiveValues.setName("NEXT LIVE VALUE PAGE");
        liveValuePageLabel.setName("LIVE VALUE PAGE");
        previousLiveValues.setToolTipText("Show previous live-value cards");
        nextLiveValues.setToolTipText("Show next live-value cards");
        previousLiveValues.getAccessibleContext().setAccessibleName(
                "Previous live-value page");
        nextLiveValues.getAccessibleContext().setAccessibleName(
                "Next live-value page");
        previousLiveValues.addActionListener(event -> {
            if (liveValuePage > 0) {
                liveValuePage--;
                renderLiveValueTiles();
            }
        });
        nextLiveValues.addActionListener(event -> {
            if (liveValuePage + 1 < liveValuePageCount()) {
                liveValuePage++;
                renderLiveValueTiles();
            }
        });
        renderLiveValueTiles();
    }

    private int liveValuePageCount() {
        return Math.max(1, (liveLatest.size() + LIVE_VALUE_PAGE_SIZE - 1)
                / LIVE_VALUE_PAGE_SIZE);
    }

    private void refreshLiveDataPresentation() {
        int count = liveDataModel.getRowCount();
        liveDataSummary.setText(count == 0 ? "No parameters"
                : count + (count == 1 ? " parameter" : " parameters"));
        CardLayout cards = (CardLayout) liveValueContent.getLayout();
        cards.show(liveValueContent, count == 0 ? "empty" : "values");
        if (count == 0) {
            liveDataEmpty.setText(emptyLiveDataMessage());
        }
        updateLiveParameterAction();
    }

    private String emptyLiveDataMessage() {
        if (liveSessionState == LoggerSessionState.CONNECTING) {
            return "<html><div style='text-align:center'><b>Connecting to ECU</b>"
                    + "<br>Live values will appear here.</div></html>";
        }
        if (liveSessionState.isLive()) {
            return "<html><div style='text-align:center'><b>Logger connected</b>"
                    + "<br>Select parameters in Logger to show live values."
                    + "</div></html>";
        }
        return "<html><div style='text-align:center'><b>Logger is offline</b>"
                + "<br>Open Logger to connect and select parameters."
                + "</div></html>";
    }

    private void updateLiveParameterAction() {
        openLiveParameter.setEnabled(focusLiveParameterAction != null
                && selectedLiveParameterId() != null);
    }

    private void openSelectedLiveParameter() {
        String parameterId = selectedLiveParameterId();
        if (parameterId != null && focusLiveParameterAction != null) {
            focusLiveParameterAction.accept(parameterId);
        }
    }

    private String selectedLiveParameterId() {
        int viewRow = liveParameters.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = liveParameters.convertRowIndexToModel(viewRow);
        return modelRow >= 0 && modelRow < liveRowIds.size()
                ? liveRowIds.get(modelRow) : null;
    }

    private static void onEventThread(Runnable update) {
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    private static void configureColumn(TableColumnModel columns, int index,
            int minimum, int preferred) {
        columns.getColumn(index).setMinWidth(minimum);
        columns.getColumn(index).setPreferredWidth(preferred);
        columns.getColumn(index).setWidth(preferred);
        columns.getColumn(index).setResizable(true);
    }

    private static JPanel section(String title, JPanel content) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 8, 0),
                BorderFactory.createTitledBorder(title)));
        section.setName(title);
        section.add(content, BorderLayout.CENTER);
        section.setAlignmentX(LEFT_ALIGNMENT);
        return section;
    }

    private static JPanel detailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 6, 6, 6));
        return panel;
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        return c;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row,
            String label, JLabel value) {
        c.gridy = row; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        JLabel key = new JLabel(label);
        key.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        panel.add(key, c);
        c.gridx = 1; c.weightx = 1;
        value.setName(label);
        panel.add(value, c);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("—");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setMinimumSize(new java.awt.Dimension(0,
                label.getPreferredSize().height));
        return label;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        return (bytes / 1024) + " KB";
    }

    private static String tableDimensions(Table table) {
        if (table instanceof Table3D) {
            Table3D table3d = (Table3D) table;
            return table3d.getSizeX() + " × " + table3d.getSizeY();
        }
        return table.getDataSize() + " values";
    }

    private static String tableScale(Table table) {
        Scale current = table.getCurrentScale();
        if (current == null) return "Raw";
        String category = safe(current.getCategory(), "Default");
        String unit = safe(current.getUnit(), "Raw");
        return "0x".equals(unit) ? category : category + "  •  " + unit;
    }

    private static String tableRange(Table table) {
        if (table.getCurrentScale() == null) return "—";
        double first = table.getMinReal();
        double second = table.getMaxReal();
        String pattern = safe(table.getCurrentScale().getFormat(), "0.###");
        try {
            DecimalFormat format = new DecimalFormat(pattern);
            return format.format(Math.min(first, second)) + " – "
                    + format.format(Math.max(first, second));
        } catch (IllegalArgumentException ignored) {
            DecimalFormat fallback = new DecimalFormat("0.###");
            return fallback.format(Math.min(first, second)) + " – "
                    + fallback.format(Math.max(first, second));
        }
    }
}
