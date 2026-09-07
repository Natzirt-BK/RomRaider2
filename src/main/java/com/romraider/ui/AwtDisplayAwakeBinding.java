/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/** Foreground native-window binding shared by both Compose desktop hosts. */
public final class AwtDisplayAwakeBinding implements AutoCloseable {
    private final Window window;
    private final DesktopDisplayAwake awake;
    private final BooleanSupplier fullScreen;
    private boolean closed;
    private final WindowAdapter windowListener = new WindowAdapter() {
        @Override public void windowGainedFocus(WindowEvent event) { update(); }
        @Override public void windowLostFocus(WindowEvent event) { update(); }
        @Override public void windowStateChanged(WindowEvent event) { update(); }
        @Override public void windowClosed(WindowEvent event) { close(); }
    };
    private final ComponentAdapter visibilityListener = new ComponentAdapter() {
        @Override public void componentShown(ComponentEvent event) { update(); }
        @Override public void componentHidden(ComponentEvent event) { update(); }
    };

    public AwtDisplayAwakeBinding(Window window, DesktopDisplayAwake awake, BooleanSupplier fullScreen) {
        requireEdt();
        this.window = Objects.requireNonNull(window);
        this.awake = Objects.requireNonNull(awake);
        this.fullScreen = Objects.requireNonNull(fullScreen);
        window.addWindowFocusListener(windowListener);
        window.addWindowStateListener(windowListener);
        window.addWindowListener(windowListener);
        window.addComponentListener(visibilityListener);
        update();
    }

    public void update() {
        requireEdt();
        if (closed) return;
        boolean minimized = window instanceof Frame && (((Frame) window).getExtendedState() & Frame.ICONIFIED) != 0;
        awake.setActive(fullScreen.getAsBoolean() && window.isShowing() && window.isFocused() && !minimized);
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        window.removeWindowFocusListener(windowListener);
        window.removeWindowStateListener(windowListener);
        window.removeWindowListener(windowListener);
        window.removeComponentListener(visibilityListener);
        awake.setActive(false);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Display-awake binding requires the AWT event thread");
    }
}
