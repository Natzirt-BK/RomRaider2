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

package com.romraider.logger.ecu.ui.handler.dash;

import java.awt.CardLayout;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class Gauge extends JPanel {
    private static final long serialVersionUID = 7354117571944547043L;
    private GaugeStyle style;
    private final CardLayout cards = new CardLayout();
    private boolean available;

    public Gauge(GaugeStyle style) {
        setLayout(cards);
        setGaugeStyle(style);
    }

    public void refreshTitle() {
        style.refreshTitle();
    }

    public void updateValue(double value) {
        final boolean valid = Double.isFinite(value);
        if (valid) style.updateValue(value);
        SwingUtilities.invokeLater(() -> {
            available = valid;
            cards.show(this, available ? "value" : "missing");
        });
    }

    public void resetValue() {
        style.resetValue();
        SwingUtilities.invokeLater(() -> {
            available = false;
            cards.show(this, "missing");
        });
    }

    public void setGaugeStyle(final GaugeStyle style) {
        this.style = style;

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                removeAll();
                available = false;
                JPanel child = new JPanel();
                style.apply(child);
                add(child, "value");
                add(new JLabel("NO VALID DATA", SwingConstants.CENTER), "missing");
                cards.show(Gauge.this, available ? "value" : "missing");
                revalidate();
                repaint();
            }
        });
    }

}
