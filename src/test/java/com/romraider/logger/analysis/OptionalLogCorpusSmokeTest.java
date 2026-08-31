/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Test;

/** Optional corpus check enabled by -Dromraider2.test.logs=/path/to/logs. */
public class OptionalLogCorpusSmokeTest {
    @Test
    public void parsesEveryConfiguredCaptureThatContainsSamples() throws Exception {
        String configured = System.getProperty("romraider2.test.logs", "");
        Assume.assumeTrue(!configured.trim().isEmpty());
        File directory = new File(configured);
        File[] logs = directory.listFiles((parent, name) ->
                name.toLowerCase(java.util.Locale.ROOT).endsWith(".csv"));
        Assume.assumeTrue(logs != null && logs.length > 0);

        RomRaiderCsvLogParser parser = new RomRaiderCsvLogParser();
        int parsed = 0;
        for (File log : logs) {
            if (!hasSampleRow(log)) continue;
            LogDataset dataset = parser.parse(log);
            assertTrue(log.getName(), dataset.getRowCount() > 0);
            assertTrue(log.getName(), dataset.getChannelCount() > 1);
            parsed++;
        }
        assertTrue("No populated CSV captures found", parsed > 0);
    }

    private static boolean hasSampleRow(File log) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(log.toPath(),
                StandardCharsets.UTF_8)) {
            reader.readLine();
            return reader.readLine() != null;
        }
    }
}
