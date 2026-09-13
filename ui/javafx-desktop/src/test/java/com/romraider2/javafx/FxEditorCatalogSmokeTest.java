/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;

import com.romraider.editor.document.EditorDocumentController;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table2D;
import com.romraider.swing.JProgressPane;
import com.romraider.util.SettingsManager;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxEditorCatalogSmokeTest {
    @Test void refreshAndConversionChoicesFollowActiveImageSize() throws Exception {
        FxEditorWindow[] window = {null};
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxEditorWindow(() -> {}, () -> {});
                Button refresh = field(window[0], "refreshImage");
                assertTrue(refresh.isDisabled());
                assertNotNull(refresh.getTooltip());
            });
            for (int size : new int[] {163840, 196608, 524288}) {
                FxTestRuntime.run(() -> {
                    Rom rom = new Rom(new RomID()); rom.setFileName("synthetic-" + size + ".bin");
                    rom.populateTables(new byte[size], new JProgressPane());
                    EditorDocumentController controller = field(window[0], "controller");
                    controller.getSession().openRom(rom);
                    controller.getSession().activateRom(rom);
                });
                FxTestRuntime.run(() -> {
                    Button refresh = field(window[0], "refreshImage");
                    MenuItem increase = field(window[0], "convertIncrease");
                    MenuItem decrease = field(window[0], "convertDecrease");
                    assertFalse(refresh.isDisabled());
                    assertEquals(size != 163840, increase.isDisable());
                    assertEquals(size != 196608, decrease.isDisable());
                    assertEquals("Convert Image", increase.getParentMenu().getText());
                });
            }
        } finally { FxTestRuntime.run(() -> { if (window[0] != null) window[0].close(); }); }
    }

    @Test void mouseCanOpenSeveralDtcCalibrations() throws Exception {
        FxEditorWindow[] window = {null};
        Rom rom = new Rom(new RomID()); rom.setFileName("dtc-synthetic.bin");
        for (int i = 0; i < 3; i++) {
            var table = new com.romraider.maps.TableSwitch();
            table.setName("(P030" + i + ") Misfire diagnostic " + i);
            table.setCategory("Diagnostic Trouble Codes");
            table.setStorageAddress(i); table.setDataSize(1);
            rom.addTableByName(table);
        }
        rom.populateTables(new byte[] {1, 1, 1}, new JProgressPane());
        int previousClicks = SettingsManager.getSettings().getTableClickCount();
        int previousBehavior = SettingsManager.getSettings().getTableClickBehavior();
        try {
            FxTestRuntime.run(() -> {
                SettingsManager.getSettings().setTableClickCount(1);
                SettingsManager.getSettings().setTableClickBehavior(0);
                window[0] = new FxEditorWindow(() -> {}, () -> {});
                EditorDocumentController controller = field(window[0], "controller");
                controller.getSession().openRom(rom);
                Stage stage = field(window[0], "stage");
                stage.setWidth(1024); stage.setHeight(700); FxWindowPlacement.show(stage);
            });
            FxTestRuntime.run(() -> {
                TreeView<?> tree = field(window[0], "navigation");
                tree.getRoot().getChildren().get(0).setExpanded(true);
            });
            int[] clickedMaps = {0, 1, 2, 1, 1};
            int[] expectedCounts = {1, 2, 3, 2, 3};
            for (int i = 0; i < clickedMaps.length; i++) {
                var clicked = rom.getTableCatalog().get(clickedMaps[i]);
                String name = clicked.getName();
                Thread.sleep(600);
                FxTestRuntime.run(() -> {
                    Stage stage = field(window[0], "stage");
                    stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                    TreeView<?> tree = field(window[0], "navigation");
                    TreeCell<?> cell = tree.lookupAll(".tree-cell").stream()
                            .filter(TreeCell.class::isInstance).map(TreeCell.class::cast)
                            .filter(candidate -> name.equals(candidate.getText())).findFirst().orElseThrow();
                    var bounds = cell.localToScreen(cell.getBoundsInLocal());
                    var robot = new javafx.scene.robot.Robot();
                    robot.mouseMove(bounds.getMinX() + 100, bounds.getCenterY());
                    robot.mouseClick(javafx.scene.input.MouseButton.PRIMARY);
                });
                Thread.sleep(250);
                int expected = expectedCounts[i];
                boolean closingTab = i == 3;
                FxTestRuntime.run(() -> {
                    TabPane tabs = field(window[0], "calibrationTabs");
                    assertEquals(expected, tabs.getTabs().size(), "Click on " + name);
                    assertEquals(!closingTab, tabs.getTabs().stream()
                            .anyMatch(tab -> tab.getUserData() == clicked));
                    if (!closingTab) assertSame(clicked,
                            tabs.getSelectionModel().getSelectedItem().getUserData());
                    assertArrayEquals(new byte[] {1, 1, 1}, rom.getBinary());
                });
            }
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) window[0].close();
                SettingsManager.getSettings().setTableClickCount(previousClicks);
                SettingsManager.getSettings().setTableClickBehavior(previousBehavior);
            });
        }
    }

    private static Rom fixture(String name) {
        Rom rom = new Rom(new RomID()); rom.setFileName(name);
        Table2D fuel = new Table2D(); fuel.setName("Fuel target");
        fuel.setCategory("Fuel//Targets"); fuel.setStorageType(1);
        fuel.setStorageAddress(0); fuel.setDataSize(3);
        fuel.getAxis().setStorageType(1); fuel.getAxis().setStorageAddress(3);
        fuel.getAxis().setDataSize(3);
        rom.addTableByName(fuel);
        Table2D ignition = new Table2D(); ignition.setName("Ignition advance");
        ignition.setCategory("Ignition//Timing"); ignition.setStorageType(1);
        ignition.setStorageAddress(6); ignition.setDataSize(3);
        ignition.getAxis().setStorageType(1); ignition.getAxis().setStorageAddress(9);
        ignition.getAxis().setDataSize(3);
        rom.addTableByName(ignition);
        rom.populateTables(new byte[] {10, 20, 30, 40, 50, 60, 5, 10, 15, 40, 50, 60}, new JProgressPane());
        return rom;
    }

    @Test void categoriesStartCollapsedAndKeepManualExpansionAcrossRefreshAndSearch() throws Exception {
        FxEditorWindow[] window = {null};
        Rom first = fixture("first-synthetic.bin"), second = fixture("second-synthetic.bin");
        boolean previousExpanded = SettingsManager.getSettings().isOpenExpanded();
        try {
            FxTestRuntime.run(() -> {
                // Old Swing preferences must not force the JavaFX catalog open.
                SettingsManager.getSettings().setOpenExpanded(true);
                window[0] = new FxEditorWindow(() -> {}, () -> {});
                EditorDocumentController controller = field(window[0], "controller");
                controller.getSession().openRom(first);
                FxWindowPlacement.show(field(window[0], "stage"));
            });
            FxTestRuntime.run(() -> {
                TreeView<?> tree = field(window[0], "navigation");
                assertEquals(2, tree.getRoot().getChildren().size());
                assertCollapsed(tree.getRoot());
                var fuel = tree.getRoot().getChildren().get(0);
                fuel.setExpanded(true); fuel.getChildren().get(0).setExpanded(true);
                EditorDocumentController controller = field(window[0], "controller");
                controller.getSession().openRom(first); // A routine document refresh.
            });
            FxTestRuntime.run(() -> {
                TreeView<?> tree = field(window[0], "navigation");
                assertTrue(tree.getRoot().getChildren().get(0).isExpanded());
                assertTrue(tree.getRoot().getChildren().get(0).getChildren().get(0).isExpanded());
                TextField search = field(window[0], "search");
                search.setText("Ignition advance");
                assertEquals(1, tree.getRoot().getChildren().size());
                assertTrue(tree.getRoot().getChildren().get(0).isExpanded());
                assertTrue(tree.getRoot().getChildren().get(0).getChildren().get(0).isExpanded());
                search.clear();
                assertTrue(tree.getRoot().getChildren().get(0).isExpanded());
                assertFalse(tree.getRoot().getChildren().get(1).isExpanded());
                EditorDocumentController controller = field(window[0], "controller");
                controller.getSession().openRom(second); controller.activateRom(second);
            });
            FxTestRuntime.run(() -> {
                TreeView<?> tree = field(window[0], "navigation");
                assertCollapsed(tree.getRoot());
                Stage stage = field(window[0], "stage");
                MenuBar bar = (MenuBar) stage.getScene().lookup(".menu-bar");
                Menu view = bar.getMenus().stream().filter(menu -> "View".equals(menu.getText())).findFirst().orElseThrow();
                view.getItems().stream().filter(item -> "Expand all categories".equals(item.getText())).findFirst().orElseThrow().fire();
                assertTrue(tree.getRoot().getChildren().stream().allMatch(TreeItem::isExpanded));
                view.getItems().stream().filter(item -> "Collapse all categories".equals(item.getText())).findFirst().orElseThrow().fire();
                assertCollapsed(tree.getRoot());
                EditorDocumentController controller = field(window[0], "controller");
                controller.activateRom(first);
            });
            FxTestRuntime.run(() -> {
                TreeView<?> tree = field(window[0], "navigation");
                assertTrue(tree.getRoot().getChildren().get(0).isExpanded());
                assertFalse(tree.getRoot().getChildren().get(1).isExpanded());
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) window[0].close();
                SettingsManager.getSettings().setOpenExpanded(previousExpanded);
            });
        }
    }

    @Test void smallEditorKeepsTableNearTopAndOnlyShowsRomStripForMultipleDocuments() throws Exception {
        FxEditorWindow[] window = {null};
        Rom rom = fixture("compact-synthetic.bin");
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxEditorWindow(() -> {}, () -> {});
                EditorDocumentController controller = field(window[0], "controller");
                controller.getSession().openRom(rom);
                controller.openTable(rom, rom.getTableCatalog().get(0));
                Stage stage = field(window[0], "stage");
                stage.setWidth(1024); stage.setHeight(700); FxWindowPlacement.show(stage);
            });
            FxTestRuntime.run(() -> {
                Stage stage = field(window[0], "stage");
                var root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                TabPane romTabs = field(window[0], "romTabs");
                assertFalse(romTabs.isVisible()); assertFalse(romTabs.isManaged());
                var grid = root.lookup(".calibration-grid"); assertNotNull(grid);
                assertTrue(grid.localToScene(grid.getBoundsInLocal()).getMinY() < 320,
                        "Compact editor should not push the table below 320 pixels");
                assertTrue(grid.getBoundsInLocal().getHeight() > 300,
                        "Grid=" + grid.getBoundsInLocal() + ", scene=" + stage.getScene().getWidth() + "x"
                                + stage.getScene().getHeight() + ", stage=" + stage.getWidth() + "x" + stage.getHeight());
                TreeView<?> tree = field(window[0], "navigation");
                tree.getRoot().getChildren().forEach(item -> item.setExpanded(false));
                EditorDocumentController controller = field(window[0], "controller");
                controller.getSession().openRom(fixture("second-synthetic.bin"));
            });
            FxTestRuntime.run(() -> {
                TabPane romTabs = field(window[0], "romTabs");
                assertTrue(romTabs.isVisible()); assertTrue(romTabs.isManaged());
                assertEquals(2, romTabs.getTabs().size());
                assertEquals(34, romTabs.getPrefHeight());
                TreeView<?> tree = field(window[0], "navigation");
                assertTrue(tree.getRoot().getChildren().stream().noneMatch(TreeItem::isExpanded),
                        "A routine refresh must not reopen the manually collapsed active branch");
                EditorDocumentController controller = field(window[0], "controller");
                Rom second = controller.getSession().snapshot().getDocuments().get(1).getRom();
                controller.closeRom(second);
            });
            FxTestRuntime.run(() -> {
                TabPane romTabs = field(window[0], "romTabs");
                assertFalse(romTabs.isManaged());
            });
        } finally { FxTestRuntime.run(() -> { if (window[0] != null) window[0].close(); }); }
    }

    private static void assertCollapsed(TreeItem<?> root) {
        for (TreeItem<?> child : root.getChildren()) {
            assertFalse(child.isExpanded()); assertCollapsed(child);
        }
    }
}
