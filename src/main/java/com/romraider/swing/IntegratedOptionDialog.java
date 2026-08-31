/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiScaleService;
import com.romraider.ui.UiThemeService;

/** Application-styled modal option dialog with integrated client-side chrome. */
public final class IntegratedOptionDialog {
    private IntegratedOptionDialog() {
    }

    public static int show(Component owner, String message, String title,
            int messageType, Object[] options, Object initialValue) {
        DialogParts parts = create(owner, message, title, messageType,
                options, initialValue);
        parts.dialog.setVisible(true);
        Object selected = parts.optionPane.getValue();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(selected)) return i;
        }
        return JOptionPane.CLOSED_OPTION;
    }

    static DialogParts create(Component owner, String message, String title,
            int messageType, Object[] options, Object initialValue) {
        Window ownerWindow = owner == null ? null
                : SwingUtilities.getWindowAncestor(owner);
        final JDialog dialog = new JDialog(ownerWindow, title,
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setName("INTEGRATED OPTION DIALOG");
        dialog.setUndecorated(true);
        dialog.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        final JOptionPane optionPane = new JOptionPane(message, messageType,
                JOptionPane.DEFAULT_OPTION, null, options, initialValue);
        optionPane.setName("INTEGRATED OPTION CONTENT");
        optionPane.setBorder(BorderFactory.createEmptyBorder(
                UiScaleService.getInstance().control(10),
                UiScaleService.getInstance().control(12),
                UiScaleService.getInstance().control(8),
                UiScaleService.getInstance().control(12)));

        dialog.setLayout(new BorderLayout());
        dialog.add(createHeader(dialog, title), BorderLayout.NORTH);
        dialog.add(optionPane, BorderLayout.CENTER);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(
                UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE)));
        dialog.getRootPane().registerKeyboardAction(new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent event) {
                optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);
                dialog.dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        optionPane.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                if (dialog.isVisible()
                        && JOptionPane.VALUE_PROPERTY.equals(
                                event.getPropertyName())
                        && event.getNewValue() != null
                        && event.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
                    dialog.dispose();
                }
            }
        });
        dialog.pack();
        Dimension minimum = new Dimension(
                UiScaleService.getInstance().control(500),
                dialog.getPreferredSize().height);
        dialog.setMinimumSize(minimum);
        if (dialog.getWidth() < minimum.width) {
            dialog.setSize(minimum.width, dialog.getHeight());
        }
        dialog.setLocationRelativeTo(owner);
        return new DialogParts(dialog, optionPane);
    }

    static JMenuBar createHeader(final JDialog dialog, String title) {
        JMenuBar header = new JMenuBar();
        header.setName("INTEGRATED DIALOG TITLE BAR");
        header.setLayout(new javax.swing.BoxLayout(header,
                javax.swing.BoxLayout.X_AXIS));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE)));

        int controlSize = UiScaleService.getInstance().control(30);
        header.add(Box.createRigidArea(new Dimension(controlSize, controlSize)));
        header.add(Box.createHorizontalGlue());
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setName("INTEGRATED DIALOG TITLE");
        titleLabel.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        header.add(titleLabel);
        header.add(Box.createHorizontalGlue());

        JButton close = new JButton("×");
        close.setName("DIALOG CLOSE");
        close.setToolTipText("Close");
        close.setFocusable(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setForeground(UiThemeService.getInstance().color(
                ThemeToken.PRIMARY_TEXT));
        close.setPreferredSize(new Dimension(controlSize, controlSize));
        close.setMaximumSize(new Dimension(controlSize, controlSize));
        close.addActionListener(event -> dialog.dispose());
        header.add(close);

        DragListener drag = new DragListener(dialog);
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);
        titleLabel.addMouseListener(drag);
        titleLabel.addMouseMotionListener(drag);
        return header;
    }

    static final class DialogParts {
        final JDialog dialog;
        final JOptionPane optionPane;

        DialogParts(JDialog dialog, JOptionPane optionPane) {
            this.dialog = dialog;
            this.optionPane = optionPane;
        }
    }

    private static final class DragListener extends MouseAdapter {
        private final Window window;
        private Point pressedAt;
        private Point windowAt;

        DragListener(Window window) {
            this.window = window;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            if (!SwingUtilities.isLeftMouseButton(event)) return;
            pressedAt = event.getLocationOnScreen();
            windowAt = window.getLocation();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (pressedAt == null || windowAt == null) return;
            Point now = event.getLocationOnScreen();
            window.setLocation(windowAt.x + now.x - pressedAt.x,
                    windowAt.y + now.y - pressedAt.y);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            pressedAt = null;
            windowAt = null;
        }
    }
}
