/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.swing.JProgressPane;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.UiThemeService;

/** Standalone screenshot fixture for the responsive ROM comparison workspace. */
public final class RomComparePanelVisualFixture {
    private RomComparePanelVisualFixture() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            throw new IllegalArgumentException(
                    "PNG path plus optional theme and width are required");
        }
        final String output = args[0];
        final ThemeMode theme = args.length > 1
                ? ThemeMode.valueOf(args[1].toUpperCase()) : ThemeMode.LIGHT;
        final int width = args.length > 2 ? Integer.parseInt(args[2]) : 900;
        SwingUtilities.invokeAndWait(() -> render(output, theme, width));
    }

    private static void render(String output, ThemeMode theme, int width) {
        try {
            UiThemeService.getInstance().apply(theme);
            Rom stock = rom("Stock calibration.bin", new byte[] {
                    10, 20, 30, 40, 50, 60});
            Rom tuned = rom("Road tune.bin", new byte[] {
                    10, 22, 30, 40, 54, 60});
            JFrame frame = new JFrame("RomRaider2 Editor — Compare ROMs");
            frame.setContentPane(new RomComparePanel(
                    Arrays.asList(stock, tuned), (left, right, comparison) -> { }));
            frame.setSize(width, 560);
            frame.setLocation(20, 20);
            frame.setVisible(true);
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

    private static Rom rom(String fileName, byte[] binary) {
        Rom rom = new Rom(new RomID());
        rom.setFileName(fileName);
        rom.addTableByName(table("Primary Open Loop Fueling", 0));
        rom.addTableByName(table("Base Ignition Timing", 2));
        rom.addTableByName(table("Target Boost", 4));
        rom.populateTables(binary, new JProgressPane());
        return rom;
    }

    private static Table1D table(String name, int address) {
        Table1D table = new Table1D();
        table.setName(name);
        table.setStorageAddress(address);
        table.setStorageType(1);
        table.setDataSize(2);
        return table;
    }
}
