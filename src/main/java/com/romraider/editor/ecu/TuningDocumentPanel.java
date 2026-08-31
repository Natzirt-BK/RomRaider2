/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import com.romraider.maps.Table;
import com.romraider.maps.TableView;

/** One map document containing editing, visualization, and datalog surfaces. */
final class TuningDocumentPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JSplitPane mapSplit;
    private final JSplitPane documentSplit;
    private final MapVisualizationHost visualizationHost;
    private final MapDocumentHeader documentHeader;
    private int visualizationDividerLocation = -1;
    private int datalogDividerLocation = -1;
    private boolean narrowMapLayout;
    private boolean visualizationVisible;

    TuningDocumentPanel(JMenuBar contextMenu, TableView tableView, Table table,
            MapVisualizationProvider visualizationProvider) {
        this(contextMenu, tableView, table, visualizationProvider, null, null);
    }

    TuningDocumentPanel(JMenuBar contextMenu, TableView tableView, Table table,
            MapVisualizationProvider visualizationProvider,
            Runnable openLoggerAction) {
        this(contextMenu, tableView, table, visualizationProvider,
                openLoggerAction, null);
    }

    TuningDocumentPanel(JMenuBar contextMenu, TableView tableView, Table table,
            MapVisualizationProvider visualizationProvider,
            Runnable openLoggerAction, Runnable focusTableSearchAction) {
        this(contextMenu, tableView, table, visualizationProvider,
                openLoggerAction, focusTableSearchAction, null, null);
    }

    TuningDocumentPanel(JMenuBar contextMenu, TableView tableView, Table table,
            MapVisualizationProvider visualizationProvider,
            Runnable openLoggerAction, Runnable focusTableSearchAction,
            BooleanSupplier favoriteState, Runnable toggleFavoriteAction) {
        super(new BorderLayout());
        setName("TUNING DOCUMENT");

        JPanel tableSurface = new JPanel(new BorderLayout());
        tableSurface.setName("CALIBRATION TABLE");
        tableSurface.setMinimumSize(new Dimension(180, 150));
        tableView.setEmbeddedDocumentMode(true);
        JScrollPane tableScroll = new JScrollPane(tableView,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScroll.setName("CALIBRATION TABLE SCROLL");
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableSurface.add(tableScroll, BorderLayout.CENTER);

        visualizationHost = new MapVisualizationHost(table,
                visualizationProvider);
        visualizationHost.setMinimumSize(new Dimension(160, 150));
        mapSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                tableSurface, visualizationHost);
        mapSplit.setName("MAP AND VISUALIZATION");
        mapSplit.setResizeWeight(0.63);
        mapSplit.setContinuousLayout(true);
        mapSplit.setDividerSize(5);
        mapSplit.setBorder(BorderFactory.createEmptyBorder());
        mapSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyMapOrientation();
            }
        });

        DatalogDockPanel datalog = new DatalogDockPanel(openLoggerAction,
                expanded -> setDatalogExpanded(expanded.booleanValue()));
        documentSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                mapSplit, datalog);
        documentSplit.setName("TUNING AND DATALOG");
        documentSplit.setResizeWeight(0.72);
        documentSplit.setContinuousLayout(true);
        documentSplit.setDividerSize(5);
        documentSplit.setBorder(BorderFactory.createEmptyBorder());
        documentSplit.setMinimumSize(new Dimension(360, 300));

        documentHeader = new MapDocumentHeader(table, contextMenu,
                visualizationHost.hasVisualization(), visible ->
                        setVisualizationVisible(visible.booleanValue()),
                focusTableSearchAction, favoriteState, toggleFavoriteAction);
        add(documentHeader, BorderLayout.NORTH);
        add(documentSplit, BorderLayout.CENTER);
        setVisualizationVisible(false);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                mapSplit.setDividerLocation(visualizationVisible ? 0.63 : 1.0);
                documentSplit.setDividerLocation(0.72);
            }
        });
    }

    JSplitPane getMapSplitForTesting() {
        return mapSplit;
    }

    JSplitPane getDocumentSplitForTesting() {
        return documentSplit;
    }

    void disposeDocument() {
        documentHeader.disposeHeader();
        visualizationHost.disposeVisualization();
    }

    void refreshMapState() {
        documentHeader.refreshState();
    }

    void showVisualization() {
        setVisualizationVisible(true);
    }

    private void applyMapOrientation() {
        int width = mapSplit.getWidth();
        if (width <= 0) return;
        boolean shouldBeNarrow = width < 680;
        if (shouldBeNarrow == narrowMapLayout) return;
        narrowMapLayout = shouldBeNarrow;
        mapSplit.setOrientation(shouldBeNarrow
                ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
        mapSplit.setResizeWeight(shouldBeNarrow ? 0.56 : 0.63);
        mapSplit.setDividerLocation(visualizationVisible
                ? (shouldBeNarrow ? 0.56 : 0.63) : 1.0);
    }

    private void setVisualizationVisible(boolean visible) {
        if (visible && !visualizationHost.hasVisualization()) return;
        visualizationVisible = visible;
        if (!visible) {
            visualizationDividerLocation = mapSplit.getDividerLocation();
            visualizationHost.setVisible(false);
            mapSplit.setDividerSize(0);
            mapSplit.setDividerLocation(1.0);
        } else {
            visualizationHost.setVisible(true);
            mapSplit.setDividerSize(5);
            int width = mapSplit.getWidth();
            if (width > 0) {
                int preferred = visualizationDividerLocation > 0
                        ? visualizationDividerLocation : (int) (width * 0.58);
                mapSplit.setDividerLocation(Math.max(220,
                        Math.min(preferred, width - 240)));
            }
            visualizationHost.requestFocusInWindow();
            visualizationHost.repaint();
        }
        mapSplit.revalidate();
        mapSplit.repaint();
    }

    private void setDatalogExpanded(boolean expanded) {
        int height = documentSplit.getHeight();
        if (!expanded) {
            datalogDividerLocation = documentSplit.getDividerLocation();
            if (height > 0) {
                documentSplit.setDividerLocation(Math.max(0, height - 42));
            }
        } else if (height > 0) {
            int preferred = datalogDividerLocation > 0
                    ? datalogDividerLocation : (int) (height * 0.72);
            documentSplit.setDividerLocation(Math.max(180,
                    Math.min(preferred, height - 120)));
        }
        documentSplit.revalidate();
        documentSplit.repaint();
    }
}
