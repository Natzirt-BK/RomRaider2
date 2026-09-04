/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2025 RomRaider.com
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

package com.romraider.editor.ecu;

import static com.romraider.Version.PRODUCT_NAME;
import static com.romraider.Version.VERSION;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.editor.workspace.EditorWorkspaceService;
import com.romraider.editor.workspace.EditorWorkspacePanel;
import com.romraider.editor.workspace.TableLocation;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.editor.recovery.RomRecoveryService;
import com.romraider.editor.recovery.RecoverySnapshot;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.editor.search.UnifiedSearchPanel;
import com.romraider.editor.compare.TableComparison;
import com.romraider.flash.FlashBackendRegistry;
import com.romraider.search.SearchEntry;
import com.romraider.search.SearchKind;
import com.romraider.search.UnifiedSearchIndex;
import com.romraider.swing.IntegratedFileChooser;
import com.romraider.ui.TouchTargetService;
import com.romraider.ui.ModernIconFactory;
import com.romraider.logger.api.LoggerParameterFocusService;
import com.romraider.logger.ecu.EcuLogger;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.Table1D;
import com.romraider.maps.Table1DView;
import com.romraider.maps.Table1DView.Table1DType;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table2DView;
import com.romraider.maps.Table3D;
import com.romraider.maps.Table3DView;
import com.romraider.maps.TableBitwiseSwitch;
import com.romraider.maps.TableBitwiseSwitchView;
import com.romraider.maps.TableSwitch;
import com.romraider.maps.TableSwitchView;
import com.romraider.maps.TableView;
import com.romraider.maps.TablePresentationListener;
import com.romraider.maps.TablePresentationService;
import com.romraider.maps.Scale;
import com.romraider.maps.RomUserInteraction;
import com.romraider.maps.RomUserInteractionService;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.maps.history.EditHistoryListener;
import com.romraider.editor.ecu.spi.RomComparisonWorkspaceContext;
import com.romraider.editor.ecu.spi.RomComparisonWorkspaceLoader;
import com.romraider.editor.ecu.spi.EditorNavigationWorkspace;
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceContext;
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceLoader;
import com.romraider.editor.document.EditorDocument;
import com.romraider.editor.document.EditorDocumentSession;
import com.romraider.net.URL;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.RomPlatformResolver;
import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;
import com.romraider.swing.AbstractFrame;
import com.romraider.swing.DebugPanel;
import com.romraider.swing.DefinitionManager;
import com.romraider.swing.ECUEditorMenuBar;
import com.romraider.ui.BrandImages;
import com.romraider.swing.ECUEditorToolBar;
import com.romraider.swing.JProgressPane;
import com.romraider.swing.IntegratedOptionDialog;
import com.romraider.swing.MDIDesktopPane;
import com.romraider.swing.RomTree;
import com.romraider.swing.RomTreeRootNode;
import com.romraider.swing.SettingsForm;
import com.romraider.swing.SwingTableFrameRegistry;
import com.romraider.swing.SwingRomTreeNode;
import com.romraider.swing.SwingRomTreeRegistry;
import com.romraider.swing.TableFrame;
import com.romraider.swing.TableToolBar;
import com.romraider.swing.TableTreeNode;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;
import com.romraider.util.ThreadUtil;
import com.romraider.xml.ConversionLayer.ConversionLayer;

public class ECUEditor extends AbstractFrame {
    private static final long serialVersionUID = -7826850987392016292L;
    private static final Logger LOGGER = Logger.getLogger(ECUEditor.class);
    protected static final ResourceBundle rb = new ResourceUtil().getBundle(
            ECUEditor.class.getName());

    private final String titleText = MessageFormat.format(
            rb.getString("TITLE"), PRODUCT_NAME, VERSION);

    private final RomTreeRootNode imageRoot = new RomTreeRootNode(
            rb.getString("OPENIMAGES"));
    private final RomTree imageList = new RomTree(imageRoot);
    private final MDIDesktopPane rightPanel = new MDIDesktopPane();
    private final EditorTabbedWorkspace documentWorkspace;
    private final JProgressPane statusPanel = new JProgressPane();
    private final JScrollPane leftScrollPane;
    private final JScrollPane rightScrollPane;
    private JSplitPane splitPane = new JSplitPane();
    private final JSplitPane workspaceSplitPane = new JSplitPane();
    private final EditorInspectorPanel inspectorPanel = new EditorInspectorPanel();
    private final EditorStatusBar workbenchStatus;
    private boolean inspectorVisible;
    private boolean inspectorRequestedVisible;
    private boolean inspectorAutoCollapsed;
    private int inspectorWidth = 270;
    private boolean navigationVisible;
    private int navigationWidth = 250;
    private Rom lastSelectedRom = null;
    private ECUEditorToolBar toolBar;
    private ECUEditorMenuBar menuBar;
    private TableToolBar tableToolBar;
    private final JPanel toolBarPanel = new JPanel();
    private final JPanel calibrationArea = new JPanel(new BorderLayout());
    private OpenImageWorker openImageWorker;
    private SetUserLevelWorker setUserLevelWorker;
    private final Settings settings = SettingsManager.getSettings();
    private final EditorDocumentSession documentSession =
            new EditorDocumentSession();
    private final TablePresentationListener tableValidationPresenter =
            new TablePresentationListener() {
                public void invalidScale(Table table, Scale scale) {
                    Runnable warning = () ->
                            TableView.showBadScalePopup(table, scale);
                    if (SwingUtilities.isEventDispatchThread()) warning.run();
                    else SwingUtilities.invokeLater(warning);
                }
            };
    private final RomUserInteraction romUserInteraction =
            new RomUserInteraction() {
                public void definitionError(Rom rom, Table table,
                        String title, String message, Throwable failure) {
                    showRomOperationMessage(message, title,
                            JOptionPane.ERROR_MESSAGE);
                }

                public boolean confirmChecksumFix(Rom rom, Table table,
                        String title, String message) {
                    final boolean[] confirmed = {false};
                    Runnable prompt = () -> {
                        Object[] choices = {rb.getString("YES"),
                                rb.getString("NO")};
                        confirmed[0] = IntegratedOptionDialog.show(
                                ECUEditor.this, message, title,
                                JOptionPane.QUESTION_MESSAGE, choices,
                                choices[0]) == 0;
                    };
                    runOnEventThreadAndWait(prompt);
                    return confirmed[0];
                }

                public void checksumValidationFailed(Rom rom, String title,
                        String message) {
                    showRomOperationMessage(message, title,
                            JOptionPane.WARNING_MESSAGE);
                }

                public void checksumUpdated(Rom rom, String message) {
                    SwingUtilities.invokeLater(() ->
                            statusPanel.complete(message));
                }
            };
    private EditorNavigationWorkspace workspacePanel;
    private EditHistoryListener editHistoryListener;
    private RomRecoveryService.Listener recoveryListener;

    public ECUEditor() {
        TablePresentationService.addFallbackListener(
                tableValidationPresenter);
        RomUserInteractionService.addHandler(romUserInteraction);
        registerSearchCommands();
        inspectorPanel.setOpenTableAction(table -> {
            if (table == null || table.getRom() == null) return;
            TableTreeNode node = SwingRomTreeRegistry.nodeFor(table.getRom())
                    .getTableNodeByName(table.getName());
            if (node == null) return;
            setLastSelectedRom(table.getRom());
            displayTable(node);
        });
        inspectorPanel.setFocusLiveParameterAction(
                parameterId -> focusLoggerParameter(parameterId));
        if (!settings.getRecentVersion().equalsIgnoreCase(VERSION)) {
            showReleaseNotes();
        }

        Dimension savedWindowSize = settings.getWindowSize();
        boolean touchLayout = com.romraider.ui.RuntimeUiProfile.displayMode(
                settings.getDisplayMode()).isTouchOptimized();
        int minimumWidth = touchLayout ? 840 : 900;
        int minimumHeight = touchLayout ? 580 : 600;
        setMinimumSize(new Dimension(minimumWidth, minimumHeight));
        setSize(Math.max(minimumWidth, savedWindowSize.width),
                Math.max(minimumHeight, savedWindowSize.height));
        Rectangle usableBounds = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (com.romraider.ui.RuntimeUiProfile.isSteamOs()) {
            setBounds(usableBounds);
            setExtendedState(MAXIMIZED_BOTH);
        } else {
            setLocation(clampWindowLocation(settings.getWindowLocation(),
                    getSize(), usableBounds));
        }
        if (!com.romraider.ui.RuntimeUiProfile.isSteamOs()
                && settings.isWindowMaximized()) {
            setExtendedState(MAXIMIZED_BOTH);
        }

        documentWorkspace = new EditorTabbedWorkspace(
                new EditorTabbedWorkspace.Listener() {
                    public void tableActivated(TableFrame frame) {
                        handleTableActivated(frame);
                    }

                    public void closeRequested(TableFrame frame) {
                        removeDisplayTable(frame);
                    }

                    public void tabsReordered(List<TableFrame> frames) {
                        persistTableOrder(frames);
                    }

                    public void reopenRequested(Table table) {
                        reopenClosedTable(table);
                    }

                    public boolean isFavorite(Table table) {
                        return table != null && table.getRom() != null
                                && EditorWorkspaceService.getInstance()
                                        .isFavorite(table.getRom(), table);
                    }

                    public void toggleFavorite(Table table) {
                        if (table == null || table.getRom() == null) return;
                        EditorWorkspaceService.getInstance().toggleFavorite(
                                table.getRom(), table);
                        if (workspacePanel != null) workspacePanel.refresh();
                    }
                }, new Runnable() {
                    public void run() { launchLogger(); }
                }, new Runnable() {
                    public void run() { focusTableSearch(); }
                });
        rightScrollPane = new JScrollPane(documentWorkspace,
                VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_NEVER);
        rightScrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
       
        leftScrollPane = new JScrollPane(imageList,
                VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);

        EditorNavigationWorkspaceContext navigationContext =
                new EditorNavigationWorkspaceContext(documentSession,
                        new EditorNavigationWorkspaceContext.Opener() {
                            public void open(TableLocation location) {
                                openTableLocation(location);
                            }
                        });
        workspacePanel = EditorNavigationWorkspaceLoader.create(
                navigationContext);
        if (workspacePanel == null) {
            workspacePanel = new EditorWorkspacePanel(imageRoot, imageList,
                    leftScrollPane,
                    new EditorWorkspacePanel.LocationOpener() {
                        public void open(TableLocation location) {
                            openTableLocation(location);
                        }
                    });
        }
        
        JScrollPane inspectorScrollPane = new JScrollPane(inspectorPanel,
                VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        inspectorScrollPane.setMinimumSize(new Dimension(200, 160));
        inspectorScrollPane.setPreferredSize(new Dimension(250, 560));
        workspaceSplitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        calibrationArea.add(rightScrollPane, BorderLayout.CENTER);
        workspaceSplitPane.setLeftComponent(calibrationArea);
        workspaceSplitPane.setRightComponent(inspectorScrollPane);
        workspaceSplitPane.setResizeWeight(1.0);
        workspaceSplitPane.setContinuousLayout(true);
        workspaceSplitPane.setDividerSize(5);
        inspectorRequestedVisible = !com.romraider.ui.RuntimeUiProfile
                .displayMode(settings.getDisplayMode()).isTouchOptimized();
        inspectorVisible = inspectorRequestedVisible;
        inspectorScrollPane.setVisible(inspectorVisible);
        workspaceSplitPane.setDividerSize(inspectorVisible ? 5 : 0);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				workspacePanel.getComponent(), workspaceSplitPane);
        navigationVisible = settings.isNavigationPanelVisible();
        navigationWidth = Math.max(190,
                Math.min(300, settings.getSplitPaneLocation()));
        workspacePanel.getComponent().setVisible(navigationVisible);
        splitPane.setDividerSize(navigationVisible ? 5 : 0);
        splitPane.setDividerLocation(navigationVisible ? navigationWidth : 0);
        splitPane.addPropertyChangeListener(this);
        splitPane.setContinuousLayout(true);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyResponsiveLayout();
            }
        });
        getContentPane().add(splitPane);

        imageList.setScrollsOnExpand(true);

        workbenchStatus = new EditorStatusBar(new EditorStatusBar.Actions() {
            public void resetChanges() { resetCurrentRomChanges(); }
            public void showRomModifications() {
                showRomModificationsWorkspace();
            }
        }, statusPanel);
        this.add(workbenchStatus, BorderLayout.SOUTH);
        recoveryListener = (rom, state, snapshot, failure) ->
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        workbenchStatus.showRecoveryState(rom, state);
                        if (failure != null) {
                            LOGGER.error("Unable to preserve ROM recovery snapshot",
                                    failure);
                        }
                    }
                });
        RomRecoveryService.getInstance().addListener(recoveryListener);
        editHistoryListener = rom -> {
            RomRecoveryService.getInstance().schedule(rom);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    documentWorkspace.refreshTabStates();
                    workspacePanel.refreshChangedMaps();
                    if (rom != getLastSelectedRom()) return;
                    inspectorPanel.showRom(rom);
                    workbenchStatus.refreshContext();
                    documentWorkspace.repaint();
                }
            });
        };
        RomEditHistory.getInstance().addListener(editHistoryListener);

        //set remaining window properties
        BrandImages.apply(this);
 
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(this);
        setTitle(titleText);
    }

    static Point clampWindowLocation(Point requested, Dimension windowSize,
            Rectangle usableBounds) {
        // KDE/Wayland can expose the full screen here even when a panel reserves
        // its bottom edge. Keep a small cross-desktop clearance for status text.
        final int bottomClearance = 40;
        Point target = requested == null ? usableBounds.getLocation() : requested;
        int maxX = usableBounds.x
                + Math.max(0, usableBounds.width - windowSize.width);
        int maxY = usableBounds.y
                + Math.max(0, usableBounds.height - windowSize.height
                        - bottomClearance);
        return new Point(
                Math.max(usableBounds.x, Math.min(target.x, maxX)),
                Math.max(usableBounds.y, Math.min(target.y, maxY)));
    }

    public void showIntegratedVisualization(TableFrame frame) {
        if (frame != null) documentWorkspace.showVisualization(frame);
    }

    public void initializeEditorUI() {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(this::initializeEditorUI);
            } catch (Exception exception) {
                throw new RuntimeException(
                        "Unable to initialize the Editor interface",
                        exception);
            }
            return;
        }

        //create menubar
        if (menuBar != null && toolBar != null) {
            showInitializedEditor();
            return;
        }

        menuBar = new ECUEditorMenuBar();
        this.setJMenuBar(menuBar);

        // create toolbars
        toolBar = new ECUEditorToolBar(rb.getString("EDTOOLS"));
        toolBar.setInspectorVisible(inspectorVisible);
        toolBar.setNavigationVisible(navigationVisible);

        tableToolBar = new TableToolBar();

        toolBarPanel.setLayout(new BorderLayout());
        toolBarPanel.add(toolBar, BorderLayout.CENTER);
        calibrationArea.add(tableToolBar, BorderLayout.NORTH);
        toolBarPanel.setVisible(true);

        this.add(toolBarPanel, BorderLayout.NORTH);
        positionInspector();
        setupDragAndDrop();
        installWorkspaceShortcuts();
        TouchTargetService.apply(this, com.romraider.ui.RuntimeUiProfile
                .displayMode(settings.getDisplayMode()));
        validate();
        showInitializedEditor();
    }

    private void showInitializedEditor() {
        if (!isVisible()) setVisible(true);
        revalidate();
        repaint();
        toFront();
    }

    public void setupDragAndDrop()
    {
        setTransferHandler(new TransferHandler() {
			private static final long serialVersionUID = 1L;

			@Override
            public boolean canImport(TransferSupport support) {
                // Accept drops of files only
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }

                try {
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles =
                            (List<File>) support.getTransferable()
                                    .getTransferData(DataFlavor.javaFileListFlavor);

                    for (File file : droppedFiles) {
                        openImage(file);
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }
    
    public void checkDefinitions() {
        if (settings.getEcuDefinitionFiles().size() <= 0) {
            Object[] options = {rb.getString("ADD_DEFINITIONS"),
                    rb.getString("NOT_NOW")};
            int answer = IntegratedOptionDialog.show(this,
                    rb.getString("ECUDEFNOTCFG"),
                    rb.getString("EDCONFIG"),
                    WARNING_MESSAGE,
                    options,
                    options[0]);
            if (answer == 0) {
                showDefinitionManager();
            }
        }
    }

    /** Opens the single editor surface for adding and prioritizing definitions. */
    public void showDefinitionManager() {
        DefinitionManager form = new DefinitionManager();
        form.setLocationRelativeTo(this);
        form.setVisible(true);
    }

    /** Reviews recoverable work from abnormal exits without touching source ROMs. */
    public void reviewRecoverySnapshots() {
        final List<RecoverySnapshot> snapshots;
        try {
            snapshots = RomRecoveryService.getInstance()
                    .discoverLatestSnapshots();
        } catch (IOException failure) {
            LOGGER.error("Unable to inspect ROM recovery snapshots", failure);
            return;
        }
        for (RecoverySnapshot snapshot : snapshots) {
            String sourcePath = snapshot.getSourcePath().trim().isEmpty()
                    ? "Original location unavailable" : snapshot.getSourcePath();
            String created = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT).format(
                            new Date(snapshot.getCreatedAt()));
            String changed = snapshot.getChangedCells() == 1
                    ? "1 changed cell" : snapshot.getChangedCells()
                            + " changed cells";
            String message = "RomRaider2 found recoverable work from an "
                    + "abnormal exit.\n\n"
                    + snapshot.getSourceName() + "\n"
                    + sourcePath + "\n"
                    + created + " — " + changed + "\n"
                    + "ROM ID: " + snapshot.getRomId() + "\n\n"
                    + "Restore opens a new unsaved workspace and never "
                    + "overwrites the original ROM.";
            Object[] options = {"Restore as Unsaved", "Discard Recovery",
                    "Keep for Later"};
            int answer = IntegratedOptionDialog.show(this, message,
                    "Recover Unsaved ROM", WARNING_MESSAGE, options,
                    options[0]);
            if (answer == 0) {
                openRecoveredImage(snapshot);
            } else if (answer == 1) {
                try {
                    RomRecoveryService.getInstance().discardAll(snapshot);
                } catch (IOException failure) {
                    LOGGER.error("Unable to discard ROM recovery snapshot",
                            failure);
                    showMessageDialog(this,
                            "RomRaider2 could not discard the recovery files.\n"
                                    + failure.getMessage(),
                            "Recovery Files", ERROR_MESSAGE);
                }
            }
        }
    }

    private void showReleaseNotes() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                    settings.getReleaseNotes()));
            try {
                StringBuffer sb = new StringBuffer();
                while (br.ready()) {
                    sb.append(br.readLine()).append(Settings.NEW_LINE);
                }
                Object[] options = {"Continue"};
                IntegratedOptionDialog.show(this,
                        createReleaseNotesPanel(sb.toString()),
                        PRODUCT_NAME + " " + VERSION + " "
                                + rb.getString("RELEASENOTES"),
                        JOptionPane.PLAIN_MESSAGE, options, options[0]);
            } finally {
                br.close();
            }
        } catch (Exception e) {
            /* Ignore */
        }
    }

    static JPanel createReleaseNotesPanel(String notes) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setName("RELEASE NOTES PANEL");
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(
                8, 8, 2, 8));

        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 3));
        heading.setOpaque(false);
        JLabel title = new JLabel("WHAT'S NEW IN RC4");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 3.0f));
        title.setForeground(UiThemeService.getInstance().color(
                ThemeToken.ACCENT));
        JLabel summary = new JLabel("A development preview of the new "
                + "desktop and portable interface.");
        summary.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        heading.add(title);
        heading.add(summary);
        panel.add(heading, BorderLayout.NORTH);

        JTextArea releaseNotes = new JTextArea(notes == null ? "" : notes);
        releaseNotes.setName("RELEASE NOTES TEXT");
        releaseNotes.setEditable(false);
        releaseNotes.setFocusable(true);
        releaseNotes.setWrapStyleWord(true);
        releaseNotes.setLineWrap(true);
        releaseNotes.setMargin(new java.awt.Insets(12, 14, 12, 14));
        releaseNotes.setFont(releaseNotes.getFont().deriveFont(Font.PLAIN,
                Math.max(13.0f, releaseNotes.getFont().getSize2D())));
        releaseNotes.setCaretPosition(0);
        JScrollPane scroller = new JScrollPane(releaseNotes,
                VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setName("RELEASE NOTES SCROLL");
        scroller.setBorder(javax.swing.BorderFactory.createLineBorder(
                UiThemeService.getInstance().color(
                        ThemeToken.RAISED_SURFACE)));
        scroller.setPreferredSize(new Dimension(700, 480));
        panel.add(scroller, BorderLayout.CENTER);
        return panel;
    }

    public void handleExit() {
        if (!commitPendingValueEdit()) return;
        if (!confirmUnsavedChanges(
                RomChangeSummary.changedRoms(getImages()), "exit")) return;
        finishExit();
    }

    private boolean commitPendingValueEdit() {
        if (tableToolBar == null) return true;
        try {
            tableToolBar.commitPendingValueEdit();
            return true;
        } catch (UserLevelException error) {
            TableView.showInvalidUserLevelPopup(error);
            return false;
        }
    }

    private void finishExit() {
        if (editHistoryListener != null) {
            RomEditHistory.getInstance().removeListener(editHistoryListener);
            editHistoryListener = null;
        }
        if (recoveryListener != null) {
            RomRecoveryService.getInstance().removeListener(recoveryListener);
            recoveryListener = null;
        }
        for (Rom rom : getImages()) {
            RomRecoveryService.getInstance().markResolved(rom);
        }
        settings.setSplitPaneLocation(navigationVisible
                ? splitPane.getDividerLocation() : navigationWidth);
        settings.setNavigationPanelVisible(navigationVisible);
        settings.setWindowMaximized(getExtendedState() == MAXIMIZED_BOTH);
        settings.setWindowSize(getSize());
        settings.setWindowLocation(getLocation());

        // A settings failure must never prevent the application from closing.
        try {
            SettingsManager.save(settings, statusPanel);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.error("Unable to save editor settings during shutdown", error);
        }

        TablePresentationService.removeListener(null,
                tableValidationPresenter);
        RomUserInteractionService.removeHandler(romUserInteraction);
        workspacePanel.close();
        documentSession.close();

        if(EcuLogger.getEcuLoggerWithoutCreation()== null) {
            System.exit(0);
        }
        else{
            ECUEditorManager.clearECUEditor();
            EcuLogger.getEcuLoggerWithoutCreation().setEcuEditor(null);
            dispose();
        }
    }

    private void showRomOperationMessage(String message, String title,
            int messageType) {
        runOnEventThreadAndWait(() -> IntegratedOptionDialog.show(
                ECUEditor.this, message, title, messageType,
                new Object[] {"OK"}, "OK"));
    }

    private static void runOnEventThreadAndWait(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (Exception failure) {
            LOGGER.error("Unable to present ROM operation message", failure);
        }
    }

    private boolean confirmUnsavedChanges(List<Rom> changedRoms,
            String action) {
        if (changedRoms == null || changedRoms.isEmpty()) return true;

        int total = 0;
        StringBuilder details = new StringBuilder(
                "The following ROM changes have not been saved:\n\n");
        for (Rom rom : changedRoms) {
            int count = RomChangeSummary.countChangedCells(rom);
            total += count;
            String name = rom.getFileName();
            if (name == null || name.trim().isEmpty()) name = "Untitled ROM";
            details.append("  • ").append(name).append(" — ");
            if (count == 0) details.append("modified ROM data\n");
            else details.append(count).append(count == 1
                    ? " changed cell\n" : " changed cells\n");
        }
        details.append("\nSave before you ").append(action).append('?');

        String saveLabel = changedRoms.size() == 1 ? "Save" : "Save All";
        Object[] choices = {saveLabel, "Discard Changes", "Cancel"};
        int answer = IntegratedOptionDialog.show(this, details.toString(),
                total == 1 ? "1 unsaved ROM change"
                        : total > 1 ? total + " unsaved ROM changes"
                        : changedRoms.size() == 1 ? "Unsaved ROM changes"
                        : changedRoms.size() + " ROMs have unsaved changes",
                JOptionPane.WARNING_MESSAGE, choices, choices[0]);
        if (answer == 1) return true;
        if (answer != 0) return false;

        for (Rom rom : changedRoms) {
            try {
                setLastSelectedRom(rom);
                if (!menuBar.saveImage(rom, true)) return false;
            } catch (Exception error) {
                LOGGER.error("Unable to save ROM before " + action, error);
                showMessageDialog(this,
                        new DebugPanel(error, settings.getSupportURL()),
                        rb.getString("EXCEPTN"), ERROR_MESSAGE);
                return false;
            }
        }
        return true;
    }

    public void handleExportDefinition() {
        Rom r = getLastSelectedRom();

        if(null != r) {
            JFileChooser fileChooser = new IntegratedFileChooser(
                    settings.getLastDefinitionDir());
            fileChooser.setFileFilter(new FileNameExtensionFilter("Editor Definition (.xml)","xml"));
            int userSelection = fileChooser.showSaveDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();

                if(!fileToSave.getName().toLowerCase().endsWith(".xml"))
                        fileToSave = new File(fileToSave.getAbsoluteFile() + ".xml");

                String s = ConversionLayer.convertDocumentToString(r.getDocument());

                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave));
                    writer.write(s);
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
        handleExit();
    }

    @Override
    public void windowOpened(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {
    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowActivated(WindowEvent e) {
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
    }

    public String getVersion() {
        return VERSION;
    }

    public void addRom(Rom input) {
        SwingRomTreeNode romNode = SwingRomTreeRegistry.nodeFor(input);
        romNode.refreshDisplayedTables();
        RomChangeService.rememberSavedBinary(input);
        EditorWorkspaceService.getInstance().indexRom(input);
        documentSession.openRom(input);

        // add to ecu image list pane
        getImageRoot().add(romNode);

        getImageList().setVisible(true);
        getImageList().expandPath(new TreePath(getImageRoot()));
        getImageList().expandPath(new TreePath(romNode.getPath()));

        if(!settings.isOpenExpanded()) {
            imageList.collapsePath(new TreePath(romNode.getPath()));
        }

        getImageList().setRootVisible(false);
        getImageList().repaint();

        // Only set if no other rom has been selected.
        if(null == getLastSelectedRom()) {
            setLastSelectedRom(input);
        }

        if (input.getRomID().isObsolete() && settings.isObsoleteWarning()) {
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new GridLayout(3, 1));
            infoPanel.add(new JLabel(rb.getString("OBSOLETEROM")));
            infoPanel.add(new URL(settings.getRomRevisionURL()));

            JCheckBox check = new JCheckBox(rb.getString("DISPLAYMSG"), true);
            check.setHorizontalAlignment(JCheckBox.RIGHT);

            check.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    settings.setObsoleteWarning(
                            ((JCheckBox) e.getSource()).isSelected());
                }
            });

            infoPanel.add(check);
            showMessageDialog(this, infoPanel,
                    rb.getString("ISOBSOLETE"),
                    INFORMATION_MESSAGE);
        }

        restoreWorkspaceTables(input);
    }

    private void restoreWorkspaceTables(Rom rom) {
        EditorWorkspaceService service = EditorWorkspaceService.getInstance();
        String romId = EditorWorkspaceService.romIdentity(rom);
        List<String> savedTables = new ArrayList<String>(
                service.preferences().getOpenTables(romId));
        if (savedTables.isEmpty()) return;

        String activeTable = service.preferences().getActiveTable(romId);
        TableFrame activeFrame = null;
        for (String tableName : savedTables) {
            TableTreeNode node = SwingRomTreeRegistry.nodeFor(rom)
                    .getTableNodeByName(tableName);
            if (node == null) continue;
            openClosedTable(node);
            if (node.getFrame() != null && tableName.equals(activeTable)) {
                activeFrame = node.getFrame();
            }
        }
        if (activeFrame != null) documentWorkspace.select(activeFrame);
        workspacePanel.refresh();
        refreshTableCompareMenus();
    }

    private void persistTableOrder(List<TableFrame> frames) {
        Map<String, List<String>> namesByRom =
                new LinkedHashMap<String, List<String>>();
        for (TableFrame frame : frames) {
            Table table = frame == null ? null : frame.getTable();
            Rom rom = table == null ? null : table.getRom();
            if (rom == null) continue;
            String romId = EditorWorkspaceService.romIdentity(rom);
            List<String> names = namesByRom.get(romId);
            if (names == null) {
                names = new ArrayList<String>();
                namesByRom.put(romId, names);
            }
            names.add(table.getName());
        }
        EditorWorkspaceService service = EditorWorkspaceService.getInstance();
        for (Map.Entry<String, List<String>> entry : namesByRom.entrySet()) {
            service.tableOrderChanged(entry.getKey(), entry.getValue());
        }
    }
    
    private void handleAlreadyOpenTable(TableFrame frame) {
        // table is already open.
        if(1 == settings.getTableClickBehavior()) { // open/focus frame
            documentWorkspace.select(frame);
        } else {
            removeDisplayTable(frame);
        }
    }
    
    public static TableView getTableViewForTable(Table t)
    {
    	TableView v = null;
	
        if(t instanceof TableSwitch)
            v = new TableSwitchView((TableSwitch)t);
        else if(t instanceof TableBitwiseSwitch)
            v = new TableBitwiseSwitchView((TableBitwiseSwitch)t);
        else if(t instanceof Table1D)
            v = new Table1DView((Table1D)t, Table1DType.NO_AXIS);
        else if(t instanceof Table2D)
            v = new Table2DView((Table2D)t);
        else if(t instanceof Table3D)
            v = new Table3DView((Table3D)t);
        
        return v;
    }
    
    private void openClosedTable(TableTreeNode node)
    {
        Table t = node.getTable();
        TableView v = getTableViewForTable(t);
        try {
            if (t != null) {    
    	        v.populateTableVisual();
            	v.drawTable();
            	
                Rom rom = RomTree.getRomNode(node);
                TableFrame frame = new TableFrame(node.getTable().getName() + " | " + rom.getFileName(), v);
                frame.pack();
                frame.RegisterTable();
                documentWorkspace.open(frame);
                documentSession.openTable(rom, t);
            }
        }
        catch(Exception e) {
            final String msg = MessageFormat.format(
                    rb.getString("POPULATEFAIL"), t.getName(),
                    e.toString());
            LOGGER.error(msg, e);
            final Exception ex = new Exception(msg, e);
            showMessageDialog(this,
                    new DebugPanel(ex, settings.getSupportURL()),
                    rb.getString("EXCEPTION"),
                    ERROR_MESSAGE);
        }
    	
    }
    
    public void displayTable(TableTreeNode node) {

        TableFrame frame = node.getFrame();

        // check if frame has been added.
        if (frame != null)
        {
        	handleAlreadyOpenTable(frame);
        }
        else
        {
        	openClosedTable(node);
        }

        Rom rom = RomTree.getRomNode(node);
        if (rom != null && node.getTable() != null && node.getFrame() != null) {
            documentSession.openTable(rom, node.getTable());
            EditorWorkspaceService.getInstance().tableOpened(rom, node.getTable());
            workspacePanel.refresh();
            inspectorPanel.showSelection(rom, node.getTable());
            workbenchStatus.showTable(rom, node.getTable());
        }
        
        documentWorkspace.repaint();
        refreshTableCompareMenus();        
    }

    public void removeDisplayTable(TableFrame frame) {
        if (frame == null) return;
        Table table = frame.getTable();
        if (table != null && table.getRom() != null) {
            documentSession.closeTable(table.getRom(), table);
            EditorWorkspaceService.getInstance().tableClosed(table.getRom(), table);
        }
        documentWorkspace.close(frame);
        frame.setVisible(false);
        if (frame.getParent() == rightPanel) rightPanel.remove(frame);
        else frame.DeregisterTable();
        SwingTableFrameRegistry.unregister(table, frame);
        try {
            frame.setClosed(true);
        } catch (PropertyVetoException exception) {
            // The table is still detached and disposed below.
        }
        frame.dispose();
        refreshUI();
        workspacePanel.refresh();
        handleTableActivated(documentWorkspace.getActiveFrame());
    }

    private void handleTableActivated(TableFrame frame) {
        if (frame == null || frame.getTable() == null) {
            if (tableToolBar != null) tableToolBar.updateTableToolBar(null);
            if (toolBar != null) toolBar.updateButtons();
            if (menuBar != null) menuBar.updateMenu();
            inspectorPanel.clearTableSelection();
            inspectorPanel.showRom(getLastSelectedRom());
            if (workbenchStatus != null) workbenchStatus.showRom(getLastSelectedRom());
            return;
        }

        Table table = frame.getTable();
        if (table.getRom() != null && table.getRom() != getLastSelectedRom()) {
            setLastSelectedRom(table.getRom());
        }
        if (table.getRom() != null) {
            documentSession.activateTable(table.getRom(), table);
            EditorWorkspaceService.getInstance().tableActivated(
                    table.getRom(), table);
        }
        frame.activateForWorkspace();
        inspectorPanel.showSelection(table.getRom(), table);
        if (workbenchStatus != null) workbenchStatus.showTable(table.getRom(), table);
    }

    private void installWorkspaceShortcuts() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK),
                "workspaceSearch");
        getRootPane().getActionMap().put("workspaceSearch", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { workspacePanel.focusSearch(); }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_P,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                "unifiedSearch");
        getRootPane().getActionMap().put("unifiedSearch", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { showUnifiedSearch(); }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK),
                "toggleNavigationPanel");
        getRootPane().getActionMap().put("toggleNavigationPanel",
                new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) {
                toggleNavigationPanel();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
                "undoEdit");
        getRootPane().getActionMap().put("undoEdit", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { undoLastEdit(); }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
                "redoEdit");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                "redoEdit");
        getRootPane().getActionMap().put("redoEdit", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { redoLastEdit(); }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
                "closeActiveWorkspaceTab");
        getRootPane().getActionMap().put("closeActiveWorkspaceTab",
                new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) {
                documentWorkspace.requestCloseActiveTab();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_W,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                "closeAllMapTabs");
        getRootPane().getActionMap().put("closeAllMapTabs",
                new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) {
                documentWorkspace.requestCloseAllMaps();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_T,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                "reopenLastMapTab");
        getRootPane().getActionMap().put("reopenLastMapTab",
                new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) {
                documentWorkspace.requestReopenLastMap();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK),
                "workspaceBack");
        getRootPane().getActionMap().put("workspaceBack", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { workspacePanel.goBack(); }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK),
                "workspaceForward");
        getRootPane().getActionMap().put("workspaceForward", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { workspacePanel.goForward(); }
        });
    }

    private void openTableLocation(TableLocation location) {
        for (EditorDocument document :
                documentSession.snapshot().getDocuments()) {
            Rom rom = document.getRom();
            if (!EditorWorkspaceService.romIdentity(rom).equals(location.getRomId())) continue;
            for (TableTreeNode node : SwingRomTreeRegistry.nodeFor(rom)
                    .getTableNodes().values()) {
                if (node.getTable().getName().equals(location.getTableName())) {
                    setLastSelectedRom(rom);
                    displayTable(node);
                    return;
                }
            }
        }
        showMessageDialog(this, "Open the matching ROM before opening "
                + location.getTableName(), "ROM not open", INFORMATION_MESSAGE);
    }

    private void reopenClosedTable(Table table) {
        if (table == null || table.getRom() == null
                || !getImages().contains(table.getRom())) return;
        TableTreeNode node = SwingRomTreeRegistry.nodeFor(table.getRom())
                .getTableNodeByName(table.getName());
        if (node == null) return;
        setLastSelectedRom(table.getRom());
        displayTable(node);
    }

    public boolean closeImage() {
        Rom rom = getLastSelectedRom();
        if (rom == null) return true;
        if (!commitPendingValueEdit()) return false;
        if (!confirmUnsavedChanges(
                RomChangeSummary.changedRoms(java.util.Collections.singletonList(rom)),
                "close this ROM")) return false;
        closeImageWithoutPrompt(rom);
        return true;
    }

    private void closeImageWithoutPrompt(Rom rom) {
        EditorWorkspaceService.getInstance().removeRomFromIndex(rom);
        RomEditHistory.getInstance().clear(rom);
        RomChangeService.forget(rom);
        RomRecoveryService.getInstance().markResolved(rom);
        ECUEditor editor = ECUEditorManager.getECUEditor();
        RomTreeRootNode imageRoot = editor.getImageRoot();

        for (TableFrame frame : new ArrayList<TableFrame>(getOpenTableFrames())) {
            if (frame.getTable() != null && frame.getTable().getRom() == rom) {
                removeDisplayTable(frame);
            }
        }

        documentSession.closeRom(rom);

        SwingRomTreeNode romNode = SwingRomTreeRegistry.nodeFor(rom);
        romNode.removeFromParent();
        SwingRomTreeRegistry.forget(rom);

        if (imageRoot.getChildCount() > 0) {
            editor.setLastSelectedRom(((SwingRomTreeNode)
                    imageRoot.getChildAt(0)).getRom());
        } else {
            editor.setLastSelectedRom(null);
        }

        editor.getStatusPanel().ready(ECUEditor.rb.getString("STATUSREADY"));
        editor.setCursor(null);
        editor.refreshAfterNewRom();

        for (Table table : rom.getTableCatalog()) {
            com.romraider.logger.ecu.ui.handler.table.TableUpdateHandler
                    .getInstance().deregisterTable(table);
        }
        rom.clearData();
    }

    public void closeAllImages() {
        while (imageRoot.getChildCount() > 0) {
            if (!closeImage()) return;
        }
    }

    public Rom getLastSelectedRom() {
        return lastSelectedRom;
    }

    public String getLastSelectedRomFileName() {
        Rom lastSelRom = getLastSelectedRom();
        return lastSelRom == null ? "" : lastSelRom.getFileName();
    }

    public void setLastSelectedRom(Rom lastSelectedRom) {
        boolean romChanged = this.lastSelectedRom != lastSelectedRom;
        this.lastSelectedRom = lastSelectedRom;
        documentSession.activateRom(lastSelectedRom);
        applyRomPlatformContext(lastSelectedRom);
        if (lastSelectedRom == null) {
            setTitle(titleText);
        } else {
            setTitle(titleText + " - " + lastSelectedRom.getFileName());
        }
        if (romChanged) inspectorPanel.clearTableSelection();
        inspectorPanel.showRom(lastSelectedRom);
        workbenchStatus.showRom(lastSelectedRom);
    }

    private void applyRomPlatformContext(Rom rom) {
        if (rom == null) return;
        Optional<VehiclePlatform> resolved =
                RomPlatformResolver.resolve(rom.getRomID());
        if (!resolved.isPresent()) return;
        PlatformContext context = PlatformContext.getInstance();
        context.setPlatform(resolved.get());
        context.setModule(VehicleModule.ENGINE_ECU);
    }

    public void toggleInspector() {
        if (inspectorVisible && workspaceSplitPane.getWidth() > 0) {
            inspectorWidth = Math.max(200, workspaceSplitPane.getWidth()
                    - workspaceSplitPane.getDividerLocation()
                    - workspaceSplitPane.getDividerSize());
        }
        inspectorRequestedVisible = !inspectorVisible;
        inspectorAutoCollapsed = false;
        setInspectorVisible(inspectorRequestedVisible);
    }

    public void toggleNavigationPanel() {
        if (navigationVisible && splitPane.getWidth() > 0) {
            navigationWidth = Math.max(190, splitPane.getDividerLocation());
        }
        setNavigationVisible(!navigationVisible);
    }

    private void setNavigationVisible(boolean visible) {
        navigationVisible = visible;
        settings.setNavigationPanelVisible(visible);
        workspacePanel.getComponent().setVisible(visible);
        splitPane.setDividerSize(visible ? 5 : 0);
        splitPane.setDividerLocation(visible ? navigationWidth : 0);
        if (toolBar != null) toolBar.setNavigationVisible(visible);
        splitPane.revalidate();
        splitPane.repaint();
        if (visible) workspacePanel.focusSearch();
    }

    public boolean isNavigationVisible() {
        return navigationVisible;
    }

    private void setInspectorVisible(boolean visible) {
        inspectorVisible = visible;
        workspaceSplitPane.getRightComponent().setVisible(inspectorVisible);
        workspaceSplitPane.setDividerSize(inspectorVisible ? 5 : 0);
        if (inspectorVisible) positionInspector();
        if (toolBar != null) toolBar.setInspectorVisible(inspectorVisible);
        workspaceSplitPane.revalidate();
        workspaceSplitPane.repaint();
    }

    private void applyResponsiveLayout() {
        int width = getContentPane().getWidth();
        if (width <= 0) return;

        int maximumNavigation = Math.max(180,
                Math.min(300, (int) (width * 0.24)));
        if (navigationVisible
                && splitPane.getDividerLocation() > maximumNavigation) {
            splitPane.setDividerLocation(maximumNavigation);
        }

        if (width < 1080 && inspectorVisible) {
            inspectorAutoCollapsed = inspectorRequestedVisible;
            setInspectorVisible(false);
        } else if (width >= 1220 && inspectorAutoCollapsed
                && inspectorRequestedVisible) {
            inspectorAutoCollapsed = false;
            setInspectorVisible(true);
        } else if (inspectorVisible) {
            positionInspector();
        }
    }

    public boolean isInspectorVisible() {
        return inspectorVisible;
    }

    private void focusTableSearch() {
        if (workspacePanel != null) workspacePanel.focusSearch();
    }

    public void undoLastEdit() {
        Rom rom = getLastSelectedRom();
        if (rom == null) return;
        try {
            RomEditHistory.getInstance().undo(rom);
        } catch (UserLevelException exception) {
            TableView.showInvalidUserLevelPopup(exception);
        }
    }

    public void redoLastEdit() {
        Rom rom = getLastSelectedRom();
        if (rom == null) return;
        try {
            RomEditHistory.getInstance().redo(rom);
        } catch (UserLevelException exception) {
            TableView.showInvalidUserLevelPopup(exception);
        }
    }

    private void positionInspector() {
        if (!inspectorVisible) return;
        int available = workspaceSplitPane.getWidth();
        if (available <= 0) available = Math.max(590, getWidth() - 250);
        int restoredWidth = Math.max(200,
                Math.min(inspectorWidth, Math.max(200, available - 360)));
        workspaceSplitPane.setDividerLocation(
                Math.max(360, available - restoredWidth));
    }

    private void saveCurrentRom() {
        if (menuBar == null || getLastSelectedRom() == null) return;
        try {
            menuBar.saveImage(false);
            refreshUI();
        } catch (Exception exception) {
            showMessageDialog(this,
                    new DebugPanel(exception, settings.getSupportURL()),
                    rb.getString("EXCEPTION"), ERROR_MESSAGE);
        }
    }

    private void resetCurrentRomChanges() {
        Rom rom = getLastSelectedRom();
        if (rom == null) return;
        int changedCells = RomChangeSummary.countChangedCells(rom);
        if (changedCells == 0) return;
        int answer = javax.swing.JOptionPane.showConfirmDialog(this,
                "Reset " + changedCells + " changed "
                + (changedCells == 1 ? "cell" : "cells")
                + " to the last opened or saved state?\n"
                + "You can undo this operation afterward.",
                "Reset ROM changes",
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.OK_OPTION) return;
        try {
            RomChangeService.resetToSaved(rom);
            refreshUI();
        } catch (UserLevelException exception) {
            TableView.showInvalidUserLevelPopup(exception);
        }
    }

    public void showConnectionCenter() {
        EcuConnectionPanel panel = new EcuConnectionPanel(
                FlashBackendRegistry.getInstance());
        documentWorkspace.openUtility("ecu-connection", "ECU Connection",
                ModernIconFactory.icon(
                        com.romraider.ui.ModernIconFactory.Action.CONNECT),
                panel);
    }

    public void showRomModificationsWorkspace() {
        PlatformContext context = PlatformContext.getInstance();
        RomModificationPanel panel = new RomModificationPanel(
                getLastSelectedRom(), context);
        documentWorkspace.openUtility("rom-modifications", "ROM Modifications",
                ModernIconFactory.icon(
                        com.romraider.ui.ModernIconFactory.Action.TOOLS),
                panel);
    }

    public void showUnifiedSearch() {
        final JDialog dialog = new JDialog(this, "Search RomRaider2", true);
        UnifiedSearchPanel panel = new UnifiedSearchPanel(
                UnifiedSearchIndex.getInstance(),
                new UnifiedSearchPanel.Listener() {
                    public void entryChosen(SearchEntry entry) {
                        dialog.dispose();
                        executeSearchEntry(entry);
                    }

                    public void closeRequested() {
                        dialog.dispose();
                    }
                });
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(480, 320));
        dialog.setLocationRelativeTo(this);
        panel.focusQuery();
        dialog.setVisible(true);
    }

    private void executeSearchEntry(SearchEntry entry) {
        if (entry.getKind() == SearchKind.TABLE) {
            openTableLocation(new TableLocation(entry.getSourceId(),
                    entry.getTargetId()));
        } else if (entry.getKind() == SearchKind.LOGGER_PARAMETER) {
            focusLoggerParameter(entry.getTargetId());
        } else if (entry.getKind() == SearchKind.DTC) {
            launchLogger();
        } else if ("open-rom".equals(entry.getTargetId())) {
            try {
                menuBar.openImageDialog();
            } catch (Exception exception) {
                showMessageDialog(this, new DebugPanel(exception,
                        settings.getSupportURL()), rb.getString("EXCEPTION"),
                        ERROR_MESSAGE);
            }
        } else if ("save-rom".equals(entry.getTargetId())) {
            saveCurrentRom();
        } else if ("open-logger".equals(entry.getTargetId())) {
            launchLogger();
        } else if ("live-data".equals(entry.getTargetId())) {
            showLiveDataWorkspace();
        } else if ("dashboard".equals(entry.getTargetId())) {
            showDashboardWorkspace();
        } else if ("connect-interface".equals(entry.getTargetId())) {
            showConnectionCenter();
        } else if ("compare-roms".equals(entry.getTargetId())) {
            showCompareWorkspace();
        } else if ("display-preferences".equals(entry.getTargetId())) {
            SettingsForm form = new SettingsForm();
            form.selectAppearanceTab();
            form.setLocationRelativeTo(this);
            form.setVisible(true);
        } else if ("toggle-inspector".equals(entry.getTargetId())) {
            toggleInspector();
        } else if ("toggle-navigation".equals(entry.getTargetId())) {
            toggleNavigationPanel();
        } else if ("search-maps".equals(entry.getTargetId())) {
            focusTableSearch();
        } else if ("undo-edit".equals(entry.getTargetId())) {
            undoLastEdit();
        } else if ("redo-edit".equals(entry.getTargetId())) {
            redoLastEdit();
        } else if ("reset-rom-changes".equals(entry.getTargetId())) {
            resetCurrentRomChanges();
        }
    }

    private void registerSearchCommands() {
        List<SearchEntry> entries = new ArrayList<SearchEntry>();
        entries.add(command("open-rom", "Open ROM", "File",
                "Open a ROM image in the editor", "load image", "browse rom"));
        entries.add(command("save-rom", "Save ROM", "File",
                "Save the active ROM", "write file", "save image"));
        entries.add(command("live-data", "Open Live Data", "Logging",
                "Open the integrated live parameter workspace", "datalog",
                "monitor", "trace"));
        entries.add(command("dashboard", "Open Dashboard", "Logging",
                "Open live values and recent trends", "gauges", "monitor"));
        entries.add(command("open-logger", "Configure Logger", "Logging",
                "Connect and select logger parameters", "logger setup",
                "parameters"));
        entries.add(command("connect-interface", "Connect Interface", "ECU",
                "Review detected interfaces and backend capabilities",
                "j2534", "openport"));
        entries.add(command("compare-roms", "Compare ROMs", "Editor",
                "Compare two open ROM images", "difference", "diff"));
        entries.add(command("toggle-inspector", "Show or Hide Inspector", "View",
                "Toggle the right-side inspector", "sidebar", "properties"));
        entries.add(command("toggle-navigation", "Show or Hide Calibrations", "View",
                "Toggle Calibrations, Favorites, and Recent Tables",
                "left sidebar", "browser", "ctrl b"));
        entries.add(command("search-maps", "Search Maps", "Editor",
                "Focus the current ROM map filter", "tables", "ctrl p"));
        entries.add(command("undo-edit", "Undo Last Edit", "Editor",
                "Undo the most recent calibration edit", "ctrl z", "history"));
        entries.add(command("redo-edit", "Redo Last Edit", "Editor",
                "Restore the most recently undone calibration edit",
                "ctrl y", "ctrl shift z"));
        entries.add(command("reset-rom-changes", "Reset ROM Changes", "Editor",
                "Restore all cells to the last opened or saved state",
                "revert rom", "discard changes"));
        entries.add(new SearchEntry(SearchKind.SETTING, "editor:commands",
                "display-preferences", "Display Preferences", "Settings",
                "Change scale, display mode, and appearance",
                java.util.Arrays.asList("theme", "scaling", "appearance")));
        UnifiedSearchIndex.getInstance().replaceSource("editor:commands", entries);
    }

    private static SearchEntry command(String id, String title, String context,
            String description, String... aliases) {
        return new SearchEntry(SearchKind.COMMAND, "editor:commands", id,
                title, context, description, java.util.Arrays.asList(aliases));
    }

    public void showCompareWorkspace() {
        final List<Rom> roms = new ArrayList<Rom>(getImages());
        if (roms.size() < 2) {
            showMessageDialog(this,
                    "Open at least two ROM images before comparing them.",
                    "Compare ROMs", INFORMATION_MESSAGE);
            return;
        }
        JComponent panel = RomComparisonWorkspaceLoader.create(roms,
                new RomComparisonWorkspaceContext.Listener() {
                    public void openComparison(Rom left, Rom right,
                            TableComparison comparison) {
                        openTableComparison(left, right, comparison);
                    }
                });
        if (panel == null) {
            panel = new RomComparePanel(roms,
                    new RomComparePanel.Listener() {
                        public void openComparison(Rom left, Rom right,
                                TableComparison comparison) {
                            openTableComparison(left, right, comparison);
                        }
                    });
        }
        documentWorkspace.openUtility("rom-comparison", "Compare ROMs",
                ModernIconFactory.icon(
                        com.romraider.ui.ModernIconFactory.Action.COMPARE), panel);
    }

    public void showLiveDataWorkspace() {
        LiveDataWorkspacePanel panel = new LiveDataWorkspacePanel(
                new Runnable() {
                    public void run() { launchLogger(); }
                }, new java.util.function.Consumer<String>() {
                    public void accept(String parameterId) {
                        focusLoggerParameter(parameterId);
                    }
                });
        documentWorkspace.openUtility("live-data", "Live Data",
                ModernIconFactory.icon(
                        com.romraider.ui.ModernIconFactory.Action.LOGGER), panel);
    }

    public void showDashboardWorkspace() {
        LiveDashboardPanel panel = new LiveDashboardPanel(new Runnable() {
            public void run() { launchLogger(); }
        });
        documentWorkspace.openUtility("dashboard", "Dashboard",
                ModernIconFactory.icon(
                        com.romraider.ui.ModernIconFactory.Action.DASHBOARD),
                panel);
    }

    private void openTableComparison(Rom left, Rom right,
            TableComparison comparison) {
        if (left == null || right == null || comparison == null
                || !comparison.isAvailableInBoth()) return;
        TableTreeNode leftNode = SwingRomTreeRegistry.nodeFor(left)
                .getTableNodeByName(
                comparison.getTableName());
        TableTreeNode rightNode = SwingRomTreeRegistry.nodeFor(right)
                .getTableNodeByName(
                comparison.getTableName());
        if (leftNode == null || rightNode == null) return;
        ensureTableOpen(leftNode);
        ensureTableOpen(rightNode);
        if (leftNode.getFrame() == null || rightNode.getFrame() == null) return;
        leftNode.getFrame().compareByTable(rightNode.getTable());
        documentWorkspace.select(leftNode.getFrame());
    }

    private void ensureTableOpen(TableTreeNode node) {
        if (node.getFrame() == null) openClosedTable(node);
        else documentWorkspace.select(node.getFrame());
    }

    public ECUEditorToolBar getToolBar() {
        return toolBar;
    }

    public void setToolBar(ECUEditorToolBar toolBar) {
        this.toolBar = toolBar;
    }

    public ECUEditorMenuBar getEditorMenuBar() {
        return menuBar;
    }

    public TableToolBar getTableToolBar() {
        return tableToolBar;
    }

    public void redrawVisableTables(Settings settings) {

    }

    public void setUserLevel(int userLevel) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        settings.setUserLevel(userLevel);
        setUserLevelWorker = new SetUserLevelWorker();
        setUserLevelWorker.addPropertyChangeListener(getStatusPanel());
        setUserLevelWorker.execute();
    }

    public Vector<Rom> getImages() {
        Vector<Rom> images = new Vector<Rom>();
        for (EditorDocument document :
                documentSession.snapshot().getDocuments()) {
            images.add(document.getRom());
        }
        return images;
    }

    public EditorDocumentSession getDocumentSession() {
        return documentSession;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        refreshUI();
    }

    public void refreshUI()
    {
        imageList.updateUI();
        imageList.repaint();
        documentWorkspace.updateUI();
        documentWorkspace.refreshTabStates();
        documentWorkspace.repaint();
        if (workspacePanel != null) workspacePanel.refreshChangedMaps();

        if(getToolBar() != null)
            getToolBar().updateButtons();
        if(getEditorMenuBar() != null)
            getEditorMenuBar().updateMenu();
        inspectorPanel.showRom(getLastSelectedRom());
        if (workbenchStatus != null) workbenchStatus.refreshContext();
    }

    public void refreshAfterNewRom() {
        refreshTableCompareMenus();
        refreshUI();
    }

    public void refreshTableCompareMenus() {
        for(TableFrame frame : getOpenTableFrames()) {
            frame.refreshSimilarOpenTables();
        }
    }

    public void openImage(String filePath){
        openImage(new File(filePath));
    }

    public void openImage(File inputFile){
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        openImageWorker = new OpenImageWorker(inputFile);
        openImageWorker.addPropertyChangeListener(getStatusPanel());
        openImageWorker.execute();
    }

    private void openRecoveredImage(RecoverySnapshot snapshot) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        openImageWorker = new OpenImageWorker(snapshot);
        openImageWorker.addPropertyChangeListener(getStatusPanel());
        openImageWorker.execute();
    }

    public void openImages(File[] inputFiles){
        for(int j = 0; j < inputFiles.length; j++) {
            openImage(inputFiles[j]);
        }
    }

    public void launchLogger() {
        EcuLogger logger = EcuLogger.getEcuLoggerWithoutCreation();
        if (logger != null) {
            logger.showAndFocus();
            return;
        }
        else {
            ThreadUtil.runAsDaemon(new Runnable() {
                @Override
                public void run() {
                    EcuLogger.startLogger(DISPOSE_ON_CLOSE,
                            ECUEditor.this, new String[] {"-logger"});
                }
            });
        }
    }

    private void focusLoggerParameter(String parameterId) {
        LoggerParameterFocusService.getInstance().requestFocus(parameterId);
        launchLogger();
    }

    public RomTreeRootNode getImageRoot() {
        return imageRoot;
    }

    public RomTree getImageList() {
        return imageList;
    }

    public JProgressPane getStatusPanel() {
        return this.statusPanel;
    }

    public MDIDesktopPane getRightPanel() {
        return this.rightPanel;
    }

    public TableFrame getActiveTableFrame() {
        return documentWorkspace.getActiveFrame();
    }

    public List<TableFrame> getOpenTableFrames() {
        return documentWorkspace.getOpenFrames();
    }

    public JScrollPane getLeftScrollPane() {
        return this.leftScrollPane;
    }

    public JScrollPane getRightScrollPane() {
        return this.rightScrollPane;
    }
}

class SetUserLevelWorker extends SwingWorker<Void, Void> {

    @Override
    protected Void doInBackground() throws Exception {
        for(Rom rom : ECUEditorManager.getECUEditor().getImages()) {
            SwingRomTreeRegistry.nodeFor(rom).refreshDisplayedTables();
        }
        return null;
    }

    public void propertyChange(PropertyChangeEvent evnt)
    {
        SwingWorker<?, ?> source = (SwingWorker<?, ?>) evnt.getSource();
        if (null != source && "state".equals( evnt.getPropertyName() )
                && (source.isDone() || source.isCancelled() ) )
        {
            source.removePropertyChangeListener(ECUEditorManager.getECUEditor().getStatusPanel());
        }
    }

    @Override
    public void done() {
        ECUEditor editor = ECUEditorManager.getECUEditor();
        setProgress(0);
        editor.getStatusPanel().ready(ECUEditor.rb.getString("STATUSREADY"));
        editor.setCursor(null);
        editor.refreshUI();
    }
}
