package controlador.extensions;

import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.util.List;
import java.util.Set;

final class CurseForgeExtensionCatalogProvider extends AbstractStubExtensionCatalogProvider {
    private final boolean apiKeyConfigured;

    CurseForgeExtensionCatalogProvider() {
        this(null);
    }

    CurseForgeExtensionCatalogProvider(String apiKey) {
        super("curseforge", "CurseForge", ExtensionSourceType.CURSEFORGE, List.of(
                createDetails(
                        "curseforge",
                        ExtensionSourceType.CURSEFORGE,
                        "jei",
                        "Just Enough Items",
                        "mezz",
                        "19.8.2",
                        "Catalogo visual de recetas preparado para Forge y NeoForge.",
                        ServerExtensionType.MOD,
                        Set.of(ServerPlatform.FORGE, ServerPlatform.NEOFORGE),
                        Set.of("1.21.1"),
                        "https://www.curseforge.com/minecraft/mc-mods/jei",
                        "https://www.curseforge.com/minecraft/mc-mods/jei/issues",
                        "All Rights Reserved",
                        Set.of("utility", "recipes"),
                        "jei-19.8.2.jar"
                ),
                createDetails(
                        "curseforge",
                        ExtensionSourceType.CURSEFORGE,
                        "worldedit-forge",
                        "WorldEdit Forge",
                        "EngineHub",
                        "7.3.2",
                        "Herramientas de edicion de mundo para servidores modded.",
                        ServerExtensionType.MOD,
                        Set.of(ServerPlatform.FORGE, ServerPlatform.NEOFORGE),
                        Set.of("1.20.6", "1.21.1"),
                        "https://www.curseforge.com/minecraft/mc-mods/worldedit-forge",
                        "https://www.curseforge.com/minecraft/mc-mods/worldedit-forge/issues",
                        "All Rights Reserved",
                        Set.of("admin", "world"),
                        "worldedit-forge-7.3.2.jar"
                )
        ));
        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Set<ExtensionCatalogCapability> getCapabilities() {
        return apiKeyConfigured
                ? Set.of(
                ExtensionCatalogCapability.SEARCH,
                ExtensionCatalogCapability.DETAILS,
                ExtensionCatalogCapability.DOWNLOAD,
                ExtensionCatalogCapability.UPDATES
        )
                : Set.of();
    }
}
