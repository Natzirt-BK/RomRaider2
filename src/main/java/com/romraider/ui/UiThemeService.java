/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import java.awt.Color;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

public final class UiThemeService {
    public interface Listener {
        void themeChanged(ThemeMode mode, ThemePalette palette);
    }

    private static final UiThemeService INSTANCE = new UiThemeService();
    private final CopyOnWriteArrayList<Listener> listeners =
            new CopyOnWriteArrayList<Listener>();
    private ThemeMode currentMode = ThemeMode.LIGHT;
    private ThemePalette currentPalette = ThemePalettes.light();

    private UiThemeService() {
    }

    public static UiThemeService getInstance() {
        return INSTANCE;
    }

    public synchronized void apply(ThemeMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Theme mode is required");
        }
        currentMode = RuntimeUiProfile.theme(mode);
        currentPalette = RuntimeUiProfile.isSteamOs()
                ? ThemePalettes.handheld()
                : ThemePalettes.forMode(currentMode);
        ApplicationThemeService.getInstance().apply(currentMode);

        Color background = color(ThemeToken.BACKGROUND);
        Color surface = color(ThemeToken.SURFACE);
        Color raised = color(ThemeToken.RAISED_SURFACE);
        Color text = color(ThemeToken.PRIMARY_TEXT);
        Color secondary = color(ThemeToken.SECONDARY_TEXT);
        Color selection = color(ThemeToken.SELECTION);

        put(background, "Panel.background", "Viewport.background",
                "OptionPane.background", "ToolBar.background",
                "MenuBar.background", "Menu.background", "PopupMenu.background",
                "MenuItem.background", "CheckBoxMenuItem.background",
                "RadioButtonMenuItem.background",
                "CheckBox.background", "RadioButton.background",
                "Desktop.background", "DesktopPane.background");
        put(surface, "TextField.background", "FormattedTextField.background",
                "PasswordField.background", "TextArea.background",
                "TextPane.background", "EditorPane.background",
                "Table.background", "List.background", "Tree.background",
                "Tree.textBackground",
                "ComboBox.background", "Spinner.background", "ToolTip.background");
        put(raised, "Button.background", "ToggleButton.background",
                "TabbedPane.background", "TableHeader.background",
                "TabbedPane.unselectedBackground", "ScrollBar.track",
                "ProgressBar.background", "SplitPane.background",
                "SplitPane.shadow", "SplitPane.darkShadow",
                "SplitPaneDivider.draggingColor", "control");
        put(raised, "ComboBox.buttonBackground");
        put(surface, "TabbedPane.contentAreaColor");
        put(text, "Label.foreground", "Button.foreground",
                "ToggleButton.foreground", "CheckBox.foreground",
                "RadioButton.foreground", "Menu.foreground",
                "MenuItem.foreground", "CheckBoxMenuItem.foreground",
                "RadioButtonMenuItem.foreground", "Table.foreground", "List.foreground",
                "Tree.foreground", "Tree.textForeground", "TextField.foreground",
                "FormattedTextField.foreground", "PasswordField.foreground",
                "TextArea.foreground", "TextField.caretForeground",
                "FormattedTextField.caretForeground",
                "PasswordField.caretForeground", "TextArea.caretForeground",
                "TextPane.caretForeground", "EditorPane.caretForeground",
                "TextPane.foreground", "EditorPane.foreground",
                "TitledBorder.titleColor", "TabbedPane.foreground",
                "ComboBox.foreground", "Spinner.foreground",
                "TableHeader.foreground", "MenuBar.foreground",
                "ToolTip.foreground", "ProgressBar.foreground",
                "OptionPane.messageForeground");
        put(secondary, "Label.disabledForeground", "textInactiveText",
                "Button.disabledText", "ToggleButton.disabledText",
                "CheckBox.disabledText", "RadioButton.disabledText",
                "Menu.disabledForeground", "MenuItem.disabledForeground",
                "CheckBoxMenuItem.disabledForeground",
                "RadioButtonMenuItem.disabledForeground",
                "ComboBox.disabledForeground", "TextField.inactiveForeground",
                "FormattedTextField.inactiveForeground",
                "PasswordField.inactiveForeground");
        put(selection, "Table.selectionBackground", "List.selectionBackground",
                "Tree.selectionBackground", "TextField.selectionBackground",
                "FormattedTextField.selectionBackground",
                "PasswordField.selectionBackground", "TextArea.selectionBackground",
                "TextPane.selectionBackground");
        put(contrastText(selection), "Table.selectionForeground",
                "List.selectionForeground", "Tree.selectionForeground",
                "TextField.selectionForeground",
                "FormattedTextField.selectionForeground",
                "PasswordField.selectionForeground", "TextArea.selectionForeground");
        put(selection, "Tree.selectionBorderColor");
        put(selection, "Menu.selectionBackground", "MenuItem.selectionBackground",
                "CheckBoxMenuItem.selectionBackground",
                "RadioButtonMenuItem.selectionBackground");
        put(contrastText(selection), "Menu.selectionForeground",
                "MenuItem.selectionForeground",
                "CheckBoxMenuItem.selectionForeground",
                "RadioButtonMenuItem.selectionForeground");
        put(selection, "TabbedPane.selected", "TabbedPane.focus");
        put(selection, "Button.select", "ToggleButton.select");
        put(background.brighter(), "TabbedPane.darkShadow",
                "TabbedPane.shadow", "ComboBox.buttonShadow");
        put(raised.brighter(), "TabbedPane.light", "TabbedPane.highlight");
        put(raised.brighter(), "SplitPane.highlight");
        put(raised.brighter(), "ComboBox.buttonHighlight");
        put(raised, "controlShadow", "Separator.foreground", "Tree.hash");
        put(background.darker(), "controlDkShadow",
                "ComboBox.buttonDarkShadow");
        put(raised.brighter(), "controlHighlight", "controlLtHighlight");
        put(color(ThemeToken.ACCENT), "ProgressBar.foreground", "ScrollBar.thumb");
        put(text, "ProgressBar.selectionForeground");
        put(text, "ProgressBar.selectionBackground");
        put(color(ThemeToken.DANGER), "RomRaider2.danger");
        put(color(ThemeToken.WARNING), "RomRaider2.warning");
        put(color(ThemeToken.SUCCESS), "RomRaider2.success");
        put(color(ThemeToken.MODIFIED_CELL), "RomRaider2.modifiedCell");
        put(color(ThemeToken.REALTIME_CELL), "RomRaider2.realtimeCell");
        put(color(ThemeToken.LIVE_TRACE), "RomRaider2.liveTrace");
        UIManager.put("RomRaider2.tableCellBorder",
                BorderFactory.createLineBorder(raised, 1));

        // Metal paints fixed light gradients over semantic background colors.
        // Basic delegates honor the palette and remain available in every JRE.
        UIManager.put("MenuBarUI", "javax.swing.plaf.basic.BasicMenuBarUI");
        UIManager.put("MenuUI", "javax.swing.plaf.basic.BasicMenuUI");
        UIManager.put("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI");
        UIManager.put("CheckBoxMenuItemUI",
                "javax.swing.plaf.basic.BasicCheckBoxMenuItemUI");
        UIManager.put("RadioButtonMenuItemUI",
                "javax.swing.plaf.basic.BasicRadioButtonMenuItemUI");
        UIManager.put("ToolBarUI", "javax.swing.plaf.basic.BasicToolBarUI");
        UIManager.put("ButtonUI",
                "com.romraider.ui.swing.RoundedButtonUI");
        UIManager.put("ToggleButtonUI",
                "com.romraider.ui.swing.RoundedButtonUI");
        UIManager.put("CheckBoxUI", "javax.swing.plaf.basic.BasicCheckBoxUI");
        UIManager.put("RadioButtonUI", "javax.swing.plaf.basic.BasicRadioButtonUI");
        UIManager.put("TabbedPaneUI",
                "com.romraider.ui.swing.ModernTabbedPaneUI");
        UIManager.put("ScrollBarUI",
                "com.romraider.ui.swing.ModernScrollBarUI");
        UIManager.put("ComboBoxUI", "javax.swing.plaf.basic.BasicComboBoxUI");
        UIManager.put("TableUI", "javax.swing.plaf.basic.BasicTableUI");
        UIManager.put("TableHeaderUI", "javax.swing.plaf.basic.BasicTableHeaderUI");
        UIManager.put("ListUI", "javax.swing.plaf.basic.BasicListUI");
        UIManager.put("TreeUI", "javax.swing.plaf.basic.BasicTreeUI");
        UIManager.put("SplitPaneUI",
                "javax.swing.plaf.basic.BasicSplitPaneUI");

        for (Listener listener : listeners) {
            listener.themeChanged(currentMode, currentPalette);
        }
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized ThemeMode getCurrentMode() {
        return currentMode;
    }

    public synchronized ThemePalette getCurrentPalette() {
        return currentPalette;
    }

    public synchronized Color color(ThemeToken token) {
        return currentPalette.get(token);
    }

    private static void put(Color color, String... keys) {
        ColorUIResource resource = new ColorUIResource(color);
        for (String key : keys) {
            UIManager.put(key, resource);
        }
    }

    public static Color contrastText(Color background) {
        if (background == null) return Color.WHITE;
        double luminance = (background.getRed() * 0.299)
                + (background.getGreen() * 0.587)
                + (background.getBlue() * 0.114);
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }
}
