package controlador.console;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representa una sugerencia final lista para ser mostrada o cacheada.
 */
public record Suggestion(
        String id,
        String insertText,
        String displayText,
        String detailText,
        SuggestionCategory category,
        String minecraftVersion,
        String serverType,
        String source,
        int priority,
        SuggestionMatch match,
        boolean replaceCurrentToken,
        boolean terminal,
        Map<String, String> metadata
) {

    public Suggestion {
        id = normalize(id);
        insertText = normalize(insertText);
        displayText = normalize(displayText);
        detailText = normalize(detailText);
        category = category == null ? SuggestionCategory.FREE_VALUE : category;
        minecraftVersion = normalize(minecraftVersion);
        serverType = normalize(serverType);
        source = normalize(source);
        match = match == null ? SuggestionMatch.none() : match;
        metadata = immutableOrderedMap(metadata);
    }

    public boolean supports(String version, String type) {
        boolean versionMatches = minecraftVersion.isBlank() || minecraftVersion.equalsIgnoreCase(normalize(version));
        boolean typeMatches = serverType.isBlank() || serverType.equalsIgnoreCase(normalize(type));
        return versionMatches && typeMatches;
    }

    public Suggestion withMatch(SuggestionMatch newMatch) {
        return new Suggestion(
                id,
                insertText,
                displayText,
                detailText,
                category,
                minecraftVersion,
                serverType,
                source,
                priority,
                newMatch,
                replaceCurrentToken,
                terminal,
                metadata
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
