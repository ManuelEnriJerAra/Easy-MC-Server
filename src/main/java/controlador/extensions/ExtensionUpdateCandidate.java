package controlador.extensions;

import modelo.extensions.ServerExtension;

public record ExtensionUpdateCandidate(
        String providerId,
        String projectId,
        ServerExtension installedExtension,
        ExtensionCatalogVersion targetVersion,
        boolean updateAvailable,
        String message
) {
}
