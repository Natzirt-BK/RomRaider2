/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2012 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.swing;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JRootPane;
import java.awt.HeadlessException;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


public abstract class AbstractFrame extends JFrame implements WindowListener, PropertyChangeListener {
    private final boolean integratedChromeEnabled = !System.getProperty(
            "os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    private IntegratedWindowChrome windowChrome;

    public AbstractFrame() throws HeadlessException {
        super();
        prepareIntegratedWindowChrome();
    }

    public AbstractFrame(String arg0) throws HeadlessException {
        super(arg0);
        prepareIntegratedWindowChrome();
    }

    private static final long serialVersionUID = 7948304087075622157L;

    private void prepareIntegratedWindowChrome() {
        // The application owns its title controls so the menu and window
        // actions form one coherent header on every Linux desktop.
        if (!integratedChromeEnabled) return;
        if (!isUndecorated()) setUndecorated(true);
        getRootPane().setWindowDecorationStyle(JRootPane.NONE);
    }

    @Override
    public void setJMenuBar(JMenuBar menuBar) {
        if (!integratedChromeEnabled) {
            super.setJMenuBar(menuBar);
            return;
        }
        if (windowChrome != null) windowChrome.detachResizeSupport();
        windowChrome = menuBar == null ? null
                : IntegratedWindowChrome.install(this, menuBar);
        super.setJMenuBar(menuBar);
        if (windowChrome != null && isDisplayable()) {
            windowChrome.attachResizeSupport();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (windowChrome != null) windowChrome.attachResizeSupport();
    }

    @Override
    public void removeNotify() {
        if (windowChrome != null) windowChrome.detachResizeSupport();
        super.removeNotify();
    }

    public void windowActivated(WindowEvent arg0) {
    }

    public void windowClosed(WindowEvent e) {
    }

    public void windowClosing(WindowEvent e) {
    }

    public void windowDeactivated(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowOpened(WindowEvent e) {
    }

    public void propertyChange(PropertyChangeEvent arg0) {
    }

}
