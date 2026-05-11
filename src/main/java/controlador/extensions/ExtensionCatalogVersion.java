package controlador.extensions;

import modelo.extensions.ServerPlatform;

import java.util.List;
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
        long publishedAtEpochMillis,
        boolean stableRelease,
        List<ExtensionDependency> dependencies
) {
    public ExtensionCatalogVersion {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public ExtensionCatalogVersion(String providerId,
                                   String projectId,
                                   String versionId,
                                   String displayName,
                                   String versionNumber,
                                   Set<ServerPlatform> supportedPlatforms,
                                   Set<String> supportedMinecraftVersions,
                                   String changelog,
                                   String fileName,
                                   String downloadUrl,
                                   long publishedAtEpochMillis) {
        this(providerId, projectId, versionId, displayName, versionNumber, supportedPlatforms, supportedMinecraftVersions,
                changelog, fileName, downloadUrl, publishedAtEpochMillis, true, List.of());
    }

    public ExtensionCatalogVersion(String providerId,
                                   String projectId,
                                   String versionId,
                                   String displayName,
                                   String versionNumber,
                                   Set<ServerPlatform> supportedPlatforms,
                                   Set<String> supportedMinecraftVersions,
                                   String changelog,
                                   String fileName,
                                   String downloadUrl,
                                   long publishedAtEpochMillis,
                                   List<ExtensionDependency> dependencies) {
        this(providerId, projectId, versionId, displayName, versionNumber, supportedPlatforms, supportedMinecraftVersions,
                changelog, fileName, downloadUrl, publishedAtEpochMillis, true, dependencies);
    }
}
