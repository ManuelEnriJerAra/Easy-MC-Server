package controlador.console;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado normalizado de un proveedor individual.
 */
public record SuggestionProviderResult(
        SuggestionProviderDescriptor descriptor,
        List<Suggestion> suggestions,
        boolean degraded,
        boolean partialCoverage,
        boolean stale,
        String message,
        Map<String, String> metadata
) {

    public SuggestionProviderResult {
        suggestions = List.copyOf(suggestions == null ? List.of() : suggestions);
        message = message == null ? "" : message.trim();
        metadata = immutableOrderedMap(metadata);
    }

    public static SuggestionProviderResult success(
            SuggestionProviderDescriptor descriptor,
            List<Suggestion> suggestions
    ) {
        return new SuggestionProviderResult(descriptor, suggestions, false, false, false, "", Map.of());
    }

    public static SuggestionProviderResult degraded(
            SuggestionProviderDescriptor descriptor,
            List<Suggestion> suggestions,
            String message
    ) {
        return new SuggestionProviderResult(descriptor, suggestions, true, true, false, message, Map.of());
    }

    public static SuggestionProviderResult failure(
            SuggestionProviderDescriptor descriptor,
            String message
    ) {
        return new SuggestionProviderResult(descriptor, List.of(), true, true, false, message, Map.of());
    }

    public static SuggestionProviderResult timeout(
            SuggestionProviderDescriptor descriptor,
            long timeoutMillis
    ) {
        return new SuggestionProviderResult(
                descriptor,
                List.of(),
                true,
                true,
                true,
                "Timeout resolviendo sugerencias tras " + timeoutMillis + "ms.",
                Map.of("timeoutMillis", String.valueOf(timeoutMillis))
        );
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
