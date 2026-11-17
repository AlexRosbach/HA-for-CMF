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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

final class Home {
    record Entity(String id, String label, String state, String unit,
                  String areaId, String areaName, int features, int brightness) {
        Entity(String id, String label, String state, String unit) {
            this(id, label, state, unit, "", "Ohne Raum", 0, 0);
        }
        String domain() { return id.substring(0, id.indexOf('.')); }
        boolean isLock() { return id.startsWith("lock."); }
        boolean supportsOpen() { return isLock() && (features & 1) != 0; }
        boolean dimmable() { return id.startsWith("light.") && (features & 1) != 0; }
        int brightnessPercent() { return Math.min(100,Math.max(0,Math.round(brightness * 100f / 255f))); }
        boolean controllable() {
            return canSwitch() || (isLock() && ("locked".equals(state) || "unlocked".equals(state)));
        }
        boolean switchable() { return id.startsWith("light.") || id.startsWith("switch."); }
        boolean actionable() { return id.startsWith("scene.") || id.startsWith("script.")
                || id.startsWith("button.") || id.startsWith("input_button."); }
        boolean canSwitch() { return (switchable() && ("on".equals(state) || "off".equals(state)))
                || (actionable() && !"unavailable".equals(state) && !"unknown".equals(state)); }
        String display() {
            if (actionable()) return "Run";
            if (isLock()) return switch (state) {
                case "locked" -> "Verriegelt";
                case "unlocked" -> "Entriegelt";
                case "locking", "unlocking" -> "Wird geändert";
                default -> state;
            };
            return switch (state) {
                case "on" -> "On";
                case "off" -> "Off";
                case "unavailable", "unknown" -> "Offline";
                default -> ascii(state + (unit.trim().isEmpty() ? "" : " " + unit), 10);
            };
        }
    }

    record Room(String id, String name) {}
    record Catalog(List<Entity> entities, List<Room> rooms) {}

    // The /api/states endpoint does not include area metadata. Query area associations
    // through Home Assistant's officially supported template functions.
    private static final String AREA_TEMPLATE =
            "{% set ns = namespace(rooms=[]) %}"
            + "{% for area in areas() %}"
            + "{% set ns.rooms = ns.rooms + [dict(id=area, name=area_name(area), entities=area_entities(area))] %}"
            + "{% endfor %}{{ ns.rooms | to_json }}";

    Catalog catalog() throws Exception {
        List<Entity> all = entities();
        List<Room> rooms = new ArrayList<>();
        Map<String, Room> membership = new HashMap<>();
        try {
            String result = request("/api/template", new JSONObject().put("template", AREA_TEMPLATE));
            JSONArray data = new JSONArray(result);
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String id = item.optString("id");
                String name = item.optString("name", id);
                if (id.isBlank() || name.isBlank()) continue;
                Room room = new Room(id, name);
                rooms.add(room);
                JSONArray linked = item.optJSONArray("entities");
                if (linked != null) {
                    for (int j = 0; j < linked.length(); j++) {
                        String entityId = linked.optString(j);
                        membership.putIfAbsent(entityId, room);
                    }
                }
            }
        } catch (Exception unavailable) {
            // Old HA installations or restricted template APIs may lack area lookup.
            // Entity browsing and controls continue without rooms.
        }
        rooms.sort(Comparator.comparing(Room::name, String.CASE_INSENSITIVE_ORDER));
        List<Entity> tagged = new ArrayList<>(all.size());
        for (Entity entity : all) {
            Room room = membership.get(entity.id());
            tagged.add(new Entity(entity.id(), entity.label(), entity.state(), entity.unit(),
                    room == null ? "" : room.id(), room == null ? "Ohne Raum" : room.name(),
                    entity.features(), entity.brightness()));
        }
        return new Catalog(tagged, rooms);
    }

    // Unlocking is intentionally separated from the generic action method: callers
    // must explicitly collect a second confirmation before invoking this.
    void setLock(String entityId, boolean unlock) throws Exception {
        if (!entityId.matches("lock\\.[a-z0-9_]+")) {
            throw new IllegalArgumentException("Keine gültige Schloss-Entität.");
        }
        Entity current = states().get(entityId);
        if (current == null || !current.isLock()) {
            throw new IOException("Schloss ist nicht verfügbar.");
        }
        if (unlock && !"locked".equals(current.state())) {
            throw new IOException("Schloss ist nicht verriegelt; Status wurde neu geladen.");
        }
        if (!unlock && !"unlocked".equals(current.state())) {
            throw new IOException("Schloss ist nicht entriegelt; Status wurde neu geladen.");
        }
        request("/api/services/lock/" + (unlock ? "unlock" : "lock"),
                new JSONObject().put("entity_id", entityId));
    }

    // Physical unlatching is deliberately separate from unlock and never sent
    // through the watch toggle protocol.
    void openLatch(String entityId) throws Exception {
        if (!entityId.matches("lock\\.[a-z0-9_]+")) {
            throw new IllegalArgumentException("Keine gültige Schloss-Entität.");
        }
        Entity current = states().get(entityId);
        if (current == null || !current.supportsOpen()) {
            throw new IOException("Dieses Schloss unterstützt das Öffnen der Tür nicht.");
        }
        if (!("locked".equals(current.state()) || "unlocked".equals(current.state()))) {
            throw new IOException("Schloss ist nicht bereit. Aktuellen Zustand prüfen.");
        }
        request("/api/services/lock/open", new JSONObject().put("entity_id", entityId));
    }

    private final URI base;
    private final String token;

    Home(String url, String token) {
        this.base = URI.create(url.trim().replaceAll("/+$", ""));
        if (!"https".equals(base.getScheme())
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
                    state.optString("state", "unknown"), attrs == null ? "" : attrs.optString("unit_of_measurement", ""),
                    "", "Ohne Raum", attrs == null ? 0 : attrs.optInt("supported_features", 0),
                    attrs == null ? 0 : attrs.optInt("brightness", 0)));
        }
        result.sort(Comparator.comparing(Entity::label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    Map<String, Entity> states() throws Exception {
        var result = new LinkedHashMap<String, Entity>();
        for (Entity entity : entities()) result.put(entity.id(), entity);
        return result;
    }

    void setBrightness(Entity entity, int percent) throws Exception {
        if (!entity.dimmable()) throw new IllegalArgumentException("Helligkeit wird nicht unterstützt.");
        int clamped = Math.max(1, Math.min(100, percent));
        request("/api/services/light/turn_on",new JSONObject()
                .put("entity_id",entity.id()).put("brightness_pct",clamped));
    }

    void set(Entity entity, boolean on) throws Exception {
        String domain = entity.id().substring(0, entity.id().indexOf('.'));
        if (entity.actionable()) {
            if (!on) return;
            String service = (domain.equals("button") || domain.equals("input_button")) ? "press" : "turn_on";
            request("/api/services/" + domain + "/" + service,
                    new JSONObject().put("entity_id", entity.id()));
        } else if (entity.switchable()) {
            request("/api/services/" + domain + (on ? "/turn_on" : "/turn_off"),
                    new JSONObject().put("entity_id", entity.id()));
        }
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
