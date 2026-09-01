/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.romraider.logger.ecu.ui.paramlist.ParameterListTable;
import com.romraider.logger.ecu.ui.paramlist.ParameterListTableModel;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Searchable parameter rail shared by the Logger's live workspaces. */
public final class LoggerParameterBrowser extends JPanel {
    private static final long serialVersionUID = 1L;
    private final ParameterListTableModel[] models;
    private final ParameterListTable[] tables;
    private final JTextField search = new PromptTextField("Search channels...");
    private final JLabel summary = new JLabel();
    private final JTabbedPane sections = new JTabbedPane(JTabbedPane.TOP,
            JTabbedPane.SCROLL_TAB_LAYOUT);

    public LoggerParameterBrowser(ParameterListTableModel[] models,
            ParameterListTable[] tables, String[] sectionNames) {
        super(new BorderLayout(0, 6));
        if (models.length != tables.length || tables.length != sectionNames.length) {
            throw new IllegalArgumentException(
                    "Logger parameter sections must have matching models, tables, and names");
        }
        this.models = models.clone();
        this.tables = tables.clone();
        setName("LOGGER PARAMETER BROWSER");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1,
                        UiThemeService.getInstance().color(
                                ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JPanel heading = new JPanel(new BorderLayout(8, 3));
        JLabel title = new JLabel("CHANNELS");
        title.setName("LOGGER PARAMETER HEADING");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
        heading.add(title, BorderLayout.WEST);
        summary.setName("LOGGER PARAMETER SUMMARY");
        summary.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        heading.add(summary, BorderLayout.EAST);

        search.setName("LOGGER PARAMETER SEARCH");
        search.setToolTipText("Search channel names and units");
        search.getAccessibleContext().setAccessibleName(
                "Search Logger channels");

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(heading, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        sections.setName("LOGGER PARAMETER SECTIONS");
        for (int index = 0; index < tables.length; index++) {
            tables[index].setName("LOGGER "
                    + sectionNames[index].toUpperCase() + " TABLE");
            tables[index].setFillsViewportHeight(true);
            JScrollPane scroll = new JScrollPane(tables[index],
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            sections.addTab(sectionNames[index], scroll);
            models[index].addTableModelListener(new TableModelListener() {
                public void tableChanged(TableModelEvent event) {
                    updateSummary();
                }
            });
        }
        add(sections, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { applyFilter(); }
            public void removeUpdate(DocumentEvent event) { applyFilter(); }
            public void changedUpdate(DocumentEvent event) { applyFilter(); }
        });
        updateSummary();
    }

    public void setActions(JComponent... controls) {
        JPanel actions = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.LEFT, 6, 0));
        actions.setName("LOGGER CHANNEL ACTIONS");
        actions.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        for (JComponent control : controls) actions.add(control);
        add(actions, BorderLayout.SOUTH);
    }

    private void applyFilter() {
        for (ParameterListTable table : tables) {
            table.setFilterText(search.getText());
        }
    }

    private void updateSummary() {
        int available = 0;
        int selected = 0;
        for (ParameterListTableModel model : models) {
            available += model.getRowCount();
            selected += model.getSelectedCount();
        }
        summary.setText(selected + " / " + available + " selected");
    }

    public JTextField getSearchField() {
        return search;
    }

    public JLabel getSummaryLabel() {
        return summary;
    }

    public JTabbedPane getSectionTabs() {
        return sections;
    }

    private static final class PromptTextField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final String prompt;

        private PromptTextField(String prompt) {
            super(18);
            this.prompt = prompt;
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent event) { repaint(); }
                public void focusLost(FocusEvent event) { repaint(); }
            });
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty() || isFocusOwner()) return;
            Graphics copy = graphics.create();
            try {
                Color color = UIManager.getColor("TextField.inactiveForeground");
                copy.setColor(color == null ? Color.GRAY : color);
                copy.setFont(getFont());
                Insets insets = getInsets();
                int y = insets.top + copy.getFontMetrics().getAscent()
                        + Math.max(0, (getHeight() - insets.top - insets.bottom
                                - copy.getFontMetrics().getHeight()) / 2);
                copy.drawString(prompt, insets.left + 2, y);
            } finally {
                copy.dispose();
            }
        }
    }
}
