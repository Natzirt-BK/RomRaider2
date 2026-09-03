/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import com.romraider.maps.Rom;
import com.romraider.platform.DimeModFeature;
import com.romraider.platform.DimeModFeatureDetector;
import com.romraider.platform.DimeModState;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.RomModification;
import com.romraider.platform.RomModificationDetector;
import com.romraider.platform.RomModificationEvidence;
import com.romraider.platform.RomModificationFeature;
import com.romraider.platform.RomModificationFeatureDetector;
import com.romraider.platform.VehiclePlatform;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.ModernTableStyle;
import com.romraider.ui.UiThemeService;

/** Truthful definition and runtime view for supported Subaru ROM mods. */
public final class RomModificationPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public RomModificationPanel(Rom rom, PlatformContext context) {
        super(new BorderLayout(0, 10));
        setName("ROM MODIFICATION PANEL");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel heading = new JLabel("ROM modifications");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD,
                heading.getFont().getSize2D() + 2.0f));
        JLabel scope = new JLabel(context.getPlatform() == VehiclePlatform.SUBARU
                ? rom == null ? "Open a Subaru ROM to inspect its definition."
                        : "Definition evidence for " + rom.getFileName()
                : "ROM modification detection is not available for this platform.");
        scope.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel titles = new JPanel(new BorderLayout(0, 4));
        titles.add(heading, BorderLayout.NORTH);
        titles.add(scope, BorderLayout.SOUTH);
        JPanel header = new JPanel(new BorderLayout(0, 9));
        header.add(titles, BorderLayout.NORTH);
        header.add(summary(rom, context), BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel tables = new JPanel(new BorderLayout(0, 10));
        JScrollPane modifications = modificationTable(rom, context);
        modifications.setPreferredSize(new Dimension(560, 135));
        tables.add(modifications, BorderLayout.NORTH);
        tables.add(mappedFeatureTable(rom, context), BorderLayout.CENTER);
        add(tables, BorderLayout.CENTER);

        JLabel safety = new JLabel("Definition evidence identifies mapped "
                + "content only. It does not verify the ROM running on a "
                + "connected ECU.");
        safety.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        safety.setToolTipText(safety.getText());
        add(safety, BorderLayout.SOUTH);
    }

    private static JPanel summary(Rom rom, PlatformContext context) {
        Map<RomModification, RomModificationEvidence> families =
                RomModificationDetector.detect(rom);
        int familyCount = 0;
        for (RomModificationEvidence evidence : families.values()) {
            if (evidence.isDetected()) familyCount++;
        }
        int featureCount = 0;
        for (Boolean mapped : RomModificationFeatureDetector.detect(rom)
                .values()) {
            if (mapped.booleanValue()) featureCount++;
        }
        if (families.get(RomModification.DIME_MOD).isDetected()) {
            for (Boolean mapped : DimeModFeatureDetector.detect(rom).values()) {
                if (mapped.booleanValue()) featureCount++;
            }
        }
        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        summary.add(chip(familyCount + (familyCount == 1
                ? " MOD FOUND" : " MODS FOUND"), ThemeToken.LIVE_TRACE));
        summary.add(chip(featureCount + (featureCount == 1
                ? " FEATURE MAPPED" : " FEATURES MAPPED"),
                ThemeToken.SUCCESS));
        boolean verified = context.getDimeModState() == DimeModState.ACTIVE;
        summary.add(chip(verified ? "ECU SESSION VERIFIED"
                : "ECU SESSION NOT VERIFIED",
                verified ? ThemeToken.SUCCESS : ThemeToken.WARNING));
        return summary;
    }

    private static JLabel chip(String text, ThemeToken foreground) {
        JLabel chip = new JLabel(text);
        chip.setOpaque(true);
        chip.setFont(chip.getFont().deriveFont(Font.BOLD,
                Math.max(10.0f, chip.getFont().getSize2D() - 1.0f)));
        chip.setForeground(UiThemeService.getInstance().color(foreground));
        chip.setBackground(UiThemeService.getInstance().color(
                ThemeToken.RAISED_SURFACE));
        chip.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
        return chip;
    }

    private static JScrollPane modificationTable(Rom rom,
            PlatformContext context) {
        DefaultTableModel model = readOnlyModel(
                "Modification", "Loaded ROM", "ECU session");
        Map<RomModification, RomModificationEvidence> detected =
                RomModificationDetector.detect(rom);
        for (RomModification modification : RomModification.values()) {
            RomModificationEvidence evidence = detected.get(modification);
            model.addRow(new Object[] {modification.getDisplayName(),
                    evidence.getDisplayName(), runtimeStatus(modification,
                            context)});
        }
        JTable table = new JTable(model);
        table.setName("ROM MODIFICATIONS");
        prepare(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setCellRenderer(
                new EvidenceRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(
                new EvidenceRenderer());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Detected families"));
        return scroll;
    }

    private static JScrollPane mappedFeatureTable(Rom rom,
            PlatformContext context) {
        DefaultTableModel model = readOnlyModel("ROM modification", "Feature",
                "Definition evidence");
        Map<RomModification, RomModificationEvidence> families =
                RomModificationDetector.detect(rom);
        Map<DimeModFeature, Boolean> dimeModFeatures =
                DimeModFeatureDetector.detect(rom);
        if (families.get(RomModification.DIME_MOD).isDetected()) {
            for (DimeModFeature feature : DimeModFeature.values()) {
                model.addRow(new Object[] {RomModification.DIME_MOD
                        .getDisplayName(), feature.getDisplayName(),
                        DimeModFeaturePanel.status(feature,
                            dimeModFeatures.get(feature).booleanValue(),
                            context.getDimeModState(), context.getPlatform(),
                            context.isRamTuneRuntimeAvailable())});
            }
        }
        Map<RomModificationFeature, Boolean> features =
                RomModificationFeatureDetector.detect(rom);
        for (RomModificationFeature feature
                : RomModificationFeature.values()) {
            if (!families.get(feature.getModification()).isDetected()) {
                continue;
            }
            model.addRow(new Object[] {
                    feature.getModification().getDisplayName(),
                    feature.getDisplayName(),
                    features.get(feature).booleanValue()
                            ? "Mapped in loaded definition" : "Not mapped"});
        }
        if (model.getRowCount() == 0) {
            model.addRow(new Object[] {"None detected", "—",
                    "Open a ROM with explicit DimeMod, CarBerry, or MerpMod markers"});
        }
        JTable table = new JTable(model);
        table.setName("ROM MODIFICATION FEATURES");
        prepare(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(175);
        table.getColumnModel().getColumn(2).setPreferredWidth(290);
        table.getColumnModel().getColumn(2).setCellRenderer(
                new EvidenceRenderer());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(
                "Mapped features"));
        return scroll;
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static void prepare(JTable table) {
        ModernTableStyle.apply(table);
        table.setRowSelectionAllowed(false);
        table.setDefaultRenderer(Object.class, new BaseRenderer());
    }

    private static final class EvidenceRenderer
            extends BaseRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean selected, boolean focus, int row,
                int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    value, selected, focus, row, column);
            label.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
            if (!selected) {
                String text = value == null ? "" : value.toString();
                ThemeToken token = isPositive(text) ? ThemeToken.SUCCESS
                        : isUnavailable(text) ? ThemeToken.SECONDARY_TEXT
                        : ThemeToken.WARNING;
                label.setForeground(UiThemeService.getInstance().color(token));
            }
            return label;
        }

        private static boolean isPositive(String text) {
            return text.startsWith("Mapped") || text.startsWith("Active")
                    || text.contains("identity match")
                    || text.contains("Branded tables");
        }

        private static boolean isUnavailable(String text) {
            return text.startsWith("Not detected")
                    || text.startsWith("Not mapped");
        }
    }

    private static class BaseRenderer
            extends ModernTableStyle.ZebraRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean selected, boolean focus, int row,
                int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    value, selected, focus, row, column);
            return label;
        }
    }

    private static String runtimeStatus(RomModification modification,
            PlatformContext context) {
        if (context.getPlatform() != VehiclePlatform.SUBARU) {
            return "Not applicable";
        }
        if (modification != RomModification.DIME_MOD) {
            return "Not queried — definition evidence only";
        }
        DimeModState state = context.getDimeModState();
        if (state == DimeModState.ACTIVE
                && context.isRamTuneRuntimeAvailable()) {
            return "Active — RAM Tune advertised; writes disabled";
        }
        return state.getDisplayName();
    }
}
