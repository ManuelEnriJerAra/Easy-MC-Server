package controlador.console;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot del estado del servidor relevante para resolver sugerencias.
 */
public record ConsoleCommandContext(
        String serverId,
        String serverDisplayName,
        String serverDir,
        String minecraftVersion,
        String serverType,
        boolean serverOnline,
        Set<String> onlinePlayers,
        Set<String> knownPlayers,
        Set<String> availableDimensions,
        Set<String> availableTags,
        Set<String> installedMods,
        Set<String> installedPlugins,
        String contentFingerprint,
        Instant capturedAt,
        Map<String, Set<String>> dynamicValues,
        Map<String, String> metadata
) {

    public ConsoleCommandContext {
        serverId = normalize(serverId);
        serverDisplayName = normalize(serverDisplayName);
        serverDir = normalize(serverDir);
        minecraftVersion = normalize(minecraftVersion);
        serverType = normalize(serverType);
        onlinePlayers = immutableOrderedSet(onlinePlayers);
        knownPlayers = immutableOrderedSet(knownPlayers);
        availableDimensions = immutableOrderedSet(availableDimensions);
        availableTags = immutableOrderedSet(availableTags);
        installedMods = immutableOrderedSet(installedMods);
        installedPlugins = immutableOrderedSet(installedPlugins);
        contentFingerprint = normalize(contentFingerprint);
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        dynamicValues = immutableNestedSetMap(dynamicValues);
        metadata = immutableOrderedMap(metadata);
    }

    public static ConsoleCommandContext empty() {
        return new ConsoleCommandContext(
                "",
                "",
                "",
                "",
                "",
                false,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                "",
                Instant.now(),
                Map.of(),
                Map.of()
        );
    }

    public Set<String> valuesOf(String key) {
        if (key == null || key.isBlank()) {
            return Set.of();
        }
        return dynamicValues.getOrDefault(key, Set.of());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Set<String> immutableOrderedSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(source));
    }

    private static Map<String, Set<String>> immutableNestedSetMap(Map<String, Set<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((key, values) -> copy.put(normalize(key), immutableOrderedSet(values)));
        return Map.copyOf(copy);
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
