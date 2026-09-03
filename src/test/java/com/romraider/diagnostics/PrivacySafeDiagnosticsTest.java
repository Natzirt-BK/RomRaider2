/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrivacySafeDiagnosticsTest {
    @Test
    public void reportExcludesPersonalPathsAndNetworkIdentifiers() {
        IllegalStateException failure = new IllegalStateException(
                "Unable to open C:\\Users\\Private Person\\Desktop\\secret-rom.bin "
                + "for owner@example.com at 192.168.10.44 /home/private/tune.bin "
                + "device 00:11:22:33:44:55 on COM17 VIN JF1SG69695H700001");

        String report = PrivacySafeDiagnostics.buildReport(failure);

        assertFalse(report.contains("Private Person"));
        assertFalse(report.contains("secret-rom.bin"));
        assertFalse(report.contains("owner@example.com"));
        assertFalse(report.contains("192.168.10.44"));
        assertFalse(report.contains("/home/private"));
        assertFalse(report.contains("00:11:22:33:44:55"));
        assertFalse(report.contains("COM17"));
        assertFalse(report.contains("JF1SG69695H700001"));
        assertTrue(report.contains("application does not send this report"));
        assertTrue(report.contains("message removed"));
    }

    @Test
    public void safeMessageAndSourceFramesRemainUseful() {
        IllegalArgumentException failure = new IllegalArgumentException(
                "Definition scaling expression is invalid");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.romraider.xml.Parser", "parse",
                        "Parser.java", 42)
        });

        String report = PrivacySafeDiagnostics.buildReport(failure);

        assertTrue(report.contains("Definition scaling expression is invalid"));
        assertTrue(report.contains("com.romraider.xml.Parser.parse(Parser.java:42)"));
        assertFalse(report.contains(System.getProperty("user.home")));
    }

    @Test
    public void runtimeSummaryExcludesPersonalAndFilesystemProperties() {
        String summary = PrivacySafeDiagnostics.buildRuntimeSummary();

        assertTrue(summary.contains("OS="));
        assertTrue(summary.contains("architecture="));
        assertTrue(summary.contains("Java="));
        assertTrue(summary.contains(System.getProperty("java.version")));
        assertFalse(summary.contains("network-redacted"));
        assertFalse(summary.contains(System.getProperty("user.home")));
        assertFalse(summary.contains(System.getProperty("user.dir")));
        assertFalse(summary.contains("java.class.path"));
        assertFalse(summary.contains("user.name"));
    }
}
