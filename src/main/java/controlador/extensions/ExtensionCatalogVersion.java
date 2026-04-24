package controlador.extensions;

import modelo.extensions.ServerPlatform;

import java.util.Set;

public record ExtensionCatalogVersion(
        String providerId,
        String projectId,
        String versionId,
        String displayName,
        String versionNumber,
        Set<ServerPlatform> supportedPlatforms,
        Set<String> supportedMinecraftVersions,
        String changelog,
        String fileName,
        String downloadUrl,
        long publishedAtEpochMillis
) {
}
