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

final class CurseForgeExtensionCatalogProvider extends AbstractStubExtensionCatalogProvider {
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
    }

    @Override
    public Set<ExtensionCatalogCapability> getCapabilities() {
        return Set.of();
    }

    @Override
    public Set<ServerExtensionType> getSupportedExtensionTypes() {
        return Set.of();
    }

    @Override
    public Set<ServerPlatform> getSupportedPlatforms() {
        return Set.of();
    }

    @Override
    public String getLimitations() {
        return "Catalogo CurseForge no implementado con API real; el soporte actual de CurseForge se limita a import/export de manifest.";
    }

    @Override
    public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) {
        return List.of();
    }

    @Override
    public Optional<ExtensionCatalogDetails> getDetails(String projectId,
                                                        ExtensionCatalogQuery query) {
        return Optional.empty();
    }

    @Override
    public Optional<ExtensionDownloadPlan> resolveDownload(String projectId,
                                                           String versionId,
                                                           Server server) {
        return Optional.empty();
    }

    @Override
    public List<ExtensionUpdateCandidate> findUpdates(Server server,
                                                      List<ServerExtension> installedExtensions) throws IOException {
        return List.of();
    }
}
