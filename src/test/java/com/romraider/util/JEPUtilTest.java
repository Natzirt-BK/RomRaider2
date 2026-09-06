/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.util;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class JEPUtilTest {
    @Test public void mapAndScalarCallsCannotPoisonEachOthersBindings() {
        assertEquals(42, JEPUtil.evaluate("42", Collections.singletonMap("input", 5.0)), 0);
        assertEquals(42, JEPUtil.evaluate("42", 1), 0);
        assertEquals(42, JEPUtil.evaluate("42", Collections.singletonMap("input", 7.0)), 0);
        assertEquals(4, JEPUtil.evaluate("x*2", 2), 0);
        assertTrue(Double.isNaN(JEPUtil.evaluate("x*2", Collections.singletonMap("input", 9.0))));
        assertEquals(6, JEPUtil.evaluate("x*2", 3), 0);
    }

    @Test public void removedVariablesDoNotReuseStaleValuesAndNewBindingsRecover() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("a", 2.0);
        values.put("b", 3.0);
        assertEquals(5, JEPUtil.evaluate("a+b", values), 0);
        values.remove("b");
        assertTrue(Double.isNaN(JEPUtil.evaluate("a+b", values)));
        values.put("b", 10.0);
        assertEquals(12, JEPUtil.evaluate("a+b", values), 0);
        Map<String, Double> reordered = new LinkedHashMap<>();
        reordered.put("b", 11.0);
        reordered.put("a", 3.0);
        assertEquals(14, JEPUtil.evaluate("a+b", reordered), 0);
    }

    @Test public void standardFunctionsWorkInBothEvaluationModesRegardlessOfOrder() {
        assertEquals(3, JEPUtil.evaluate("sqrt(x)", Collections.singletonMap("x", 9.0)), 0);
        assertEquals(4, JEPUtil.evaluate("sqrt(x)", 16), 0);
        assertEquals(5, JEPUtil.evaluate("sqrt(input)", Collections.singletonMap("input", 25.0)), 0);
        assertTrue(Double.isNaN(JEPUtil.evaluate("sqrt(input)", 36)));
        assertEquals(7, JEPUtil.evaluate("sqrt(input)", Collections.singletonMap("input", 49.0)), 0);
    }

    @Test public void nullAndInvalidBindingsDoNotReusePreviousNumbers() {
        Map<String, Double> values = new HashMap<>();
        values.put("x", 12.0);
        assertEquals(13, JEPUtil.evaluate("x+1", values), 0);
        values.put("x", null);
        assertTrue(Double.isNaN(JEPUtil.evaluate("x+1", values)));
        values.put("x", 0.0);
        assertEquals(1, JEPUtil.evaluate("x+1", values), 0);
    }

    @Test public void cacheKeepsItsDeclaredCapacityAndEvictsLeastRecentlyUsed() {
        JEPUtil.LRUCache<String, Integer> cache = new JEPUtil.LRUCache<>(2);
        cache.put("a", 1); cache.put("b", 2);
        assertEquals(2, cache.size());
        cache.get("a"); cache.put("c", 3);
        assertEquals(2, cache.size());
        assertTrue(cache.containsKey("a"));
        assertFalse(cache.containsKey("b"));
    }
}
