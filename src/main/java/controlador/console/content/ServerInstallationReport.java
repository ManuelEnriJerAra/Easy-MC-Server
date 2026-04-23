package controlador.console.content;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Informe reusable sobre el contenido instalado de un servidor.
 */
public record ServerInstallationReport(
        String serverId,
        String serverDisplayName,
        Path serverDirectory,
        String detectedServerType,
        String detectedMinecraftVersion,
        Path executableJar,
        List<InstalledComponent> serverComponents,
        List<InstalledComponent> mods,
        List<InstalledComponent> plugins,
        List<InstalledComponent> datapacks,
        List<PotentialContentSource> potentialContentSources,
        String contentFingerprint,
        List<String> warnings,
        Instant capturedAt,
        Map<String, String> metadata
) {

    public ServerInstallationReport {
        serverId = normalize(serverId);
        serverDisplayName = normalize(serverDisplayName);
        detectedServerType = normalize(detectedServerType);
        detectedMinecraftVersion = normalize(detectedMinecraftVersion);
        serverComponents = List.copyOf(serverComponents == null ? List.of() : serverComponents);
        mods = List.copyOf(mods == null ? List.of() : mods);
        plugins = List.copyOf(plugins == null ? List.of() : plugins);
        datapacks = List.copyOf(datapacks == null ? List.of() : datapacks);
        potentialContentSources = List.copyOf(potentialContentSources == null ? List.of() : potentialContentSources);
        contentFingerprint = normalize(contentFingerprint);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public List<InstalledComponent> allInstalledComponents() {
        java.util.ArrayList<InstalledComponent> merged = new java.util.ArrayList<>();
        merged.addAll(serverComponents);
        merged.addAll(mods);
        merged.addAll(plugins);
        merged.addAll(datapacks);
        return List.copyOf(merged);
    }

    public boolean hasModdedContent() {
        return !mods.isEmpty() || !plugins.isEmpty() || !datapacks.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
