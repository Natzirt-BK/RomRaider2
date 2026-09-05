/* Synthetic-only diagnostic entry point; uses a COPY of the packaged image. */
package com.romraider2.javafx;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.*;
import com.romraider.editor.document.EditorDocumentController;
import com.romraider.editor.calibration.TableCalibrationEditController;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.*;
import com.romraider.swing.JProgressPane;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class PackagedDocumentSafety {
    interface Action { void run() throws Exception; }
    static void fx(Action action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { action.run(); done.complete(null); }
            catch (Throwable failure) { done.completeExceptionally(failure); }
        });
        done.get(20, TimeUnit.SECONDS);
    }
    @SuppressWarnings("unchecked") static <T> T field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true); return (T) field.get(owner);
    }
    static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }
    static Rom rom(String name, byte[] bytes) {
        Table1D table = new Table1D();
        table.setName("Synthetic value"); table.setStorageType(1);
        table.setStorageAddress(0); table.setDataSize(1);
        Rom rom = new Rom(new RomID()); rom.setFileName(name); rom.addTableByName(table);
        rom.populateTables(bytes, new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);
        return rom;
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(root);
        System.setProperty("romraider2.settings.dir", root.resolve("settings").toString());
        System.setProperty("romraider2.log.dir", root.resolve("logs").toString());
        Platform.startup(() -> Platform.setImplicitExit(false));
        try { verify(root); System.out.println("PACKAGED_DOCUMENT_SAFETY_PASS"); }
        finally { Platform.exit(); }
    }
    static void verify(Path root) throws Exception {
        FxEditorWindow[] window = new FxEditorWindow[1];
        EditorDocumentController[] controller = new EditorDocumentController[1];
        TableCalibrationEditController[] edits = new TableCalibrationEditController[1];
        Rom active = rom("synthetic-original.bin", new byte[] {10});
        Rom background = rom("synthetic-background.bin", new byte[] {40});
        Path original = root.resolve("synthetic-original.bin");
        Path target = root.resolve("synthetic save as.bin");
        Files.write(original, new byte[] {10}); active.setFullFileName(original.toFile());
        fx(() -> {
            window[0] = new FxEditorWindow(() -> {}, () -> {});
            controller[0] = field(window[0], "controller");
            controller[0].getSession().openRom(active);
            controller[0].getSession().openRom(background);
            edits[0] = new TableCalibrationEditController(active.getTables().get(0));
            edits[0].setCellValue(0, 0, "20");
            FxWindowPlacement.show(field(window[0], "stage"));
        });
        fx(() -> {
            Stage stage = field(window[0], "stage");
            Platform.runLater(() -> {
                for (Window candidate : new ArrayList<>(Window.getWindows())) {
                    if (!(candidate.getScene().getRoot() instanceof DialogPane pane)) continue;
                    ButtonType cancel = pane.getButtonTypes().stream().filter(type ->
                            type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE).findFirst().orElseThrow();
                    ((Button) pane.lookupButton(cancel)).fire(); return;
                }
            });
            MenuBar menus = (MenuBar) stage.getScene().lookup(".menu-bar");
            menus.getMenus().get(0).getItems().stream().filter(item -> "Exit".equals(item.getText()))
                    .findFirst().orElseThrow().fire();
            check(stage.isShowing() && RomChangeService.hasBinaryChanges(active), "dirty File Exit cancellation");
            TabPane tabs = field(window[0], "romTabs");
            Tab tab = tabs.getTabs().stream().filter(t -> t.getUserData() == background).findFirst().orElseThrow();
            Event.fireEvent(tab, new Event(Tab.TAB_CLOSE_REQUEST_EVENT));
            check(controller[0].getSession().snapshot().getActiveRom() == active
                    && controller[0].getSession().snapshot().getDocuments().size() == 1, "background tab closes only itself");
        });
        BlockingQueue<Runnable> completion = new LinkedBlockingQueue<>();
        CompletableFuture<File>[] saved = new CompletableFuture[1];
        fx(() -> {
            saved[0] = controller[0].save(active, target.toFile(), completion::add);
            edits[0].setCellValue(0, 0, "30");
            window[0].close();
            check(((Stage) field(window[0], "stage")).isShowing(), "pending save prevents window disposal");
        });
        Runnable publish = completion.poll(20, TimeUnit.SECONDS);
        if (publish == null) throw new AssertionError("save publication not queued");
        fx(publish::run); saved[0].get(20, TimeUnit.SECONDS);
        fx(() -> {
            check(Files.readAllBytes(target)[0] == 20 && active.getBinary()[0] == 30
                    && RomChangeService.hasBinaryChanges(active), "Save As preserves later unsaved edits");
            check(Files.readAllBytes(original)[0] == 10, "Save As preserves original file");
            edits[0].undo();
            check(active.getBinary()[0] == 20 && !RomChangeService.hasBinaryChanges(active), "undo returns to saved baseline");
            edits[0].redo();
            check(active.getBinary()[0] == 30 && RomChangeService.hasBinaryChanges(active), "redo remains dirty");
            saved[0] = controller[0].save(active, target.toFile(), Platform::runLater);
        });
        saved[0].get(20, TimeUnit.SECONDS);
        fx(() -> {
            check(!RomChangeService.hasBinaryChanges(active), "completed save clears only written edits");
            Rom reopened = rom("synthetic-reopened.bin", Files.readAllBytes(target));
            check(reopened.getBinary()[0] == 30 && !RomChangeService.hasBinaryChanges(reopened), "reopen saved synthetic bytes");
            RomChangeService.forget(reopened); reopened.clearData();
            edits[0].close(); window[0].close();
            check(!((Stage) field(window[0], "stage")).isShowing(), "clean close completes");
        });
    }
}
