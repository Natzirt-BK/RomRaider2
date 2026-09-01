/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.architecture;

import static org.junit.Assert.assertTrue;

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
                "editor/calibration", "editor/compare", "editor/recovery",
                "flash", "livetune", "logger/api")) {
            collectUiImports(sourceRoot.resolve(packagePath), violations);
        }

        assertTrue("UI-neutral packages import Swing/AWT: " + violations,
                violations.isEmpty());
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
