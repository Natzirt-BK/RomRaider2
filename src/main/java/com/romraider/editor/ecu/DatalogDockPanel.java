/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.api.LoggerLiveDataListener;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.UiThemeService;

/** Truthful empty-state dock for future live and recorded datalog sessions. */
final class DatalogDockPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel status = new JLabel("NO SESSION");
    private final LiveTracePlot plot = new LiveTracePlot();
    private final Set<String> liveParameters = new LinkedHashSet<String>();
    private boolean liveDataAttached;
    private LoggerSessionState sessionState = LoggerSessionState.STOPPED;
    private final LoggerLiveDataListener liveDataListener =
            new LoggerLiveDataListener() {
        public void sessionStateChanged(final LoggerSessionState state) {
            onEventThread(() -> updateSessionState(state));
        }
        public void sampleUpdated(final LiveDataSample sample) {
            onEventThread(() -> updateSample(sample));
        }
        public void parameterRemoved(final String parameterId) {
            onEventThread(() -> removeSample(parameterId));
        }
    };

    DatalogDockPanel() {
        this(null, null);
    }

    DatalogDockPanel(final Runnable openLoggerAction) {
        this(openLoggerAction, null);
    }

    DatalogDockPanel(final Runnable openLoggerAction,
            final Consumer<Boolean> expandedAction) {
        super(new BorderLayout());
        setName("DATALOG VIEWER");
        setMinimumSize(new Dimension(300, 110));
        setPreferredSize(new Dimension(700, 190));
        setBorder(BorderFactory.createLineBorder(UiThemeService.getInstance()
                .color(ThemeToken.RAISED_SURFACE)));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        JLabel title = new JLabel("DATALOG VIEWER");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        status.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        status.setName("DATALOG SESSION STATUS");
        header.add(title, BorderLayout.WEST);
        JPanel headerActions = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);
        headerActions.add(status);
        final JToggleButton expanded = new JToggleButton(
                ModernIconFactory.icon(Action.HIDE_BOTTOM_PANEL));
        expanded.setName("TOGGLE DATALOG VIEWER");
        expanded.setSelected(true);
        expanded.setFocusable(false);
        expanded.setToolTipText("Collapse datalog viewer");
        expanded.getAccessibleContext().setAccessibleName(
                "Collapse datalog viewer");
        headerActions.add(expanded);
        header.add(headerActions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND));
        content.add(plot, BorderLayout.CENTER);

        JButton openLogger = new JButton("Configure Logger",
                ModernIconFactory.icon(Action.LOGGER));
        openLogger.setName("CONFIGURE LOGGER");
        openLogger.setEnabled(openLoggerAction != null);
        openLogger.setToolTipText("Open the logger and select live parameters");
        if (openLoggerAction != null) {
            openLogger.addActionListener(event -> openLoggerAction.run());
        }
        JPanel launcher = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.CENTER, 8, 8));
        launcher.setName("LOGGER LAUNCH ACTION");
        launcher.setBackground(content.getBackground());
        launcher.add(openLogger);
        content.add(launcher, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        expanded.addActionListener(event -> {
            boolean showing = expanded.isSelected();
            content.setVisible(showing);
            setMinimumSize(new Dimension(300, showing ? 110 : 34));
            expanded.setIcon(ModernIconFactory.icon(showing
                    ? Action.HIDE_BOTTOM_PANEL : Action.SHOW_BOTTOM_PANEL));
            expanded.setToolTipText(showing
                    ? "Collapse datalog viewer" : "Expand datalog viewer");
            expanded.getAccessibleContext().setAccessibleName(
                    expanded.getToolTipText());
            if (expandedAction != null) expandedAction.accept(showing);
            revalidate();
            repaint();
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!liveDataAttached) {
            liveDataAttached = true;
            LoggerLiveDataBus.getInstance().addListener(liveDataListener);
            plot.setSeries(LoggerLiveDataBus.getInstance().getRecentSamples());
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

    void updateSessionState(LoggerSessionState state) {
        sessionState = state == null ? LoggerSessionState.STOPPED : state;
        refreshLiveSummary();
    }

    void updateSample(LiveDataSample sample) {
        if (sample == null) return;
        liveParameters.add(sample.getParameterId());
        plot.appendSample(sample);
        refreshLiveSummary();
    }

    void removeSample(String parameterId) {
        liveParameters.remove(parameterId);
        plot.removeSeries(parameterId);
        refreshLiveSummary();
    }

    private void refreshLiveSummary() {
        status.setText(sessionState.getDisplayName()
                + (liveParameters.isEmpty() ? ""
                        : "  •  " + liveParameters.size() + " PARAMETERS"));
        status.setForeground(UiThemeService.getInstance().color(
                sessionState.isLive() ? ThemeToken.SUCCESS
                        : ThemeToken.SECONDARY_TEXT));
        plot.setMessage(sessionState.isLive()
                ? (liveParameters.isEmpty()
                        ? "Select parameters in Logger to begin"
                        : liveParameters.size() + " live parameters")
                : "No live or recorded samples");
    }

    private static void onEventThread(Runnable update) {
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

}
