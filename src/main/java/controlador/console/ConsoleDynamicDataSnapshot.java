package controlador.console;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Datos dinámicos de consola leídos desde archivos runtime del servidor.
 */
public record ConsoleDynamicDataSnapshot(
        Set<String> knownPlayers,
        Set<String> ops,
        Set<String> whitelist,
        Set<String> bannedPlayers,
        Set<String> bannedIps
) {

    public ConsoleDynamicDataSnapshot {
        knownPlayers = immutableOrderedSet(knownPlayers);
        ops = immutableOrderedSet(ops);
        whitelist = immutableOrderedSet(whitelist);
        bannedPlayers = immutableOrderedSet(bannedPlayers);
        bannedIps = immutableOrderedSet(bannedIps);
    }

    public static ConsoleDynamicDataSnapshot empty() {
        return new ConsoleDynamicDataSnapshot(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public Map<String, Set<String>> toDynamicValuesMap() {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        values.put("ops", ops);
        values.put("whitelist", whitelist);
        values.put("bannedPlayers", bannedPlayers);
        values.put("bannedIps", bannedIps);
        values.put("liveCommands", Set.of());
        return Map.copyOf(values);
    }

    public Set<String> allKnownPlayersIncludingLists() {
        LinkedHashSet<String> merged = new LinkedHashSet<>(knownPlayers);
        merged.addAll(ops);
        merged.addAll(whitelist);
        merged.addAll(bannedPlayers);
        return Set.copyOf(merged);
    }

    private static Set<String> immutableOrderedSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(values));
    }
}
