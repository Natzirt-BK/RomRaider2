/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2020 RomRaider.com
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

package com.romraider.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.ResourceBundle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.romraider.activity.ActivitySnapshot;
import com.romraider.activity.ActivityState;
import com.romraider.activity.ApplicationActivityListener;
import com.romraider.activity.ApplicationActivityService;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.util.ResourceUtil;

public class JProgressPane extends JPanel implements PropertyChangeListener{

    private static final long serialVersionUID = -6827936662738014543L;
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            JProgressPane.class.getName());
    private final ApplicationActivityService activities =
            ApplicationActivityService.getInstance();
    private final JLabel activityStatus = new JLabel();
    private final JLabel elapsed = new JLabel();
    JProgressBar progressBar = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);
    private final JPanel messageArea = new JPanel();
    private final ApplicationActivityListener activityListener = activity ->
            onEventThread(() -> render(activity));
    private final Timer elapsedTimer = new Timer(1000,
            event -> refreshElapsed());
    private ActivitySnapshot displayedActivity;
    private boolean activityAttached;
    String status = "ready";
    int percent = 0;

    public JProgressPane() {

        this.setName("EDITOR PROGRESS STATUS");
        // Reserve enough height for one unclipped line of status text.
        this.setPreferredSize(new Dimension(500, 26));
        this.setMinimumSize(new Dimension(200, 24));
        this.setLayout(new BorderLayout(6, 0));
        activityStatus.setName("APPLICATION ACTIVITY STATUS");
        activityStatus.setCursor(Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR));
        activityStatus.setToolTipText(
                "Show current and recently completed application activity");
        activityStatus.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    showActivityDrawer();
                }
            }
        });
        activityStatus.getAccessibleContext().setAccessibleName(
                "Application activity status");
        elapsed.setName("APPLICATION ACTIVITY ELAPSED");
        elapsed.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        messageArea.setOpaque(false);
        messageArea.setAlignmentY(Component.CENTER_ALIGNMENT);
        messageArea.setLayout(new BoxLayout(messageArea, BoxLayout.X_AXIS));
        messageArea.add(activityStatus);
        messageArea.add(Box.createHorizontalStrut(8));
        messageArea.add(elapsed);
        progressBar.setName("EDITOR TASK PROGRESS");
        progressBar.setMinimumSize(new Dimension(90, 8));
        progressBar.setPreferredSize(new Dimension(130, 12));
        progressBar.setValue(0);
        progressBar.setVisible(false);

        this.add(progressBar, BorderLayout.WEST);
        this.add(messageArea, BorderLayout.CENTER);
        elapsedTimer.setRepeats(true);
        render(activities.getCurrent());
    }

    public void update(String status, int percent) {
        this.status = safe(status);
        this.percent = Math.max(0, Math.min(100, percent));
        if (this.percent >= 100) activities.complete(this.status);
        else activities.update(this.status, this.percent);
    }

    public void setStatus(String status) {
        this.status = safe(status);
        activities.updateIndeterminate(this.status);
    }

    public void ready(String status) {
        this.status = safe(status);
        this.percent = 0;
        activities.ready(this.status);
    }

    public void complete(String status) {
        this.status = safe(status);
        activities.complete(this.status);
    }

    public void failed(String status) {
        this.status = safe(status);
        activities.fail(this.status);
    }

    public JProgressBar getProgressBar() {
        return this.progressBar;
    }

    /** Adds compact persistent status beside the current task message. */
    public void addStatusComponent(Component component) {
        if (component == null) return;
        if (component instanceof JComponent) {
            ((JComponent) component).setAlignmentY(
                    Component.CENTER_ALIGNMENT);
        }
        messageArea.add(Box.createHorizontalStrut(8));
        messageArea.add(component);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!activityAttached) {
            activityAttached = true;
            activities.addListener(activityListener);
        }
        elapsedTimer.start();
    }

    @Override
    public void removeNotify() {
        elapsedTimer.stop();
        if (activityAttached) {
            activities.removeListener(activityListener);
            activityAttached = false;
        }
        super.removeNotify();
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if("progress".equals(evt.getPropertyName())) {
            int progress = (Integer) evt.getNewValue();
            update(status, progress);
        }
    }

    private void render(ActivitySnapshot activity) {
        displayedActivity = activity;
        ActivityState state = activity.getState();
        String marker = state == ActivityState.SUCCEEDED ? "✓"
                : state == ActivityState.FAILED ? "!" : "●";
        activityStatus.setText(marker + " " + activity.getMessage());
        activityStatus.setForeground(UiThemeService.getInstance().color(
                state == ActivityState.SUCCEEDED ? ThemeToken.SUCCESS
                : state == ActivityState.FAILED ? ThemeToken.DANGER
                : state == ActivityState.RUNNING ? ThemeToken.ACCENT
                : ThemeToken.SECONDARY_TEXT));
        boolean running = state == ActivityState.RUNNING;
        progressBar.setVisible(running);
        progressBar.setIndeterminate(running && !activity.hasMeasuredProgress());
        if (activity.hasMeasuredProgress()) {
            progressBar.setValue(activity.getProgressPercent());
            progressBar.setStringPainted(true);
            progressBar.setString(activity.getProgressPercent() + "%");
        } else {
            progressBar.setStringPainted(false);
        }
        elapsed.setVisible(running);
        refreshElapsed();
        revalidate();
        repaint();
    }

    private void refreshElapsed() {
        ActivitySnapshot activity = displayedActivity;
        if (activity == null || activity.getState() != ActivityState.RUNNING) {
            elapsed.setText("");
            return;
        }
        elapsed.setText(formatElapsed(activity.getElapsedMillis(
                System.currentTimeMillis())));
    }

    private void showActivityDrawer() {
        JPopupMenu drawer = new JPopupMenu();
        drawer.setName("APPLICATION ACTIVITY DRAWER");
        ActivitySnapshot current = activities.getCurrent();
        drawer.add(disabledItem("CURRENT ACTIVITY"));
        drawer.add(disabledItem(activityLine(current, true)));
        List<ActivitySnapshot> history = activities.getHistory();
        drawer.addSeparator();
        drawer.add(disabledItem("RECENT ACTIVITY"));
        if (history.isEmpty()) {
            drawer.add(disabledItem("No completed operations yet"));
        } else {
            int shown = Math.min(6, history.size());
            for (int index = 0; index < shown; index++) {
                drawer.add(disabledItem(activityLine(history.get(index), false)));
            }
        }
        Dimension size = drawer.getPreferredSize();
        drawer.show(activityStatus, 0, -size.height);
    }

    private static JMenuItem disabledItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(false);
        return item;
    }

    private static String activityLine(ActivitySnapshot activity,
            boolean current) {
        String marker = activity.getState() == ActivityState.SUCCEEDED ? "✓ "
                : activity.getState() == ActivityState.FAILED ? "! " : "● ";
        long elapsedMillis = activity.getElapsedMillis(System.currentTimeMillis());
        String progress = activity.hasMeasuredProgress()
                ? " • " + activity.getProgressPercent() + "%" : "";
        String duration = activity.getState() == ActivityState.IDLE && current
                ? "" : " • " + formatElapsed(elapsedMillis);
        return marker + activity.getMessage() + progress + duration;
    }

    private static String formatElapsed(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return seconds < 60 ? seconds + "s"
                : (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty()
                ? rb.getString("READY") : value.trim();
    }

    private static void onEventThread(Runnable update) {
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }
}
