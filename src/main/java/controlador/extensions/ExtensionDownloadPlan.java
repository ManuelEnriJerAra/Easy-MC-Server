package controlador.extensions;

import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

public record ExtensionDownloadPlan(
        String providerId,
        String projectId,
        String versionId,
        String versionNumber,
        String iconUrl,
        String fileName,
        String downloadUrl,
        ExtensionSourceType sourceType,
        ServerExtensionType extensionType,
        ServerPlatform platform,
        String minecraftVersionConstraint,
        boolean ready,
        String message
) {
}
