/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Scrollable;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.romraider.maps.Table;
import com.romraider.maps.TableView;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.swing.TableFrame;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.TouchTargetService;
import com.romraider.ui.UiThemeService;
import com.romraider.util.SettingsManager;

/** A modern document surface that hosts calibration tables in closable tabs. */
public final class EditorTabbedWorkspace extends JPanel implements Scrollable {
    public interface Listener {
        void tableActivated(TableFrame frame);
        void closeRequested(TableFrame frame);
        default void tabsReordered(List<TableFrame> frames) { }
        default void reopenRequested(Table table) { }
        default boolean isFavorite(Table table) { return false; }
        default void toggleFavorite(Table table) { }
    }

    private static final long serialVersionUID = 1L;
    private static final String EMPTY_CARD = "empty";
    private static final String TABS_CARD = "tabs";

    private final CardLayout cards = new CardLayout();
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<TableFrame, Component> documents =
            new LinkedHashMap<TableFrame, Component>();
    private final Map<TableFrame, JLabel> modifiedIndicators =
            new LinkedHashMap<TableFrame, JLabel>();
    private final Map<String, Component> utilityDocuments =
            new LinkedHashMap<String, Component>();
    private final Deque<Table> recentlyClosedMaps = new ArrayDeque<Table>();
    private final Listener listener;
    private final Runnable openLoggerAction;
    private final Runnable focusTableSearchAction;
    private final MapVisualizationRegistry visualizationRegistry;

    public EditorTabbedWorkspace(Listener listener) {
        this(listener, null);
    }

    public EditorTabbedWorkspace(Listener listener, Runnable openLoggerAction) {
        this(listener, openLoggerAction, (Runnable) null,
                MapVisualizationRegistry.createDefault());
    }

    public EditorTabbedWorkspace(Listener listener, Runnable openLoggerAction,
            Runnable focusTableSearchAction) {
        this(listener, openLoggerAction, focusTableSearchAction,
                MapVisualizationRegistry.createDefault());
    }

    EditorTabbedWorkspace(Listener listener, Runnable openLoggerAction,
            MapVisualizationRegistry visualizationRegistry) {
        this(listener, openLoggerAction, null, visualizationRegistry);
    }

    EditorTabbedWorkspace(Listener listener, Runnable openLoggerAction,
            Runnable focusTableSearchAction,
            MapVisualizationRegistry visualizationRegistry) {
        super();
        this.listener = listener;
        this.openLoggerAction = openLoggerAction;
        this.focusTableSearchAction = focusTableSearchAction;
        this.visualizationRegistry = visualizationRegistry;
        setLayout(cards);
        add(createEmptyState(), EMPTY_CARD);

        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setBackground(UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND));
        tabs.getAccessibleContext().setAccessibleName("Open calibration tables");
        tabs.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent event) {
                TableFrame active = getActiveFrame();
                if (EditorTabbedWorkspace.this.listener != null) {
                    EditorTabbedWorkspace.this.listener.tableActivated(active);
                }
                if (active != null) active.getTableView().requestFocusInWindow();
                updateTabHeaderStyles();
            }
        });
        add(tabs, TABS_CARD);
        showCorrectCard();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect,
            int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect,
            int orientation, int direction) {
        int extent = orientation == SwingConstants.VERTICAL
                ? visibleRect.height : visibleRect.width;
        return Math.max(24, extent - 24);
    }

    /**
     * The workbench owns the outer viewport and must always follow its size.
     * Individual document surfaces own any content scrolling they require.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Custom tab headers use semantic colors rather than UI defaults.
        // Re-resolve them whenever a runtime theme switch updates the tree.
        if (tabs != null) {
            tabs.setBackground(UiThemeService.getInstance().color(
                    ThemeToken.BACKGROUND));
            updateTabHeaderStyles();
        }
    }

    public void open(TableFrame frame) {
        if (frame == null) return;
        if (documents.containsKey(frame)) {
            select(frame);
            return;
        }

        TableView tableView = frame.getTableView();
        if (tableView.getParent() != null) tableView.getParent().remove(tableView);
        JMenuBar tableMenu = frame.getTableMenuBar();
        frame.setJMenuBar(null);

        MapVisualizationProvider visualizationProvider = visualizationRegistry == null
                ? null : visualizationRegistry.findProvider(frame.getTable());
        TuningDocumentPanel document = new TuningDocumentPanel(tableMenu,
                tableView, frame.getTable(), visualizationProvider,
                openLoggerAction, focusTableSearchAction,
                listener == null ? null
                        : () -> listener.isFavorite(frame.getTable()),
                listener == null ? null
                        : () -> listener.toggleFavorite(frame.getTable()));
        TouchTargetService.apply(document,
                com.romraider.ui.RuntimeUiProfile.displayMode(
                        SettingsManager.getSettings().getDisplayMode()));
        documents.put(frame, document);

        Table table = frame.getTable();
        String title = table == null ? frame.getTitle() : table.getName();
        Icon icon = frame.getFrameIcon();
        tabs.addTab(title, icon, document, frame.getTitle());
        int index = tabs.indexOfComponent(document);
        Component header = createTabHeader(frame, title, icon);
        installTabSelectionSupport(header, document);
        installTabDragSupport(header, frame);
        installMapTabContextMenu(header, frame, title);
        TouchTargetService.apply(header,
                com.romraider.ui.RuntimeUiProfile.displayMode(
                        SettingsManager.getSettings().getDisplayMode()));
        tabs.setTabComponentAt(index, header);
        tabs.setSelectedComponent(document);
        updateTabHeaderStyles();
        refreshTabStates();
        refreshMapTabContextMenus();
        showCorrectCard();
        revalidate();
        repaint();
    }

    public void select(TableFrame frame) {
        Component document = documents.get(frame);
        if (document == null) return;
        tabs.setSelectedComponent(document);
        if (listener != null) listener.tableActivated(frame);
        frame.getTableView().requestFocusInWindow();
    }

    public void showVisualization(TableFrame frame) {
        Component document = documents.get(frame);
        if (document instanceof TuningDocumentPanel) {
            ((TuningDocumentPanel) document).showVisualization();
        }
    }

    public void close(TableFrame frame) {
        Component document = documents.remove(frame);
        if (document == null) return;
        modifiedIndicators.remove(frame);
        if (document instanceof TuningDocumentPanel) {
            ((TuningDocumentPanel) document).disposeDocument();
        }
        tabs.remove(document);
        updateTabHeaderStyles();
        refreshMapTabContextMenus();
        showCorrectCard();
        if (documents.isEmpty() && listener != null) listener.tableActivated(null);
        revalidate();
        repaint();
    }

    public void openUtility(final String id, String title, Icon icon,
            Component document) {
        if (id == null || document == null) return;
        Component existing = utilityDocuments.get(id);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return;
        }
        utilityDocuments.put(id, document);
        tabs.addTab(title, icon, document, title);
        int index = tabs.indexOfComponent(document);
        Component header = createUtilityTabHeader(id, title, icon);
        installTabSelectionSupport(header, document);
        tabs.setTabComponentAt(index, header);
        tabs.setSelectedComponent(document);
        updateTabHeaderStyles();
        showCorrectCard();
        revalidate();
        repaint();
    }

    public void closeUtility(String id) {
        Component document = utilityDocuments.remove(id);
        if (document == null) return;
        tabs.remove(document);
        updateTabHeaderStyles();
        showCorrectCard();
        revalidate();
        repaint();
    }

    /** Requests a normal owner-managed close for the selected tab. */
    public void requestCloseActiveTab() {
        Component selected = tabs.getSelectedComponent();
        if (selected == null) return;
        TableFrame frame = frameForComponent(selected);
        if (frame != null) {
            requestClose(frame);
            return;
        }
        for (Map.Entry<String, Component> entry
                : new ArrayList<Map.Entry<String, Component>>(
                        utilityDocuments.entrySet())) {
            if (entry.getValue() == selected) {
                closeUtility(entry.getKey());
                return;
            }
        }
    }

    public TableFrame getActiveFrame() {
        Component selected = tabs.getSelectedComponent();
        if (selected == null) return null;
        for (Map.Entry<TableFrame, Component> entry : documents.entrySet()) {
            if (entry.getValue() == selected) return entry.getKey();
        }
        return null;
    }

    public List<TableFrame> getOpenFrames() {
        List<TableFrame> ordered = new ArrayList<TableFrame>();
        for (int index = 0; index < tabs.getTabCount(); index++) {
            TableFrame frame = frameForComponent(tabs.getComponentAt(index));
            if (frame != null) ordered.add(frame);
        }
        return ordered;
    }

    void moveTab(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= tabs.getTabCount()
                || toIndex < 0 || toIndex >= tabs.getTabCount()
                || fromIndex == toIndex) return;

        Component selected = tabs.getSelectedComponent();
        Component document = tabs.getComponentAt(fromIndex);
        Component header = tabs.getTabComponentAt(fromIndex);
        String title = tabs.getTitleAt(fromIndex);
        Icon icon = tabs.getIconAt(fromIndex);
        String tooltip = tabs.getToolTipTextAt(fromIndex);
        boolean enabled = tabs.isEnabledAt(fromIndex);

        tabs.removeTabAt(fromIndex);
        tabs.insertTab(title, icon, document, tooltip, toIndex);
        tabs.setEnabledAt(toIndex, enabled);
        tabs.setTabComponentAt(toIndex, header);
        if (selected != null) tabs.setSelectedComponent(selected);
        updateTabHeaderStyles();
        if (listener != null) listener.tabsReordered(getOpenFrames());
    }

    int getOpenCount() {
        return documents.size();
    }

    JTabbedPane getTabsForTesting() {
        return tabs;
    }

    /** Refreshes map-level saved state without rebuilding any documents. */
    public void refreshTabStates() {
        for (Map.Entry<TableFrame, Component> entry : documents.entrySet()) {
            TableFrame frame = entry.getKey();
            Table table = frame == null ? null : frame.getTable();
            int changedCells = RomChangeSummary.countChangedCells(table);
            JLabel indicator = modifiedIndicators.get(frame);
            if (indicator != null) {
                indicator.setVisible(changedCells > 0);
                indicator.setToolTipText(changedCells > 0
                        ? changedCells + (changedCells == 1
                                ? " unsaved cell" : " unsaved cells")
                        : "No unsaved changes");
            }
            int index = tabs.indexOfComponent(entry.getValue());
            if (index >= 0) tabs.setToolTipTextAt(index,
                    mapTabTooltip(table, changedCells));
            if (entry.getValue() instanceof TuningDocumentPanel) {
                ((TuningDocumentPanel) entry.getValue()).refreshMapState();
            }
        }
        updateTabHeaderStyles();
    }

    private Component createEmptyState() {
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND));
        JPanel message = new JPanel(new BorderLayout(0, 12));
        message.setBackground(UiThemeService.getInstance().color(
                ThemeToken.SURFACE));
        message.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        UiThemeService.getInstance().color(
                                ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(32, 42, 32, 42)));

        JLabel icon = new JLabel(ModernIconFactory.icon(Action.OPEN));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        message.add(icon, BorderLayout.NORTH);

        JLabel text = new JLabel("<html><div style='text-align:center'>"
                + "<b>OPEN A CALIBRATION TABLE</b><br><br>"
                + "Choose a table from the browser and it will open here.<br>"
                + "<small>Ctrl+O opens a ROM &nbsp;•&nbsp; Ctrl+P searches tables</small>"
                + "</div></html>", SwingConstants.CENTER);
        text.setFont(text.getFont().deriveFont(Font.PLAIN,
                text.getFont().getSize2D() + 1.0f));
        text.setForeground(UiThemeService.getInstance().color(
                ThemeToken.PRIMARY_TEXT));
        message.add(text, BorderLayout.CENTER);
        center.add(message);
        return center;
    }

    private Component createTabHeader(final TableFrame frame, String title, Icon icon) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(true);
        JLabel label = new JLabel(title, icon, SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        header.add(label);

        JLabel modified = new JLabel("●");
        modified.setName("UNSAVED MAP INDICATOR");
        modified.setVisible(false);
        modified.getAccessibleContext().setAccessibleName(
                "Unsaved changes in " + title);
        modifiedIndicators.put(frame, modified);
        header.add(modified);

        JButton close = new JButton(ModernIconFactory.icon(Action.CLOSE));
        close.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setFocusable(false);
        close.setToolTipText("Close " + title);
        close.getAccessibleContext().setAccessibleName("Close " + title);
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                requestClose(frame);
            }
        });
        header.add(close);
        return header;
    }

    private void installMapTabContextMenu(Component header,
            final TableFrame frame, String title) {
        final JPopupMenu menu = new JPopupMenu();
        menu.setName("MAP TAB CONTEXT MENU");
        JMenuItem close = new JMenuItem("Close " + title,
                ModernIconFactory.icon(Action.CLOSE));
        close.setName("CLOSE MAP TAB");
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W,
                InputEvent.CTRL_DOWN_MASK));
        close.setEnabled(listener != null);
        close.addActionListener(event -> requestClose(frame));
        menu.add(close);

        JMenuItem closeOthers = new JMenuItem("Close Other Maps");
        closeOthers.setName("CLOSE OTHER MAP TABS");
        closeOthers.setEnabled(listener != null && documents.size() > 1);
        closeOthers.addActionListener(event -> requestCloseOtherMaps(frame));
        menu.add(closeOthers);

        JMenuItem closeAll = new JMenuItem("Close All Maps");
        closeAll.setName("CLOSE ALL MAP TABS");
        closeAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        closeAll.setEnabled(listener != null && !documents.isEmpty());
        closeAll.addActionListener(event -> requestCloseAllMaps());
        menu.add(closeAll);

        menu.addSeparator();
        JMenuItem reopen = new JMenuItem("Reopen Last Closed Map",
                ModernIconFactory.icon(Action.OPEN));
        reopen.setName("REOPEN MAP TAB");
        reopen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        reopen.setEnabled(listener != null && !recentlyClosedMaps.isEmpty());
        reopen.addActionListener(event -> requestReopenLastMap());
        menu.add(reopen);
        TouchTargetService.apply(menu,
                com.romraider.ui.RuntimeUiProfile.displayMode(
                        SettingsManager.getSettings().getDisplayMode()));

        if (header instanceof JComponent) {
            ((JComponent) header).putClientProperty("TAB_CONTEXT_MENU", menu);
        }
        MouseAdapter popupListener = new MouseAdapter() {
            public void mousePressed(MouseEvent event) { showPopup(event); }
            public void mouseReleased(MouseEvent event) { showPopup(event); }
            private void showPopup(MouseEvent event) {
                if (!event.isPopupTrigger()) return;
                int tabIndex = tabs.indexOfComponent(documents.get(frame));
                if (tabIndex >= 0) tabs.setSelectedIndex(tabIndex);
                menu.show(event.getComponent(), event.getX(), event.getY());
            }
        };
        header.addMouseListener(popupListener);
        if (header instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) header).getComponents()) {
                if (!(child instanceof JButton)) child.addMouseListener(popupListener);
            }
        }
    }

    private void requestClose(TableFrame frame) {
        if (listener == null || frame == null) return;
        rememberRecentlyClosed(frame.getTable());
        listener.closeRequested(frame);
    }

    private void requestCloseOtherMaps(TableFrame retained) {
        if (listener == null) return;
        for (TableFrame frame : new ArrayList<TableFrame>(getOpenFrames())) {
            if (frame != retained) requestClose(frame);
        }
    }

    public void requestCloseAllMaps() {
        if (listener == null) return;
        for (TableFrame frame : new ArrayList<TableFrame>(getOpenFrames())) {
            requestClose(frame);
        }
    }

    public void requestReopenLastMap() {
        if (listener == null || recentlyClosedMaps.isEmpty()) return;
        Table table = recentlyClosedMaps.removeLast();
        refreshMapTabContextMenus();
        listener.reopenRequested(table);
    }

    private void rememberRecentlyClosed(Table table) {
        if (table == null) return;
        recentlyClosedMaps.remove(table);
        recentlyClosedMaps.addLast(table);
        while (recentlyClosedMaps.size() > 12) recentlyClosedMaps.removeFirst();
        refreshMapTabContextMenus();
    }

    private void refreshMapTabContextMenus() {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            Component header = tabs.getTabComponentAt(index);
            if (!(header instanceof JComponent)) continue;
            Object value = ((JComponent) header).getClientProperty(
                    "TAB_CONTEXT_MENU");
            if (!(value instanceof JPopupMenu)) continue;
            for (Component component : ((JPopupMenu) value).getComponents()) {
                if (!(component instanceof JMenuItem)) continue;
                JMenuItem item = (JMenuItem) component;
                if ("CLOSE OTHER MAP TABS".equals(item.getName())) {
                    item.setEnabled(listener != null && documents.size() > 1);
                } else if ("CLOSE ALL MAP TABS".equals(item.getName())) {
                    item.setEnabled(listener != null && !documents.isEmpty());
                } else if ("REOPEN MAP TAB".equals(item.getName())) {
                    item.setEnabled(listener != null
                            && !recentlyClosedMaps.isEmpty());
                }
            }
        }
    }

    private Component createUtilityTabHeader(final String id, String title,
            Icon icon) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(true);
        JLabel label = new JLabel(title, icon, SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        header.add(label);
        JButton close = new JButton(ModernIconFactory.icon(Action.CLOSE));
        close.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setFocusable(false);
        close.setToolTipText("Close " + title);
        close.getAccessibleContext().setAccessibleName("Close " + title);
        close.addActionListener(event -> closeUtility(id));
        header.add(close);
        return header;
    }

    private void installTabDragSupport(final Component header,
            final TableFrame frame) {
        MouseAdapter drag = new MouseAdapter() {
            public void mouseDragged(MouseEvent event) {
                Component document = documents.get(frame);
                int from = document == null ? -1 : tabs.indexOfComponent(document);
                java.awt.Point point = SwingUtilities.convertPoint(
                        event.getComponent(), event.getPoint(), tabs);
                int to = tabs.indexAtLocation(point.x, point.y);
                if (from >= 0 && to >= 0 && from != to) moveTab(from, to);
            }
        };
        header.addMouseMotionListener(drag);
        if (header instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) header).getComponents()) {
                if (!(child instanceof JButton)) child.addMouseMotionListener(drag);
            }
        }
    }

    private void installTabSelectionSupport(final Component header,
            final Component document) {
        MouseAdapter selection = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = tabs.indexOfComponent(document);
                if (index >= 0) tabs.setSelectedIndex(index);
            }
        };
        header.addMouseListener(selection);
        if (header instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) header).getComponents()) {
                if (!(child instanceof JButton)) child.addMouseListener(selection);
            }
        }
    }

    private TableFrame frameForComponent(Component component) {
        for (Map.Entry<TableFrame, Component> entry : documents.entrySet()) {
            if (entry.getValue() == component) return entry.getKey();
        }
        return null;
    }

    private void updateTabHeaderStyles() {
        int selectedIndex = tabs.getSelectedIndex();
        for (int index = 0; index < tabs.getTabCount(); index++) {
            Component component = tabs.getTabComponentAt(index);
            if (!(component instanceof JPanel)) continue;
            JPanel header = (JPanel) component;
            boolean selected = index == selectedIndex;
            header.setBackground(UiThemeService.getInstance().color(selected
                    ? ThemeToken.SURFACE : ThemeToken.BACKGROUND));
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 0,
                            UiThemeService.getInstance().color(selected
                                    ? ThemeToken.ACCENT : ThemeToken.BACKGROUND)),
                    BorderFactory.createEmptyBorder(3, 4, 3, 4)));
            for (Component child : header.getComponents()) {
                ThemeToken foreground = "UNSAVED MAP INDICATOR".equals(
                        child.getName()) && child.isVisible()
                                ? ThemeToken.DANGER
                                : selected ? ThemeToken.PRIMARY_TEXT
                                        : ThemeToken.SECONDARY_TEXT;
                child.setForeground(UiThemeService.getInstance().color(
                        foreground));
            }
        }
    }

    private static String mapTabTooltip(Table table, int changedCells) {
        String name = table == null || table.getName() == null
                ? "Calibration map" : table.getName();
        StringBuilder tooltip = new StringBuilder("<html><b>")
                .append(escape(name)).append("</b>");
        if (table != null && table.getRom() != null
                && table.getRom().getFileName() != null) {
            tooltip.append("<br>ROM: ").append(escape(
                    table.getRom().getFileName()));
        }
        tooltip.append("<br>").append(changedCells == 0
                ? "No unsaved changes"
                : changedCells + (changedCells == 1
                        ? " unsaved cell" : " unsaved cells"));
        return tooltip.append("<br>Right-click for tab actions</html>")
                .toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void showCorrectCard() {
        cards.show(this, documents.isEmpty() && utilityDocuments.isEmpty()
                ? EMPTY_CARD : TABS_CARD);
    }
}
