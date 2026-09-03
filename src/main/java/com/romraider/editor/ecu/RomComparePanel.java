/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import com.romraider.editor.compare.RomComparisonResult;
import com.romraider.editor.compare.RomComparisonService;
import com.romraider.editor.compare.TableComparison;
import com.romraider.editor.compare.TableComparisonStatus;
import com.romraider.maps.Rom;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ModernTableStyle;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.ui.swing.ResponsiveFlowLayout;

/** Responsive compare document hosted inside the main editor workspace. */
public final class RomComparePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public interface Listener {
        void openComparison(Rom left, Rom right, TableComparison comparison);
    }

    private final JComboBox<Rom> leftRom = new JComboBox<Rom>();
    private final JComboBox<Rom> rightRom = new JComboBox<Rom>();
    private final JCheckBox differencesOnly = new JCheckBox("Differences only", true);
    private final JLabel summary = new JLabel("Select two ROMs to compare");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Status", "Calibration table"}, 0) {
        private static final long serialVersionUID = 1L;
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable results = new JTable(model);
    private final JButton open = new JButton("Open Selected Comparison");
    private final List<TableComparison> comparisons =
            new ArrayList<TableComparison>();
    private final Listener listener;

    public RomComparePanel(List<Rom> roms, Listener listener) {
        super(new BorderLayout(0, 10));
        this.listener = listener;
        setName("ROM COMPARE WORKSPACE");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Compare ROMs");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 3.0f));
        JLabel help = new JLabel(
                "Compare calibration tables and open modified maps side by side.");
        help.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel heading = new JPanel(new BorderLayout(0, 3));
        heading.add(title, BorderLayout.NORTH);
        heading.add(help, BorderLayout.SOUTH);

        JPanel selectors = new JPanel(new ResponsiveFlowLayout(
                FlowLayout.LEFT, 8, 4));
        selectors.setName("ROM COMPARE SELECTORS");
        selectors.add(new JLabel("Left ROM"));
        selectors.add(leftRom);
        selectors.add(new JLabel("Right ROM"));
        selectors.add(rightRom);
        JButton compare = new JButton("Compare",
                ModernIconFactory.icon(Action.COMPARE));
        compare.setName("COMPARE SELECTED ROMS");
        selectors.add(compare);
        selectors.add(differencesOnly);

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.add(heading, BorderLayout.NORTH);
        north.add(selectors, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        results.setName("ROM COMPARISON RESULTS");
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ModernTableStyle.apply(results);
        results.getColumnModel().getColumn(0).setPreferredWidth(135);
        results.getColumnModel().getColumn(0).setMaxWidth(180);
        results.getColumnModel().getColumn(0).setCellRenderer(
                new StatusRenderer());
        results.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) openSelected();
            }
        });
        results.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    openSelected();
                    event.consume();
                }
            }
        });
        results.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateOpenAction();
        });
        add(new JScrollPane(results), BorderLayout.CENTER);

        open.setName("OPEN SELECTED ROM COMPARISON");
        open.addActionListener(event -> openSelected());
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        summary.setName("ROM COMPARISON SUMMARY");
        footer.add(summary, BorderLayout.CENTER);
        footer.add(open, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        RomRenderer renderer = new RomRenderer();
        leftRom.setRenderer(renderer);
        rightRom.setRenderer(renderer);
        if (roms != null) {
            for (Rom rom : roms) {
                leftRom.addItem(rom);
                rightRom.addItem(rom);
            }
        }
        if (rightRom.getItemCount() > 1) rightRom.setSelectedIndex(1);
        compare.addActionListener(event -> compareSelected());
        differencesOnly.addActionListener(event -> renderRows());
        compareSelected();
    }

    private void compareSelected() {
        Rom left = (Rom) leftRom.getSelectedItem();
        Rom right = (Rom) rightRom.getSelectedItem();
        RomComparisonResult result = RomComparisonService.compare(left, right);
        comparisons.clear();
        comparisons.addAll(result.getTables());
        summary.setText(result.isIdentical()
                ? result.getEqualCount() + " unchanged tables • ROM calibrations match"
                : result.getDifferentCount() + " modified • "
                  + result.getMissingCount() + " missing • "
                  + result.getEqualCount() + " unchanged");
        summary.setForeground(UiThemeService.getInstance().color(
                result.isIdentical() ? ThemeToken.SUCCESS : ThemeToken.PRIMARY_TEXT));
        renderRows();
    }

    private void renderRows() {
        model.setRowCount(0);
        for (TableComparison comparison : comparisons) {
            if (differencesOnly.isSelected()
                    && comparison.getStatus() == TableComparisonStatus.EQUAL) continue;
            model.addRow(new Object[] {comparison, comparison.getTableName()});
        }
        if (model.getRowCount() > 0) results.setRowSelectionInterval(0, 0);
        else updateOpenAction();
    }

    private void openSelected() {
        int row = results.getSelectedRow();
        if (row < 0 || listener == null) return;
        Object value = model.getValueAt(results.convertRowIndexToModel(row), 0);
        if (!(value instanceof TableComparison)) return;
        TableComparison comparison = (TableComparison) value;
        if (!comparison.isAvailableInBoth()) return;
        listener.openComparison((Rom) leftRom.getSelectedItem(),
                (Rom) rightRom.getSelectedItem(), comparison);
    }

    private void updateOpenAction() {
        int row = results.getSelectedRow();
        if (row < 0 || listener == null) {
            open.setEnabled(false);
            return;
        }
        Object value = model.getValueAt(results.convertRowIndexToModel(row), 0);
        open.setEnabled(value instanceof TableComparison
                && ((TableComparison) value).isAvailableInBoth());
    }

    private static final class StatusRenderer
            extends ModernTableStyle.ZebraRenderer {
        private static final long serialVersionUID = 1L;
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            TableComparison comparison = (TableComparison) value;
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    comparison.getStatus().getDisplayName(), selected, focus,
                    row, column);
            if (!selected) {
                ThemeToken token = comparison.getStatus() == TableComparisonStatus.EQUAL
                        ? ThemeToken.SUCCESS
                        : comparison.getStatus() == TableComparisonStatus.DIFFERENT
                                ? ThemeToken.DANGER : ThemeToken.WARNING;
                label.setForeground(UiThemeService.getInstance().color(token));
            }
            return label;
        }
    }

    private static final class RomRenderer extends JLabel
            implements ListCellRenderer<Rom> {
        private static final long serialVersionUID = 1L;
        private RomRenderer() { setOpaque(true); }
        public Component getListCellRendererComponent(JList<? extends Rom> list,
                Rom rom, int index, boolean selected, boolean focus) {
            setText(rom == null ? "No ROM" : rom.getFileName());
            setBackground(selected ? list.getSelectionBackground()
                    : list.getBackground());
            setForeground(selected ? list.getSelectionForeground()
                    : list.getForeground());
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return this;
        }
    }
}
