/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.romraider.logger.ecu.ui.handler.livedata.LiveDataTableModel;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.ModernTableStyle;
import com.romraider.ui.UiThemeService;

/** Live-value table with a useful first-run state instead of an empty grid. */
public final class LoggerLiveDataPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String EMPTY = "empty";
    private static final String TABLE = "table";
    private final LiveDataTableModel model;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JButton reset = new JButton("Reset Min / Max");
    private final JTable table;

    public LoggerLiveDataPanel(final LiveDataTableModel model,
            final Runnable resetAction) {
        super(new BorderLayout());
        this.model = model;
        setName("LOGGER LIVE DATA WORKSPACE");
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        JLabel title = new JLabel("DATA");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JLabel description = new JLabel(
                "Live channel values with session minimums and maximums");
        description.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        titles.add(title, BorderLayout.NORTH);
        titles.add(description, BorderLayout.SOUTH);
        header.add(titles, BorderLayout.WEST);
        reset.setName("RESET LOGGER LIVE DATA");
        reset.setToolTipText("Reset the minimum and maximum for every live value");
        reset.addActionListener(event -> resetAction.run());
        header.add(reset, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(header, BorderLayout.NORTH);

        table = new JTable(model);
        table.setName("LOGGER LIVE DATA TABLE");
        ModernTableStyle.apply(table);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setCellRenderer(
                new ModernTableStyle.TokenRenderer(ThemeToken.LIVE_TRACE));
        JScrollPane scroll = new JScrollPane(table,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        content.add(scroll, TABLE);
        content.add(buildEmptyState(), EMPTY);
        add(content, BorderLayout.CENTER);

        model.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent event) { refreshState(); }
        });
        refreshState();
    }

    private JPanel buildEmptyState() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setName("LOGGER LIVE DATA EMPTY STATE");
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.insets = new Insets(4, 20, 4, 20);

        JLabel title = new JLabel("Choose channels to begin");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 3.0f));
        constraints.gridy = 0;
        empty.add(title, constraints);

        JLabel detail = new JLabel(
                "Search and select channels on the left, then connect to begin live sampling.");
        detail.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        constraints.gridy = 1;
        empty.add(detail, constraints);
        return empty;
    }

    private void refreshState() {
        boolean hasRows = model.getRowCount() > 0;
        cards.show(content, hasRows ? TABLE : EMPTY);
        reset.setEnabled(hasRows);
    }

    public JButton getResetButton() {
        return reset;
    }

    public JTable getTable() {
        return table;
    }
}
