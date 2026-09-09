/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.query.EcuQuery;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Three-column diagnostic CSV, including every displayed standard/DimeMod code. */
public final class DiagnosticReportCsv {
    private DiagnosticReportCsv() {}

    public static String format(List<EcuQuery> codes, Set<String> currentDime, Set<String> memorizedDime,
            String header, String trueLabel, String falseLabel, String eol) {
        StringBuilder csv = new StringBuilder(header).append(eol);
        for (EcuQuery code : codes) {
            double flags = code.getResponse();
            row(csv, code.getLoggerData().getName(), flags == 1 || flags == 3, flags == 2 || flags == 3,
                    trueLabel, falseLabel, eol);
        }
        Set<String> current = currentDime == null ? Set.of() : currentDime;
        Set<String> memorized = memorizedDime == null ? Set.of() : memorizedDime;
        TreeSet<String> dime = new TreeSet<>(current);
        dime.addAll(memorized);
        for (String code : dime) row(csv, code, current.contains(code), memorized.contains(code), trueLabel, falseLabel, eol);
        return csv.toString();
    }

    private static void row(StringBuilder csv, String name, boolean current, boolean memorized,
            String trueLabel, String falseLabel, String eol) {
        csv.append(field(name)).append(',').append(field(current ? trueLabel : falseLabel)).append(',')
                .append(field(memorized ? trueLabel : falseLabel)).append(eol);
    }

    private static String field(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    public static void writeNew(Path target, String csv) throws IOException {
        boolean created = false;
        try (OutputStream stream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            stream.write(csv.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            if (created) {
                try { Files.deleteIfExists(target); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }
}
