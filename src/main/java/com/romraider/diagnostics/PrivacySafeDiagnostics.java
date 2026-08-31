/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.diagnostics;

import static com.romraider.Version.PRODUCT_NAME;
import static com.romraider.Version.VERSION;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** Builds diagnostic text without collecting user or vehicle data. */
public final class PrivacySafeDiagnostics {
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])");
    private static final Pattern MAC_ADDRESS = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b");
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{0,4}:){2,}[0-9a-f]{0,4}\\b");
    private static final Pattern SERIAL_PORT = Pattern.compile(
            "(?i)(?:\\bCOM[0-9]{1,3}\\b|/dev/(?:tty|cu\\.)[^\\s]+)");
    private static final Pattern VEHICLE_IDENTIFIER = Pattern.compile(
            "(?i)(?:\\b[A-HJ-NPR-Z0-9]{17}\\b|\\b[0-9A-F]{8}\\b|"
            + "\\b[A-Z][A-Z0-9]{5,}(?:_[A-Z0-9]+)+\\b)");
    private static final Pattern SENSITIVE_FILE = Pattern.compile(
            "(?i)\\b[^\\s]+\\.(?:bin|hex|srf|rom|xml|csv)\\b");
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]|\\\\\\\\)");
    private static final Pattern UNIX_ABSOLUTE_PATH = Pattern.compile(
            "(^|[\\s=:])/(?:home|users|tmp|private|var|mnt|media|run|opt|srv|data)(?:/|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern URI_WITH_USER_DATA = Pattern.compile(
            "(?i)\\b(?:file|ftp|https?)://[^\\s]+[?#][^\\s]+");

    private PrivacySafeDiagnostics() { }

    public static String buildReport(Throwable failure) {
        StringBuilder report = new StringBuilder();
        report.append(PRODUCT_NAME).append(' ').append(VERSION)
                .append(" diagnostic report\n");
        report.append("Created (UTC): ").append(Instant.now()).append('\n');
        report.append("Contains: application/runtime metadata and a filtered stack trace.\n");
        report.append("Excluded: ROM data, definitions, logs, usernames, local paths, ")
                .append("serial ports, network identifiers, and vehicle identifiers.\n");
        report.append("Review before sharing; the application does not send this report.\n\n");
        report.append("Runtime\n");
        appendProperty(report, "OS", "os.name");
        appendProperty(report, "OS version", "os.version");
        appendProperty(report, "Architecture", "os.arch");
        appendProperty(report, "Java", "java.version");
        appendProperty(report, "Java vendor", "java.vendor");
        report.append("\nException\n");
        if (failure == null) {
            report.append("No exception was supplied.\n");
        } else {
            Set<Throwable> visited = Collections.newSetFromMap(
                    new IdentityHashMap<Throwable, Boolean>());
            appendThrowable(report, failure, "", visited);
        }
        return redactFreeText(report.toString());
    }

    static String sanitizeMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "";
        String value = message.trim();
        if (containsSensitiveLocation(value)
                || EMAIL.matcher(value).find()
                || IPV4.matcher(value).find()
                || IPV6.matcher(value).find()
                || MAC_ADDRESS.matcher(value).find()
                || SERIAL_PORT.matcher(value).find()
                || VEHICLE_IDENTIFIER.matcher(value).find()
                || SENSITIVE_FILE.matcher(value).find()
                || URI_WITH_USER_DATA.matcher(value).find()) {
            return "[message removed: contained local or identifying data]";
        }
        return redactFreeText(value);
    }

    private static void appendProperty(StringBuilder report, String label,
            String property) {
        String value = System.getProperty(property);
        report.append(label).append(": ")
                .append(value == null || value.trim().isEmpty()
                        ? "unknown" : redactFreeText(value.trim()))
                .append('\n');
    }

    private static void appendThrowable(StringBuilder report, Throwable failure,
            String prefix, Set<Throwable> visited) {
        if (!visited.add(failure)) {
            report.append(prefix).append("[circular exception reference]\n");
            return;
        }
        report.append(prefix).append(failure.getClass().getName());
        String message = sanitizeMessage(failure.getMessage());
        if (!message.isEmpty()) report.append(": ").append(message);
        report.append('\n');
        for (StackTraceElement frame : failure.getStackTrace()) {
            report.append(prefix).append("  at ")
                    .append(frame.getClassName()).append('.')
                    .append(frame.getMethodName()).append('(')
                    .append(safeSourceLocation(frame)).append(")\n");
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            report.append(prefix).append("Suppressed: ");
            appendThrowable(report, suppressed, prefix + "  ", visited);
        }
        if (failure.getCause() != null) {
            report.append(prefix).append("Caused by: ");
            appendThrowable(report, failure.getCause(), prefix + "  ", visited);
        }
    }

    private static String safeSourceLocation(StackTraceElement frame) {
        String file = frame.getFileName();
        if (file == null || file.indexOf('/') >= 0 || file.indexOf('\\') >= 0) {
            file = "Unknown Source";
        }
        return frame.getLineNumber() >= 0
                ? file + ":" + frame.getLineNumber() : file;
    }

    private static boolean containsSensitiveLocation(String value) {
        if (WINDOWS_ABSOLUTE_PATH.matcher(value).find()
                || UNIX_ABSOLUTE_PATH.matcher(value).find()) return true;
        for (String property : new String[] {
                "user.home", "user.dir", "java.io.tmpdir", "user.name"
        }) {
            String sensitive = System.getProperty(property);
            if (sensitive != null && sensitive.length() > 2
                    && value.toLowerCase().contains(sensitive.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String redactFreeText(String value) {
        String redacted = EMAIL.matcher(value).replaceAll("<email-redacted>");
        redacted = IPV4.matcher(redacted).replaceAll("<network-redacted>");
        redacted = IPV6.matcher(redacted).replaceAll("<network-redacted>");
        redacted = MAC_ADDRESS.matcher(redacted).replaceAll("<hardware-redacted>");
        for (String property : new String[] {
                "user.home", "user.dir", "java.io.tmpdir", "user.name"
        }) {
            String sensitive = System.getProperty(property);
            if (sensitive != null && sensitive.length() > 2) {
                redacted = replaceIgnoreCase(redacted, sensitive, "<redacted>");
            }
        }
        return redacted;
    }

    private static String replaceIgnoreCase(String source, String target,
            String replacement) {
        return Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE)
                .matcher(source).replaceAll(replacement);
    }
}
