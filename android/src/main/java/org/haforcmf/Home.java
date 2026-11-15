package org.haforcmf;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Home {
    record Entity(String id, String label, String state, String unit) {
        boolean switchable() { return id.startsWith("light.") || id.startsWith("switch."); }
        boolean canSwitch() { return switchable() && ("on".equals(state) || "off".equals(state)); }
        String display() {
            return switch (state) {
                case "on" -> "On";
                case "off" -> "Off";
                case "unavailable", "unknown" -> "Offline";
                default -> ascii(state + (unit.trim().isEmpty() ? "" : " " + unit), 10);
            };
        }
    }

    private final URI base;
    private final String token;

    Home(String url, String token) {
        this.base = URI.create(url.trim().replaceAll("/+$", ""));
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a Home Assistant URL and access token.");
        }
        this.token = token.trim();
    }

    private String request(String path, JSONObject body) throws Exception {
        var connection = (HttpURLConnection) URI.create(base + path).toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(3500);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Home Assistant HTTP " + status);
            try (var input = connection.getInputStream()) {
                return new String(readBytes(input), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    List<Entity> entities() throws Exception {
        var result = new ArrayList<Entity>();
        var states = new JSONArray(request("/api/states", null));
        for (int i = 0; i < states.length(); i++) {
            JSONObject state = states.getJSONObject(i);
            String id = state.getString("entity_id");
            if (!id.matches("[a-z0-9_]+\\.[a-z0-9_]+")) continue;
            JSONObject attrs = state.optJSONObject("attributes");
            result.add(new Entity(id, attrs == null ? id : attrs.optString("friendly_name", id),
                    state.optString("state", "unknown"), attrs == null ? "" : attrs.optString("unit_of_measurement", "")));
        }
        result.sort(Comparator.comparing(Entity::label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    Map<String, Entity> states() throws Exception {
        var result = new LinkedHashMap<String, Entity>();
        for (Entity entity : entities()) result.put(entity.id(), entity);
        return result;
    }

    void set(Entity entity, boolean on) throws Exception {
        if (!entity.switchable()) return;
        String domain = entity.id().substring(0, entity.id().indexOf('.'));
        request("/api/services/" + domain + (on ? "/turn_on" : "/turn_off"),
                new JSONObject().put("entity_id", entity.id()));
    }

    static byte[] readBytes(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    static String ascii(String value, int limit) {
        String clean = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").replaceAll("[^ -~]", "").trim();
        return clean.substring(0, Math.min(limit, clean.length()));
    }
}
