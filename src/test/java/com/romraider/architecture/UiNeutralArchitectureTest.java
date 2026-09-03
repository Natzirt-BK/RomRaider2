/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.architecture;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

public class UiNeutralArchitectureTest {
    @Test
    public void applicationServicesDoNotImportSwingOrAwt() throws Exception {
        Path sourceRoot = Paths.get("src", "main", "java", "com",
                "romraider");
        List<String> violations = new ArrayList<String>();
        for (String packagePath : Arrays.asList("activity",
                "desktop",
                "editor/calibration", "editor/compare", "editor/document",
                "editor/io", "editor/recovery",
                "flash", "livetune", "logger/api", "logger/runtime")) {
            collectUiImports(sourceRoot.resolve(packagePath), violations);
        }

        inspect(sourceRoot.resolve("logger/external/core/ExternalDataSource.java"),
                violations);
        inspect(sourceRoot.resolve(
                "logger/ecu/comms/manager/QueryManagerImpl.java"),
                violations);
        inspect(sourceRoot.resolve("ui/ApplicationThemeService.java"),
                violations);
        inspect(sourceRoot.resolve("ECUExec.java"), violations);

        assertTrue("UI-neutral packages import Swing/AWT: " + violations,
                violations.isEmpty());
    }

    @Test
    public void calibrationModelDoesNotOwnSwingFrames() throws Exception {
        Path tableSource = Paths.get("src", "main", "java", "com",
                "romraider", "maps", "Table.java");
        String source = new String(Files.readAllBytes(tableSource),
                StandardCharsets.UTF_8);

        assertFalse("Table must not import or store a Swing TableFrame",
                source.contains("TableFrame"));
        assertFalse("Table must not retain a tableFrame presentation field",
                source.contains("tableFrame"));
        assertFalse("Table must not import or store a Swing TableView",
                source.contains("TableView"));
        assertFalse("Table must not retain a tableView presentation field",
                source.contains("tableView"));

        Path cellSource = Paths.get("src", "main", "java", "com",
                "romraider", "maps", "DataCell.java");
        String cell = new String(Files.readAllBytes(cellSource),
                StandardCharsets.UTF_8);
        assertFalse("DataCell must not own a Swing DataCellView",
                cell.contains("DataCellView"));
        assertFalse("DataCell must not call the Swing editor manager",
                cell.contains("ECUEditorManager"));

        Path romSource = Paths.get("src", "main", "java", "com",
                "romraider", "maps", "Rom.java");
        String rom = new String(Files.readAllBytes(romSource),
                StandardCharsets.UTF_8);
        assertFalse("Rom must not inherit from a Swing tree node",
                rom.contains("extends DefaultMutableTreeNode"));
        assertFalse("Rom must not own Swing table tree nodes",
                rom.contains("TableTreeNode"));
        assertFalse("Rom must not import Swing or AWT",
                rom.contains("javax.swing") || rom.contains("java.awt")
                        || rom.contains("com.romraider.swing"));
    }

    @Test
    public void productionDesktopDoesNotAutomaticallyFallBackToSwing()
            throws Exception {
        Path source = Paths.get("src", "main", "java", "com",
                "romraider", "ECUExec.java");
        String ecuExec = new String(Files.readAllBytes(source),
                StandardCharsets.UTF_8);
        int launch = ecuExec.indexOf("LegacySwingApplication.launch(args)");
        int guard = ecuExec.lastIndexOf("if (legacySwingRequested())", launch);

        assertTrue("Legacy Swing launch must remain behind an explicit guard",
                launch >= 0 && guard >= 0);
    }

    private static void collectUiImports(Path directory,
            List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> inspect(path, violations));
        }
    }

    private static void inspect(Path path, List<String> violations) {
        try {
            for (String line : Files.readAllLines(path,
                    StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import java.awt.")
                        || trimmed.startsWith("import javax.swing.")
                        || trimmed.startsWith("import com.romraider.swing.")) {
                    violations.add(path + ": " + trimmed);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to inspect " + path,
                    failure);
        }
    }
}
