package modelo.extensions;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum ServerPlatform {
    UNKNOWN("UNKNOWN", ServerLoader.UNKNOWN, ServerEcosystemType.UNKNOWN),
    VANILLA("VANILLA", ServerLoader.VANILLA, ServerEcosystemType.NONE),
    FORGE("FORGE", ServerLoader.FORGE, ServerEcosystemType.MODS),
    NEOFORGE("NEOFORGE", ServerLoader.NEOFORGE, ServerEcosystemType.MODS),
    FABRIC("FABRIC", ServerLoader.FABRIC, ServerEcosystemType.MODS),
    QUILT("QUILT", ServerLoader.QUILT, ServerEcosystemType.MODS),
    PAPER("PAPER", ServerLoader.PAPER, ServerEcosystemType.PLUGINS),
    SPIGOT("SPIGOT", ServerLoader.SPIGOT, ServerEcosystemType.PLUGINS),
    BUKKIT("BUKKIT", ServerLoader.BUKKIT, ServerEcosystemType.PLUGINS),
    PURPUR("PURPUR", ServerLoader.PAPER, ServerEcosystemType.PLUGINS),
    PUFFERFISH("PUFFERFISH", ServerLoader.PAPER, ServerEcosystemType.PLUGINS);

    private final String legacyTypeName;
    private final ServerLoader defaultLoader;
    private final ServerEcosystemType defaultEcosystemType;

    ServerPlatform(String legacyTypeName, ServerLoader defaultLoader, ServerEcosystemType defaultEcosystemType) {
        this.legacyTypeName = legacyTypeName;
        this.defaultLoader = defaultLoader;
        this.defaultEcosystemType = defaultEcosystemType;
    }

    public String getLegacyTypeName() {
        return legacyTypeName;
    }

    public ServerLoader getDefaultLoader() {
        return defaultLoader;
    }

    public ServerEcosystemType getDefaultEcosystemType() {
        return defaultEcosystemType;
    }

    public Set<ServerCapability> defaultCapabilities() {
        EnumSet<ServerCapability> capabilities = EnumSet.of(
                ServerCapability.CORE_SERVER,
                ServerCapability.WORLD_MANAGEMENT,
                ServerCapability.PLAYER_MANAGEMENT,
                ServerCapability.SERVER_CONFIGURATION,
                ServerCapability.CONSOLE_ACCESS,
                ServerCapability.PERFORMANCE_MONITORING
        );
        if (defaultEcosystemType == ServerEcosystemType.MODS || defaultEcosystemType == ServerEcosystemType.PLUGINS) {
            capabilities.add(ServerCapability.EXTENSIONS);
        }
        if (defaultEcosystemType == ServerEcosystemType.MODS) {
            capabilities.add(ServerCapability.MOD_EXTENSIONS);
        }
        if (defaultEcosystemType == ServerEcosystemType.PLUGINS) {
            capabilities.add(ServerCapability.PLUGIN_EXTENSIONS);
        }
        return capabilities;
    }

    public static ServerPlatform fromLegacyType(String legacyType) {
        if (legacyType == null || legacyType.isBlank()) {
            return UNKNOWN;
        }
        String normalized = legacyType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VANILLA" -> VANILLA;
            case "FORGE" -> FORGE;
            case "NEOFORGE" -> NEOFORGE;
            case "FABRIC" -> FABRIC;
            case "QUILT" -> QUILT;
            case "PAPER" -> PAPER;
            case "SPIGOT" -> SPIGOT;
            case "BUKKIT" -> BUKKIT;
            case "PURPUR" -> PURPUR;
            case "PUFFERFISH" -> PUFFERFISH;
            default -> UNKNOWN;
        };
    }
}
