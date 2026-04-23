package controlador.console;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Descriptor declarativo de un proveedor de sugerencias.
 */
public record SuggestionProviderDescriptor(
        String id,
        String displayName,
        Set<SuggestionProviderCoverage> coverage,
        SuggestionProviderFreshness freshness,
        SuggestionProviderCost cost,
        boolean supportsOffline,
        boolean helperAware,
        Map<String, String> metadata
) {

    public SuggestionProviderDescriptor {
        id = normalize(id);
        displayName = normalize(displayName);
        coverage = immutableOrderedSet(coverage);
        freshness = freshness == null ? SuggestionProviderFreshness.CACHED : freshness;
        cost = cost == null ? SuggestionProviderCost.MEDIUM : cost;
        metadata = immutableOrderedMap(metadata);
    }

    public boolean covers(SuggestionProviderCoverage targetCoverage) {
        return targetCoverage != null && coverage.contains(targetCoverage);
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
