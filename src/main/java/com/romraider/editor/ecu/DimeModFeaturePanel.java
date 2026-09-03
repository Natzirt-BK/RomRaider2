/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Component;
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
import com.romraider.platform.VehiclePlatform;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.ModernTableStyle;
import com.romraider.ui.UiThemeService;

/** Truthful definition/runtime capability view for Subaru DimeMod work. */
public final class DimeModFeaturePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public DimeModFeaturePanel(Rom rom, PlatformContext context) {
        super(new BorderLayout(0, 10));
        setName("DIMEMOD FEATURE PANEL");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        DimeModState state = context.getDimeModState();
        JLabel heading = new JLabel("DimeMod: " + state.getDisplayName());
        heading.setFont(heading.getFont().deriveFont(Font.BOLD,
                heading.getFont().getSize2D() + 2.0f));
        JLabel scope = new JLabel(context.getPlatform() == VehiclePlatform.SUBARU
                ? rom == null ? "Open a Subaru ROM to inspect mapped features."
                        : "Definition evidence for " + rom.getFileName()
                : "DimeMod is not applicable to the selected platform.");
        scope.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.add(heading, BorderLayout.NORTH);
        header.add(scope, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new Object[] {"Feature", "Availability"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        Map<DimeModFeature, Boolean> detected =
                DimeModFeatureDetector.detect(rom);
        for (DimeModFeature feature : DimeModFeature.values()) {
            model.addRow(new Object[] {feature.getDisplayName(), status(feature,
                    detected.get(feature).booleanValue(), state,
                    context.getPlatform(),
                    context.isRamTuneRuntimeAvailable())});
        }
        JTable table = new JTable(model);
        table.setName("DIMEMOD FEATURES");
        ModernTableStyle.apply(table);
        table.setRowSelectionAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setCellRenderer(
                new AvailabilityRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel safety = new JLabel("RAM writes remain disabled; mapped means "
                + "the loaded definition contains matching tables, not that "
                + "the connected ECU has been runtime-verified.");
        safety.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        safety.setToolTipText(safety.getText());
        add(safety, BorderLayout.SOUTH);
    }

    private static final class AvailabilityRenderer
            extends ModernTableStyle.ZebraRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean selected, boolean focus, int row,
                int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    value, selected, focus, row, column);
            if (!selected) {
                String text = value == null ? "" : value.toString();
                ThemeToken token = text.startsWith("Mapped in")
                        ? ThemeToken.SUCCESS
                        : text.startsWith("Mapped ") ? ThemeToken.WARNING
                        : ThemeToken.SECONDARY_TEXT;
                label.setForeground(UiThemeService.getInstance().color(token));
            }
            return label;
        }
    }

    static String status(DimeModFeature feature, boolean detected,
            DimeModState state, VehiclePlatform platform,
            boolean ramTuneRuntimeAvailable) {
        if (platform != VehiclePlatform.SUBARU) return "Not applicable";
        if (feature == DimeModFeature.RAM_TUNE) {
            if (!detected) return "Not mapped in loaded definition";
            if (state != DimeModState.ACTIVE) {
                return "Mapped — runtime not verified";
            }
            return ramTuneRuntimeAvailable
                    ? "Mapped and runtime verified — writes disabled"
                    : "Mapped — runtime feature unavailable";
        }
        if (detected) return "Mapped in loaded definition";
        if (state == DimeModState.NOT_PRESENT) return "Unavailable";
        if (state == DimeModState.ACTIVE || state == DimeModState.PRESENT) {
            return "Not detected in loaded definition";
        }
        return "Unknown";
    }
}
