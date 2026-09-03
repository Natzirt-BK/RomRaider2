/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.romraider.maps.Scale;
import com.romraider.maps.Table;
import com.romraider.maps.Table3D;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.EditHistoryListener;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.TableMenuBar;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Compact map identity and action surface inspired by the target workbench. */
final class MapDocumentHeader extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Table table;
    private final JButton undo = new JButton(ModernIconFactory.icon(Action.UNDO));
    private final JButton redo = new JButton(ModernIconFactory.icon(Action.REDO));
    private final JButton search = new JButton(
            ModernIconFactory.icon(Action.SEARCH));
    private final JToggleButton favorite = new JToggleButton(
            ModernIconFactory.icon(Action.FAVORITE));
    private final JToggleButton calibrationPreview = new JToggleButton(
            "Preview", ModernIconFactory.icon(Action.TABLE_2D));
    private final JLabel changeStatus = new JLabel();
    private final BooleanSupplier favoriteState;
    private final RomEditHistory history = RomEditHistory.getInstance();
    private final EditHistoryListener historyListener;

    MapDocumentHeader(Table table, JMenuBar contextMenu,
            boolean integratedVisualizationAvailable,
            Consumer<Boolean> visualizationVisibilityAction,
            Runnable focusTableSearchAction, BooleanSupplier favoriteState,
            Runnable toggleFavoriteAction) {
        super(new BorderLayout(16, 0));
        this.table = table;
        this.favoriteState = favoriteState;
        historyListener = rom -> {
            if (table != null && table.getRom() == rom) updateStateLater();
        };
        history.addListener(historyListener);
        setName("MAP DOCUMENT HEADER");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        UiThemeService.getInstance().color(
                                ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(8, 12, 8, 8)));

        JPanel identity = new JPanel(new BorderLayout(0, 3));
        identity.setName("MAP IDENTITY");
        identity.setOpaque(false);
        JLabel title = new JLabel(table == null ? "Calibration map"
                : safe(table.getName(), "Calibration map"));
        title.setName("MAP TITLE");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 1.0f));
        JLabel metadata = new JLabel(metadata(table));
        metadata.setName("MAP METADATA");
        metadata.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        metadata.setToolTipText(metadataTooltip(table));
        JPanel metadataRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        metadataRow.setOpaque(false);
        metadataRow.add(metadata);
        changeStatus.setName("MAP CHANGE STATUS");
        changeStatus.setForeground(UiThemeService.getInstance().color(
                ThemeToken.DANGER));
        metadataRow.add(changeStatus);
        identity.add(title, BorderLayout.NORTH);
        identity.add(metadataRow, BorderLayout.SOUTH);
        add(identity, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setName("MAP ACTIONS");
        actions.setOpaque(false);
        favorite.setName("TOGGLE MAP FAVORITE");
        favorite.setFocusable(false);
        favorite.setEnabled(favoriteState != null
                && toggleFavoriteAction != null && table != null);
        favorite.getAccessibleContext().setAccessibleName(
                "Toggle favorite map");
        favorite.addActionListener(event -> {
            if (toggleFavoriteAction != null) toggleFavoriteAction.run();
            updateFavoriteState();
        });
        actions.add(favorite);
        search.setName("SEARCH MAPS");
        search.setToolTipText("Focus calibration search (Ctrl+P)");
        search.getAccessibleContext().setAccessibleName("Search maps");
        search.setEnabled(focusTableSearchAction != null);
        search.addActionListener(event -> focusTableSearchAction.run());
        actions.add(search);
        undo.setName("UNDO EDIT");
        redo.setName("REDO EDIT");
        undo.getAccessibleContext().setAccessibleName("Undo last edit");
        redo.getAccessibleContext().setAccessibleName("Redo last edit");
        undo.addActionListener(event -> applyHistory(false));
        redo.addActionListener(event -> applyHistory(true));
        actions.add(undo);
        actions.add(redo);
        if (contextMenu instanceof TableMenuBar) {
            TableMenuBar menu = (TableMenuBar) contextMenu;
            final AccentToggleButton view3d = new AccentToggleButton("3D View",
                    ModernIconFactory.icon(Action.VIEW_3D));
            view3d.setName("OPEN 3D VIEW");
            view3d.setEnabled(integratedVisualizationAvailable);
            view3d.setSelected(false);
            updateVisualizationTooltip(view3d, false);
            view3d.addActionListener(event -> {
                if (visualizationVisibilityAction != null) {
                    visualizationVisibilityAction.accept(view3d.isSelected());
                }
                updateVisualizationTooltip(view3d, view3d.isSelected());
            });
            actions.add(view3d);

            JButton properties = new JButton(
                    ModernIconFactory.icon(Action.SETTINGS));
            properties.setName("MAP PROPERTIES");
            properties.setToolTipText("Map properties");
            properties.getAccessibleContext().setAccessibleName(
                    "Map properties");
            properties.addActionListener(
                    event -> menu.getTableProperties().doClick());
            actions.add(properties);
        }
        actions.add(optionsButton(contextMenu));
        add(actions, BorderLayout.EAST);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyResponsiveActionLabels();
            }
        });
        updateState();
        applyResponsiveActionLabels();
    }

    void setCalibrationPreview(boolean available,
            Consumer<Boolean> visibilityAction) {
        calibrationPreview.setName("CALIBRATION WORKSPACE TOGGLE");
        calibrationPreview.setVisible(available);
        calibrationPreview.setEnabled(available && visibilityAction != null);
        calibrationPreview.setFocusable(false);
        calibrationPreview.getAccessibleContext().setAccessibleName(
                "Toggle replacement calibration grid");
        calibrationPreview.setText("New Grid");
        calibrationPreview.setToolTipText(
                "Open the replacement calibration grid");
        calibrationPreview.addActionListener(event -> {
            boolean selected = calibrationPreview.isSelected();
            calibrationPreview.setText(selected ? "Classic Grid" : "New Grid");
            calibrationPreview.setToolTipText(selected
                    ? "Return to the classic calibration grid"
                    : "Open the replacement calibration grid");
            if (visibilityAction != null) {
                visibilityAction.accept(Boolean.valueOf(selected));
            }
        });
        JPanel actions = findActionsPanel();
        if (actions != null && calibrationPreview.getParent() != actions) {
            int insertion = Math.max(0, actions.getComponentCount() - 3);
            actions.add(calibrationPreview, insertion);
            actions.revalidate();
            actions.repaint();
        }
    }

    private JPanel findActionsPanel() {
        for (java.awt.Component component : getComponents()) {
            if (component instanceof JPanel
                    && "MAP ACTIONS".equals(component.getName())) {
                return (JPanel) component;
            }
        }
        return null;
    }

    void disposeHeader() {
        history.removeListener(historyListener);
    }

    @Override
    public void doLayout() {
        applyResponsiveActionLabels();
        super.doLayout();
    }

    private void applyHistory(boolean redoAction) {
        if (table == null || table.getRom() == null) return;
        try {
            if (redoAction) history.redo(table.getRom());
            else history.undo(table.getRom());
        } catch (UserLevelException exception) {
            com.romraider.maps.TableView.showInvalidUserLevelPopup(exception);
        }
    }

    private void updateStateLater() {
        if (SwingUtilities.isEventDispatchThread()) updateState();
        else SwingUtilities.invokeLater(new Runnable() {
            public void run() { updateState(); }
        });
    }

    void refreshState() {
        updateStateLater();
    }

    private void updateState() {
        updateHistoryState();
        updateChangeStatus();
        updateFavoriteState();
    }

    private void updateHistoryState() {
        com.romraider.maps.Rom rom = table == null ? null : table.getRom();
        boolean canUndo = rom != null && history.canUndo(rom);
        boolean canRedo = rom != null && history.canRedo(rom);
        undo.setEnabled(canUndo);
        redo.setEnabled(canRedo);
        String undoDescription = rom == null ? "" :
                history.nextUndoDescription(rom);
        String redoDescription = rom == null ? "" :
                history.nextRedoDescription(rom);
        undo.setToolTipText(canUndo ? "Undo: " + undoDescription
                + " (Ctrl+Z)" : "Nothing to undo");
        redo.setToolTipText(canRedo ? "Redo: " + redoDescription
                + " (Ctrl+Y)" : "Nothing to redo");
    }

    private void applyResponsiveActionLabels() {
        boolean showLabels = getWidth() >= 820;
        search.setText(showLabels ? "Search" : null);
        undo.setText(showLabels ? "Undo" : null);
        redo.setText(showLabels ? "Redo" : null);
    }

    private void updateChangeStatus() {
        int changedCells = com.romraider.editor.workspace.RomChangeSummary
                .countChangedCells(table);
        changeStatus.setVisible(changedCells > 0);
        changeStatus.setText("● " + changedCells + " changed");
        changeStatus.setToolTipText(changedCells > 0
                ? changedCells + (changedCells == 1
                        ? " unsaved cell in this map"
                        : " unsaved cells in this map")
                : "No unsaved changes in this map");
    }

    private void updateFavoriteState() {
        boolean selected = favoriteState != null && favoriteState.getAsBoolean();
        favorite.setSelected(selected);
        favorite.setToolTipText(selected
                ? "Remove this map from Favorites"
                : "Add this map to Favorites");
    }

    private static String metadata(Table table) {
        if (table == null) return "No map metadata";
        String dimensions;
        if (table instanceof Table3D) {
            Table3D table3d = (Table3D) table;
            dimensions = table3d.getSizeX() + " × " + table3d.getSizeY();
        } else {
            dimensions = table.getDataSize() + " values";
        }
        Scale current = table.getCurrentScale();
        String unit = current == null ? "Raw" : safe(current.getUnit(), "Raw");
        if ("0x".equals(unit)) unit = "Hex";
        String category = safe(table.getCategory(), "Other");
        return category + "   •   " + dimensions + "   •   " + unit;
    }

    private static String metadataTooltip(Table table) {
        if (table == null) return "No map metadata";
        StringBuilder text = new StringBuilder("Address 0x")
                .append(Integer.toHexString(table.getStorageAddress())
                        .toUpperCase());
        String description = table.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            text.append(" • ").append(description.trim());
        }
        return text.toString();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static void updateVisualizationTooltip(AccentToggleButton button,
            boolean visible) {
        button.setText(visible ? "Hide 3D" : "Show 3D");
        if (!button.isEnabled()) {
            button.setText("3D View");
            button.setToolTipText(
                    "Open a 3D calibration table to use the surface view");
        } else {
            button.setToolTipText(visible
                    ? "Hide the integrated interactive map surface"
                    : "Show the integrated interactive map surface");
        }
        button.refreshStyle();
    }

    /** A primary action treatment that remains readable in every theme. */
    private static final class AccentToggleButton extends JToggleButton {
        private static final long serialVersionUID = 1L;

        private AccentToggleButton(String text, javax.swing.Icon icon) {
            super(text, icon);
            setFocusPainted(false);
            refreshStyle();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            refreshStyle();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            refreshStyle();
        }

        private void refreshStyle() {
            if (isEnabled()) {
                java.awt.Color accent = UiThemeService.getInstance().color(
                        ThemeToken.LIVE_TRACE);
                setOpaque(true);
                setContentAreaFilled(true);
                setBackground(accent);
                setForeground(UiThemeService.contrastText(accent));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(accent.brighter()),
                        BorderFactory.createEmptyBorder(3, 7, 3, 7)));
            } else {
                setBackground(UIManager.getColor("ToggleButton.background"));
                setForeground(UIManager.getColor("ToggleButton.disabledText"));
                javax.swing.border.Border border = UIManager.getBorder(
                        "ToggleButton.border");
                setBorder(border == null
                        ? BorderFactory.createEmptyBorder(4, 8, 4, 8)
                        : border);
            }
        }
    }

    private static JButton optionsButton(JMenuBar contextMenu) {
        final JButton options = new JButton("Options ▾");
        options.setName("MAP OPTIONS");
        options.setToolTipText("Map, edit, view, and compare actions");
        options.getAccessibleContext().setAccessibleName("Map options");
        final JPopupMenu popup = new JPopupMenu();
        if (contextMenu != null) {
            while (contextMenu.getMenuCount() > 0) {
                JMenu menu = contextMenu.getMenu(0);
                if (menu == null) break;
                contextMenu.remove(menu);
                popup.add(menu);
            }
        }
        options.putClientProperty("MAP_OPTIONS_POPUP", popup);
        options.setEnabled(popup.getComponentCount() > 0);
        options.addActionListener(event ->
                popup.show(options, 0, options.getHeight()));
        return options;
    }
}
