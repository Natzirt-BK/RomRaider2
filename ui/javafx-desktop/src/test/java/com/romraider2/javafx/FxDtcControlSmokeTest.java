/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.maps.*;
import com.romraider.swing.JProgressPane;
import com.romraider.editor.workspace.RomChangeService;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxDtcControlSmokeTest {
    @Test void monitorGateDoesNotClaimToDisableEveryPathForTheCode() throws Exception {
        FxTestRuntime.run(() -> {
            TableSwitch table = new TableSwitch();
            table.setName("  (P0130) Oxygen sensor monitor A");
            table.setStorageAddress(0); table.setStorageType(2); table.setDataSize(1);
            table.setBitMask(4);
            Rom rom = new Rom(new RomID()); rom.addTableByName(table);
            rom.populateTables(new byte[] {(byte) 0x8e, (byte) 0x89}, new JProgressPane());
            table.getCurrentScale().setUnit("monitor gate");
            byte[] original = rom.getBinary().clone();
            RomChangeService.rememberSavedBinary(rom);
            FxCalibrationPane pane = new FxCalibrationPane(table);
            Stage stage = new Stage(); stage.setScene(new Scene(pane, 700, 480));
            try {
                stage.show(); pane.applyCss(); pane.layout();
                Label state = field(pane, "dtcState");
                ToggleButton toggle = field(pane, "dtcToggle");
                assertEquals("Monitor gate disabled", state.getText());
                assertTrue(pane.lookupAll(".title").stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).anyMatch(label -> "P0130".equals(label.getText())));
                assertNotNull(pane.lookup("#dtc-scope-note"));
                toggle.fire();
                assertEquals("Monitor gate enabled", state.getText());
                assertEquals(0x8e, rom.getBinary()[0] & 255);
                assertEquals(0x8d, rom.getBinary()[1] & 255);
                FxEditorControlsSmokeTest.button(pane, "Restore saved state").fire();
                assertArrayEquals(original, rom.getBinary());
                assertEquals("Monitor gate disabled", state.getText());
            } finally {
                pane.close(); stage.close(); RomChangeService.forget(rom);
            }
        });
    }
}
