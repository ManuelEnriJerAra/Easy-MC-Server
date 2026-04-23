package controlador.console.content;

import controlador.console.SuggestionCategory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fuente potencial de entidades, items u otros identificadores custom.
 */
public record PotentialContentSource(
        ContentSourceKind kind,
        String sourceId,
        String description,
        Path path,
        Set<SuggestionCategory> supportedCategories,
        int confidence,
        Map<String, String> metadata
) {

    public PotentialContentSource {
        kind = kind == null ? ContentSourceKind.UNKNOWN : kind;
        sourceId = normalize(sourceId);
        description = normalize(description);
        supportedCategories = supportedCategories == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(supportedCategories));
        confidence = Math.max(0, Math.min(confidence, 100));
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public String stableDescriptor() {
        String normalizedPath = path == null ? "" : path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return kind.name() + "|" + sourceId + "|" + normalizedPath + "|" + confidence;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
