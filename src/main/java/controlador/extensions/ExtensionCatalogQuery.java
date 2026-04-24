package controlador.extensions;

import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

public record ExtensionCatalogQuery(
        String queryText,
        ServerPlatform platform,
        ServerExtensionType extensionType,
        String minecraftVersion,
        int limit
) {
    public ExtensionCatalogQuery {
        platform = platform == null ? ServerPlatform.UNKNOWN : platform;
        extensionType = extensionType == null ? ServerExtensionType.UNKNOWN : extensionType;
        limit = limit <= 0 ? 20 : limit;
    }
}
