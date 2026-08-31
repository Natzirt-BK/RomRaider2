/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.Table3D;
import com.romraider.editor.recovery.RecoveryState;
import com.romraider.editor.recovery.RomRecoveryService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.editor.workspace.TableChangeSummary;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.PlatformContextListener;
import com.romraider.platform.VehiclePlatform;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.swing.JProgressPane;

/** Persistent ROM identity, state, and primary actions for the Editor shell. */
public final class EditorStatusBar extends JPanel implements PlatformContextListener {
    public interface Actions {
        void resetChanges();
        default void showDimeModFeatures() { }
    }

    private static final long serialVersionUID = 1L;
    private final JLabel identity = new JLabel("No ROM open");
    private final JLabel state = new JLabel("● NO ROM");
    private final JLabel context = new JLabel("Ready");
    private final JLabel recovery = new JLabel();
    private final JButton dimeMod = new JButton();
    private final JButton reset = new JButton("Reset Changes",
            ModernIconFactory.icon(Action.UNDO));
    private final JPanel actionPanel = new JPanel(new java.awt.FlowLayout(
            java.awt.FlowLayout.RIGHT, 8, 0));
    private String fullIdentity = "No ROM open";
    private String fullContext = "";
    private Rom currentRom;
    private Table currentTable;
    private int lastChangedCells = -1;
    private final Timer changeMonitor;
    private boolean platformContextAttached;

    public EditorStatusBar(final Actions actions) {
        this(actions, new JProgressPane());
    }

    public EditorStatusBar(final Actions actions, JProgressPane progressStatus) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        javax.swing.UIManager.getColor("controlShadow")),
                BorderFactory.createEmptyBorder(2, 8, 1, 8)));
        identity.setName("ROM IDENTITY");
        state.setName("ROM LOAD STATE");
        identity.setVerticalAlignment(JLabel.CENTER);
        state.setVerticalAlignment(JLabel.CENTER);
        state.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        recovery.setName("ROM RECOVERY STATUS");
        recovery.setVerticalAlignment(JLabel.CENTER);
        recovery.setVisible(false);
        // Keep activity and ROM identity in one non-wrapping row so the bottom
        // bar never grows or clips a second status line.
        progressStatus.addStatusComponent(state);
        progressStatus.addStatusComponent(identity);
        progressStatus.addStatusComponent(recovery);
        context.setName("VEHICLE AND ROM CONTEXT");
        context.setHorizontalAlignment(JLabel.CENTER);
        dimeMod.setName("DIMEMOD STATUS CHIP");
        dimeMod.setMargin(new Insets(0, 7, 0, 7));
        dimeMod.setFocusable(false);
        dimeMod.setToolTipText("Open detected DimeMod features");
        dimeMod.addActionListener(event -> actions.showDimeModFeatures());
        JPanel contextPanel = new JPanel(new java.awt.BorderLayout(6, 0));
        contextPanel.setOpaque(false);
        contextPanel.add(context, java.awt.BorderLayout.CENTER);
        contextPanel.add(dimeMod, java.awt.BorderLayout.EAST);
        actionPanel.setOpaque(false);
        actionPanel.add(reset);
        progressStatus.setMinimumSize(new Dimension(0, 24));
        context.setMinimumSize(new Dimension(0, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 0.48;
        add(progressStatus, constraints);
        constraints.gridx = 1;
        constraints.weightx = 0.52;
        constraints.insets = new Insets(0, 10, 0, 10);
        add(contextPanel, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0.0;
        constraints.insets = new Insets(0, 0, 0, 0);
        add(actionPanel, constraints);
        reset.setEnabled(false);
        reset.setMargin(new Insets(0, 8, 0, 8));
        Dimension resetSize = reset.getPreferredSize();
        reset.setPreferredSize(new Dimension(resetSize.width,
                Math.max(20, resetSize.height / 2)));
        reset.addActionListener(event -> actions.resetChanges());
        reset.setName("RESET ROM CHANGES");
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                updateResponsiveText();
            }
        });
        changeMonitor = new Timer(1200, event -> refreshChangedCells());
        changeMonitor.setRepeats(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!platformContextAttached) {
            platformContextAttached = true;
            PlatformContext.getInstance().addListener(this);
        }
        changeMonitor.start();
    }

    @Override
    public void removeNotify() {
        changeMonitor.stop();
        if (platformContextAttached) {
            PlatformContext.getInstance().removeListener(this);
            platformContextAttached = false;
        }
        super.removeNotify();
    }

    public void showRom(Rom rom) {
        currentRom = rom;
        currentTable = null;
        render();
    }

    public void showTable(Rom rom, Table table) {
        currentRom = rom;
        currentTable = table;
        render();
    }

    public void refreshContext() {
        render();
    }

    public void showRecoveryState(Rom rom, RecoveryState recoveryState) {
        if (rom == currentRom) renderRecovery(recoveryState);
    }

    public void platformContextChanged(PlatformContext ignored) {
        if (SwingUtilities.isEventDispatchThread()) render();
        else SwingUtilities.invokeLater(new Runnable() {
            public void run() { render(); }
        });
    }

    private void render() {
        renderDimeModChip();
        Rom rom = currentRom;
        if (rom == null) {
            lastChangedCells = 0;
            state.setText("● NO ROM");
            state.setForeground(UiThemeService.getInstance().color(
                    ThemeToken.SECONDARY_TEXT));
            state.setToolTipText("Open a ROM to view its change state");
            fullIdentity = "No ROM open";
            renderRecovery(RecoveryState.IDLE);
            fullContext = platformText();
            updateResponsiveText();
            reset.setEnabled(false);
            return;
        }
        List<TableChangeSummary> changes = RomChangeSummary.summarize(rom);
        lastChangedCells = totalChangedCells(changes);
        boolean binaryChanges = RomChangeService.hasBinaryChanges(rom);
        state.setText(lastChangedCells > 0
                ? "● " + lastChangedCells + " CHANGED"
                : binaryChanges ? "● UNSAVED RECOVERY" : "● ROM LOADED");
        state.setForeground(UiThemeService.getInstance().color(
                lastChangedCells == 0 && !binaryChanges
                        ? ThemeToken.SUCCESS : ThemeToken.DANGER));
        state.setToolTipText(binaryChanges && lastChangedCells == 0
                ? "Recovered ROM data must be saved to a new file or discarded"
                : changeTooltip(changes));
        fullIdentity = "ROM: " + rom.getFileName();
        renderRecovery(RomRecoveryService.getInstance().getState(rom));
        fullContext = platformText() + "   •   " + rom.getTables().size()
                + " tables   •   " + (rom.getRealFileSize() / 1024) + " KB"
                + "   •   " + checksumText(rom);
        if (currentTable != null) {
            fullContext = fullContext + "   •   "
                    + tableDimensions(currentTable) + "   •   "
                    + (currentTable.isLocked() ? "Locked" : "Editable");
        }
        updateResponsiveText();
        reset.setEnabled(lastChangedCells > 0);
        reset.setToolTipText(lastChangedCells > 0
                ? "Restore all changed cells to the last opened or saved state"
                : "No calibration changes to reset");
    }

    private void renderDimeModChip() {
        PlatformContext platform = PlatformContext.getInstance();
        boolean visible = platform.getPlatform() == VehiclePlatform.SUBARU;
        dimeMod.setVisible(visible);
        dimeMod.setText("DimeMod: "
                + platform.getDimeModState().getDisplayName());
        dimeMod.setEnabled(visible);
    }

    private void refreshChangedCells() {
        int changed = RomChangeSummary.countChangedCells(currentRom);
        if (changed != lastChangedCells) render();
    }

    private void renderRecovery(RecoveryState recoveryState) {
        RecoveryState display = recoveryState == null
                ? RecoveryState.IDLE : recoveryState;
        recovery.setVisible(display != RecoveryState.IDLE);
        recovery.setToolTipText("Recovery snapshots are stored separately; "
                + "the opened ROM file is never overwritten");
        switch (display) {
            case SCHEDULED:
                recovery.setText("○ RECOVERY QUEUED");
                recovery.setForeground(UiThemeService.getInstance().color(
                        ThemeToken.SECONDARY_TEXT));
                break;
            case SAVED:
                recovery.setText("● RECOVERY SAVED");
                recovery.setForeground(UiThemeService.getInstance().color(
                        ThemeToken.SUCCESS));
                break;
            case FAILED:
                recovery.setText("● RECOVERY FAILED");
                recovery.setForeground(UiThemeService.getInstance().color(
                        ThemeToken.DANGER));
                break;
            default:
                recovery.setText("");
                break;
        }
    }

    void updateResponsiveText() {
        int width = getWidth();
        if (width <= 0) {
            identity.setText(fullIdentity);
            context.setText(fullContext);
            return;
        }
        int actionWidth = actionPanel.getPreferredSize().width;
        int available = Math.max(240, width - actionWidth - 36);
        int leftWidth = Math.max(170, (int) (available * 0.48));
        int fixedLeftWidth = 20;
        java.awt.Container row = identity.getParent();
        if (row != null) {
            for (Component component : row.getComponents()) {
                if (component != identity && component.isVisible()) {
                    fixedLeftWidth += component.getPreferredSize().width;
                }
            }
        }
        int identityWidth = Math.max(70, leftWidth - fixedLeftWidth);
        int contextWidth = Math.max(100, available - leftWidth - 20);
        identity.setText(elide(fullIdentity, identityWidth,
                identity.getFontMetrics(identity.getFont())));
        context.setText(elide(fullContext, contextWidth,
                context.getFontMetrics(context.getFont())));
        identity.setToolTipText(fullIdentity);
        context.setToolTipText(fullContext);
    }

    static String elide(String text, int availableWidth,
            FontMetrics metrics) {
        if (text == null || metrics.stringWidth(text) <= availableWidth) {
            return text;
        }
        String ellipsis = "…";
        int limit = Math.max(0, availableWidth
                - metrics.stringWidth(ellipsis));
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (metrics.stringWidth(text.substring(0, middle)) <= limit) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, low) + ellipsis;
    }

    private static int totalChangedCells(List<TableChangeSummary> changes) {
        int total = 0;
        for (TableChangeSummary change : changes) total += change.getChangedCells();
        return total;
    }

    private static String changeTooltip(List<TableChangeSummary> changes) {
        if (changes.isEmpty()) return "No unsaved calibration cell changes";
        StringBuilder tooltip = new StringBuilder("<html><b>Unsaved changes</b>");
        int shown = Math.min(5, changes.size());
        for (int index = 0; index < shown; index++) {
            TableChangeSummary change = changes.get(index);
            tooltip.append("<br>").append(escape(change.getTableName()))
                    .append(": ").append(change.getChangedCells());
        }
        if (changes.size() > shown) {
            tooltip.append("<br>+").append(changes.size() - shown)
                    .append(" more tables");
        }
        return tooltip.append("</html>").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String platformText() {
        PlatformContext current = PlatformContext.getInstance();
        return current.getPlatform() + "   •   " + current.getModule();
    }

    private static String tableDimensions(Table table) {
        if (table instanceof Table3D) {
            Table3D surface = (Table3D) table;
            return surface.getSizeX() + " × " + surface.getSizeY();
        }
        return table.getDataSize() + " values";
    }

    private static String checksumText(Rom rom) {
        int count = rom.getTotalAmountOfChecksums();
        if (count == 0) return "Checksum not defined";
        return count + (count == 1 ? " checksum managed" : " checksums managed");
    }
}
