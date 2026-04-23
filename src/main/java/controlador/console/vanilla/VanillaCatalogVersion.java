package controlador.console.vanilla;

import controlador.console.SuggestionCategory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot versionado de un catálogo vanilla.
 */
public record VanillaCatalogVersion(
        String version,
        String source,
        String generationStrategy,
        Instant generatedAt,
        Map<SuggestionCategory, List<String>> categories,
        Map<String, String> metadata
) {

    public VanillaCatalogVersion {
        version = normalize(version);
        source = normalize(source);
        generationStrategy = normalize(generationStrategy);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        categories = immutableCategories(categories);
        metadata = immutableOrderedMap(metadata);
    }

    public List<String> valuesOf(SuggestionCategory category) {
        if (category == null) {
            return List.of();
        }
        return categories.getOrDefault(category, List.of());
    }

    public Set<SuggestionCategory> availableCategories() {
        return Collections.unmodifiableSet(categories.keySet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<SuggestionCategory, List<String>> immutableCategories(
            Map<SuggestionCategory, List<String>> source
    ) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        EnumMap<SuggestionCategory, List<String>> copy = new EnumMap<>(SuggestionCategory.class);
        source.forEach((key, values) -> {
            if (key == null) {
                return;
            }
            LinkedHashSet<String> deduplicated = new LinkedHashSet<>(values == null ? List.<String>of() : values);
            copy.put(key, List.copyOf(new ArrayList<>(deduplicated)));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
