/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.romraider.maps.Table;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Hosts an integrated renderer without coupling document layout to its API. */
final class MapVisualizationHost extends JPanel {
    private static final long serialVersionUID = 1L;
    private final MapVisualizationProvider provider;
    private JComponent visualization;

    MapVisualizationHost(Table table, MapVisualizationProvider provider) {
        super(new BorderLayout());
        this.provider = provider;
        setName("MAP VISUALIZATION");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiThemeService.getInstance()
                        .color(ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        String mapName = table == null || table.getName() == null
                || table.getName().trim().isEmpty() ? "MAP" : table.getName().trim();
        JLabel title = new JLabel(mapName + " 3D",
                ModernIconFactory.icon(Action.VIEW_3D), SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        header.add(title, BorderLayout.WEST);
        JComponent content = createContent(table);
        if (content instanceof MapVisualizationControls) {
            final MapVisualizationControls controls =
                    (MapVisualizationControls) content;
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);
            JLabel hint = new JLabel(controls.getInteractionHint());
            hint.setForeground(UiThemeService.getInstance().color(
                    ThemeToken.SECONDARY_TEXT));
            JButton reset = new JButton(ModernIconFactory.icon(Action.REFRESH));
            reset.setName("RESET 3D VIEW");
            reset.setToolTipText("Reset surface rotation and zoom");
            reset.getAccessibleContext().setAccessibleName("Reset 3D view");
            reset.addActionListener(event -> controls.resetView());
            actions.add(hint);
            actions.add(reset);
            actions.setMinimumSize(new Dimension(0,
                    actions.getPreferredSize().height));
            header.add(actions, BorderLayout.EAST);
            header.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    hint.setVisible(header.getWidth() >= 470);
                }
            });
            hint.setVisible(false);
        }
        header.setMinimumSize(new Dimension(0, header.getPreferredSize().height));
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    boolean hasVisualization() {
        return visualization != null;
    }

    void disposeVisualization() {
        if (provider != null && visualization != null) {
            try {
                provider.dispose(visualization);
            } catch (RuntimeException ignored) {
                // Closing a document must remain safe if a provider fails.
            } catch (LinkageError ignored) {
                // Native renderer teardown failure is isolated from the editor.
            }
            visualization = null;
        }
    }

    private JComponent createContent(Table table) {
        if (provider == null) return emptyState(table);
        try {
            if (!provider.supports(table)) return emptyState(table);
            visualization = provider.createVisualization(table);
            return visualization == null ? emptyState(table) : visualization;
        } catch (RuntimeException failure) {
            return errorState(provider.getName(), failure);
        } catch (LinkageError failure) {
            return errorState(provider.getName(), failure);
        }
    }

    private static JPanel errorState(String providerName, Throwable failure) {
        JPanel center = new JPanel(new GridBagLayout());
        String detail = failure.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = failure.getClass().getSimpleName();
        }
        JLabel label = new JLabel("<html><div style='text-align:center'>"
                + "<b>Surface renderer unavailable</b><br>"
                + escape(providerName) + ": " + escape(detail)
                + "</div></html>", SwingConstants.CENTER);
        label.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        center.add(label);
        return center;
    }

    private static String escape(String value) {
        if (value == null) return "Unknown renderer";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static JPanel emptyState(Table table) {
        JPanel center = new JPanel(new GridBagLayout());
        String message = table != null
                && table.getType() == Table.TableType.TABLE_3D
                ? "<html><div style='text-align:center'><b>Integrated surface preview</b><br>"
                        + "Use 3D View in the map toolbar while the embedded renderer is completed.</div></html>"
                : "<html><div style='text-align:center'><b>No surface for this map</b><br>"
                        + "Open a 3D calibration table to use this view.</div></html>";
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        center.add(label);
        return center;
    }
}
