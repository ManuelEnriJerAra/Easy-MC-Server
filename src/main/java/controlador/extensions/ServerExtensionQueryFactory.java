package controlador.extensions;

import modelo.Server;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;

final class ServerExtensionQueryFactory {
    private ServerExtensionQueryFactory() {
    }

    static ExtensionCatalogQuery forServer(Server server, String queryText, int limit) {
        if (server == null) {
            return new ExtensionCatalogQuery(queryText, ServerPlatform.UNKNOWN, ServerExtensionType.UNKNOWN, null, limit);
        }
        return new ExtensionCatalogQuery(
                queryText,
                resolvedPlatform(server),
                resolvedExtensionType(server),
                normalize(server.getVersion()),
                limit
        );
    }

    static String resolveLoaderFacet(Server server, ExtensionCatalogQuery query) {
        if (server != null && server.getLoader() != null && server.getLoader() != ServerLoader.UNKNOWN && server.getLoader() != ServerLoader.NONE) {
            return switch (server.getLoader()) {
                case FORGE -> "forge";
                case NEOFORGE -> "neoforge";
                case FABRIC -> "fabric";
                case QUILT -> "quilt";
                case PAPER -> "paper";
                case SPIGOT -> "spigot";
                case BUKKIT -> "bukkit";
                default -> platformFacet(query == null ? ServerPlatform.UNKNOWN : query.platform());
            };
        }
        return platformFacet(query == null ? ServerPlatform.UNKNOWN : query.platform());
    }

    private static ServerPlatform resolvedPlatform(Server server) {
        return server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
    }

    private static ServerExtensionType resolvedExtensionType(Server server) {
        ServerEcosystemType ecosystemType = server.getEcosystemType() == null ? ServerEcosystemType.UNKNOWN : server.getEcosystemType();
        return switch (ecosystemType) {
            case MODS -> ServerExtensionType.MOD;
            case PLUGINS -> ServerExtensionType.PLUGIN;
            default -> ServerExtensionType.UNKNOWN;
        };
    }

    private static String platformFacet(ServerPlatform platform) {
        if (platform == null) {
            return null;
        }
        return switch (platform) {
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case FABRIC -> "fabric";
            case QUILT -> "quilt";
            case PAPER, PURPUR, PUFFERFISH -> "paper";
            case SPIGOT -> "spigot";
            case BUKKIT -> "bukkit";
            default -> null;
        };
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
