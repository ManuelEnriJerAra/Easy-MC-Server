package controlador.console;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Gramática versionada y serializable de un conjunto de comandos.
 */
public record CommandGrammar(
        String id,
        String name,
        String minecraftVersion,
        String serverType,
        String source,
        CommandNode root,
        Set<SuggestionCategory> supportedCategories,
        Instant generatedAt,
        Map<String, String> metadata
) {

    public CommandGrammar {
        id = normalize(id);
        name = normalize(name);
        minecraftVersion = normalize(minecraftVersion);
        serverType = normalize(serverType);
        source = normalize(source);
        root = root == null ? CommandNode.root(List.of(), source) : root;
        supportedCategories = immutableOrderedSet(supportedCategories);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        metadata = immutableOrderedMap(metadata);
    }

    public boolean supports(String version, String type) {
        boolean versionMatches = minecraftVersion.isBlank() || minecraftVersion.equalsIgnoreCase(normalize(version));
        boolean typeMatches = serverType.isBlank() || serverType.equalsIgnoreCase(normalize(type));
        return versionMatches && typeMatches;
    }

    public List<CommandNode> rootChildren() {
        return root.children();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> Set<T> immutableOrderedSet(Set<T> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(source));
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
