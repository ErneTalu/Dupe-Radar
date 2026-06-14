package com.duperadar.addon.utils;

import org.jetbrains.annotations.NotNull;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DupeRadarUtils { // TODO - More classes to extract these

    private DupeRadarUtils() {}

    // ---------- Models ----------

    public static class ExploitEntry {
        public final String id;
        public final String name;
        public final String status; // working, patched

        public ExploitEntry(String id, String name, String status) {
            this.id = id;
            this.name = name;
            this.status = status;
        }
    }

    // ---------- HTTP ----------

    public static URL createURL(@NotNull String url) throws MalformedURLException {
        return URI.create(url).toURL();
    }

    public static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String httpGet(String url, String bearerToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) createURL(url).openConnection();
        if (bearerToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }

        int code = conn.getResponseCode();
        if (code != 200) throw new RuntimeException("HTTP " + code);

        return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ---------- Autism integration ----------

    public static List<String> getServerPlugins() {
        try {
            autismclient.modules.AutismModule global = autismclient.modules.AutismModule.get();
            if (global == null) return List.of();

            autismclient.util.AutismServerInfoOverlay overlay = global.getServerDataOverlay();
            if (overlay == null) return List.of();

            java.lang.reflect.Field field = overlay.getClass().getDeclaredField("detectedPlugins");
            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<String> plugins = (List<String>) field.get(overlay);
            return plugins == null ? List.of() : new ArrayList<>(plugins);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<String> parsePluginNames(String json) {
        List<String> names = new ArrayList<>();

        int idx = 0;
        while ((idx = json.indexOf("\"name\":\"", idx)) >= 0) {
            idx += 8;
            int end = json.indexOf("\"", idx);
            if (end > idx) {
                names.add(json.substring(idx, end));
                idx = end;
            }
        }

        return names;
    }

    public static List<ExploitEntry> parseExploits(String json) {
        List<ExploitEntry> out = new ArrayList<>();
        int arrStart = json.indexOf("\"exploits\":");
        if (arrStart < 0) return out;
        int bracket = json.indexOf("[", arrStart);
        if (bracket < 0) return out;

        int i = bracket + 1;
        int depth = 0;
        int objStart = -1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String obj = json.substring(objStart, i + 1);
                    String id = extractStr(obj, "id");
                    String name = extractStr(obj, "name");
                    boolean patched = hasNonNullString(obj, "marked_patched_at");
                    boolean working = hasNonNullString(obj, "marked_working_at");
                    String realStatus = patched ? "patched" : working ? "working" : "verified";
                    if (id != null && name != null) {
                        out.add(new ExploitEntry(id, name, realStatus));
                    }
                    objStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
            i++;
        }
        return out;
    }

    public static String extractStr(String obj, String key) {
        String search = "\"" + key + "\":\"";

        int idx = obj.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        int end = idx;

        while (end < obj.length()) {
            char c = obj.charAt(end);
            if (c == '"' && obj.charAt(end - 1) != '\\') break;
            end++;
        }
        return end > idx ? obj.substring(idx, end) : null;
    }

    public static boolean hasNonNullString(String obj, String key) {
        String search = "\"" + key + "\":";
        int idx = obj.indexOf(search);
        if (idx < 0) return false;
        idx += search.length();

        while (idx < obj.length() && Character.isWhitespace(obj.charAt(idx))) idx++;
        return idx < obj.length() && obj.charAt(idx) == '"';
    }

    // ---------- Matching ----------

    public static List<String> matchPlugins(List<String> serverPlugins, List<String> dbPlugins) {
        List<String> matched = new ArrayList<>();
        for (String sp : serverPlugins) {
            for (String dp : dbPlugins) {
                if (sp.equalsIgnoreCase(dp)) {
                    matched.add(dp);
                    break;
                }
            }
        }
        return matched;
    }

    public static Map<String, List<ExploitEntry>> fetchExploitsForPlugins(
        List<String> matchedNames, String bearerToken) {
        Map<String, List<ExploitEntry>> results = new LinkedHashMap<>();

        for (String name : matchedNames) {
            try {
                String url = "https://dupedb.net/api/exploits/search?plugin="
                    + urlEncode(name)
                    + "&status=working,patched,verified&limit=50";
                String resp = httpGet(url, bearerToken);
                List<ExploitEntry> exploits = parseExploits(resp);
                if (!exploits.isEmpty()) {
                    results.put(name, exploits);
                }
            } catch (Exception ignored) {}
        }
        return results;
    }

    // ---------- String helpers ----------

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    public static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
