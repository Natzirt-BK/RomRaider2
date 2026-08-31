/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;

/** Applies reversible minimum hit targets to interactive Swing controls. */
public final class TouchTargetService {
    private static final String ORIGINAL_PREFERRED = "romraider2.originalPreferredSize";
    private static final String ORIGINAL_MINIMUM = "romraider2.originalMinimumSize";
    private static final String ORIGINAL_FLOW_GAP = "romraider2.originalFlowGap";
    private static final int MINIMUM_TOUCH_TARGET = 48;

    private TouchTargetService() {
    }

    public static void apply(Component root, DisplayMode mode) {
        if (root == null || mode == null) return;
        applyOne(root, mode.isTouchOptimized());
        if (root instanceof Container) {
            applySpacing((Container) root, mode.isTouchOptimized());
            for (Component child : ((Container) root).getComponents()) apply(child, mode);
        }
    }

    /** Removes metrics applied by the previous mode before Swing installs new defaults. */
    public static void prepareForModeChange(Component root) {
        prepareForModeChange(root, DisplayMode.NORMAL);
    }

    public static void prepareForModeChange(Component root,
            DisplayMode nextMode) {
        if (root == null) return;
        if (root instanceof JComponent) restore((JComponent) root);
        if (root instanceof Container) {
            restoreSpacing((Container) root);
        }
        if (nextMode != null && nextMode.isTouchOptimized()) {
            if (root instanceof AbstractButton || root instanceof JComboBox) {
                remember((JComponent) root);
            }
            if (root instanceof Container) rememberSpacing((Container) root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                prepareForModeChange(child, nextMode);
            }
        }
    }

    private static void applyOne(Component component, boolean touch) {
        if (!(component instanceof AbstractButton) && !(component instanceof JComboBox)) return;
        JComponent control = (JComponent) component;
        if (touch) {
            remember(control);
            Dimension preferred = control.getPreferredSize();
            int minimum = Math.max(MINIMUM_TOUCH_TARGET,
                    UiScaleService.getInstance().control(36));
            if (control instanceof JMenuItem) minimum = Math.max(44,
                    UiScaleService.getInstance().control(32));
            Dimension target = new Dimension(Math.max(preferred.width, minimum),
                    Math.max(preferred.height, minimum));
            control.setMinimumSize(target);
            control.setPreferredSize(target);
        } else {
            restore(control);
        }
    }

    private static void applySpacing(Container container, boolean touch) {
        if (!(container.getLayout() instanceof FlowLayout) || !(container instanceof JComponent)) return;
        JComponent component = (JComponent) container;
        FlowLayout layout = (FlowLayout) container.getLayout();
        if (touch) {
            if (component.getClientProperty(ORIGINAL_FLOW_GAP) == null) {
                component.putClientProperty(ORIGINAL_FLOW_GAP,
                        new Dimension(layout.getHgap(), layout.getVgap()));
            }
            layout.setHgap(Math.max(layout.getHgap(),
                    UiScaleService.getInstance().control(6)));
            layout.setVgap(Math.max(layout.getVgap(),
                    UiScaleService.getInstance().control(4)));
        } else {
            restoreSpacing(container);
        }
    }

    private static void restoreSpacing(Container container) {
        if (!(container.getLayout() instanceof FlowLayout)
                || !(container instanceof JComponent)) return;
        JComponent component = (JComponent) container;
        FlowLayout layout = (FlowLayout) container.getLayout();
        Object original = component.getClientProperty(ORIGINAL_FLOW_GAP);
        if (original instanceof Dimension) {
            layout.setHgap(((Dimension) original).width);
            layout.setVgap(((Dimension) original).height);
        }
        component.putClientProperty(ORIGINAL_FLOW_GAP, null);
    }

    private static void rememberSpacing(Container container) {
        if (!(container.getLayout() instanceof FlowLayout)
                || !(container instanceof JComponent)) return;
        JComponent component = (JComponent) container;
        if (component.getClientProperty(ORIGINAL_FLOW_GAP) == null) {
            FlowLayout layout = (FlowLayout) container.getLayout();
            component.putClientProperty(ORIGINAL_FLOW_GAP,
                    new Dimension(layout.getHgap(), layout.getVgap()));
        }
    }

    private static void remember(JComponent component) {
        if (component.getClientProperty(ORIGINAL_PREFERRED) == null) {
            component.putClientProperty(ORIGINAL_PREFERRED, component.getPreferredSize());
            component.putClientProperty(ORIGINAL_MINIMUM, component.getMinimumSize());
        }
    }

    private static void restore(JComponent component) {
        Object preferred = component.getClientProperty(ORIGINAL_PREFERRED);
        Object minimum = component.getClientProperty(ORIGINAL_MINIMUM);
        if (preferred instanceof Dimension) component.setPreferredSize((Dimension) preferred);
        if (minimum instanceof Dimension) component.setMinimumSize((Dimension) minimum);
        component.putClientProperty(ORIGINAL_PREFERRED, null);
        component.putClientProperty(ORIGINAL_MINIMUM, null);
    }
}
