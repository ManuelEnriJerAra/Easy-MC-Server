package controlador.extensions;

import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.util.List;
import java.util.Set;

public record ExtensionDownloadPlan(
        String providerId,
        String projectId,
        String versionId,
        String displayName,
        String author,
        String description,
        String versionNumber,
        String iconUrl,
        String fileName,
        String downloadUrl,
        String projectUrl,
        String issuesUrl,
        String websiteUrl,
        String licenseName,
        long downloads,
        String clientSide,
        String serverSide,
        Set<String> categories,
        ExtensionSourceType sourceType,
        ServerExtensionType extensionType,
        ServerPlatform platform,
        String minecraftVersionConstraint,
        boolean ready,
        String message,
        List<ExtensionDependency> dependencies
) {
    public ExtensionDownloadPlan(String providerId,
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
                                 String message) {
        this(providerId, projectId, versionId, null, null, null, versionNumber, iconUrl, fileName, downloadUrl,
                null, null, null, null, 0L, "unknown", "unknown", Set.of(),
                sourceType, extensionType, platform, minecraftVersionConstraint, ready, message, List.of());
    }

    public ExtensionDownloadPlan(String providerId,
                                 String projectId,
                                 String versionId,
                                 String displayName,
                                 String author,
                                 String description,
                                 String versionNumber,
                                 String iconUrl,
                                 String fileName,
                                 String downloadUrl,
                                 String projectUrl,
                                 String issuesUrl,
                                 String websiteUrl,
                                 String licenseName,
                                 long downloads,
                                 String clientSide,
                                 String serverSide,
                                 Set<String> categories,
                                 ExtensionSourceType sourceType,
                                 ServerExtensionType extensionType,
                                 ServerPlatform platform,
                                 String minecraftVersionConstraint,
                                 boolean ready,
                                 String message) {
        this(providerId, projectId, versionId, displayName, author, description, versionNumber, iconUrl, fileName, downloadUrl,
                projectUrl, issuesUrl, websiteUrl, licenseName, downloads, clientSide, serverSide, categories,
                sourceType, extensionType, platform, minecraftVersionConstraint, ready, message, List.of());
    }
}
