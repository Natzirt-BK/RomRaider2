package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.romraider.editor.document.EditorDocumentController;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxDocumentSafetySmokeTest {
    private static <T> T field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    private static Rom rom(String name) {
        Rom rom = new Rom(new RomID());
        rom.setFileName(name);
        return rom;
    }

    @Test void programmaticCloseHonorsVetoAndClosesAfterApproval() throws Exception {
        FxTestRuntime.run(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 400, 300));
            stage.setOnCloseRequest(Event::consume);
            stage.show();
            FxCloseRequest.request(stage);
            assertTrue(stage.isShowing());
            stage.setOnCloseRequest(null);
            FxCloseRequest.request(stage);
            assertFalse(stage.isShowing());
        });
    }

    @Test void backgroundTabClosesItsOwnDocumentWithoutChangingActiveRom() throws Exception {
        FxEditorWindow[] window = new FxEditorWindow[1];
        Rom active = rom("active-synthetic.bin"), background = rom("background-synthetic.bin");
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxEditorWindow(() -> {}, () -> {});
                EditorDocumentController c = field(window[0], "controller");
                c.getSession().openRom(active); c.getSession().openRom(background);
                FxWindowPlacement.show(field(window[0], "stage"));
            });
            FxTestRuntime.run(() -> {
                TabPane tabs = field(window[0], "romTabs");
                Tab tab = tabs.getTabs().stream().filter(t -> t.getUserData() == background)
                        .findFirst().orElseThrow();
                Event.fireEvent(tab, new Event(Tab.TAB_CLOSE_REQUEST_EVENT));
                EditorDocumentController c = field(window[0], "controller");
                assertEquals(1, c.getSession().snapshot().getDocuments().size());
                assertSame(active, c.getSession().snapshot().getActiveRom());
            });
        } finally {
            FxTestRuntime.run(() -> { if (window[0] != null) window[0].close(); });
        }
    }

    @Test void dirtyFileExitAndNativeCloseBothAllowCancelThenExplicitDiscard() throws Exception {
        FxEditorWindow[] window = new FxEditorWindow[1];
        AtomicInteger prompts = new AtomicInteger();
        FxTestRuntime.run(() -> {
            window[0] = new FxEditorWindow(() -> {}, () -> {});
            Rom dirty = rom("unsaved-synthetic.bin");
            RomChangeService.rememberSavedBinary(dirty);
            RomChangeService.markUnsaved(dirty);
            EditorDocumentController c = field(window[0], "controller");
            c.getSession().openRom(dirty);
            FxWindowPlacement.show(field(window[0], "stage"));
        });
        FxTestRuntime.run(() -> {
            Stage stage = field(window[0], "stage");
            MenuBar menus = (MenuBar) stage.getScene().lookup(".menu-bar");
            MenuItem exit = menus.getMenus().get(0).getItems().stream()
                    .filter(i -> "Exit".equals(i.getText())).findFirst().orElseThrow();
            answerNextDialog(false, prompts);
            exit.fire();
            assertTrue(stage.isShowing());
            EditorDocumentController c = field(window[0], "controller");
            assertTrue(c.getSession().snapshot().getActiveDocument().isDirty());
            answerNextDialog(false, prompts);
            Event.fireEvent(stage, new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
            assertTrue(stage.isShowing());
            answerNextDialog(true, prompts);
            exit.fire();
            assertFalse(stage.isShowing());
            assertEquals(3, prompts.get());
        });
    }

    private static void answerNextDialog(boolean approve, AtomicInteger prompts) {
        Platform.runLater(() -> {
            for (Window window : new ArrayList<>(Window.getWindows())) {
                if (!(window.getScene().getRoot() instanceof DialogPane pane)) continue;
                prompts.incrementAndGet();
                ButtonType answer = pane.getButtonTypes().stream().filter(type ->
                        type.getButtonData() == (approve ? ButtonBar.ButtonData.OK_DONE
                                : ButtonBar.ButtonData.CANCEL_CLOSE)).findFirst().orElseThrow();
                ((Button) pane.lookupButton(answer)).fire();
                return;
            }
        });
    }
}
