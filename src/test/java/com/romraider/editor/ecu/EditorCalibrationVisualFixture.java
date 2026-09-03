/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table3D;
import com.romraider.maps.Table3DView;
import com.romraider.swing.JProgressPane;
import com.romraider.swing.TableFrame;
import com.romraider.swing.TableToolBar;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.UiThemeService;

/** Standalone screenshot fixture for the active calibration workspace. */
public final class EditorCalibrationVisualFixture {
    private EditorCalibrationVisualFixture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 4) {
            throw new IllegalArgumentException(
                    "PNG path plus optional theme, width, and height are required");
        }
        final String output = args[0];
        final ThemeMode theme = args.length > 1
                ? ThemeMode.valueOf(args[1].toUpperCase()) : ThemeMode.LIGHT;
        final int width = args.length > 2 ? Integer.parseInt(args[2]) : 1180;
        final int height = args.length > 3 ? Integer.parseInt(args[3]) : 720;
        SwingUtilities.invokeAndWait(() -> render(output, theme, width, height));
    }

    private static void render(String output, ThemeMode theme, int width,
            int height) {
        try {
            UiThemeService.getInstance().apply(theme);
            Table3D table = table();
            Rom rom = new Rom(new RomID());
            rom.setFileName("Example calibration.bin");
            rom.addTableByName(table);
            rom.populateTables(binary(), new JProgressPane());
            RomChangeService.rememberSavedBinary(rom);

            Table3DView view = new Table3DView(table);
            view.populateTableVisual();
            view.drawTable();
            table.get3dData()[3][2].setBinValue(126);
            view.drawTable();
            view.startHighlight(2, 2);
            view.highlight(4, 2);

            TableFrame tableFrame = new TableFrame(
                    table.getName() + " | " + rom.getFileName(), view);
            EditorTabbedWorkspace workspace = new EditorTabbedWorkspace(null,
                    null, null, MapVisualizationRegistry.createDefault());
            workspace.open(tableFrame);
            TableToolBar tableToolBar = new TableToolBar();
            tableToolBar.updateTableToolBar(table);

            JPanel content = new JPanel(new BorderLayout());
            content.add(tableToolBar, BorderLayout.NORTH);
            content.add(workspace, BorderLayout.CENTER);
            JFrame frame = new JFrame("RomRaider2 Editor — Calibration");
            frame.setContentPane(content);
            frame.setSize(width, height);
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

    private static Table3D table() {
        Table3D table = new Table3D();
        table.setName("Primary Open Loop Fueling");
        table.setCategory("Fueling / Open Loop");
        table.setDescription("Requested fuel target by engine speed and load.");
        table.setStorageAddress(0x100);
        table.setStorageType(1);
        table.setSizeX(8);
        table.setSizeY(6);
        table.getXAxis().setName("Engine Load");
        table.getXAxis().setStorageAddress(0x180);
        table.getXAxis().setStorageType(1);
        table.getXAxis().setDataSize(8);
        table.getYAxis().setName("Engine Speed");
        table.getYAxis().setStorageAddress(0x190);
        table.getYAxis().setStorageType(1);
        table.getYAxis().setDataSize(6);
        return table;
    }

    private static byte[] binary() {
        byte[] binary = new byte[0x200];
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 8; column++) {
                binary[0x100 + row * 8 + column] = (byte)
                        (92 + row * 5 + column * 2);
            }
        }
        for (int column = 0; column < 8; column++) {
            binary[0x180 + column] = (byte) (20 + column * 20);
        }
        for (int row = 0; row < 6; row++) {
            binary[0x190 + row] = (byte) (20 + row * 30);
        }
        return binary;
    }
}
