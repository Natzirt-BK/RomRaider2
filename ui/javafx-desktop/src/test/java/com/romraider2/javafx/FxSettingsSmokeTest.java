/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.util.SettingsManager;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxSettingsSmokeTest {
    private static CheckBox check(Node node, String text) {
        if (node instanceof CheckBox box && text.equals(box.getText())) return box;
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) {
            CheckBox box = check(child, text); if (box != null) return box;
        }
        return null;
    }

    @Test void cancellationDoesNotApplyAndApplyPreservesLegacyClickMeaning() throws Exception {
        FxTestRuntime.run(() -> {
            var settings = SettingsManager.getSettings();
            int original = settings.getTableClickBehavior();
            boolean testing = SettingsManager.getTesting();
            SettingsManager.setTesting(true);
            int[] applied = {0};
            Stage stage = null;
            try {
                settings.setTableClickBehavior(1); // Legacy 1 means focus, not close.
                stage = FxSettingsWindow.show(null, () -> applied[0]++);
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                CheckBox toggle = check(stage.getScene().getRoot(), "Click an open table to close it");
                assertNotNull(toggle); assertFalse(toggle.isSelected()); toggle.setSelected(true);
                FxEditorControlsSmokeTest.button(stage.getScene().getRoot(), "Cancel").fire();
                assertEquals(1, settings.getTableClickBehavior()); assertEquals(0, applied[0]);
                stage = FxSettingsWindow.show(null, () -> applied[0]++);
                stage.setHeight(400); stage.setWidth(700);
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                TabPane tabs = (TabPane) stage.getScene().lookup(".tab-pane");
                assertEquals(6, tabs.getTabs().size());
                assertTrue(tabs.getTabs().stream().allMatch(tab -> tab.getContent() instanceof ScrollPane));
                check(stage.getScene().getRoot(), "Click an open table to close it").setSelected(true);
                FxEditorControlsSmokeTest.button(stage.getScene().getRoot(), "Apply settings").fire();
                assertEquals(0, settings.getTableClickBehavior()); assertEquals(1, applied[0]);
                assertFalse(stage.isShowing());
            } finally {
                if (stage != null) stage.close();
                settings.setTableClickBehavior(original); SettingsManager.setTesting(testing);
            }
        });
    }
}
