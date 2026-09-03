/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.romraider.editor.workspace.LiveTunePlanProjectionService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.editor.workspace.TableChangeSummary;
import com.romraider.livetune.LiveTuneChange;
import com.romraider.livetune.LiveTuneDraft;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.platform.DimeModState;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ModernTableStyle;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Read-only preview of changed ROM bytes that could form a live-tune plan. */
public final class LiveTunePreviewPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel status = new JLabel("OFFLINE PREVIEW");
    private final JLabel summary = new JLabel("No staged changes");
    private final JLabel tableMetric = metric("0", "TABLES");
    private final JLabel rangeMetric = metric("—", "RAM RANGE");
    private final JLabel byteMetric = metric("0", "BYTES");
    private final JTextArea safety = new JTextArea();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Table", "RAM address", "Bytes", "Before → After"},
            0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable changes = new JTable(model);
    private final JToggleButton selectedScope = new JToggleButton("Selected");
    private final JToggleButton allScope = new JToggleButton("All changed");
    private Rom rom;
    private Table selectedTable;

    public LiveTunePreviewPanel() {
        super(new BorderLayout(0, 10));
        setName("LIVE TUNE PREVIEW");
        setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));

        JLabel title = new JLabel("LIVE TUNE PLAN");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        status.setName("LIVE TUNE PREVIEW STATUS");
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        status.setOpaque(true);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        status.setBackground(UiThemeService.getInstance().color(
                ThemeToken.RAISED_SURFACE));
        status.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(status, BorderLayout.EAST);

        JTextArea explanation = new JTextArea(
                "Review the RAM bytes produced by unsaved table edits. "
                + "This screen cannot connect to or write to an ECU.");
        explanation.setName("LIVE TUNE PREVIEW EXPLANATION");
        explanation.setEditable(false);
        explanation.setFocusable(false);
        explanation.setOpaque(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setRows(2);
        explanation.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));

        JPanel scope = new JPanel(new GridLayout(1, 2, 4, 0));
        selectedScope.setName("PREVIEW SELECTED TABLE");
        allScope.setName("PREVIEW ALL CHANGED TABLES");
        selectedScope.setToolTipText("Preview only the selected table");
        allScope.setToolTipText("Preview every changed table in this ROM");
        ButtonGroup scopes = new ButtonGroup();
        scopes.add(selectedScope);
        scopes.add(allScope);
        selectedScope.setSelected(true);
        selectedScope.addActionListener(event -> refreshPreview());
        allScope.addActionListener(event -> refreshPreview());
        scope.add(selectedScope);
        scope.add(allScope);

        JPanel header = new JPanel(new BorderLayout(0, 7));
        header.add(titleRow, BorderLayout.NORTH);
        header.add(explanation, BorderLayout.CENTER);
        header.add(scope, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new GridLayout(1, 3, 6, 0));
        metrics.setName("LIVE TUNE PREVIEW METRICS");
        metrics.add(metricCard(tableMetric));
        metrics.add(metricCard(rangeMetric));
        metrics.add(metricCard(byteMetric));

        changes.setName("LIVE TUNE CHANGE TABLE");
        ModernTableStyle.apply(changes);
        changes.setRowSelectionAllowed(true);
        changes.setColumnSelectionAllowed(false);
        changes.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        changes.getColumnModel().getColumn(0).setPreferredWidth(130);
        changes.getColumnModel().getColumn(1).setPreferredWidth(88);
        changes.getColumnModel().getColumn(2).setPreferredWidth(46);
        changes.getColumnModel().getColumn(3).setPreferredWidth(190);
        DefaultTableCellRenderer right = new ModernTableStyle.ZebraRenderer();
        right.setHorizontalAlignment(JLabel.RIGHT);
        changes.getColumnModel().getColumn(2).setCellRenderer(right);

        JPanel center = new JPanel(new BorderLayout(0, 7));
        center.add(metrics, BorderLayout.NORTH);
        center.add(new JScrollPane(changes), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        safety.setName("LIVE TUNE SAFETY SUMMARY");
        safety.setEditable(false);
        safety.setFocusable(false);
        safety.setOpaque(false);
        safety.setLineWrap(true);
        safety.setWrapStyleWord(true);
        safety.setRows(5);
        safety.setBorder(BorderFactory.createTitledBorder("SAFETY GATES"));
        safety.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel footer = new JPanel(new BorderLayout(6, 6));
        footer.add(summary, BorderLayout.NORTH);
        footer.add(safety, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh preview",
                ModernIconFactory.icon(Action.REFRESH));
        refresh.setName("REFRESH LIVE TUNE PREVIEW");
        refresh.setToolTipText("Recalculate the read-only preview");
        refresh.addActionListener(event -> refreshPreview());
        JPanel refreshRow = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 0, 0));
        refreshRow.add(refresh);
        footer.add(refreshRow, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        getAccessibleContext().setAccessibleDescription(
                "Read-only preview of changed ROM bytes and live-tuning safety gates");
        refreshPreview();
    }

    public void showSelection(Rom rom, Table table) {
        this.rom = rom;
        this.selectedTable = table != null && table.getRom() == rom
                ? table : null;
        refreshPreview();
    }

    public void platformContextChanged() {
        refreshSafety(null);
    }

    void refreshPreview() {
        model.setRowCount(0);
        LiveTuneDraft draft = null;
        String emptyReason = null;
        try {
            Collection<Table> scope = previewScope();
            if (rom == null) emptyReason = "Open a ROM to build a preview.";
            else if (scope.isEmpty()) emptyReason = allScope.isSelected()
                    ? "This ROM has no changed calibration tables."
                    : "Select a changed calibration table.";
            else draft = LiveTunePlanProjectionService.preview(rom, scope);
        } catch (IllegalStateException noChanges) {
            emptyReason = noChanges.getMessage();
        } catch (IllegalArgumentException unavailable) {
            emptyReason = unavailable.getMessage();
        }

        if (draft == null) {
            setStatus("OFFLINE PREVIEW", ThemeToken.SECONDARY_TEXT);
            summary.setText(emptyReason == null
                    ? "No staged changes" : emptyReason);
            tableMetric.setText("0");
            rangeMetric.setText("—");
            byteMetric.setText("0");
            refreshSafety(null);
            return;
        }

        for (LiveTuneChange change : draft.getChanges()) {
            model.addRow(new Object[] {change.getTableName(),
                    String.format("0x%06X", change.getAddress()),
                    change.getLength(), changeText(change)});
        }
        setStatus("READY TO REVIEW", ThemeToken.SUCCESS);
        summary.setText(draft.getChanges().size() + " byte range"
                + (draft.getChanges().size() == 1 ? "" : "s")
                + " staged for offline review");
        tableMetric.setText(String.valueOf(draft.getTableCount()));
        rangeMetric.setText(String.format("%06X–%06X",
                draft.getStartAddress(), draft.getEndAddress()));
        byteMetric.setText(String.valueOf(draft.getTotalBytes()));
        refreshSafety(draft);
    }

    private Collection<Table> previewScope() {
        if (rom == null) return Collections.emptyList();
        if (!allScope.isSelected()) {
            return selectedTable == null ? Collections.emptyList()
                    : Collections.singletonList(selectedTable);
        }
        List<Table> tables = new ArrayList<Table>();
        for (TableChangeSummary changed : RomChangeSummary.summarize(rom)) {
            Table table = rom.getTableByName(changed.getTableName());
            if (table != null) tables.add(table);
        }
        return tables;
    }

    private void refreshSafety(LiveTuneDraft draft) {
        PlatformContext context = PlatformContext.getInstance();
        boolean subaru = context.getPlatform() == VehiclePlatform.SUBARU;
        boolean engine = context.getModule() == VehicleModule.ENGINE_ECU;
        boolean dimeMod = context.getDimeModState() == DimeModState.ACTIVE;
        boolean runtime = context.isRamTuneRuntimeAvailable();
        boolean metadata = context.hasQualifiedRamTuneMetadata();
        safety.setText(line(draft != null, "Mapped changed bytes") + "\n"
                + line(subaru && engine, "Subaru engine ECU selected") + "\n"
                + line(dimeMod, "DimeMod runtime active") + "\n"
                + line(runtime, "RAM Tune reported by the runtime") + "\n"
                + line(metadata, "RAM Tune signature and LUT metadata valid")
                + context.getRamTuneRuntimeMetadata().map(value -> " — "
                        + value.getDisplaySummary()).orElse("") + "\n"
                + "○ Exact ECU identity is required during a future connection");
        safety.setCaretPosition(0);
    }

    private static String line(boolean pass, String text) {
        return (pass ? "● " : "○ ") + text;
    }

    private void setStatus(String text, ThemeToken token) {
        status.setText(text);
        status.setForeground(UiThemeService.getInstance().color(token));
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiThemeService.getInstance()
                        .color(token)),
                BorderFactory.createEmptyBorder(3, 7, 3, 7)));
    }

    private static JPanel metricCard(JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiThemeService.getInstance()
                        .color(ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(6, 7, 6, 7)));
        card.add(value, BorderLayout.CENTER);
        JLabel caption = new JLabel(value.getName());
        caption.setFont(caption.getFont().deriveFont(Font.BOLD,
                Math.max(9.0f, caption.getFont().getSize2D() - 2.0f)));
        caption.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        card.add(caption, BorderLayout.SOUTH);
        return card;
    }

    private static JLabel metric(String value, String name) {
        JLabel label = new JLabel(value);
        label.setName(name);
        label.setFont(label.getFont().deriveFont(Font.BOLD,
                label.getFont().getSize2D() + 1.0f));
        return label;
    }

    private static String changeText(LiveTuneChange change) {
        return hex(change.getExpected()) + " → " + hex(change.getReplacement());
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        int shown = Math.min(8, bytes.length);
        for (int index = 0; index < shown; index++) {
            if (index > 0) text.append(' ');
            text.append(String.format("%02X", bytes[index] & 0xFF));
        }
        if (shown < bytes.length) text.append(" …");
        return text.toString();
    }
}
