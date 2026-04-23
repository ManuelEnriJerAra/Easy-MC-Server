package controlador.console.vanilla;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Snapshot cacheable de los comandos vanilla root descubiertos para una version concreta.
 */
public record VanillaCommandCatalog(
        String version,
        List<String> commands,
        boolean fromPersistentCache,
        String source,
        Instant generatedAt,
        Map<String, String> metadata
) {

    public VanillaCommandCatalog {
        version = normalize(version);
        commands = List.copyOf(new LinkedHashSet<>(commands == null ? List.<String>of() : commands));
        source = normalize(source);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
