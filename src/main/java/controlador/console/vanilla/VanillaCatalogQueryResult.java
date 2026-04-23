package controlador.console.vanilla;

import controlador.console.SuggestionCategory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado rápido de consulta sobre el catálogo versionado.
 */
public record VanillaCatalogQueryResult(
        String version,
        String prefix,
        Map<SuggestionCategory, List<String>> matches,
        boolean fromPersistentCache,
        Map<String, String> metadata
) {

    public VanillaCatalogQueryResult {
        version = version == null ? "" : version.trim();
        prefix = prefix == null ? "" : prefix;
        matches = matches == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(matches));
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }
}
