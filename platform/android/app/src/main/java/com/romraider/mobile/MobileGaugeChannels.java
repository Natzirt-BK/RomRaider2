/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;

/** Six explicitly assigned display slots per protocol. Never imports logger choices implicitly. */
final class MobileGaugeChannels {
    private MobileGaugeChannels() { }
    static List<String> read(SharedPreferences preferences, String protocol) {
        List<String> slots = new ArrayList<>(Collections.nCopies(6, ""));
        try {
            String saved = preferences.getString("gauge_channels_v1_" + protocol, "[]");
            if (saved == null || saved.length() > 16384) return slots;
            JSONArray array = new JSONArray(saved);
            if (array.length() > 6) return slots;
            for (int i = 0; i < array.length(); i++) {
                String id = array.getString(i);
                if (!id.isEmpty() && id.length() <= 240 && !slots.contains(id)) slots.set(i, id);
            }
        } catch (org.json.JSONException | ClassCastException ignored) { }
        return slots;
    }
    static void write(SharedPreferences preferences, String protocol, List<String> slots) {
        if (slots.size() != 6) throw new IllegalArgumentException("Six display slots are required");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : slots) {
            if (id == null || id.length() > 240 || (!id.isEmpty() && !seen.add(id)))
                throw new IllegalArgumentException("Display channels must be unique and have a valid ID");
        }
        preferences.edit().putString("gauge_channels_v1_" + protocol, new JSONArray(slots).toString()).apply();
    }
}
