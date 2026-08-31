/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.swing.UIManager;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.After;
import org.junit.Test;
import org.w3c.dom.Document;

import com.romraider.Settings;
import com.romraider.swing.AbstractFrame;
import com.romraider.ui.swing.ModernSearchField;
import com.romraider.ui.swing.DisplayPreferencesPanel;
import com.romraider.xml.DOMSettingsUnmarshaller;

public class UiDisplayServicesTest {
    @After
    public void restoreNormalMetrics() {
        UiScaleService.getInstance().apply(UiScale.PERCENT_100, DisplayMode.NORMAL);
        UiThemeService.getInstance().apply(ThemeMode.SYSTEM);
    }

    @Test
    public void everyThemeDefinesEverySemanticToken() {
        for (ThemeMode mode : ThemeMode.values()) {
            ThemePalette palette = ThemePalettes.forMode(mode);
            for (ThemeToken token : ThemeToken.values()) {
                assertNotNull(mode + " missing " + token, palette.get(token));
            }
        }
    }

    @Test
    public void darkThemeOverridesLegacyLightMenuAndToolbarDelegates() {
        UiThemeService.getInstance().apply(ThemeMode.DARK);
        assertEquals(ThemePalettes.dark().get(ThemeToken.BACKGROUND),
                UIManager.getColor("MenuBar.background"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.RAISED_SURFACE),
                UIManager.getColor("TabbedPane.unselectedBackground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.SELECTION),
                UIManager.getColor("TabbedPane.selected"));
        assertEquals("javax.swing.plaf.basic.BasicMenuBarUI", UIManager.get("MenuBarUI"));
        assertEquals("javax.swing.plaf.basic.BasicMenuUI", UIManager.get("MenuUI"));
        assertEquals("javax.swing.plaf.basic.BasicMenuItemUI", UIManager.get("MenuItemUI"));
        assertEquals("javax.swing.plaf.basic.BasicToolBarUI", UIManager.get("ToolBarUI"));
        assertEquals("javax.swing.plaf.basic.BasicButtonUI", UIManager.get("ButtonUI"));
        assertEquals("javax.swing.plaf.basic.BasicTabbedPaneUI",
                UIManager.get("TabbedPaneUI"));
        assertEquals("javax.swing.plaf.basic.BasicComboBoxUI",
                UIManager.get("ComboBoxUI"));
        assertEquals("javax.swing.plaf.basic.BasicTableHeaderUI",
                UIManager.get("TableHeaderUI"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.PRIMARY_TEXT),
                UIManager.getColor("Menu.foreground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.PRIMARY_TEXT),
                UIManager.getColor("TabbedPane.foreground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.SURFACE),
                UIManager.getColor("ComboBox.background"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.RAISED_SURFACE),
                UIManager.getColor("ComboBox.buttonBackground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.BACKGROUND),
                UIManager.getColor("CheckBox.background"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.BACKGROUND),
                UIManager.getColor("MenuItem.background"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.PRIMARY_TEXT),
                UIManager.getColor("MenuItem.foreground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.BACKGROUND),
                UIManager.getColor("Desktop.background"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.RAISED_SURFACE),
                UIManager.getColor("ProgressBar.background"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.SURFACE),
                UIManager.getColor("Tree.textBackground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.PRIMARY_TEXT),
                UIManager.getColor("Tree.textForeground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.PRIMARY_TEXT),
                UIManager.getColor("FormattedTextField.foreground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.PRIMARY_TEXT),
                UIManager.getColor("FormattedTextField.caretForeground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.PRIMARY_TEXT),
                UIManager.getColor("OptionPane.messageForeground"));
        assertEquals(ThemePalettes.dark().get(ThemeToken.RAISED_SURFACE),
                UIManager.getColor("controlShadow"));
        assertNotNull(UIManager.getBorder("RomRaider2.tableCellBorder"));
        assertEquals(Color.WHITE,
                UiThemeService.contrastText(new Color(20, 30, 40)));
        assertEquals(Color.BLACK,
                UiThemeService.contrastText(new Color(230, 235, 240)));
    }

    @Test
    public void touchModeEnlargesControlsIndependentlyFromScale() {
        UiScaleService service = UiScaleService.getInstance();
        service.apply(UiScale.PERCENT_100, DisplayMode.NORMAL);
        int normalRow = ((Integer) UIManager.get("Table.rowHeight")).intValue();
        double normalFontScale = service.getCurrentScale();

        service.apply(UiScale.PERCENT_100, DisplayMode.TOUCH);
        int touchRow = ((Integer) UIManager.get("Table.rowHeight")).intValue();

        assertTrue(touchRow > normalRow);
        assertTrue(service.getCurrentControlScale() > service.getCurrentScale());
        assertTrue(service.getCurrentScale() > normalFontScale);
        assertTrue(DisplayMode.TOUCH.isTouchOptimized());
    }

    @Test
    public void touchModeEnforcesSeparatedFingerSizedActionTargets() {
        UiScaleService.getInstance().apply(UiScale.PERCENT_100, DisplayMode.TOUCH);
        JPanel controls = new JPanel();
        JButton button = new JButton("Open");
        JComboBox combo = new JComboBox(new String[] {"Evo", "Subaru"});
        controls.add(button);
        controls.add(combo);
        TouchTargetService.apply(controls, DisplayMode.TOUCH);

        int minimum = Math.max(48, UiScaleService.getInstance().control(36));
        assertTrue(button.getPreferredSize().height >= minimum);
        assertTrue(combo.getPreferredSize().height >= minimum);
        assertTrue(((java.awt.FlowLayout) controls.getLayout()).getHgap() >=
                UiScaleService.getInstance().control(6));
        assertTrue(((java.awt.Insets) UIManager.get("Button.margin")).left
                > ((java.awt.Insets) UIManager.get("CheckBox.margin")).left);
    }

    @Test
    public void everyModeTransitionRestoresOriginalNormalControlMetrics() {
        UiScaleService service = UiScaleService.getInstance();
        service.apply(UiScale.PERCENT_100, DisplayMode.NORMAL);
        JPanel controls = new JPanel();
        JButton button = new JButton("Open");
        JComboBox combo = new JComboBox(new String[] {"Subaru", "Evo"});
        controls.add(button);
        controls.add(combo);
        Dimension originalButton = button.getPreferredSize();
        Dimension originalCombo = combo.getPreferredSize();
        int originalHgap = ((java.awt.FlowLayout) controls.getLayout()).getHgap();
        int originalVgap = ((java.awt.FlowLayout) controls.getLayout()).getVgap();

        for (DisplayMode mode : DisplayMode.values()) {
            TouchTargetService.prepareForModeChange(controls, mode);
            service.apply(UiScale.PERCENT_100, mode);
            javax.swing.SwingUtilities.updateComponentTreeUI(controls);
            TouchTargetService.apply(controls, mode);

            TouchTargetService.prepareForModeChange(controls);
            service.apply(UiScale.PERCENT_100, DisplayMode.NORMAL);
            javax.swing.SwingUtilities.updateComponentTreeUI(controls);
            TouchTargetService.apply(controls, DisplayMode.NORMAL);

            assertEquals(mode.toString(), originalButton,
                    button.getPreferredSize());
            assertEquals(mode.toString(), originalCombo,
                    combo.getPreferredSize());
            assertEquals(mode.toString(), originalHgap,
                    ((java.awt.FlowLayout) controls.getLayout()).getHgap());
            assertEquals(mode.toString(), originalVgap,
                    ((java.awt.FlowLayout) controls.getLayout()).getVgap());
        }
    }

    @Test
    public void modernIconsScaleWithTouchControls() {
        UiScaleService service = UiScaleService.getInstance();
        service.apply(UiScale.PERCENT_100, DisplayMode.NORMAL);
        int normal = ModernIconFactory.icon(ModernIconFactory.Action.OPEN).getIconWidth();
        service.apply(UiScale.PERCENT_100, DisplayMode.TOUCH);
        int touch = ModernIconFactory.icon(ModernIconFactory.Action.OPEN).getIconWidth();
        assertTrue(touch > normal);
        assertNotNull(ModernIconFactory.icon(
                ModernIconFactory.Action.TABLE_1D));
        assertNotNull(ModernIconFactory.icon(
                ModernIconFactory.Action.TABLE_2D));
        assertNotNull(ModernIconFactory.icon(
                ModernIconFactory.Action.TABLE_3D));
        assertNotNull(ModernIconFactory.icon(
                ModernIconFactory.Action.SWITCH));
    }

    @Test
    public void mainWindowControlsAreIntegratedIntoTheMenuBar() {
        AbstractFrame frame = new AbstractFrame() { };
        try {
            frame.setTitle("RomRaider2 Test");
            JMenuBar menuBar = new JMenuBar();
            menuBar.add(new JMenu("File"));
            frame.setJMenuBar(menuBar);
            frame.setTitle("RomRaider2 Updated");

            assertTrue(frame.isUndecorated());
            assertEquals(JRootPane.NONE,
                    frame.getRootPane().getWindowDecorationStyle());
            assertNotNull(findNamed(menuBar, JButton.class,
                    "WINDOW MINIMIZE"));
            assertNotNull(findNamed(menuBar, JButton.class,
                    "WINDOW MAXIMIZE"));
            assertNotNull(findNamed(menuBar, JButton.class,
                    "WINDOW CLOSE"));
            assertEquals("RomRaider2 Updated",
                    findNamed(menuBar, JLabel.class,
                            "INTEGRATED WINDOW TITLE").getText());
            JComponent grip = findNamed(frame.getLayeredPane(),
                    JComponent.class, "WINDOW RESIZE GRIP");
            assertNotNull(grip);
            assertTrue(grip.getPreferredSize().width >= 24);
            assertEquals(java.awt.Cursor.SE_RESIZE_CURSOR,
                    grip.getCursor().getType());
            JComponent leftGrip = findNamed(frame.getLayeredPane(),
                    JComponent.class, "WINDOW LEFT RESIZE GRIP");
            assertNotNull(leftGrip);
            assertTrue(leftGrip.getPreferredSize().width >= 24);
            assertEquals(java.awt.Cursor.SW_RESIZE_CURSOR,
                    leftGrip.getCursor().getType());
        } finally {
            frame.dispose();
        }
    }

    @Test
    public void modernSearchHintDoesNotDependOnLookAndFeelExtensions() {
        ModernSearchField search = new ModernSearchField(
                "Search calibrations...");
        ModernSearchField withoutHint = new ModernSearchField("");
        BufferedImage image = paint(search);
        BufferedImage blank = paint(withoutHint);

        assertTrue(search.getInsets().left >= 29);
        boolean differs = false;
        for (int y = 0; y < image.getHeight() && !differs; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != blank.getRGB(x, y)) {
                    differs = true;
                    break;
                }
            }
        }
        assertTrue(differs);
    }

    private static BufferedImage paint(ModernSearchField search) {
        search.setSize(260, 32);
        BufferedImage image = new BufferedImage(260, 32,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            search.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Test
    public void automaticScaleMatchesTheActiveDisplayEnvironment() {
        boolean headless = GraphicsEnvironment.isHeadless();
        double expected = UiScaleService.automaticScaleFactor(
                System.getProperty("os.name", ""), headless,
                headless ? 96
                        : Toolkit.getDefaultToolkit().getScreenResolution());
        assertEquals(expected,
                UiScaleService.getInstance().automaticScaleFactor(), 0.0);
    }

    @Test
    public void automaticScaleDoesNotDoubleWindowsPerMonitorDpi() {
        assertEquals(1.0, UiScaleService.automaticScaleFactor(
                "Windows 11", false, 144), 0.0);
        assertEquals(1.5, UiScaleService.automaticScaleFactor(
                "Linux", false, 144), 0.0);
    }

    @Test
    public void displaySelectorsRemainVisibleAsThreeEqualColumns() {
        DisplayPreferencesPanel panel = new DisplayPreferencesPanel();
        assertTrue(panel.getLayout() instanceof GridLayout);
        assertEquals(3, panel.getComponentCount());
        assertNotNull(findNamed(panel, JComboBox.class, "DISPLAY SCALE"));
        assertNotNull(findNamed(panel, JComboBox.class, "DISPLAY MODE"));
        assertNotNull(findNamed(panel, JComboBox.class, "DISPLAY THEME"));
    }

    @Test
    public void restoresVersionedDisplayPreferences() throws Exception {
        Settings settings = load("<settings><display-preferences schema=\"1\" "
                + "scale=\"PERCENT_150\" theme=\"HIGH_CONTRAST\" "
                + "mode=\"TOUCH\"/></settings>");
        assertEquals(UiScale.PERCENT_150, settings.getUiScale());
        assertEquals(ThemeMode.HIGH_CONTRAST, settings.getThemeMode());
        assertEquals(DisplayMode.TOUCH, settings.getDisplayMode());
    }

    @Test
    public void restoresNavigationRailVisibilityWithWindowLayout()
            throws Exception {
        Settings settings = load("<settings><window><splitpane location=\"245\" "
                + "navigation-visible=\"false\"/></window></settings>");
        assertEquals(245, settings.getSplitPaneLocation());
        assertFalse(settings.isNavigationPanelVisible());
    }

    @Test
    public void invalidDisplayPreferencesMigrateToSafeDefaults() throws Exception {
        Settings settings = load("<settings><display-preferences schema=\"1\" "
                + "scale=\"HUGE\" theme=\"NEON\" mode=\"TABLET\"/></settings>");
        assertEquals(UiScale.AUTOMATIC, settings.getUiScale());
        assertEquals(ThemeMode.LIGHT, settings.getThemeMode());
        assertEquals(DisplayMode.NORMAL, settings.getDisplayMode());
    }

    @Test
    public void unknownFutureDisplaySchemaDoesNotGuess() throws Exception {
        Settings settings = load("<settings><display-preferences schema=\"5\" "
                + "scale=\"PERCENT_300\" theme=\"LIGHT\" mode=\"IN_CAR\"/></settings>");
        assertEquals(UiScale.AUTOMATIC, settings.getUiScale());
        assertEquals(ThemeMode.LIGHT, settings.getThemeMode());
        assertEquals(DisplayMode.NORMAL, settings.getDisplayMode());
    }

    private Settings load(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return new DOMSettingsUnmarshaller().unmarshallSettings(document.getDocumentElement());
    }

    private static <T extends Component> T findNamed(Container root,
            Class<T> type, String name) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component) && name.equals(component.getName())) {
                return type.cast(component);
            }
            if (component instanceof Container) {
                T match = findNamed((Container) component, type, name);
                if (match != null) return match;
            }
        }
        return null;
    }
}
