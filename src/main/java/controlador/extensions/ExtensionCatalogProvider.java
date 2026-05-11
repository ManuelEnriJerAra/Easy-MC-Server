package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExtensionCatalogProvider {
    String getProviderId();

    default String getDisplayName() {
        return getProviderId();
    }

    default ExtensionSourceType getSourceType() {
        return ExtensionSourceType.UNKNOWN;
    }

    default Set<ExtensionCatalogCapability> getCapabilities() {
        return Set.of();
    }

    default Set<ServerExtensionType> getSupportedExtensionTypes() {
        return Set.of();
    }

    default Set<ServerPlatform> getSupportedPlatforms() {
        return Set.of();
    }

    default String getLimitations() {
        return null;
    }

    default ExtensionCatalogProviderDescriptor describeProvider() {
        return new ExtensionCatalogProviderDescriptor(
                getProviderId(),
                getDisplayName(),
                getSourceType(),
                getCapabilities(),
                getSupportedExtensionTypes(),
                getSupportedPlatforms(),
                getLimitations()
        );
    }

    default boolean supportsSearch() {
        return getCapabilities().contains(ExtensionCatalogCapability.SEARCH);
    }

    default boolean supportsQuery(ExtensionCatalogQuery query) {
        if (!supportsSearch()) {
            return false;
        }
        if (query == null) {
            return true;
        }
        Set<ServerExtensionType> supportedTypes = getSupportedExtensionTypes();
        if (query.extensionType() != null
                && query.extensionType() != ServerExtensionType.UNKNOWN
                && !supportedTypes.isEmpty()
                && !supportedTypes.contains(query.extensionType())) {
            return false;
        }
        Set<ServerPlatform> supportedPlatforms = getSupportedPlatforms();
        return query.platform() == null
                || query.platform() == ServerPlatform.UNKNOWN
                || supportedPlatforms.isEmpty()
                || supportedPlatforms.contains(query.platform());
    }

    default List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
        return List.of();
    }

    default Optional<ExtensionCatalogDetails> getDetails(String projectId,
                                                         ExtensionCatalogQuery query) throws IOException {
        return Optional.empty();
    }

    default Optional<ExtensionDownloadPlan> resolveDownload(String projectId,
                                                            String versionId,
                                                            Server server) throws IOException {
        return Optional.empty();
    }

    default List<ExtensionUpdateCandidate> findUpdates(Server server,
                                                       List<ServerExtension> installedExtensions) throws IOException {
        return List.of();
    }

    default List<ExtensionCatalogEntry> search(String query,
                                               ServerPlatform platform,
                                               ServerExtensionType extensionType) throws IOException {
        return search(new ExtensionCatalogQuery(query, platform, extensionType, null, 20));
    }

    default List<ExtensionCatalogEntry> findCompatible(ServerPlatform platform,
                                                       ServerExtensionType extensionType,
                                                       Set<String> installedExtensionIds) throws IOException {
        return List.of();
    }
}
