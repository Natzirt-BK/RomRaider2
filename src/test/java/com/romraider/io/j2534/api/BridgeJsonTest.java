package com.romraider.io.j2534.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public final class BridgeJsonTest {
    @Test
    public void roundTripsBridgeRequestsIncludingWindowsPathsAndUnsignedBytes() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("dll_path", "C:\\Program Files (x86)\\Tactrix\\op20pt32.dll");
        parameters.put("protocol_id", Long.valueOf(3));
        parameters.put("data", Arrays.asList(0, 128, 255));
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("id", Long.valueOf(7));
        request.put("method", "Open");
        request.put("params", parameters);

        String json = BridgeJson.write(request);
        assertEquals(json, BridgeJson.write(BridgeJson.read(json)));
    }

    @Test
    public void parsesNestedBridgeMessageResponses() {
        Object parsed = BridgeJson.read("{\"id\":2,\"status\":\"ok\",\"data\":[{"
                + "\"timestampUs\":42,\"rawArbId\":2016,\"data\":[128,240,16]}]}");
        Map<?, ?> response = (Map<?, ?>) parsed;
        assertEquals(Long.valueOf(2), response.get("id"));
        List<?> messages = (List<?>) response.get("data");
        Map<?, ?> message = (Map<?, ?>) messages.get(0);
        assertEquals(Long.valueOf(2016), message.get("rawArbId"));
    }

    @Test
    public void supportsNullDataAndEscapedDiagnosticText() {
        Map<?, ?> response = (Map<?, ?>) BridgeJson.read(
                "{\"id\":3,\"status\":\"ok\",\"data\":null,"
                + "\"note\":\"line 1\\nline 2 \\u2713\"}");
        assertNull(response.get("data"));
        assertEquals("line 1\nline 2 ✓", response.get("note"));
    }
}
