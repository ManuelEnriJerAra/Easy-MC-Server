package controlador;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GestorUsuariosConocidos {
    private static final String JSON_FILE = "easy-mc-known-users.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, KnownUser> USERS_BY_NAME = new LinkedHashMap<>();

    private static boolean loaded;

    private GestorUsuariosConocidos() {
    }

    public static synchronized List<String> buscarSugerencias(String query, int limit) {
        ensureLoaded();
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = normalize(query);
        return USERS_BY_NAME.values().stream()
                .filter(user -> user.name != null && !user.name.isBlank())
                .sorted(Comparator
                        .comparingInt((KnownUser user) -> matchPriority(user, normalizedQuery))
                        .thenComparing(user -> user.name, String.CASE_INSENSITIVE_ORDER))
                .filter(user -> matchPriority(user, normalizedQuery) < Integer.MAX_VALUE)
                .limit(Math.max(1, limit))
                .map(user -> user.name)
                .toList();
    }

    public static synchronized void recordarUsuarios(Collection<String> usernames, String source) {
        ensureLoaded();
        if (usernames == null || usernames.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (String username : usernames) {
            changed |= upsert(username, null, null, source);
        }
        if (changed) {
            save();
        }
    }

    public static synchronized void recordarPerfil(MojangAPI.PlayerProfile profile, String source) {
        if (profile == null) {
            return;
        }
        recordarUsuario(profile.getName(), profile.getUuid(), profile.getSkinUrl(), source);
    }

    public static synchronized void recordarUsuario(String username, String uuid, String skinUrl, String source) {
        ensureLoaded();
        if (!upsert(username, uuid, skinUrl, source)) {
            return;
        }
        save();
    }

    private static int matchPriority(KnownUser user, String normalizedQuery) {
        if (user == null || user.name == null || user.name.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String normalizedName = normalize(user.name);
        if (normalizedName.equals(normalizedQuery)) {
            return 0;
        }
        if (normalizedName.startsWith(normalizedQuery)) {
            return 1;
        }
        if (normalizedName.contains(normalizedQuery)) {
            return 2;
        }
        return Integer.MAX_VALUE;
    }

    private static boolean upsert(String username, String uuid, String skinUrl, String source) {
        if (username == null || username.isBlank()) {
            return false;
        }

        String normalizedName = normalize(username);
        KnownUser existing = USERS_BY_NAME.get(normalizedName);
        boolean changed = false;
        if (existing == null) {
            existing = new KnownUser();
            existing.name = username.strip();
            USERS_BY_NAME.put(normalizedName, existing);
            changed = true;
        } else if (!existing.name.equals(username.strip())) {
            existing.name = username.strip();
            changed = true;
        }

        if (uuid != null && !uuid.isBlank() && !uuid.equals(existing.uuid)) {
            existing.uuid = uuid.strip();
            changed = true;
        }
        if (skinUrl != null && !skinUrl.isBlank() && !skinUrl.equals(existing.skinUrl)) {
            existing.skinUrl = skinUrl.strip();
            changed = true;
        }
        if (source != null && !source.isBlank() && existing.sources.add(source.strip())) {
            changed = true;
        }

        String now = Instant.now().toString();
        if (!now.equals(existing.lastSeenAt)) {
            existing.lastSeenAt = now;
            changed = true;
        }
        return changed;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = getJsonPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(path.toFile());
            if (root == null || !root.isArray()) {
                return;
            }
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                String name = node.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                KnownUser user = new KnownUser();
                user.name = name.strip();
                String uuid = node.path("uuid").asText(null);
                user.uuid = uuid == null || uuid.isBlank() ? null : uuid.strip();
                String skinUrl = node.path("skinUrl").asText(null);
                user.skinUrl = skinUrl == null || skinUrl.isBlank() ? null : skinUrl.strip();
                String lastSeenAt = node.path("lastSeenAt").asText(null);
                user.lastSeenAt = lastSeenAt == null || lastSeenAt.isBlank() ? Instant.now().toString() : lastSeenAt.strip();
                JsonNode sourcesNode = node.path("sources");
                if (sourcesNode.isArray()) {
                    for (JsonNode sourceNode : sourcesNode) {
                        String source = sourceNode.asText(null);
                        if (source != null && !source.isBlank()) {
                            user.sources.add(source.strip());
                        }
                    }
                }
                USERS_BY_NAME.put(normalize(user.name), user);
            }
        } catch (Exception e) {
            System.err.println("No se ha podido cargar " + JSON_FILE + ": " + e.getMessage());
        }
    }

    private static void save() {
        Path path = getJsonPath();
        ArrayNode root = MAPPER.createArrayNode();
        USERS_BY_NAME.values().stream()
                .sorted(Comparator.comparing(user -> user.name, String.CASE_INSENSITIVE_ORDER))
                .forEach(user -> root.add(toJson(user)));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
        } catch (IOException e) {
            System.err.println("No se ha podido guardar " + JSON_FILE + ": " + e.getMessage());
        }
    }

    private static ObjectNode toJson(KnownUser user) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", user.name);
        if (user.uuid != null && !user.uuid.isBlank()) {
            node.put("uuid", user.uuid);
        }
        if (user.skinUrl != null && !user.skinUrl.isBlank()) {
            node.put("skinUrl", user.skinUrl);
        }
        if (user.lastSeenAt != null && !user.lastSeenAt.isBlank()) {
            node.put("lastSeenAt", user.lastSeenAt);
        }
        ArrayNode sourcesNode = node.putArray("sources");
        user.sources.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(sourcesNode::add);
        return node;
    }

    private static Path getJsonPath() {
        return GestorConfiguracion.getBaseDirectory().resolve(JSON_FILE);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static final class KnownUser {
        private String name;
        private String uuid;
        private String skinUrl;
        private String lastSeenAt = Instant.now().toString();
        private final LinkedHashSet<String> sources = new LinkedHashSet<>();
    }
}
