/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2021 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.swing;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.JToggleButton;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.romraider.Settings;
import com.romraider.editor.ecu.ECUEditor;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.api.LoggerLiveDataListener;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.BrandImages;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public class ECUEditorToolBar extends JToolBar implements ActionListener {

    private static final long serialVersionUID = 7778170684606193919L;
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            ECUEditorToolBar.class.getName());
    private final JButton openImage = new JButton("Open ROM");
    private final JButton saveImage = new JButton("Save ROM");
    private final JButton connect = new JButton("Connect");
    private final JButton readEcu = new JButton("Read ECU");
    private final JButton writeEcu = new JButton("Write ECU");
    private final JButton liveData = new JButton("Live Data");
    private final JButton dashboard = new JButton("Dashboard");
    private final JButton compare = new JButton("Compare");
    private final JButton preferences = new JButton("Preferences");
    private final JButton moreActions = new JButton("More \u25be");
    private final JButton search = new JButton(
            ModernIconFactory.icon(Action.SEARCH));
    private final JToggleButton navigation = new JToggleButton(
            ModernIconFactory.icon(Action.HIDE_LEFT_PANEL));
    private final JToggleButton inspector = new JToggleButton(
            ModernIconFactory.icon(Action.SHOW_RIGHT_PANEL));
    private final JLabel connectionStatus = new JLabel("● ECU OFFLINE");
    private final JLabel brand = new JLabel(
            "<html><b>RomRaider2</b><br>ECU Studio</html>",
            BrandImages.icon(32),
            JLabel.LEFT);
    private final JSeparator fileSeparator = groupSeparator();
    private final JSeparator ecuSeparator = groupSeparator();
    private final JSeparator viewSeparator = groupSeparator();
    private final LoggerLiveDataListener liveDataListener =
            new LoggerLiveDataListener() {
        public void sessionStateChanged(final LoggerSessionState state) {
            if (SwingUtilities.isEventDispatchThread()) showSessionState(state);
            else SwingUtilities.invokeLater(() -> showSessionState(state));
        }
        public void sampleUpdated(LiveDataSample sample) { }
        public void parameterRemoved(String parameterId) { }
    };
    private boolean liveDataAttached;

    public ECUEditorToolBar(String name) {
        super(name);
        this.setFloatable(false);
        this.setRollover(true);
        this.setLayout(new BorderLayout(12, 0));
        this.setBackground(UiThemeService.getInstance().color(
                ThemeToken.SURFACE));
        this.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        UiThemeService.getInstance().color(
                                ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));

        this.updateIcons();

        brand.setIconTextGap(8);
        brand.setForeground(UiThemeService.getInstance().color(
                ThemeToken.PRIMARY_TEXT));
        this.add(brand, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        actions.setOpaque(false);
        actions.add(openImage);
        actions.add(saveImage);
        actions.add(fileSeparator);
        actions.add(connect);
        actions.add(readEcu);
        actions.add(writeEcu);
        actions.add(ecuSeparator);
        actions.add(liveData);
        actions.add(dashboard);
        actions.add(compare);
        actions.add(viewSeparator);
        actions.add(preferences);
        actions.add(moreActions);
        this.add(actions, BorderLayout.CENTER);

        JPanel context = new JPanel(new BorderLayout(10, 0));
        context.setOpaque(false);
        connectionStatus.setName("ECU CONNECTION STATUS");
        connectionStatus.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        connectionStatus.setToolTipText(
                "No integrated ECU communication session is active");
        context.add(connectionStatus, BorderLayout.WEST);
        JPanel viewControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        viewControls.setOpaque(false);
        viewControls.add(navigation);
        viewControls.add(search);
        viewControls.add(inspector);
        context.add(viewControls, BorderLayout.EAST);
        this.add(context, BorderLayout.EAST);

        configureActionButton(openImage);
        configureActionButton(saveImage);
        configureActionButton(connect);
        configureActionButton(readEcu);
        configureActionButton(writeEcu);
        configureActionButton(liveData);
        configureActionButton(dashboard);
        configureActionButton(compare);
        configureActionButton(preferences);
        configureActionButton(moreActions);
        moreActions.setName("MORE ACTIONS");
        moreActions.setToolTipText("More editor actions");
        moreActions.getAccessibleContext().setAccessibleName(
                "More editor actions");
        search.setFocusPainted(false);
        search.setFocusable(false);
        search.setPreferredSize(new Dimension(36, 36));
        search.setMinimumSize(new Dimension(36, 36));
        navigation.setFocusPainted(false);
        navigation.setFocusable(false);
        navigation.setPreferredSize(new Dimension(36, 36));
        navigation.setMinimumSize(new Dimension(36, 36));
        inspector.setFocusPainted(false);
        inspector.setFocusable(false);
        inspector.setPreferredSize(new Dimension(36, 36));
        inspector.setMinimumSize(new Dimension(36, 36));
        connect.setName("CONNECT INTERFACE");
        readEcu.setName("READ ECU");
        writeEcu.setName("WRITE ECU");
        liveData.setName("OPEN LIVE DATA");
        dashboard.setName("OPEN DASHBOARD");
        compare.setName("COMPARE ROMS");
        preferences.setName("OPEN PREFERENCES");
        search.setName("UNIFIED SEARCH");
        search.setToolTipText("Search everything (Ctrl+Shift+P)");
        search.getAccessibleContext().setAccessibleName("Search everything");
        navigation.setName("TOGGLE NAVIGATION");
        navigation.setToolTipText("Hide Calibrations and saved table lists (Ctrl+B)");
        navigation.getAccessibleContext().setAccessibleName(
                "Hide Calibrations and saved table lists");
        inspector.setName("TOGGLE INSPECTOR");
        inspector.setToolTipText("Show Inspector");
        inspector.getAccessibleContext().setAccessibleName("Show Inspector");
        writeEcu.setForeground(UiThemeService.getInstance().color(
                ThemeToken.DANGER));

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                updateResponsiveContext();
            }
        });

        this.updateButtons();

        openImage.addActionListener(this);
        saveImage.addActionListener(this);
        connect.addActionListener(this);
        liveData.addActionListener(this);
        dashboard.addActionListener(this);
        compare.addActionListener(this);
        preferences.addActionListener(this);
        moreActions.addActionListener(event -> showMoreActions());
        search.addActionListener(this);
        navigation.addActionListener(this);
        inspector.addActionListener(this);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        updateResponsiveContext();
        if (!liveDataAttached) {
            liveDataAttached = true;
            LoggerLiveDataBus.getInstance().addListener(liveDataListener);
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

    private static void configureActionButton(JButton button) {
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(5);
        button.setMargin(new Insets(3, 7, 3, 7));
        Dimension preferred = button.getPreferredSize();
        button.setPreferredSize(new Dimension(preferred.width, 36));
        button.setMinimumSize(new Dimension(preferred.width, 36));
    }

    private void updateResponsiveContext() {
        int width = getWidth();
        int actionLevel = responsiveActionLevel(width);
        boolean showConnection = width >= 1120;
        boolean showAllActions = actionLevel == 2;
        boolean showMediumActions = actionLevel >= 1;
        brand.setVisible(width >= 760);
        saveImage.setVisible(showMediumActions);
        readEcu.setVisible(showAllActions);
        writeEcu.setVisible(showAllActions);
        dashboard.setVisible(showMediumActions);
        compare.setVisible(showAllActions);
        preferences.setVisible(showAllActions);
        fileSeparator.setVisible(showAllActions);
        ecuSeparator.setVisible(showAllActions);
        viewSeparator.setVisible(showAllActions);
        moreActions.setVisible(!showAllActions);
        if (connectionStatus.isVisible() != showConnection) {
            connectionStatus.setVisible(showConnection);
        }
        revalidate();
        repaint();
    }

    static int responsiveActionLevel(int width) {
        if (width >= 1250) return 2;
        if (width >= 900) return 1;
        return 0;
    }

    private void showMoreActions() {
        JPopupMenu menu = new JPopupMenu();
        addOverflowAction(menu, saveImage);
        addOverflowAction(menu, readEcu);
        addOverflowAction(menu, writeEcu);
        addOverflowAction(menu, dashboard);
        addOverflowAction(menu, compare);
        addOverflowAction(menu, preferences);
        if (menu.getComponentCount() > 0) {
            menu.show(moreActions, 0, moreActions.getHeight());
        }
    }

    private static void addOverflowAction(JPopupMenu menu, JButton button) {
        if (button.isVisible()) return;
        JMenuItem item = new JMenuItem(button.getText(), button.getIcon());
        item.setEnabled(button.isEnabled());
        item.setToolTipText(button.getToolTipText());
        item.addActionListener(event -> button.doClick());
        menu.add(item);
    }

    private void showSessionState(LoggerSessionState state) {
        LoggerSessionState current = state == null
                ? LoggerSessionState.STOPPED : state;
        connectionStatus.setText("● " + current.getDisplayName());
        connectionStatus.setForeground(UiThemeService.getInstance().color(
                current.isLive() ? ThemeToken.SUCCESS
                        : ThemeToken.SECONDARY_TEXT));
        connectionStatus.setToolTipText(current.isLive()
                ? "Logger session is providing live ECU data"
                : "No integrated ECU communication session is active");
    }

    public void updateIcons() {
        openImage.setIcon(ModernIconFactory.icon(Action.OPEN));
        saveImage.setIcon(ModernIconFactory.icon(Action.SAVE));
        connect.setIcon(ModernIconFactory.icon(Action.CONNECT));
        readEcu.setIcon(ModernIconFactory.icon(Action.DOWNLOAD));
        writeEcu.setIcon(ModernIconFactory.icon(Action.EXPORT));
        liveData.setIcon(ModernIconFactory.icon(Action.LOGGER));
        dashboard.setIcon(ModernIconFactory.icon(Action.DASHBOARD));
        compare.setIcon(ModernIconFactory.icon(Action.COMPARE));
        preferences.setIcon(ModernIconFactory.icon(Action.SETTINGS));
        repaint();
    }

    public void updateButtons() {
        ECUEditor editor = ECUEditorManager.getECUEditorWithoutCreation();
        String file = editor == null ? ""
                : editor.getLastSelectedRomFileName();

        openImage.setToolTipText(rb.getString("OPEN"));
        saveImage.setToolTipText(MessageFormat.format(
                rb.getString("SAVEAS"), file));
        liveData.setToolTipText("Open integrated live data");
        dashboard.setToolTipText("Open the live dashboard");
        connect.setToolTipText("Open ECU connection and capability workspace");
        readEcu.setToolTipText(
                "Unavailable until a validated read-capable ECU protocol is connected");
        writeEcu.setToolTipText(
                "Unavailable until a validated write protocol and preflight are implemented");
        compare.setToolTipText("Compare two open ROM images");
        preferences.setToolTipText("Scale, display mode, and appearance");

        if ("".equals(file)) {
            saveImage.setEnabled(false);
        } else {
            saveImage.setEnabled(true);
        }
        readEcu.setEnabled(false);
        writeEcu.setEnabled(false);
        connect.setEnabled(true);
        compare.setEnabled(editor != null && editor.getImages().size() > 1);
        revalidate();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == openImage) {
            try {
                ((ECUEditorMenuBar) getEditor().getJMenuBar()).openImageDialog();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(getEditor(), new DebugPanel(ex,
                        getSettings().getSupportURL()),
                        rb.getString("EXCEPTION"), JOptionPane.ERROR_MESSAGE);
            }
        } else if (e.getSource() == saveImage) {
            try {
                ((ECUEditorMenuBar) getEditor().getJMenuBar()).saveImage(false);
                getEditor().refreshUI();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(getEditor(), new DebugPanel(ex,
                        getSettings().getSupportURL()),
                        rb.getString("EXCEPTION"), JOptionPane.ERROR_MESSAGE);
            }
        } else if (e.getSource() == connect) {
            getEditor().showConnectionCenter();
        } else if (e.getSource() == liveData) {
            getEditor().showLiveDataWorkspace();
        } else if (e.getSource() == dashboard) {
            getEditor().showDashboardWorkspace();
        } else if (e.getSource() == compare) {
            getEditor().showCompareWorkspace();
        } else if (e.getSource() == preferences) {
            SettingsForm form = new SettingsForm();
            form.selectAppearanceTab();
            form.setLocationRelativeTo(getEditor());
            form.setVisible(true);
        } else if (e.getSource() == inspector) {
            getEditor().toggleInspector();
        } else if (e.getSource() == navigation) {
            getEditor().toggleNavigationPanel();
        } else if (e.getSource() == search) {
            getEditor().showUnifiedSearch();
        }
    }

    public void setInspectorVisible(boolean visible) {
        inspector.setSelected(visible);
        inspector.setIcon(ModernIconFactory.icon(visible
                ? Action.HIDE_RIGHT_PANEL : Action.SHOW_RIGHT_PANEL));
        inspector.setToolTipText(visible ? "Hide Inspector" : "Show Inspector");
        inspector.getAccessibleContext().setAccessibleName(
                inspector.getToolTipText());
    }

    public void setNavigationVisible(boolean visible) {
        navigation.setSelected(visible);
        navigation.setIcon(ModernIconFactory.icon(visible
                ? Action.HIDE_LEFT_PANEL : Action.SHOW_LEFT_PANEL));
        navigation.setToolTipText(visible
                ? "Hide Calibrations and saved table lists (Ctrl+B)"
                : "Show Calibrations and saved table lists (Ctrl+B)");
        navigation.getAccessibleContext().setAccessibleName(
                navigation.getToolTipText());
    }

    private static JSeparator groupSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 28));
        separator.setMaximumSize(new Dimension(1, 28));
        return separator;
    }

    private Settings getSettings() {
        return SettingsManager.getSettings();
    }

    private ECUEditor getEditor() {
        return ECUEditorManager.getECUEditor();
    }
}
