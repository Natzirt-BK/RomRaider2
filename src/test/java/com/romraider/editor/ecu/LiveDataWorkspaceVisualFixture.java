/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.UiThemeService;

/** Standalone screenshot fixture for integrated Editor live data. */
public final class LiveDataWorkspaceVisualFixture {
    private LiveDataWorkspaceVisualFixture() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "PNG path plus optional theme are required");
        }
        String output = args[0];
        ThemeMode theme = args.length == 2
                ? ThemeMode.valueOf(args[1].toUpperCase()) : ThemeMode.DARK;
        SwingUtilities.invokeAndWait(() -> render(output, theme));
    }

    private static void render(String output, ThemeMode theme) {
        try {
            UiThemeService.getInstance().apply(theme);
            LiveDataWorkspacePanel panel = new LiveDataWorkspacePanel(
                    new Runnable() { public void run() { } });
            JFrame frame = new JFrame("RomRaider2 Editor — Live Data");
            frame.setContentPane(panel);
            frame.setSize(1040, 620);
            frame.setLocation(20, 20);
            frame.setVisible(true);
            panel.updateSessionState(LoggerSessionState.LIVE_ECU);
            long start = System.currentTimeMillis() - 2_000L;
            for (int index = 0; index < 30; index++) {
                long time = start + index * 65L;
                sample(panel, "rpm", "Engine Speed", 2800 + index * 42,
                        "rpm", time, "%.0f");
                sample(panel, "boost", "Manifold Relative Pressure",
                        4.2 + Math.sin(index / 5.0) * 2.6,
                        "psi", time, "%.1f");
                sample(panel, "afr", "Wideband AFR", 14.4 - index * 0.035,
                        "AFR", time, "%.2f");
                sample(panel, "knock", "Fine Learning Knock Correction",
                        index > 21 ? -1.4 : 0.0, "°", time, "%.1f");
            }
            frame.doLayout();
            frame.validate();
            BufferedImage image = new BufferedImage(frame.getWidth(),
                    frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            frame.paint(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", new File(output));
            frame.dispose();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void sample(LiveDataWorkspacePanel panel, String id,
            String name, double value, String units, long time, String format) {
        panel.updateSample(new LiveDataSample(id, name, value,
                String.format(format, value), units, time));
    }
}
