package controlador.console;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado híbrido tras combinar múltiples proveedores.
 */
public record SuggestionAggregateResult(
        List<Suggestion> suggestions,
        List<SuggestionProviderResult> providerResults,
        boolean degraded,
        Map<String, String> metadata
) {

    public SuggestionAggregateResult {
        suggestions = List.copyOf(suggestions == null ? List.of() : suggestions);
        providerResults = List.copyOf(providerResults == null ? List.of() : providerResults);
        metadata = immutableOrderedMap(metadata);
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
