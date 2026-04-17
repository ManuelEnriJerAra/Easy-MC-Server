package controlador.extensions;

import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.util.Set;

public record ExtensionCatalogEntry(
        String providerId,
        String projectId,
        String versionId,
        String displayName,
        String version,
        String description,
        ServerExtensionType extensionType,
        Set<ServerPlatform> compatiblePlatforms,
        String downloadUrl
) {
}
