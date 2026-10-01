package org.zektopic.zekals;

import android.content.res.AssetManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The desktop v1 pack format; assets are local and require no network permissions. */
public final class LanguagePack {
    public final String code, name;
    public final Locale locale;
    public final boolean rtl;
    private final JSONObject data;
    public LanguagePack(AssetManager assets, String filename) throws Exception {
        try (InputStream input = assets.open("languages/" + filename)) {
            data = new JSONObject(new String(readBounded(input), StandardCharsets.UTF_8));
        }
        rtl = data.getString("direction").equals("rtl");
        code = data.getString("code"); name = data.getString("name");
        if (data.getInt("schemaVersion") != 1 || !code.matches("[a-z]{2,3}(-[A-Za-z0-9]+)*") || !filename.equals(code + ".json")) throw new IllegalArgumentException("Invalid language pack");
        locale = Locale.forLanguageTag(data.getString("locale"));
        if (locale.getLanguage().isEmpty()) throw new IllegalArgumentException("Invalid locale");
        if (phrases().size() > 24) throw new IllegalArgumentException("Too many phrases");
        for (List<String> page : pages()) if (page.isEmpty() || page.size() > 160) throw new IllegalArgumentException("Invalid keyboard");
    }
    private static byte[] readBounded(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > 65536) throw new IllegalArgumentException("Pack too large");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
    public String text(String key, String fallback) { return data.optJSONObject("ui").optString(key, fallback); }
    public List<String> phrases() throws Exception { return strings(data.getJSONArray("phrases")); }
    public List<List<String>> pages() throws Exception {
        List<List<String>> result = new ArrayList<>(); List<String> first = new ArrayList<>();
        JSONArray rows = data.getJSONArray("keyboard");
        for (int i = 0; i < rows.length(); i++) first.addAll(strings(rows.getJSONArray(i)));
        result.add(first);
        JSONArray extras = data.getJSONArray("keyboardPages");
        for (int i = 0; i < extras.length(); i++) result.add(strings(extras.getJSONArray(i)));
        result.add(Arrays.asList("1","2","3","4","5","6","7","8","9","0",".",",","?","!",":",";","+","-","=","@"));
        return result;
    }
    /**
     * A page's keys as rows for a full-width keyboard: the pack's own rows when they look like keyboard
     * rows, otherwise balanced rows of up to ten keys (four rows at most), so keys stay large.
     */
    public List<List<String>> rows(int page) throws Exception {
        List<String> keys = pages().get(page);
        if (page == 0) {
            JSONArray rows = data.getJSONArray("keyboard"); List<List<String>> own = new ArrayList<>();
            boolean usable = rows.length() >= 2 && rows.length() <= 4;
            for (int i = 0; i < rows.length(); i++) { List<String> row = strings(rows.getJSONArray(i)); own.add(row); if (row.size() < 6 || row.size() > 12) usable = false; }
            if (usable) return own;
        }
        int count = Math.max(1, Math.min(4, (keys.size() + 9) / 10)), base = keys.size() / count, extra = keys.size() % count, start = 0;
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) { int size = base + (i < extra ? 1 : 0); result.add(keys.subList(start, start + size)); start += size; }
        return result;
    }
    private static List<String> strings(JSONArray values) throws Exception {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            String value = values.getString(i);
            if (value.isEmpty() || value.length() > 500) throw new IllegalArgumentException("Invalid text");
            result.add(value);
        }
        return result;
    }
}
