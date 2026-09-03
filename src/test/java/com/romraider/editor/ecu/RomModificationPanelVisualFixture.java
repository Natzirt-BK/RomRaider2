/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.VehiclePlatform;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.UiThemeService;

/** Standalone fixture for checking the ROM modification details workspace. */
public final class RomModificationPanelVisualFixture {
    private RomModificationPanelVisualFixture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("PNG output path is required");
        }
        final String output = args[0];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    UiThemeService.getInstance().apply(ThemeMode.LIGHT);
                    PlatformContext context = PlatformContext.getInstance();
                    context.setPlatform(VehiclePlatform.SUBARU);
                    RomID identity = new RomID();
                    identity.setXmlid("CarBerry");
                    Rom rom = new Rom(identity);
                    rom.setFileName("CarBerry-4.2-test.bin");
                    Table1D merp = new Table1D();
                    merp.setName("MerpMod SD Mode Switch");
                    merp.setCategory("MerpMod - Speed Density");
                    rom.addTableByName(merp);
                    Table1D flex = new Table1D();
                    flex.setName("Flex Fuel Ethanol Content Sensor Scaling");
                    flex.setCategory("CarBerry - Flex Fuel");
                    rom.addTableByName(flex);

                    JFrame frame = new JFrame("ROM Modifications");
                    frame.setContentPane(new RomModificationPanel(rom, context));
                    frame.setSize(820, 620);
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
        });
    }
}
