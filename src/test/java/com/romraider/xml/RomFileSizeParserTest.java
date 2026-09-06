/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.xml;

import static org.junit.Assert.*;
import org.junit.Test;

public class RomFileSizeParserTest {
    @Test public void parsesBytesAndCaseInsensitiveUnitsWithoutShortStringFailure() {
        assertEquals(5, RomAttributeParser.parseFileSize("5"));
        assertEquals(0, RomAttributeParser.parseFileSize("0"));
        assertEquals(512, RomAttributeParser.parseFileSize("512b"));
        assertEquals(524288, RomAttributeParser.parseFileSize("512KB"));
        assertEquals(1048576, RomAttributeParser.parseFileSize(" 1 mb "));
    }
    @Test public void rejectsMissingNegativeAndOverflowingSizes() {
        for (String input : new String[] {null, "", "b", "-1", "-1kb", "2097152kb", "2048mb", "999999999999999999999999"}) {
            try { RomAttributeParser.parseFileSize(input); fail("Invalid size accepted: " + input); }
            catch (NumberFormatException expected) { }
        }
    }
}
