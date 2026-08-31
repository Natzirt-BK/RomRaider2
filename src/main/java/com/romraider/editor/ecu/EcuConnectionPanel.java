/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.romraider.flash.FlashBackendRegistry;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Truthful connection/flash entry surface while providers are still absent. */
final class EcuConnectionPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    EcuConnectionPanel(FlashBackendRegistry registry) {
        super(new BorderLayout(0, 14));
        setName("ECU CONNECTION CENTER");
        setPreferredSize(new Dimension(560, 390));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("Connect interface and ECU");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 3.0f));
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.add(title, BorderLayout.NORTH);
        JLabel explanation = new JLabel("<html>Selecting and opening hardware will "
                + "be handled by modular device providers outside the editor UI.</html>");
        explanation.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        heading.add(explanation, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(2, 1, 0, 12));
        JPanel readiness = section("BACKEND READINESS");
        readiness.add(row("Interface providers",
                count(registry.getDeviceProviders().size(), "registered")));
        readiness.add(row("Validated protocols",
                count(registry.getProtocols().size(), "registered")));
        readiness.add(row("Active interface", "Not connected"));
        readiness.add(row("Detected ECU", "Not identified"));
        content.add(readiness);

        JPanel workflow = section("PLANNED WORKFLOW");
        workflow.add(row("1", "Connect interface"));
        workflow.add(row("2", "Detect and identify ECU"));
        workflow.add(row("3", "Read ECU and open ROM"));
        workflow.add(row("4", "Validate, write, verify, and reconnect"));
        content.add(workflow);
        add(content, BorderLayout.CENTER);

        JLabel safety = new JLabel("<html><b>No ECU commands will be sent.</b> "
                + "Read and Write stay disabled until a validated provider and "
                + "protocol explicitly advertise those capabilities.</html>");
        safety.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        safety.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiThemeService.getInstance()
                        .color(ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        add(safety, BorderLayout.SOUTH);
    }

    private static JPanel section(String title) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 3));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static JPanel row(String key, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        JLabel keyLabel = new JLabel(key);
        keyLabel.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        row.add(keyLabel, BorderLayout.WEST);
        row.add(new JLabel(value), BorderLayout.CENTER);
        return row;
    }

    private static String count(int count, String suffix) {
        return count + " " + suffix;
    }
}
