/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;

import javax.swing.UIManager;
import javax.swing.plaf.DimensionUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;

public final class UiScaleService {
    private static final UiScaleService INSTANCE = new UiScaleService();
    private static final String[] FONT_KEYS = {
        "Button.font", "CheckBox.font", "ComboBox.font", "EditorPane.font",
        "FormattedTextField.font", "Label.font", "List.font", "Menu.font",
        "MenuBar.font", "MenuItem.font", "OptionPane.font", "Panel.font",
        "PasswordField.font", "PopupMenu.font", "ProgressBar.font",
        "RadioButton.font", "ScrollPane.font", "Spinner.font",
        "TabbedPane.font", "Table.font", "TableHeader.font", "TextArea.font",
        "TextField.font", "TextPane.font", "TitledBorder.font",
        "ToggleButton.font", "ToolBar.font", "ToolTip.font", "Tree.font"
    };

    private final Map<String, Font> baseFonts = new HashMap<String, Font>();
    private double currentScale = 1.0;
    private double currentControlScale = 1.0;

    private UiScaleService() {
    }

    public static UiScaleService getInstance() {
        return INSTANCE;
    }

    public synchronized void apply(UiScale scale, DisplayMode mode) {
        if (scale == null || mode == null) {
            throw new IllegalArgumentException("Scale and display mode are required");
        }
        captureBaseFonts();
        double selected = scale.isAutomatic()
                ? automaticScaleFactor()
                : scale.getConfiguredFactor();
        currentScale = clamp(selected * mode.getFontDensity(), 0.65, 3.5);
        currentControlScale = clamp(selected * mode.getControlDensity(), 0.65, 4.0);

        for (String key : FONT_KEYS) {
            Font base = baseFonts.get(key);
            if (base != null) {
                float size = Math.max(8.0f, (float) (base.getSize2D() * currentScale));
                UIManager.put(key, new FontUIResource(base.deriveFont(size)));
            }
        }

        UIManager.put("CheckBox.margin", insets(3, 5));
        UIManager.put("RadioButton.margin", insets(3, 5));
        int actionGap = mode.isTouchOptimized() ? 8 : 3;
        UIManager.put("Button.margin", insets(mode.isTouchOptimized() ? 8 : 5,
                mode.isTouchOptimized() ? 14 : 10));
        UIManager.put("ToggleButton.margin", insets(mode.isTouchOptimized() ? 8 : 5,
                mode.isTouchOptimized() ? 14 : 10));
        UIManager.put("Menu.margin", insets(actionGap, 10));
        UIManager.put("MenuItem.margin", insets(actionGap, 10));
        UIManager.put("Button.iconTextGap", control(mode.isTouchOptimized() ? 8 : 5));
        UIManager.put("MenuItem.iconTextGap", control(mode.isTouchOptimized() ? 10 : 6));
        UIManager.put("ComboBox.padding", insets(mode.isTouchOptimized() ? 7 : 3, 8));
        UIManager.put("TabbedPane.tabInsets", insets(mode.isTouchOptimized() ? 9 : 5,
                mode.isTouchOptimized() ? 14 : 10));
        UIManager.put("TabbedPane.selectedTabPadInsets", insets(2, 2));
        UIManager.put("ToolBar.margin", insets(3, 4));

        UIManager.put("ScrollBar.width", control(16));
        UIManager.put("Tree.rowHeight", control(21));
        UIManager.put("Table.rowHeight", control(21));
        UIManager.put("List.fixedCellHeight", control(21));
        UIManager.put("SplitPane.dividerSize", control(7));
        UIManager.put("Slider.width", control(16));
        UIManager.put("ProgressBar.cellLength", control(2));
        UIManager.put("InternalFrame.titleButtonWidth", control(18));
        UIManager.put("InternalFrame.titleButtonHeight", control(18));
        UIManager.put("OptionPane.minimumSize", dimension(280, 110));
    }

    public synchronized double getCurrentScale() {
        return currentScale;
    }

    public synchronized double getCurrentControlScale() {
        return currentControlScale;
    }

    public int scale(int logicalPixels) {
        return Math.max(1, (int) Math.round(logicalPixels * getCurrentScale()));
    }

    public int control(int logicalPixels) {
        return Math.max(1, (int) Math.round(logicalPixels * getCurrentControlScale()));
    }

    public InsetsUIResource insets(int vertical, int horizontal) {
        return new InsetsUIResource(control(vertical), control(horizontal),
                control(vertical), control(horizontal));
    }

    public DimensionUIResource dimension(int width, int height) {
        return new DimensionUIResource(control(width), control(height));
    }

    public double automaticScaleFactor() {
        boolean headless = GraphicsEnvironment.isHeadless();
        int dpi = headless ? 96
                : Toolkit.getDefaultToolkit().getScreenResolution();
        return automaticScaleFactor(System.getProperty("os.name", ""),
                headless, dpi);
    }

    static double automaticScaleFactor(String osName, boolean headless,
            int dpi) {
        if (headless) return 1.0;
        // Java 21 already applies Windows per-monitor DPI scaling to every
        // Swing window. Scaling once more from the reported DPI makes controls
        // and fonts oversized and causes fixed layouts to overflow.
        if (osName != null && osName.toLowerCase(java.util.Locale.ROOT)
                .contains("windows")) return 1.0;
        return clamp(dpi / 96.0, 0.75, 3.0);
    }

    private void captureBaseFonts() {
        for (String key : FONT_KEYS) {
            if (!baseFonts.containsKey(key)) {
                Font font = UIManager.getFont(key);
                if (font != null) {
                    baseFonts.put(key, font);
                }
            }
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
