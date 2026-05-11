package controlador.extensions;

import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

public record ExtensionCatalogQuery(
        String queryText,
        ServerPlatform platform,
        ServerExtensionType extensionType,
        String minecraftVersion,
        int limit,
        String sortOrder,
        ExtensionSideFilter sideFilter,
        String providerId
) {
    public ExtensionCatalogQuery(String queryText,
                                 ServerPlatform platform,
                                 ServerExtensionType extensionType,
                                 String minecraftVersion,
                                 int limit) {
        this(queryText, platform, extensionType, minecraftVersion, limit, null, ExtensionSideFilter.ANY, null);
    }

    public ExtensionCatalogQuery(String queryText,
                                 ServerPlatform platform,
                                 ServerExtensionType extensionType,
                                 String minecraftVersion,
                                 int limit,
                                 String sortOrder) {
        this(queryText, platform, extensionType, minecraftVersion, limit, sortOrder, ExtensionSideFilter.ANY, null);
    }

    public ExtensionCatalogQuery(String queryText,
                                 ServerPlatform platform,
                                 ServerExtensionType extensionType,
                                 String minecraftVersion,
                                 int limit,
                                 String sortOrder,
                                 ExtensionSideFilter sideFilter) {
        this(queryText, platform, extensionType, minecraftVersion, limit, sortOrder, sideFilter, null);
    }

    public ExtensionCatalogQuery {
        platform = platform == null ? ServerPlatform.UNKNOWN : platform;
        extensionType = extensionType == null ? ServerExtensionType.UNKNOWN : extensionType;
        limit = limit <= 0 ? 20 : limit;
        sideFilter = sideFilter == null ? ExtensionSideFilter.ANY : sideFilter;
    }
}
