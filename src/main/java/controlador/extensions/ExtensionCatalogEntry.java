package controlador.extensions;

import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.util.Set;

public record ExtensionCatalogEntry(
        String providerId,
        String projectId,
        String versionId,
        String displayName,
        String author,
        String version,
        String description,
        ExtensionSourceType sourceType,
        ServerExtensionType extensionType,
        Set<ServerPlatform> compatiblePlatforms,
        Set<String> compatibleMinecraftVersions,
        String iconUrl,
        String projectUrl,
        String downloadUrl
) {
}
