package controlador.extensions;

import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface ExtensionCatalogProvider {
    String getProviderId();

    default boolean supportsSearch() {
        return false;
    }

    default List<ExtensionCatalogEntry> search(String query,
                                               ServerPlatform platform,
                                               ServerExtensionType extensionType) throws IOException {
        return List.of();
    }

    default List<ExtensionCatalogEntry> findCompatible(ServerPlatform platform,
                                                       ServerExtensionType extensionType,
                                                       Set<String> installedExtensionIds) throws IOException {
        return List.of();
    }
}
