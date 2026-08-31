/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.api.LoggerLiveDataListener;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.ui.swing.ModernSearchField;

/** Integrated, UI-independent consumer of the existing logger data bus. */
public final class LiveDataWorkspacePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String EMPTY_PARAMETERS = "empty";
    private static final String PARAMETER_TABLE = "table";
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS");

    private final JLabel sessionStatus = new JLabel("● ECU OFFLINE");
    private final JLabel parameterSummary = new JLabel("No parameters selected");
    private final JLabel traceSummary = new JLabel(
            "Showing up to 5 traces • select rows to focus");
    private final JLabel emptyState = new JLabel(
            "<html><div style='text-align:center'><b>No live parameters</b><br>"
                    + "Configure Logger to connect and select parameters.</div></html>",
            JLabel.CENTER);
    private final JTextField filter = new ModernSearchField(
            "Search live parameters...");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Parameter", "Value", "Units", "Updated"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable parameters = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter =
            new TableRowSorter<DefaultTableModel>(model);
    private final LiveTracePlot plot = new LiveTracePlot();
    private final JButton findInLogger = new JButton("Find in Logger",
            ModernIconFactory.icon(Action.SEARCH));
    private final CardLayout parameterCards = new CardLayout();
    private final JPanel parameterContent = new JPanel(parameterCards);
    private final Map<String, Integer> rows =
            new LinkedHashMap<String, Integer>();
    private final List<String> rowIds = new ArrayList<String>();
    private final LoggerLiveDataListener liveDataListener =
            new LoggerLiveDataListener() {
        public void sessionStateChanged(final LoggerSessionState state) {
            onEventThread(() -> updateSessionState(state));
        }
        public void sampleUpdated(final LiveDataSample sample) {
            onEventThread(() -> updateSample(sample));
        }
        public void parameterRemoved(final String parameterId) {
            onEventThread(() -> removeSample(parameterId));
        }
    };
    private boolean liveDataAttached;
    private LoggerSessionState sessionState = LoggerSessionState.STOPPED;

    public LiveDataWorkspacePanel(Runnable configureLoggerAction) {
        this(configureLoggerAction, null);
    }

    public LiveDataWorkspacePanel(Runnable configureLoggerAction,
            Consumer<String> focusParameterAction) {
        super(new BorderLayout(0, 10));
        setName("LIVE DATA WORKSPACE");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(buildHeader(configureLoggerAction), BorderLayout.NORTH);
        add(buildWorkspace(focusParameterAction), BorderLayout.CENTER);
        refreshSummary();
    }

    private JPanel buildHeader(final Runnable configureLoggerAction) {
        JLabel title = new JLabel("Live Data");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 3.0f));
        JLabel help = new JLabel(
                "Monitor logger parameters in the active ROM workspace.");
        help.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel copy = new JPanel(new BorderLayout(0, 3));
        copy.add(title, BorderLayout.NORTH);
        copy.add(help, BorderLayout.SOUTH);

        sessionStatus.setName("LIVE WORKSPACE SESSION STATUS");
        sessionStatus.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JButton configure = new JButton("Configure Logger",
                ModernIconFactory.icon(Action.LOGGER));
        configure.setName("CONFIGURE LOGGER");
        configure.setEnabled(configureLoggerAction != null);
        configure.setToolTipText(
                "Open logger setup to connect and choose parameters");
        if (configureLoggerAction != null) {
            configure.addActionListener(event -> configureLoggerAction.run());
        }
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.add(sessionStatus);
        actions.add(configure);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.add(copy, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JSplitPane buildWorkspace(final Consumer<String> focusParameterAction) {
        JPanel parameterPanel = new JPanel(new BorderLayout(0, 7));
        parameterPanel.setName("LIVE PARAMETER BROWSER");
        parameterPanel.setMinimumSize(new Dimension(220, 160));

        JLabel title = new JLabel("PARAMETERS");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        parameterSummary.setName("LIVE PARAMETER SUMMARY");
        parameterSummary.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel listHeading = new JPanel(new BorderLayout());
        listHeading.add(title, BorderLayout.WEST);
        listHeading.add(parameterSummary, BorderLayout.EAST);
        parameterPanel.add(listHeading, BorderLayout.NORTH);

        filter.setName("LIVE WORKSPACE FILTER");
        filter.getAccessibleContext().setAccessibleName(
                "Search live parameters");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { updateFilter(); }
            public void removeUpdate(DocumentEvent event) { updateFilter(); }
            public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });

        configureTable(focusParameterAction);
        JScrollPane tableScroll = new JScrollPane(parameters,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel browser = new JPanel(new BorderLayout(0, 7));
        browser.add(filter, BorderLayout.NORTH);
        parameterContent.setName("LIVE PARAMETER CONTENT");
        parameterContent.add(tableScroll, PARAMETER_TABLE);
        emptyState.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        emptyState.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        JPanel emptyPanel = new JPanel(new BorderLayout());
        emptyPanel.setName("LIVE PARAMETER EMPTY STATE");
        emptyPanel.add(emptyState, BorderLayout.CENTER);
        parameterContent.add(emptyPanel, EMPTY_PARAMETERS);
        browser.add(parameterContent, BorderLayout.CENTER);
        parameterPanel.add(browser, BorderLayout.CENTER);

        findInLogger.setName("FIND LIVE PARAMETER IN LOGGER");
        findInLogger.setEnabled(false);
        findInLogger.setToolTipText(
                "Open Logger and reveal the selected data item");
        if (focusParameterAction != null) {
            findInLogger.addActionListener(event -> {
                String parameterId = firstSelectedParameterId();
                if (parameterId != null) focusParameterAction.accept(parameterId);
            });
        }
        JPanel parameterActions = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 0, 0));
        parameterActions.add(findInLogger);
        parameterPanel.add(parameterActions, BorderLayout.SOUTH);

        JPanel graphPanel = new JPanel(new BorderLayout(0, 7));
        graphPanel.setName("LIVE TRACE WORKSPACE");
        graphPanel.setMinimumSize(new Dimension(240, 160));
        JLabel graphTitle = new JLabel("LIVE TRACE");
        graphTitle.setFont(graphTitle.getFont().deriveFont(Font.BOLD));
        traceSummary.setName("LIVE TRACE SUMMARY");
        traceSummary.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel graphHeading = new JPanel(new BorderLayout());
        graphHeading.add(graphTitle, BorderLayout.WEST);
        graphHeading.add(traceSummary, BorderLayout.EAST);
        graphPanel.add(graphHeading, BorderLayout.NORTH);
        plot.setMinimumSize(new Dimension(220, 150));
        plot.setMaximumSeries(5);
        graphPanel.add(plot, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                parameterPanel, graphPanel);
        split.setName("LIVE DATA SPLIT");
        split.setResizeWeight(0.38);
        split.setDividerLocation(0.38);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private void configureTable(final Consumer<String> focusParameterAction) {
        parameters.setName("LIVE WORKSPACE PARAMETERS");
        parameters.setFillsViewportHeight(true);
        parameters.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        parameters.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        parameters.setRowSorter(sorter);
        parameters.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) refreshTraceSelection();
        });
        parameters.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2
                        && focusParameterAction != null
                        && parameters.rowAtPoint(event.getPoint()) >= 0) {
                    String parameterId = firstSelectedParameterId();
                    if (parameterId != null) {
                        focusParameterAction.accept(parameterId);
                    }
                }
            }
        });
        parameters.setShowVerticalLines(false);
        parameters.getTableHeader().setReorderingAllowed(false);
        TableColumnModel columns = parameters.getColumnModel();
        configureColumn(columns, 0, 120, 170);
        configureColumn(columns, 1, 60, 78);
        configureColumn(columns, 2, 48, 58);
        configureColumn(columns, 3, 75, 88);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(JLabel.RIGHT);
        columns.getColumn(1).setCellRenderer(right);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        columns.getColumn(2).setCellRenderer(center);
        columns.getColumn(3).setCellRenderer(center);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!liveDataAttached) {
            liveDataAttached = true;
            LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance();
            bus.addListener(liveDataListener);
            plot.setSeries(bus.getRecentSamples());
        }
    }

    @Override
    public void removeNotify() {
        if (liveDataAttached) {
            LoggerLiveDataBus.getInstance().removeListener(liveDataListener);
            liveDataAttached = false;
        }
        super.removeNotify();
    }

    void updateSessionState(LoggerSessionState state) {
        sessionState = state == null ? LoggerSessionState.STOPPED : state;
        refreshSummary();
    }

    void updateSample(LiveDataSample sample) {
        if (sample == null) return;
        Integer row = rows.get(sample.getParameterId());
        Object[] values = {sample.getName(), sample.getDisplayValue(),
                sample.getUnits(), formatTime(sample.getTimestampMillis())};
        if (row == null) {
            row = model.getRowCount();
            rows.put(sample.getParameterId(), row);
            rowIds.add(sample.getParameterId());
            model.addRow(values);
        } else {
            for (int column = 0; column < values.length; column++) {
                model.setValueAt(values[column], row, column);
            }
        }
        plot.appendSample(sample);
        refreshSummary();
    }

    void removeSample(String parameterId) {
        Integer row = rows.remove(parameterId);
        if (row == null) return;
        model.removeRow(row);
        rowIds.remove(row.intValue());
        rows.clear();
        for (int index = 0; index < rowIds.size(); index++) {
            rows.put(rowIds.get(index), index);
        }
        plot.removeSeries(parameterId);
        refreshSummary();
    }

    private void refreshSummary() {
        int count = model.getRowCount();
        sessionStatus.setText("● " + sessionState.getDisplayName());
        sessionStatus.setForeground(UiThemeService.getInstance().color(
                sessionState.isLive() ? ThemeToken.SUCCESS
                        : ThemeToken.SECONDARY_TEXT));
        parameterSummary.setText(count == 0 ? "No parameters selected"
                : count + (count == 1 ? " parameter" : " parameters"));
        emptyState.setVisible(count == 0);
        parameterCards.show(parameterContent,
                count == 0 ? EMPTY_PARAMETERS : PARAMETER_TABLE);
        plot.setMessage(sessionState.isLive()
                ? "Select parameters in Logger to begin"
                : "No live or recorded samples");
    }

    private void updateFilter() {
        String query = filter.getText().trim();
        sorter.setRowFilter(query.isEmpty() ? null
                : RowFilter.regexFilter("(?i)"
                        + java.util.regex.Pattern.quote(query), 0));
    }

    private void refreshTraceSelection() {
        Set<String> selected = new LinkedHashSet<String>();
        for (int viewRow : parameters.getSelectedRows()) {
            int modelRow = parameters.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < rowIds.size()) {
                selected.add(rowIds.get(modelRow));
            }
        }
        plot.setVisibleSeries(selected);
        findInLogger.setEnabled(selected.size() == 1
                && findInLogger.getActionListeners().length > 0);
        traceSummary.setText(selected.isEmpty()
                ? "Showing up to 5 traces • select rows to focus"
                : selected.size() + (selected.size() == 1
                        ? " selected trace" : " selected traces"));
    }

    private String firstSelectedParameterId() {
        int viewRow = parameters.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = parameters.convertRowIndexToModel(viewRow);
        return modelRow >= 0 && modelRow < rowIds.size()
                ? rowIds.get(modelRow) : null;
    }

    private static void configureColumn(TableColumnModel columns, int index,
            int minimum, int preferred) {
        columns.getColumn(index).setMinWidth(minimum);
        columns.getColumn(index).setPreferredWidth(preferred);
        columns.getColumn(index).setMaxWidth(Integer.MAX_VALUE);
        columns.getColumn(index).setResizable(true);
    }

    private static String formatTime(long millis) {
        synchronized (TIME_FORMAT) {
            return TIME_FORMAT.format(new Date(millis));
        }
    }

    private static void onEventThread(Runnable update) {
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }
}
