package controlador.console;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Consulta que se entrega a los proveedores para resolver sugerencias.
 */
public record SuggestionRequest(
        String inputText,
        String activeToken,
        int caretOffset,
        Set<SuggestionCategory> requestedCategories,
        int limit,
        boolean slashPrefixed,
        Map<String, String> metadata
) {

    public SuggestionRequest {
        inputText = normalize(inputText);
        activeToken = normalize(activeToken);
        requestedCategories = immutableOrderedSet(requestedCategories);
        limit = limit <= 0 ? 25 : limit;
        metadata = immutableOrderedMap(metadata);
    }

    public static SuggestionRequest ofToken(String activeToken, Set<SuggestionCategory> requestedCategories) {
        return new SuggestionRequest(activeToken, activeToken, activeToken == null ? 0 : activeToken.length(),
                requestedCategories, 25, false, Map.of());
    }

    public boolean requests(SuggestionCategory category) {
        return category != null && (requestedCategories.isEmpty() || requestedCategories.contains(category));
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static Set<SuggestionCategory> immutableOrderedSet(Set<SuggestionCategory> source) {
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
