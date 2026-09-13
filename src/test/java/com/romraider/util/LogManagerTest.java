package com.romraider.util;

import static org.junit.Assert.*;
import java.nio.file.Path;
import org.junit.Test;

public class LogManagerTest {
    @Test public void logLocationMatchesDefaultAndOverrideWithoutCreatingDirectories() {
        String previous = System.getProperty("romraider2.log.dir");
        try {
            System.clearProperty("romraider2.log.dir");
            assertEquals(Path.of(System.getProperty("user.home"), ".RomRaider2", "logs"), LogManager.getLogDirectory());
            System.setProperty("romraider2.log.dir", "synthetic-log-location");
            assertEquals(Path.of("synthetic-log-location").toAbsolutePath(), LogManager.getLogDirectory());
            System.setProperty("romraider2.log.dir", " ");
            assertEquals(Path.of(System.getProperty("user.home"), ".RomRaider2", "logs"), LogManager.getLogDirectory());
        } finally {
            if (previous == null) System.clearProperty("romraider2.log.dir"); else System.setProperty("romraider2.log.dir", previous);
        }
    }
}
