/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Device-local presentation overrides, isolated by protocol (or the standalone demo). */
final class MobileGaugeStyles {
    private static final String KEY = "logger_channel_gauge_styles_v1";
    private static final int MAX_STYLES = 512, MAX_CHARS = 65536;
    private final SharedPreferences preferences;
    private final Map<String, MobileGaugeTheme> styles = new LinkedHashMap<>();

    MobileGaugeStyles(SharedPreferences preferences) {
        this.preferences = preferences;
        try {
            String saved = preferences.getString(KEY, "{}");
            if (saved == null || saved.length() > MAX_CHARS) return;
            JSONObject object = new JSONObject(saved);
            if (object.length() > MAX_STYLES) return;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int split = key.indexOf('\n');
                if (split < 1 || key.length() > 256 || split == key.length() - 1) continue;
                try { styles.put(key, MobileGaugeTheme.valueOf(object.getString(key))); }
                catch (IllegalArgumentException | org.json.JSONException ignored) { }
            }
        } catch (ClassCastException | org.json.JSONException ignored) { /* Invalid preferences use the default. */ }
    }

    MobileGaugeTheme override(String scope, String id) { return styles.get(key(scope, id)); }
    MobileGaugeTheme resolve(String scope, String id, MobileGaugeTheme fallback) {
        MobileGaugeTheme selected = override(scope, id);
        return selected == null ? fallback : selected;
    }
    void set(String scope, String id, MobileGaugeTheme theme) {
        String key = key(scope, id);
        Map<String, MobileGaugeTheme> next = new LinkedHashMap<>(styles);
        if (theme == null) next.remove(key);
        else {
            if (!styles.containsKey(key) && styles.size() >= MAX_STYLES)
                throw new IllegalStateException("Too many saved channel styles. Reset unused styles first.");
            next.put(key, theme);
        }
        save(next);
    }
    void clear(String scope) {
        Map<String, MobileGaugeTheme> next = new LinkedHashMap<>(styles);
        next.keySet().removeIf(key -> key.startsWith(scope + "\n"));
        save(next);
    }
    private void save(Map<String, MobileGaugeTheme> next) {
        JSONObject object = new JSONObject();
        try {
            for (Map.Entry<String, MobileGaugeTheme> entry : next.entrySet())
                object.put(entry.getKey(), entry.getValue().name());
        } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        String encoded = object.toString();
        if (encoded.length() > MAX_CHARS) throw new IllegalStateException("Saved channel styles are too large. Reset unused styles first.");
        preferences.edit().putString(KEY, encoded).apply();
        styles.clear();
        styles.putAll(next);
    }
    private static String key(String scope, String id) {
        if (scope == null || scope.isEmpty() || scope.indexOf('\n') >= 0
                || id == null || id.isEmpty() || scope.length() + id.length() + 1 > 256)
            throw new IllegalArgumentException("Channel identity is too long for a saved style.");
        return scope + "\n" + id;
    }
}
