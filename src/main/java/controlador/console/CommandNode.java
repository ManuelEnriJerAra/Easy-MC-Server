package controlador.console;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Nodo inmutable de una gramática de comandos.
 */
public record CommandNode(
        String id,
        String token,
        NodeType nodeType,
        String description,
        ArgumentHint argumentHint,
        List<String> aliases,
        List<CommandNode> children,
        boolean executable,
        int priority,
        Set<String> supportedVersions,
        Set<String> supportedServerTypes,
        String source,
        Map<String, String> metadata
) {

    public CommandNode {
        id = normalize(id);
        token = normalize(token);
        nodeType = Objects.requireNonNullElse(nodeType, NodeType.LITERAL);
        description = normalize(description);
        aliases = List.copyOf(aliases == null ? List.of() : aliases);
        children = List.copyOf(children == null ? List.of() : children);
        supportedVersions = immutableOrderedSet(supportedVersions);
        supportedServerTypes = immutableOrderedSet(supportedServerTypes);
        source = normalize(source);
        metadata = immutableOrderedMap(metadata);
    }

    public static CommandNode root(List<CommandNode> children, String source) {
        return new CommandNode(
                "root",
                "",
                NodeType.ROOT,
                "",
                null,
                List.of(),
                children,
                false,
                0,
                Set.of(),
                Set.of(),
                source,
                Map.of()
        );
    }

    public boolean supportsVersion(String version) {
        return supportedVersions.isEmpty() || supportedVersions.contains(normalize(version));
    }

    public boolean supportsServerType(String serverType) {
        return supportedServerTypes.isEmpty() || supportedServerTypes.contains(normalize(serverType));
    }

    public boolean matchesToken(String candidate) {
        String normalizedCandidate = normalize(candidate);
        if (token.equalsIgnoreCase(normalizedCandidate)) {
            return true;
        }
        return aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(normalizedCandidate));
    }

    public enum NodeType {
        ROOT,
        LITERAL,
        ARGUMENT
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
