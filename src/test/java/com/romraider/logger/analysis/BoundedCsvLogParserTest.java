/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.CancellationException;
import org.junit.Test;

public class BoundedCsvLogParserTest {
    private final RomRaiderCsvLogParser parser = new RomRaiderCsvLogParser();
    private LogDataset parse(String text, RomRaiderCsvLogParser.Limits limits) throws IOException { return parser.parse("synthetic.csv", new StringReader(text), limits); }
    @Test public void boundedAndExistingParsersAgreeOnBomQuotedHeadersMissingAndLineEndings() throws Exception {
        String text = "\ufeff\"Time (msec)\",\"AFR, external (AFR)\"\r\n0,14.7\r100,\n\n200,NaN";
        var expected = parser.parse("synthetic.csv", new StringReader(text)); var actual = parse(text, RomRaiderCsvLogParser.REVIEW_LIMITS);
        assertEquals(expected.getRowCount(), actual.getRowCount()); assertEquals(expected.getChannels().get(1).getLabel(), actual.getChannels().get(1).getLabel());
        for (int row = 0; row < expected.getRowCount(); row++) for (int column = 0; column < 2; column++) assertEquals(expected.getValue(row, column), actual.getValue(row, column), 0);
    }
    @Test public void rowCellAndColumnLimitsRejectInsteadOfReturningPartialData() throws Exception {
        reject("A,B\n1,2\n3,4\n", new RomRaiderCsvLogParser.Limits(1, 2, 4, 20, 100));
        reject("A,B\n1,2\n3,4\n", new RomRaiderCsvLogParser.Limits(2, 2, 3, 20, 100));
        reject("A,B\n1,2\n", new RomRaiderCsvLogParser.Limits(2, 1, 4, 20, 100));
        assertEquals(2, parse("A,B\n1,2\n3,4\n", new RomRaiderCsvLogParser.Limits(2, 2, 4, 20, 100)).getRowCount());
    }
    @Test public void lineAndTotalLimitsAreEnforcedDuringReading() throws Exception {
        reject("Value\n12345\n", new RomRaiderCsvLogParser.Limits(2, 1, 2, 4, 100));
        reject("A\n12345\n", new RomRaiderCsvLogParser.Limits(2, 1, 2, 5, 7));
        assertEquals(1, parse("A\n12345\n", new RomRaiderCsvLogParser.Limits(2, 1, 2, 5, 8)).getRowCount());
        Reader endless = new Reader() {
            int reads;
            @Override public int read(char[] buffer, int off, int len) { if (++reads > 100) throw new AssertionError("Unbounded source read"); buffer[off] = '1'; return 1; }
            @Override public void close() { }
        };
        try { parser.parse("synthetic.csv", endless, new RomRaiderCsvLogParser.Limits(2, 1, 2, 8, 100)); fail("Ignored line limit"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("line length")); }
    }
    @Test public void crLookaheadDoesNotDoubleCountCharacters() throws Exception {
        assertEquals(2, parse("A\r1\r2", new RomRaiderCsvLogParser.Limits(2, 1, 2, 1, 5)).getRowCount());
        assertEquals(1, parse("A\r\n1\r\n", new RomRaiderCsvLogParser.Limits(1, 1, 1, 1, 6)).getRowCount());
    }
    @Test public void cancellationAndMalformedRecordsDoNotYieldPartialLogs() throws Exception {
        reject("A,B\n1,2\n3\n", RomRaiderCsvLogParser.REVIEW_LIMITS);
        try { Thread.currentThread().interrupt(); try { parse("A\n1\n", RomRaiderCsvLogParser.REVIEW_LIMITS); fail("Ignored cancellation"); } catch (CancellationException expected) { } }
        finally { Thread.interrupted(); }
    }
    @Test public void boundedImportsAlsoLimitUiLabelsNumericFieldsAndErrorPreviews() throws Exception {
        reject("A".repeat(513) + "\n1\n", RomRaiderCsvLogParser.REVIEW_LIMITS);
        reject("A\n" + "1".repeat(1025) + "\n", RomRaiderCsvLogParser.REVIEW_LIMITS);
        try { parse("A\n" + "invalid".repeat(100) + "\n", RomRaiderCsvLogParser.REVIEW_LIMITS); fail("Expected invalid number"); }
        catch (IOException expected) { assertTrue(expected.getMessage().length() < 180); }
    }
    private void reject(String text, RomRaiderCsvLogParser.Limits limits) throws IOException { try { parse(text, limits); fail("Expected bounded import rejection"); } catch (IOException expected) { } }
}
