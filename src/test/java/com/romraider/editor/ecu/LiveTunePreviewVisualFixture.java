/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.platform.DimeModState;
import com.romraider.platform.PlatformContext;
import com.romraider.swing.JProgressPane;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.UiThemeService;

/** Manual screenshot fixture; never included in a release package. */
public final class LiveTunePreviewVisualFixture {
    private LiveTunePreviewVisualFixture() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "PNG path plus optional theme are required");
        }
        final String output = args[0];
        final ThemeMode theme = args.length == 2
                ? ThemeMode.valueOf(args[1].toUpperCase()) : ThemeMode.DARK;
        Table1D table = new Table1D();
        table.setName("Primary Open Loop Fueling");
        table.setCategory("Fueling//Open Loop");
        table.setDescription("Requested fueling under open-loop operation.");
        table.setStorageAddress(0x120);
        table.setStorageType(1);
        table.setDataSize(8);
        byte[] binary = new byte[0x200];
        for (int index = 0; index < 8; index++) {
            binary[0x120 + index] = (byte) (120 + index);
        }
        Rom rom = new Rom(new RomID());
        rom.addTableByName(table);
        rom.populateTables(binary, new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);
        table.getDataCell(2).setBinValue(132);
        table.getDataCell(3).setBinValue(134);
        table.getDataCell(6).setBinValue(140);
        PlatformContext.getInstance().setDimeModRuntime(
                DimeModState.ACTIVE, true);

        SwingUtilities.invokeAndWait(() -> {
            UiThemeService.getInstance().apply(theme);
            EditorInspectorPanel inspector = new EditorInspectorPanel();
            inspector.showSelection(rom, table);
            JTabbedPane tabs = find(inspector, JTabbedPane.class);
            tabs.setSelectedIndex(4);
            JFrame frame = new JFrame(
                    "RomRaider2 Editor — Tune Preview");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.add(inspector);
            frame.setSize(470, 820);
            frame.setLocation(40, 40);
            frame.setVisible(true);
            frame.doLayout();
            frame.validate();
            try {
                BufferedImage image = new BufferedImage(frame.getWidth(),
                        frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                frame.paint(graphics);
                graphics.dispose();
                ImageIO.write(image, "png", new File(output));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            } finally {
                frame.dispose();
            }
        });
    }

    private static <T extends Component> T find(Container parent,
            Class<T> type) {
        T match = findOrNull(parent, type);
        if (match != null) return match;
        throw new IllegalStateException("Fixture component not found");
    }

    private static <T extends Component> T findOrNull(Container parent,
            Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container) {
                T match = findOrNull((Container) child, type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
