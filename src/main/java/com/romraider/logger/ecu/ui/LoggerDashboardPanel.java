/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Gauge workspace with contextual controls and a useful empty state. */
public final class LoggerDashboardPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String EMPTY = "empty";
    private static final String GAUGES = "gauges";
    private final JPanel gaugePanel;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JButton reset = new JButton("Reset Min / Max");
    private final JButton style = new JButton("Gauge Style");

    public LoggerDashboardPanel(final JPanel gaugePanel,
            final Runnable resetAction, final Runnable styleAction) {
        super(new BorderLayout());
        this.gaugePanel = gaugePanel;
        setName("LOGGER DASHBOARD WORKSPACE");
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        JLabel title = new JLabel("DASHBOARD");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JLabel description = new JLabel(
                "Live gauges for the channels selected on the left");
        description.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        titles.add(title, BorderLayout.NORTH);
        titles.add(description, BorderLayout.SOUTH);
        header.add(titles, BorderLayout.WEST);

        JPanel actions = new JPanel();
        style.setName("LOGGER GAUGE STYLE");
        style.setToolTipText("Change the Dashboard gauge presentation (F12)");
        style.addActionListener(event -> styleAction.run());
        reset.setName("RESET LOGGER DASHBOARD");
        reset.setToolTipText("Reset the minimum and maximum on every gauge");
        reset.addActionListener(event -> resetAction.run());
        actions.add(style);
        actions.add(reset);
        header.add(actions, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(header, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(gaugePanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(40);
        content.add(scroll, GAUGES);
        content.add(buildEmptyState(), EMPTY);
        add(content, BorderLayout.CENTER);

        gaugePanel.addContainerListener(new ContainerAdapter() {
            public void componentAdded(ContainerEvent event) { refreshState(); }
            public void componentRemoved(ContainerEvent event) { refreshState(); }
        });
        refreshState();
    }

    private JPanel buildEmptyState() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setName("LOGGER DASHBOARD EMPTY STATE");
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.insets = new Insets(4, 20, 4, 20);

        JLabel title = new JLabel("Build your live dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 3.0f));
        constraints.gridy = 0;
        empty.add(title, constraints);

        JLabel detail = new JLabel(
                "Select channels on the left and their gauges will appear here.");
        detail.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        constraints.gridy = 1;
        empty.add(detail, constraints);
        return empty;
    }

    private void refreshState() {
        boolean hasGauges = gaugePanel.getComponentCount() > 0;
        cards.show(content, hasGauges ? GAUGES : EMPTY);
        reset.setEnabled(hasGauges);
        style.setEnabled(hasGauges);
    }

    public JButton getResetButton() {
        return reset;
    }

    public JButton getStyleButton() {
        return style;
    }
}
