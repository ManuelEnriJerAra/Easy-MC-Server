package controlador.console.content;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describe un mod, plugin o componente instalado detectado localmente.
 */
public record InstalledComponent(
        InstalledComponentKind kind,
        String componentId,
        String displayName,
        String version,
        Path path,
        String loader,
        Map<String, String> metadata
) {

    public InstalledComponent {
        kind = kind == null ? InstalledComponentKind.UNKNOWN : kind;
        componentId = normalize(componentId);
        displayName = normalize(displayName);
        version = normalize(version);
        loader = normalize(loader);
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public String stableDescriptor() {
        String normalizedPath = path == null ? "" : path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return kind.name() + "|" + componentId + "|" + displayName + "|" + version + "|" + loader + "|" + normalizedPath;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
