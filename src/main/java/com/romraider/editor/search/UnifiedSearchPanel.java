/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.search;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.romraider.search.SearchEntry;
import com.romraider.search.SearchResult;
import com.romraider.search.UnifiedSearchIndex;
import com.romraider.ui.swing.ModernSearchField;

/** Search palette UI; all searchable data and actions remain outside Swing. */
public final class UnifiedSearchPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public interface Listener {
        void entryChosen(SearchEntry entry);
        void closeRequested();
    }

    private final UnifiedSearchIndex index;
    private final Listener listener;
    private final JTextField query = new ModernSearchField(
            "Search maps, logger parameters, DTCs, settings, and commands");
    private final DefaultListModel<SearchResult> model =
            new DefaultListModel<SearchResult>();
    private final JList<SearchResult> results = new JList<SearchResult>(model);
    private final JLabel summary = new JLabel(" ");

    public UnifiedSearchPanel(UnifiedSearchIndex index, Listener listener) {
        super(new BorderLayout(0, 8));
        this.index = index;
        this.listener = listener;
        setName("UNIFIED SEARCH PALETTE");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        setPreferredSize(new Dimension(620, 430));

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        query.setName("UNIFIED SEARCH QUERY");
        JButton close = new JButton("Close");
        close.addActionListener(event -> close());
        searchRow.add(query, BorderLayout.CENTER);
        searchRow.add(close, BorderLayout.EAST);
        add(searchRow, BorderLayout.NORTH);

        results.setName("UNIFIED SEARCH RESULTS");
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setCellRenderer(new ResultRenderer());
        results.setFixedCellHeight(48);
        results.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) chooseSelected();
            }
        });
        add(new JScrollPane(results), BorderLayout.CENTER);

        summary.setName("UNIFIED SEARCH SUMMARY");
        summary.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        add(summary, BorderLayout.SOUTH);

        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { refreshResults(); }
            public void removeUpdate(DocumentEvent event) { refreshResults(); }
            public void changedUpdate(DocumentEvent event) { refreshResults(); }
        });
        query.addActionListener(event -> chooseSelected());
        query.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_DOWN && model.size() > 0) {
                    results.requestFocusInWindow();
                    results.setSelectedIndex(Math.min(model.size() - 1,
                            Math.max(0, results.getSelectedIndex() + 1)));
                    event.consume();
                }
            }
        });
        results.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                "chooseSearchResult");
        results.getActionMap().put("chooseSearchResult", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { chooseSelected(); }
        });
        InputMap focusedWindow = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        focusedWindow.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "closeSearchPalette");
        getActionMap().put("closeSearchPalette", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent event) { close(); }
        });
        refreshResults();
    }

    public void focusQuery() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { query.requestFocusInWindow(); }
        });
    }

    void refreshResults() {
        List<SearchResult> matches = index.search(query.getText(), 60);
        model.clear();
        for (SearchResult result : matches) model.addElement(result);
        if (!matches.isEmpty()) results.setSelectedIndex(0);
        summary.setText(matches.isEmpty() ? "No matches"
                : matches.size() + (matches.size() == 1 ? " result" : " results")
                  + "  •  Enter to open");
    }

    private void chooseSelected() {
        SearchResult selected = results.getSelectedValue();
        if (selected != null && listener != null) {
            listener.entryChosen(selected.getEntry());
        }
    }

    private void close() {
        if (listener != null) listener.closeRequested();
    }

    private static final class ResultRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list,
                    value, index, selected, focused);
            SearchEntry entry = ((SearchResult) value).getEntry();
            label.setText("<html><b>" + escape(entry.getTitle()) + "</b>"
                    + " <font color='#7f8b96'>" + escape(entry.getKind().getDisplayName())
                    + "</font><br><font size='-1'>" + escape(entry.getContext())
                    + "</font></html>");
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            label.setFont(label.getFont().deriveFont(Font.PLAIN));
            return label;
        }

        private static String escape(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }
}
