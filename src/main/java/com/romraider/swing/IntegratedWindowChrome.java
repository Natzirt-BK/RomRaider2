/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiScaleService;
import com.romraider.ui.UiThemeService;

/** Client-side title controls integrated into the application's menu row. */
final class IntegratedWindowChrome {
    private static final int RESIZE_EDGE = 10;
    private final JFrame frame;
    private final JLabel title = new JLabel("", SwingConstants.CENTER);
    private final WindowButton maximize;
    private final ResizeGrip leftResizeGrip = new ResizeGrip(true);
    private final ResizeGrip resizeGrip = new ResizeGrip(false);
    private final ResizeListener resizeListener = new ResizeListener();
    private boolean resizeAttached;

    private IntegratedWindowChrome(JFrame frame, JMenuBar menuBar) {
        this.frame = frame;
        title.setName("INTEGRATED WINDOW TITLE");
        title.setText(frame.getTitle());
        title.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));

        JPanel dragArea = new JPanel(new java.awt.BorderLayout());
        dragArea.setName("INTEGRATED WINDOW DRAG AREA");
        dragArea.setOpaque(false);
        dragArea.setMinimumSize(new Dimension(40, 24));
        dragArea.setPreferredSize(new Dimension(300, 28));
        dragArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        dragArea.add(title, java.awt.BorderLayout.CENTER);
        DragListener drag = new DragListener();
        dragArea.addMouseListener(drag);
        dragArea.addMouseMotionListener(drag);
        title.addMouseListener(drag);
        title.addMouseMotionListener(drag);

        WindowButton minimize = new WindowButton(Control.MINIMIZE);
        maximize = new WindowButton(Control.MAXIMIZE);
        WindowButton close = new WindowButton(Control.CLOSE);
        minimize.addActionListener(event -> frame.setExtendedState(
                frame.getExtendedState() | JFrame.ICONIFIED));
        maximize.addActionListener(event -> toggleMaximized());
        close.addActionListener(event -> frame.dispatchEvent(
                new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));

        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE)));
        menuBar.add(dragArea);
        menuBar.add(minimize);
        menuBar.add(maximize);
        menuBar.add(close);
        frame.addPropertyChangeListener("title",
                event -> title.setText(String.valueOf(event.getNewValue())));
        frame.addWindowStateListener(event -> {
            maximize.repaint();
            updateResizeGrip();
        });
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateResizeGrip();
            }

            @Override
            public void componentShown(ComponentEvent event) {
                updateResizeGrip();
            }
        });
        frame.getLayeredPane().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateResizeGrip();
            }
        });
        frame.getLayeredPane().add(leftResizeGrip, JLayeredPane.DRAG_LAYER);
        frame.getLayeredPane().add(resizeGrip, JLayeredPane.DRAG_LAYER);
        updateResizeGrip();
        SwingUtilities.invokeLater(this::updateResizeGrip);
    }

    static IntegratedWindowChrome install(JFrame frame, JMenuBar menuBar) {
        Object existing = menuBar.getClientProperty(
                IntegratedWindowChrome.class.getName());
        if (existing instanceof IntegratedWindowChrome) {
            return (IntegratedWindowChrome) existing;
        }
        IntegratedWindowChrome chrome = new IntegratedWindowChrome(frame,
                menuBar);
        menuBar.putClientProperty(IntegratedWindowChrome.class.getName(), chrome);
        return chrome;
    }

    void attachResizeSupport() {
        if (resizeAttached) return;
        Toolkit.getDefaultToolkit().addAWTEventListener(resizeListener,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        resizeAttached = true;
        updateResizeGrip();
    }

    void detachResizeSupport() {
        if (!resizeAttached) return;
        Toolkit.getDefaultToolkit().removeAWTEventListener(resizeListener);
        resizeAttached = false;
    }

    private void toggleMaximized() {
        int state = frame.getExtendedState();
        if ((state & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH) {
            frame.setExtendedState(state & ~JFrame.MAXIMIZED_BOTH);
        } else {
            frame.setExtendedState(state | JFrame.MAXIMIZED_BOTH);
        }
    }

    private boolean isMaximized() {
        return (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH)
                == JFrame.MAXIMIZED_BOTH;
    }

    static Rectangle resizeFromBottomCorner(Rectangle startingBounds,
            int dx, int dy, Dimension minimum, boolean left) {
        Rectangle next = new Rectangle(startingBounds);
        if (left) {
            next.x += dx;
            next.width -= dx;
            if (next.width < minimum.width) {
                next.x = startingBounds.x + startingBounds.width
                        - minimum.width;
                next.width = minimum.width;
            }
        } else {
            next.width = Math.max(minimum.width, startingBounds.width + dx);
        }
        next.height = Math.max(minimum.height, startingBounds.height + dy);
        return next;
    }

    private void updateResizeGrip() {
        int size = resizeGrip.getPreferredSize().width;
        int y = Math.max(0, frame.getLayeredPane().getHeight() - size);
        leftResizeGrip.setBounds(0, y, size, size);
        resizeGrip.setBounds(Math.max(0, frame.getLayeredPane().getWidth() - size),
                y, size, size);
        boolean visible = frame.isResizable() && !isMaximized();
        leftResizeGrip.setVisible(visible);
        resizeGrip.setVisible(visible);
        frame.getLayeredPane().moveToFront(leftResizeGrip);
        frame.getLayeredPane().moveToFront(resizeGrip);
    }

    private final class DragListener extends MouseAdapter {
        private Point pressedAt;
        private Point windowAt;

        @Override
        public void mousePressed(MouseEvent event) {
            if (!SwingUtilities.isLeftMouseButton(event) || isMaximized()) return;
            pressedAt = event.getLocationOnScreen();
            windowAt = frame.getLocation();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (pressedAt == null || windowAt == null || isMaximized()) return;
            Point now = event.getLocationOnScreen();
            frame.setLocation(windowAt.x + now.x - pressedAt.x,
                    windowAt.y + now.y - pressedAt.y);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            pressedAt = null;
            windowAt = null;
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event)
                    && event.getClickCount() == 2) toggleMaximized();
        }
    }

    private final class ResizeListener implements AWTEventListener {
        private int direction = Cursor.DEFAULT_CURSOR;
        private Point pressedAt;
        private Rectangle startingBounds;

        public void eventDispatched(AWTEvent awtEvent) {
            if (!(awtEvent instanceof MouseEvent)) return;
            MouseEvent event = (MouseEvent) awtEvent;
            Object source = event.getSource();
            if (!(source instanceof Component)) return;
            Window owner = SwingUtilities.getWindowAncestor((Component) source);
            if (owner != frame || isMaximized() || !frame.isResizable()) return;

            if (event.getID() == MouseEvent.MOUSE_MOVED) {
                direction = resizeDirection(event.getLocationOnScreen());
                frame.setCursor(Cursor.getPredefinedCursor(direction));
            } else if (event.getID() == MouseEvent.MOUSE_PRESSED
                    && (event.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0) {
                direction = resizeDirection(event.getLocationOnScreen());
                if (direction != Cursor.DEFAULT_CURSOR) {
                    pressedAt = event.getLocationOnScreen();
                    startingBounds = frame.getBounds();
                }
            } else if (event.getID() == MouseEvent.MOUSE_DRAGGED
                    && pressedAt != null && startingBounds != null) {
                resize(event.getLocationOnScreen());
            } else if (event.getID() == MouseEvent.MOUSE_RELEASED) {
                pressedAt = null;
                startingBounds = null;
            }
        }

        private int resizeDirection(Point screen) {
            Point frameAt = frame.getLocationOnScreen();
            int x = screen.x - frameAt.x;
            int y = screen.y - frameAt.y;
            boolean left = x >= 0 && x <= RESIZE_EDGE;
            boolean right = x >= frame.getWidth() - RESIZE_EDGE;
            boolean top = y >= 0 && y <= RESIZE_EDGE;
            boolean bottom = y >= frame.getHeight() - RESIZE_EDGE;
            if (left && top) return Cursor.NW_RESIZE_CURSOR;
            if (right && top) return Cursor.NE_RESIZE_CURSOR;
            if (left && bottom) return Cursor.SW_RESIZE_CURSOR;
            if (right && bottom) return Cursor.SE_RESIZE_CURSOR;
            if (left) return Cursor.W_RESIZE_CURSOR;
            if (right) return Cursor.E_RESIZE_CURSOR;
            if (top) return Cursor.N_RESIZE_CURSOR;
            if (bottom) return Cursor.S_RESIZE_CURSOR;
            return Cursor.DEFAULT_CURSOR;
        }

        private void resize(Point now) {
            int dx = now.x - pressedAt.x;
            int dy = now.y - pressedAt.y;
            Rectangle next = new Rectangle(startingBounds);
            if (direction == Cursor.W_RESIZE_CURSOR
                    || direction == Cursor.NW_RESIZE_CURSOR
                    || direction == Cursor.SW_RESIZE_CURSOR) {
                next.x += dx;
                next.width -= dx;
            }
            if (direction == Cursor.E_RESIZE_CURSOR
                    || direction == Cursor.NE_RESIZE_CURSOR
                    || direction == Cursor.SE_RESIZE_CURSOR) next.width += dx;
            if (direction == Cursor.N_RESIZE_CURSOR
                    || direction == Cursor.NW_RESIZE_CURSOR
                    || direction == Cursor.NE_RESIZE_CURSOR) {
                next.y += dy;
                next.height -= dy;
            }
            if (direction == Cursor.S_RESIZE_CURSOR
                    || direction == Cursor.SW_RESIZE_CURSOR
                    || direction == Cursor.SE_RESIZE_CURSOR) next.height += dy;

            Dimension minimum = frame.getMinimumSize();
            if (next.width < minimum.width) {
                if (next.x != startingBounds.x) {
                    next.x = startingBounds.x + startingBounds.width
                            - minimum.width;
                }
                next.width = minimum.width;
            }
            if (next.height < minimum.height) {
                if (next.y != startingBounds.y) {
                    next.y = startingBounds.y + startingBounds.height
                            - minimum.height;
                }
                next.height = minimum.height;
            }
            frame.setBounds(next);
        }
    }

    private enum Control { MINIMIZE, MAXIMIZE, CLOSE }

    /** Visible, direct manipulation target for the frameless window corner. */
    private final class ResizeGrip extends JPanel {
        private static final long serialVersionUID = 1L;
        private final boolean left;
        private Point pressedAt;
        private Rectangle startingBounds;
        private boolean hovered;

        ResizeGrip(boolean left) {
            this.left = left;
            setName(left ? "WINDOW LEFT RESIZE GRIP" : "WINDOW RESIZE GRIP");
            setToolTipText(left ? "Drag to resize from lower-left"
                    : "Drag to resize from lower-right");
            setCursor(Cursor.getPredefinedCursor(left
                    ? Cursor.SW_RESIZE_CURSOR : Cursor.SE_RESIZE_CURSOR));
            setPreferredSize(new Dimension(24, 24));
            setOpaque(false);
            MouseAdapter resize = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)
                            || isMaximized()) return;
                    pressedAt = event.getLocationOnScreen();
                    startingBounds = frame.getBounds();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (pressedAt == null || startingBounds == null) return;
                    Point now = event.getLocationOnScreen();
                    int dx = now.x - pressedAt.x;
                    int dy = now.y - pressedAt.y;
                    Dimension minimum = frame.getMinimumSize();
                    frame.setBounds(resizeFromBottomCorner(startingBounds,
                            dx, dy, minimum, left));
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    pressedAt = null;
                    startingBounds = null;
                }
            };
            addMouseListener(resize);
            addMouseMotionListener(resize);
            getAccessibleContext().setAccessibleName(left
                    ? "Resize window from lower-left corner"
                    : "Resize window from lower-right corner");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color surface = UiThemeService.getInstance().color(
                        ThemeToken.RAISED_SURFACE);
                g.setColor(new Color(surface.getRed(), surface.getGreen(),
                        surface.getBlue(), 210));
                int[] x = left ? new int[] {0, 19, 0}
                        : new int[] {getWidth() - 19, getWidth(), getWidth()};
                int[] y = left ? new int[] {getHeight() - 19, getHeight(),
                        getHeight()} : new int[] {getHeight(),
                                getHeight() - 19, getHeight()};
                g.fillPolygon(x, y, 3);
                g.setColor(UiThemeService.getInstance().color(hovered
                        ? ThemeToken.ACCENT : ThemeToken.PRIMARY_TEXT));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int edge = left ? 3 : getWidth() - 4;
                int bottom = getHeight() - 4;
                for (int offset = 0; offset < 3; offset++) {
                    int inset = 5 + offset * 5;
                    if (left) {
                        g.drawLine(edge, bottom - inset, edge + inset, bottom);
                    } else {
                        g.drawLine(edge - inset, bottom, edge, bottom - inset);
                    }
                }
            } finally {
                g.dispose();
            }
        }
    }

    private final class WindowButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final Control control;
        private boolean hovered;

        WindowButton(Control control) {
            this.control = control;
            setName("WINDOW " + control.name());
            setToolTipText(control == Control.MINIMIZE ? "Minimize"
                    : control == Control.MAXIMIZE ? "Maximize or restore"
                    : "Close");
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            int height = Math.max(26, UiScaleService.getInstance().control(26));
            setPreferredSize(new Dimension(Math.max(38, height + 10), height));
            setMinimumSize(getPreferredSize());
            setMaximumSize(new Dimension(getPreferredSize().width,
                    getPreferredSize().height));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
            getAccessibleContext().setAccessibleName(getToolTipText());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (hovered) {
                    Color hover = control == Control.CLOSE
                            ? UiThemeService.getInstance().color(ThemeToken.DANGER)
                            : UiThemeService.getInstance().color(
                                    ThemeToken.RAISED_SURFACE);
                    g.setColor(hover);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                g.setColor(hovered && control == Control.CLOSE
                        ? UiThemeService.contrastText(UiThemeService.getInstance()
                                .color(ThemeToken.DANGER))
                        : UiThemeService.getInstance().color(
                                ThemeToken.PRIMARY_TEXT));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                if (control == Control.MINIMIZE) {
                    g.drawLine(cx - 6, cy + 3, cx + 6, cy + 3);
                } else if (control == Control.CLOSE) {
                    g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                    g.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
                } else if (isMaximized()) {
                    g.drawRect(cx - 6, cy - 4, 9, 8);
                    g.drawRect(cx - 3, cy - 7, 9, 8);
                } else {
                    g.drawRect(cx - 6, cy - 5, 12, 10);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
