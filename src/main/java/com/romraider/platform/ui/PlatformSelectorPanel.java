/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.romraider.platform.ui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.romraider.Settings;
import com.romraider.platform.PlatformCapabilities;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.PlatformContextListener;
import com.romraider.platform.PlatformRegistry;
import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;
import com.romraider.util.SettingsManager;

public final class PlatformSelectorPanel extends JPanel
        implements PlatformContextListener {
    private static final long serialVersionUID = 1L;

    private final PlatformContext context = PlatformContext.getInstance();
    private final Settings settings = SettingsManager.getSettings();
    private final JComboBox platformSelector = new JComboBox(VehiclePlatform.values());
    private final JComboBox moduleSelector = new JComboBox();
    private boolean refreshing;
    private boolean listening;

    public PlatformSelectorPanel() {
        this(false);
    }

    public PlatformSelectorPanel(boolean compact) {
        super(new FlowLayout(compact ? FlowLayout.RIGHT : FlowLayout.LEFT,
                compact ? 4 : 5, 0));
        setBorder(BorderFactory.createEmptyBorder(2, compact ? 0 : 5, 2, 0));

        if (!compact) add(new JLabel("Vehicle:"));
        add(platformSelector);
        if (!compact) add(new JLabel("Module:"));
        add(moduleSelector);

        platformSelector.setName("VEHICLE PLATFORM");
        moduleSelector.setName("VEHICLE MODULE");
        platformSelector.getAccessibleContext().setAccessibleName(
                "Vehicle platform");
        moduleSelector.getAccessibleContext().setAccessibleName(
                "Vehicle module");
        platformSelector.setToolTipText("Select the active vehicle platform");
        moduleSelector.setToolTipText("Modules are filtered by platform capability");
        refreshFromContext();
        platformSelector.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (!refreshing) {
                    context.setPlatform((VehiclePlatform) platformSelector.getSelectedItem());
                    persistSelection();
                }
            }
        });
        moduleSelector.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (!refreshing && moduleSelector.getSelectedItem() != null) {
                    context.setModule((VehicleModule) moduleSelector.getSelectedItem());
                    persistSelection();
                }
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!listening) {
            listening = true;
            context.addListener(this);
        }
        refreshFromContext();
    }

    @Override
    public void removeNotify() {
        if (listening) {
            context.removeListener(this);
            listening = false;
        }
        super.removeNotify();
    }

    public void platformContextChanged(PlatformContext changedContext) {
        persistSelection();
        if (SwingUtilities.isEventDispatchThread()) {
            refreshFromContext();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    refreshFromContext();
                }
            });
        }
    }

    private void persistSelection() {
        settings.setVehiclePlatform(context.getPlatform());
        settings.setVehicleModule(context.getModule());
    }

    private void refreshFromContext() {
        refreshing = true;
        try {
            VehiclePlatform platform = context.getPlatform();
            PlatformCapabilities capabilities = PlatformRegistry.get(platform);
            platformSelector.setSelectedItem(platform);
            moduleSelector.removeAllItems();
            for (VehicleModule module : capabilities.getModules()) {
                moduleSelector.addItem(module);
            }
            moduleSelector.setSelectedItem(context.getModule());
        } finally {
            refreshing = false;
        }
    }
}
