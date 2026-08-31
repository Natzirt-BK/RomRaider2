/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.api.LoggerLiveDataListener;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Read-only dashboard backed exclusively by the reusable logger data bus. */
public final class LiveDashboardPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAXIMUM_CARDS = 6;
    private static final int MAXIMUM_HISTORY = 120;
    private static final String EMPTY = "empty";
    private static final String VALUES = "values";

    private final JLabel sessionStatus = new JLabel("● ECU OFFLINE");
    private final JLabel summary = new JLabel("No parameters selected");
    private final JPanel cards = new JPanel(new GridLayout(0, 3, 10, 10));
    private final CardLayout contentCards = new CardLayout();
    private final JPanel content = new JPanel(contentCards);
    private final Map<String, LiveDataSample> latest =
            new LinkedHashMap<String, LiveDataSample>();
    private final Map<String, LinkedList<LiveDataSample>> histories =
            new LinkedHashMap<String, LinkedList<LiveDataSample>>();
    private final Map<String, LiveValueCard> visibleCards =
            new LinkedHashMap<String, LiveValueCard>();
    private LoggerSessionState sessionState = LoggerSessionState.STOPPED;
    private boolean liveDataAttached;
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

    public LiveDashboardPanel(Runnable configureLoggerAction) {
        super(new BorderLayout(0, 12));
        setName("LIVE DASHBOARD WORKSPACE");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(buildHeader(configureLoggerAction), BorderLayout.NORTH);
        buildContent();
        add(content, BorderLayout.CENTER);
        refreshSummary();
    }

    private JPanel buildHeader(final Runnable configureLoggerAction) {
        JLabel title = new JLabel("Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 3.0f));
        JLabel help = new JLabel(
                "At-a-glance logger values with actual recent sample trends.");
        help.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel copy = new JPanel(new BorderLayout(0, 3));
        copy.add(title, BorderLayout.NORTH);
        copy.add(help, BorderLayout.SOUTH);

        sessionStatus.setName("DASHBOARD SESSION STATUS");
        sessionStatus.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JButton configure = new JButton("Configure Logger",
                ModernIconFactory.icon(Action.LOGGER));
        configure.setName("DASHBOARD CONFIGURE LOGGER");
        configure.setEnabled(configureLoggerAction != null);
        configure.setToolTipText(
                "Open logger setup to connect and choose parameters");
        if (configureLoggerAction != null) {
            configure.addActionListener(event -> configureLoggerAction.run());
        }
        JPanel status = new JPanel(new BorderLayout(12, 0));
        status.add(sessionStatus, BorderLayout.WEST);
        status.add(configure, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.add(copy, BorderLayout.CENTER);
        header.add(status, BorderLayout.EAST);
        return header;
    }

    private void buildContent() {
        cards.setName("LIVE DASHBOARD CARDS");
        JPanel values = new JPanel(new BorderLayout(0, 8));
        summary.setName("LIVE DASHBOARD SUMMARY");
        summary.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        values.add(summary, BorderLayout.NORTH);
        values.add(cards, BorderLayout.CENTER);

        JLabel empty = new JLabel(
                "<html><div style='text-align:center'><b>No live values</b><br>"
                        + "Configure Logger to connect and select parameters."
                        + "</div></html>", JLabel.CENTER);
        empty.setName("LIVE DASHBOARD EMPTY STATE");
        empty.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        content.add(empty, EMPTY);
        content.add(values, VALUES);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!liveDataAttached) {
            liveDataAttached = true;
            LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance();
            bus.addListener(liveDataListener);
            seedHistory(bus.getRecentSamples());
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
        refreshSummary();
    }

    void updateSample(LiveDataSample sample) {
        if (sample == null) return;
        latest.put(sample.getParameterId(), sample);
        LinkedList<LiveDataSample> history = histories.get(
                sample.getParameterId());
        if (history == null) {
            history = new LinkedList<LiveDataSample>();
            histories.put(sample.getParameterId(), history);
        }
        history.addLast(sample);
        while (history.size() > MAXIMUM_HISTORY) history.removeFirst();
        LiveValueCard card = visibleCards.get(sample.getParameterId());
        if (card == null && visibleCards.size() < MAXIMUM_CARDS) {
            card = addCard(sample.getParameterId());
        }
        if (card != null) card.setSamples(history);
        refreshSummary();
    }

    void removeSample(String parameterId) {
        if (parameterId == null) return;
        latest.remove(parameterId);
        histories.remove(parameterId);
        if (visibleCards.containsKey(parameterId)) renderCards();
        refreshSummary();
    }

    private void seedHistory(Map<String, List<LiveDataSample>> recent) {
        if (recent == null) return;
        for (Map.Entry<String, List<LiveDataSample>> entry : recent.entrySet()) {
            LinkedList<LiveDataSample> samples =
                    new LinkedList<LiveDataSample>(entry.getValue());
            while (samples.size() > MAXIMUM_HISTORY) samples.removeFirst();
            histories.put(entry.getKey(), samples);
            if (!samples.isEmpty()) {
                latest.put(entry.getKey(), samples.getLast());
            }
        }
        renderCards();
        refreshSummary();
    }

    private void renderCards() {
        cards.removeAll();
        visibleCards.clear();
        for (String parameterId : latest.keySet()) {
            if (visibleCards.size() >= MAXIMUM_CARDS) break;
            LiveValueCard card = addCard(parameterId);
            card.setSamples(histories.get(parameterId));
        }
        cards.revalidate();
        cards.repaint();
    }

    private LiveValueCard addCard(String parameterId) {
        LiveValueCard card = new LiveValueCard();
        visibleCards.put(parameterId, card);
        cards.add(card);
        cards.revalidate();
        return card;
    }

    private void refreshSummary() {
        int total = latest.size();
        int shown = Math.min(total, MAXIMUM_CARDS);
        sessionStatus.setText("● " + sessionState.getDisplayName());
        sessionStatus.setForeground(UiThemeService.getInstance().color(
                sessionState.isLive() ? ThemeToken.SUCCESS
                        : ThemeToken.SECONDARY_TEXT));
        summary.setText(total == 0 ? "No parameters selected"
                : shown == total
                        ? shown + (shown == 1 ? " live parameter"
                                : " live parameters")
                        : "Showing " + shown + " of " + total
                                + " live parameters");
        contentCards.show(content, total == 0 ? EMPTY : VALUES);
    }

    private static void onEventThread(Runnable update) {
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    /** One truthful value card; its graph is normalized only to recent samples. */
    private static final class LiveValueCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final SimpleDateFormat TIME_FORMAT =
                new SimpleDateFormat("HH:mm:ss.SSS");
        private final JLabel name = new JLabel("Parameter");
        private final JLabel value = new JLabel("—");
        private final JLabel updated = new JLabel("Waiting for samples");
        private final MiniTrace trace = new MiniTrace();

        LiveValueCard() {
            super(new BorderLayout(0, 7));
            setName("LIVE DASHBOARD CARD");
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiThemeService.getInstance()
                            .color(ThemeToken.RAISED_SURFACE)),
                    BorderFactory.createEmptyBorder(12, 12, 10, 12)));
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            value.setFont(value.getFont().deriveFont(Font.BOLD,
                    value.getFont().getSize2D() + 11.0f));
            updated.setForeground(UiThemeService.getInstance().color(
                    ThemeToken.SECONDARY_TEXT));
            JPanel reading = new JPanel(new BorderLayout(8, 0));
            reading.add(name, BorderLayout.WEST);
            reading.add(updated, BorderLayout.EAST);
            add(reading, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            add(trace, BorderLayout.SOUTH);
        }

        void setSamples(List<LiveDataSample> samples) {
            if (samples == null || samples.isEmpty()) return;
            LiveDataSample sample = samples.get(samples.size() - 1);
            name.setText(sample.getName());
            value.setText(sample.getDisplayValue()
                    + (sample.getUnits().isEmpty() ? ""
                            : "  " + sample.getUnits()));
            synchronized (TIME_FORMAT) {
                updated.setText(TIME_FORMAT.format(
                        new Date(sample.getTimestampMillis())));
            }
            trace.setSamples(samples);
            setToolTipText(sample.getName() + ": " + sample.getDisplayValue()
                    + (sample.getUnits().isEmpty() ? ""
                            : " " + sample.getUnits()));
        }
    }

    private static final class MiniTrace extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final DecimalFormat RANGE_FORMAT =
                new DecimalFormat("0.###");
        private List<LiveDataSample> samples =
                new ArrayList<LiveDataSample>();

        MiniTrace() {
            setName("LIVE DASHBOARD MINI TRACE");
            setOpaque(false);
            setPreferredSize(new java.awt.Dimension(180, 80));
        }

        void setSamples(List<LiveDataSample> samples) {
            this.samples = samples == null ? new ArrayList<LiveDataSample>()
                    : new ArrayList<LiveDataSample>(samples);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 4;
                int right = Math.max(left + 20, getWidth() - 4);
                int top = 5;
                int bottom = Math.max(top + 15, getHeight() - 22);
                g.setColor(UiThemeService.getInstance().color(
                        ThemeToken.RAISED_SURFACE));
                g.drawLine(left, bottom, right, bottom);
                if (samples.isEmpty()) return;
                double minimum = Double.POSITIVE_INFINITY;
                double maximum = Double.NEGATIVE_INFINITY;
                for (LiveDataSample sample : samples) {
                    minimum = Math.min(minimum, sample.getRawValue());
                    maximum = Math.max(maximum, sample.getRawValue());
                }
                double plottedMinimum = minimum;
                double plottedMaximum = maximum;
                if (Double.compare(plottedMinimum, plottedMaximum) == 0) {
                    plottedMinimum -= 0.5;
                    plottedMaximum += 0.5;
                }
                g.setColor(UiThemeService.getInstance().color(
                        ThemeToken.LIVE_TRACE));
                g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int previousX = -1;
                int previousY = -1;
                for (int index = 0; index < samples.size(); index++) {
                    int x = samples.size() == 1 ? right
                            : left + (int) Math.round(index * (right - left)
                                    / (double) (samples.size() - 1));
                    double normalized = (samples.get(index).getRawValue()
                            - plottedMinimum) / (plottedMaximum - plottedMinimum);
                    int y = bottom - (int) Math.round(normalized
                            * (bottom - top));
                    if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
                    else g.fillOval(x - 2, y - 2, 4, 4);
                    previousX = x;
                    previousY = y;
                }
                String units = samples.get(samples.size() - 1).getUnits();
                String range = Double.compare(minimum, maximum) == 0
                        ? "Recent " + RANGE_FORMAT.format(minimum)
                        : "Recent range " + RANGE_FORMAT.format(minimum)
                                + "–" + RANGE_FORMAT.format(maximum);
                if (!units.isEmpty()) range += " " + units;
                g.setColor(UiThemeService.getInstance().color(
                        ThemeToken.SECONDARY_TEXT));
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(range, left,
                        Math.min(getHeight() - 3, bottom + metrics.getAscent() + 4));
            } finally {
                g.dispose();
            }
        }
    }
}
