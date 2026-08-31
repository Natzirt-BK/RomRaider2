/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.logger.ecu.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SerialPortComboBoxTest {
    @Test
    public void j2534PresentationDoesNotImplyThatAComPortIsUsed() {
        SerialPortComboBox comboBox = new SerialPortComboBox();
        comboBox.addItem("ttyS0");

        comboBox.showJ2534Interface();

        assertEquals(1, comboBox.getItemCount());
        assertEquals("J2534", comboBox.getSelectedItem());
        assertFalse(comboBox.isEnabled());
        assertTrue(comboBox.getToolTipText().contains("no COM port"));
    }
}
