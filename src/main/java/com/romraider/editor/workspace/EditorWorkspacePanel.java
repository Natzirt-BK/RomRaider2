/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import javax.swing.tree.DefaultMutableTreeNode;

import com.romraider.maps.Rom;
import com.romraider.editor.ecu.spi.EditorNavigationWorkspace;
import com.romraider.swing.RomFilterPanel;
import com.romraider.swing.RomTree;
import com.romraider.swing.TableTreeNode;
import com.romraider.swing.SwingRomTreeNode;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Visible editor navigation surface backed by persistent workspace services. */
public final class EditorWorkspacePanel extends JPanel
        implements EditorNavigationWorkspace {
    public interface LocationOpener {
        void open(TableLocation location);
    }

    private static final long serialVersionUID = 1L;
    private final EditorWorkspaceService service = EditorWorkspaceService.getInstance();
    private final DefaultMutableTreeNode imageRoot;
    private final RomTree imageList;
    private final LocationOpener opener;
    private final RomFilterPanel searchPanel;
    private final DefaultListModel<TableLocation> favoritesModel =
            new DefaultListModel<TableLocation>();
    private final DefaultListModel<TableLocation> recentModel =
            new DefaultListModel<TableLocation>();
    private final DefaultListModel<TableLocation> changedModel =
            new DefaultListModel<TableLocation>();
    private final Map<TableLocation, Integer> changedCounts =
            new LinkedHashMap<TableLocation, Integer>();
    private final JList<TableLocation> favorites =
            new PlaceholderList<TableLocation>(favoritesModel,
                    "No favorite tables yet");
    private final JList<TableLocation> recent =
            new PlaceholderList<TableLocation>(recentModel,
                    "No recent tables yet");
    private final JList<TableLocation> changed =
            new PlaceholderList<TableLocation>(changedModel,
                    "No changed maps");
    private final JButton favoriteButton = new JButton(
            ModernIconFactory.icon(Action.FAVORITE));
    private final JButton backButton = new JButton(
            ModernIconFactory.icon(Action.BACK));
    private final JButton forwardButton = new JButton(
            ModernIconFactory.icon(Action.FORWARD));
    private final JLabel favoritesLabel = sectionLabel("FAVORITES");
    private final JLabel recentLabel = sectionLabel("RECENT TABLES");
    private final JLabel changedLabel = sectionLabel("CHANGED MAPS");
    private final JPanel changedSection;

    public EditorWorkspacePanel(javax.swing.tree.DefaultMutableTreeNode imageRoot,
            RomTree imageList, JScrollPane tableBrowser, LocationOpener opener) {
        super(new BorderLayout(0, 8));
        this.imageRoot = imageRoot;
        this.imageList = imageList;
        this.opener = opener;
        this.searchPanel = new RomFilterPanel(imageRoot, imageList);

        setBackground(UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND));
        JPanel browserHeader = new JPanel(new BorderLayout(0, 7));
        browserHeader.setBackground(UiThemeService.getInstance().color(
                ThemeToken.SURFACE));
        browserHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        UiThemeService.getInstance().color(
                                ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JPanel browserTitle = new JPanel(new BorderLayout(6, 0));
        browserTitle.setOpaque(false);
        browserTitle.add(sectionLabel("CALIBRATIONS"), BorderLayout.WEST);
        JPanel navigationActions = new JPanel(new GridLayout(1, 2, 3, 0));
        navigationActions.setOpaque(false);
        compactHistoryButton(backButton, "Previous table (Alt+Left)");
        compactHistoryButton(forwardButton, "Next table (Alt+Right)");
        navigationActions.add(backButton);
        navigationActions.add(forwardButton);
        browserTitle.add(navigationActions, BorderLayout.EAST);
        browserHeader.add(browserTitle, BorderLayout.NORTH);
        browserHeader.add(searchPanel, BorderLayout.CENTER);
        add(browserHeader, BorderLayout.NORTH);

        configureList(favorites, true);
        configureList(recent, false);
        configureList(changed, false);
        changed.setCellRenderer(new ChangedTableRenderer());

        changedSection = section(changedLabel, changed);
        JPanel saved = savedSections(section(favoritesHeader(), favorites),
                changedSection, section(recentLabel, recent));

        JSplitPane navigation = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tableBrowser, saved);
        navigation.setBorder(BorderFactory.createEmptyBorder());
        navigation.setBackground(UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND));
        navigation.setDividerSize(5);
        navigation.setResizeWeight(1.0);
        navigation.setContinuousLayout(true);
        navigation.setDividerLocation(0.60);
        add(navigation, BorderLayout.CENTER);

        favoriteButton.setToolTipText("Add or remove the selected table from Favorites");
        favoriteButton.setName("TOGGLE FAVORITE TABLE");
        favoriteButton.setText(null);
        favoriteButton.setFocusable(false);
        favoriteButton.setFocusPainted(false);
        favoriteButton.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        favoriteButton.getAccessibleContext().setAccessibleName("Toggle favorite table");
        backButton.getAccessibleContext().setAccessibleName("Previous table");
        forwardButton.getAccessibleContext().setAccessibleName("Next table");
        favoriteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) { toggleSelectedFavorite(); }
        });
        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) { open(service.navigation().back()); }
        });
        forwardButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) { open(service.navigation().forward()); }
        });
        imageList.addTreeSelectionListener(event -> refresh());
        configureFavoriteMenu();
        refresh();
    }

    private JPanel favoritesHeader() {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.add(favoritesLabel, BorderLayout.WEST);
        header.add(favoriteButton, BorderLayout.EAST);
        return header;
    }

    private static JPanel section(Component header, JList<TableLocation> list) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        UiThemeService.getInstance().color(
                                ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(7, 10, 4, 10)));
        panel.add(header, BorderLayout.NORTH);
        JScrollPane scroller = new JScrollPane(list,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createLineBorder(
                UiThemeService.getInstance().color(
                        ThemeToken.RAISED_SURFACE)));
        panel.add(scroller, BorderLayout.CENTER);
        return panel;
    }

    private static void addSavedSection(JPanel parent, JPanel section,
            int row, double weight) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.weighty = weight;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new java.awt.Insets(row == 0 ? 0 : 3, 0, 0, 0);
        parent.add(section, constraints);
    }

    static JPanel savedSections(JPanel favoritesSection, JPanel changedSection,
            JPanel recentSection) {
        JPanel saved = new JPanel(new GridBagLayout());
        saved.setName("SAVED TABLE SECTIONS");
        addSavedSection(saved, favoritesSection, 0, 0.34);
        addSavedSection(saved, changedSection, 1, 0.24);
        addSavedSection(saved, recentSection, 2, 0.42);
        saved.setMinimumSize(new Dimension(180, 240));
        saved.setPreferredSize(new Dimension(240, 300));
        saved.setBackground(UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND));
        return saved;
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setName(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD,
                Math.max(10.0f, label.getFont().getSize2D() - 1.0f)));
        label.setForeground(UiThemeService.getInstance().color(
                ThemeToken.ACCENT));
        return label;
    }

    public void focusSearch() {
        searchPanel.focusSearch();
    }

    public void goBack() {
        open(service.navigation().back());
    }

    public void goForward() {
        open(service.navigation().forward());
    }

    public javax.swing.JComponent getComponent() { return this; }

    public void close() { }

    public void refresh() {
        replace(favoritesModel, service.preferences().getFavorites());
        replace(recentModel, service.preferences().getRecent());
        favoritesLabel.setText("FAVORITES  " + favoritesModel.getSize());
        recentLabel.setText("RECENT TABLES  " + recentModel.getSize());
        refreshChangedMaps();
        Object selected = imageList.getLastSelectedPathComponent();
        boolean tableSelected = selected instanceof TableTreeNode;
        favoriteButton.setEnabled(tableSelected);
        favoriteButton.setToolTipText(tableSelected
                ? "Add the selected calibration table to Favorites"
                : "Select a calibration table above to add it to Favorites");
        if (tableSelected) {
            TableTreeNode node = (TableTreeNode) selected;
            Rom rom = RomTree.getRomNode(node);
            if (rom != null && service.isFavorite(rom, node.getTable())) {
                favoriteButton.setToolTipText(
                        "Remove the selected calibration table from Favorites");
            }
        }
        backButton.setEnabled(service.navigation().canGoBack());
        forwardButton.setEnabled(service.navigation().canGoForward());
    }

    public void refreshChangedMaps() {
        changedCounts.clear();
        changedCounts.putAll(service.changedTables(openRoms()));
        replace(changedModel, new ArrayList<TableLocation>(
                changedCounts.keySet()));
        changedLabel.setText("CHANGED MAPS  " + changedModel.getSize());
        changedSection.setVisible(!changedModel.isEmpty());
        revalidate();
        repaint();
    }

    private List<Rom> openRoms() {
        List<Rom> roms = new ArrayList<Rom>();
        for (int index = 0; index < imageRoot.getChildCount(); index++) {
            Object child = imageRoot.getChildAt(index);
            if (child instanceof SwingRomTreeNode) {
                roms.add(((SwingRomTreeNode) child).getRom());
            }
        }
        return roms;
    }

    private static void compactHistoryButton(JButton button, String tooltip) {
        button.setText(null);
        button.setFocusable(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        button.setToolTipText(tooltip);
    }

    private void toggleSelectedFavorite() {
        Object selected = imageList.getLastSelectedPathComponent();
        if (!(selected instanceof TableTreeNode)) return;
        TableTreeNode node = (TableTreeNode) selected;
        Rom rom = RomTree.getRomNode(node);
        if (rom == null) return;
        service.toggleFavorite(rom, node.getTable());
        refresh();
    }

    private void configureFavoriteMenu() {
        imageList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showFavoriteMenu(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showFavoriteMenu(event);
            }
        });
    }

    private void showFavoriteMenu(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        TreePath path = imageList.getPathForLocation(event.getX(), event.getY());
        if (path == null || !(path.getLastPathComponent() instanceof TableTreeNode)) {
            return;
        }
        imageList.setSelectionPath(path);
        TableTreeNode node = (TableTreeNode) path.getLastPathComponent();
        Rom rom = RomTree.getRomNode(node);
        if (rom == null) return;
        boolean favorite = service.isFavorite(rom, node.getTable());
        JMenuItem toggle = new JMenuItem(favorite
                ? "Remove from Favorites" : "Add to Favorites",
                ModernIconFactory.icon(Action.FAVORITE));
        toggle.addActionListener(action -> {
            service.toggleFavorite(rom, node.getTable());
            refresh();
        });
        JPopupMenu menu = new JPopupMenu();
        menu.add(toggle);
        menu.show(imageList, event.getX(), event.getY());
    }

    private void configureList(final JList<TableLocation> list,
            boolean favoriteList) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(36);
        list.setCellRenderer(new TableLocationRenderer(favoriteList));
        list.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (favoriteList) showSavedFavoriteMenu(list, event);
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (favoriteList) showSavedFavoriteMenu(list, event);
            }

            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 1
                        && SwingUtilities.isLeftMouseButton(event)) {
                    open(locationAt(list, event.getPoint()));
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    open(list.getSelectedValue());
                } else if (favoriteList && (event.getKeyCode() == KeyEvent.VK_DELETE
                        || event.getKeyCode() == KeyEvent.VK_BACK_SPACE)) {
                    removeFavorite(list.getSelectedValue());
                    event.consume();
                }
            }
        });
        if (favoriteList) {
            list.setToolTipText(
                    "Single-click to open • right-click or Delete to remove");
        }
    }

    private void showSavedFavoriteMenu(JList<TableLocation> list,
            MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        TableLocation location = locationAt(list, event.getPoint());
        if (location == null) return;
        JMenuItem remove = new JMenuItem("Remove from Favorites",
                ModernIconFactory.icon(Action.FAVORITE));
        remove.addActionListener(action -> removeFavorite(location));
        JPopupMenu menu = new JPopupMenu();
        menu.add(remove);
        menu.show(list, event.getX(), event.getY());
    }

    private void removeFavorite(TableLocation location) {
        if (location == null) return;
        service.removeFavorite(location);
        refresh();
    }

    private void open(TableLocation location) {
        if (location != null) opener.open(location);
    }

    static TableLocation locationAt(JList<TableLocation> list, Point point) {
        int index = list.locationToIndex(point);
        if (index < 0) return null;
        Rectangle bounds = list.getCellBounds(index, index);
        if (bounds == null || !bounds.contains(point)) return null;
        list.setSelectedIndex(index);
        return list.getModel().getElementAt(index);
    }

    private static void replace(DefaultListModel<TableLocation> model,
            java.util.List<TableLocation> values) {
        model.clear();
        for (TableLocation value : values) model.addElement(value);
    }

    static final class TableLocationRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private final boolean favoriteList;

        TableLocationRenderer(boolean favoriteList) {
            this.favoriteList = favoriteList;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean hasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list,
                    value, index, selected, hasFocus);
            if (value instanceof TableLocation) {
                TableLocation location = (TableLocation) value;
                label.setText(location.getTableName());
                label.setToolTipText("ROM: " + location.getRomId());
                label.setIcon(ModernIconFactory.icon(favoriteList
                        ? Action.FAVORITE : Action.DEFINITION));
                if (!selected) {
                    label.setBackground(UiThemeService.getInstance().color(
                            index % 2 == 0 ? ThemeToken.SURFACE
                                    : ThemeToken.BACKGROUND));
                    label.setForeground(UiThemeService.getInstance().color(
                            ThemeToken.PRIMARY_TEXT));
                }
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, selected ? 3 : 0,
                                0, 0, UiThemeService.getInstance().color(
                                        ThemeToken.ACCENT)),
                        BorderFactory.createEmptyBorder(2, 5, 2, 5)));
            } else {
                label.setToolTipText(null);
                label.setIcon(null);
            }
            return label;
        }
    }

    final class ChangedTableRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean hasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list,
                    value, index, selected, hasFocus);
            if (value instanceof TableLocation) {
                TableLocation location = (TableLocation) value;
                Integer count = changedCounts.get(location);
                int changedCells = count == null ? 0 : count.intValue();
                label.setText(location.getTableName() + "  •  "
                        + changedCells);
                label.setIcon(ModernIconFactory.icon(Action.UNDO));
                if (!selected) {
                    label.setBackground(UiThemeService.getInstance().color(
                            index % 2 == 0 ? ThemeToken.SURFACE
                                    : ThemeToken.BACKGROUND));
                    label.setForeground(UiThemeService.getInstance().color(
                            ThemeToken.PRIMARY_TEXT));
                }
                label.setToolTipText("ROM: " + location.getRomId() + " • "
                        + changedCells + (changedCells == 1
                                ? " unsaved cell" : " unsaved cells"));
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, selected ? 3 : 0,
                                0, 0, UiThemeService.getInstance().color(
                                        ThemeToken.DANGER)),
                        BorderFactory.createEmptyBorder(2, 5, 2, 5)));
            }
            return label;
        }
    }

    static final class PlaceholderList<E> extends JList<E> {
        private static final long serialVersionUID = 1L;
        private final String placeholder;

        PlaceholderList(ListModel<E> model, String placeholder) {
            super(model);
            this.placeholder = placeholder;
            setFixedCellHeight(36);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getModel().getSize() != 0) return;
            graphics.setColor(UiThemeService.getInstance().color(
                    ThemeToken.SECONDARY_TEXT));
            FontMetrics metrics = graphics.getFontMetrics();
            int x = Math.max(8, (getWidth() - metrics.stringWidth(placeholder)) / 2);
            int y = Math.max(metrics.getAscent() + 8,
                    (getHeight() + metrics.getAscent()) / 2);
            graphics.drawString(placeholder, x, y);
        }
    }
}
