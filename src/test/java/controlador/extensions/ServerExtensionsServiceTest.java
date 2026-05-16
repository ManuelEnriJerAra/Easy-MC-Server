package controlador.extensions;

import com.sun.net.httpserver.HttpServer;
import modelo.Server;
import modelo.extensions.ExtensionInstallState;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionRemoteDependency;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerExtensionsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldPersistUntrackedMetadataForManualInstalls() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Path forgeDir = tempDir.resolve("forge-server");
        createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);

        Path sourceJar = tempDir.resolve("manual-mod.jar");
        createJar(
                sourceJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader="javafml"
                        loaderVersion="[47,)"
                        [[mods]]
                        modId="trackedexample"
                        version="1.0.0"
                        displayName="Tracked Example"
                        authors="Dora"
                        logoFile="assets/tracked/icon.png"
                        displayURL="https://example.test/tracked"
                        issueTrackerURL="https://example.test/tracked/issues"
                        license="MIT"
                        description="Forge test mod"
                        [[dependencies.trackedexample]]
                        modId="minecraft"
                        mandatory=true
                        versionRange="[1.20.1,1.21)"
                        """,
                        "META-INF/MANIFEST.MF",
                        """
                        Manifest-Version: 1.0
                        Implementation-Title: Manifest fallback
                        Implementation-Vendor: Manifest Author
                        """,
                        "assets/tracked/icon.png",
                        """
                        fake image bytes
                        """
                )
        );

        ServerExtension installed = service.installManualJar(server, sourceJar);
        ExtensionLocalMetadata metadata = installed.getLocalMetadata();

        assertThat(metadata.getInstalledVersion()).isEqualTo("1.0.0");
        assertThat(metadata.getAuthors()).contains("Dora");
        assertThat(metadata.getSupportedLoaders()).contains("Forge", "javafml");
        assertThat(metadata.getSupportedMinecraftVersions()).contains("[1.20.1,1.21)");
        assertThat(metadata.getLocalDependencyDescriptions()).anySatisfy(dependency ->
                assertThat(dependency).contains("minecraft").contains("[1.20.1,1.21)"));
        assertThat(metadata.getLocalIconPath()).isEqualTo("assets/tracked/icon.png");
        assertThat(metadata.getLocalIconUrl()).startsWith("jar:file:").endsWith("!/assets/tracked/icon.png");
        assertThat(metadata.getWebsiteUrl()).isEqualTo("https://example.test/tracked");
        assertThat(metadata.getIssuesUrl()).isEqualTo("https://example.test/tracked/issues");
        assertThat(metadata.getLicenseName()).isEqualTo("MIT");
        assertThat(metadata.getEmbeddedMetadataFiles()).contains("META-INF/mods.toml", "META-INF/MANIFEST.MF");
        assertThat(metadata.getManifestAttributes()).containsEntry("Implementation-Title", "Manifest fallback");
        assertThat(metadata.getKnownRemoteVersion()).isNull();
        assertThat(metadata.getKnownRemoteVersionId()).isNull();
        assertThat(metadata.getUpdateState()).isEqualTo(ExtensionUpdateState.UNTRACKED);
        assertThat(metadata.getUpdateMessage()).contains("sin origen remoto");
    }

    @Test
    void shouldPersistKnownRemoteVersionAndUpdateState() {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = new Server();
        ServerExtension installed = new ServerExtension();
        installed.setId("ext-1");
        installed.setDisplayName("ViaVersion");
        installed.setVersion("5.2.1");
        installed.setExtensionType(ServerExtensionType.PLUGIN);
        installed.setPlatform(ServerPlatform.PAPER);

        ExtensionSource source = new ExtensionSource();
        source.setType(ExtensionSourceType.HANGAR);
        source.setProvider("hangar");
        source.setProjectId("31");
        source.setVersionId("old");
        installed.setSource(source);
        server.setExtensions(List.of(installed));

        ExtensionCatalogVersion latest = new ExtensionCatalogVersion(
                "hangar",
                "31",
                "24490",
                "5.9.0-SNAPSHOT+976",
                "5.9.0-SNAPSHOT+976",
                java.util.Set.of(ServerPlatform.PAPER),
                java.util.Set.of("1.21.1"),
                "Fixes",
                "ViaVersion.jar",
                "https://hangarcdn.papermc.io/example.jar",
                1L
        );

        boolean changed = service.applyUpdateMetadata(server, List.of(new ExtensionUpdateCandidate(
                "hangar",
                "31",
                installed,
                latest,
                true,
                "Hay una version compatible mas reciente en Hangar."
        )));

        assertThat(changed).isTrue();
        assertThat(installed.getLocalMetadata().getInstalledVersion()).isEqualTo("5.2.1");
        assertThat(installed.getLocalMetadata().getKnownRemoteVersion()).isEqualTo("5.9.0-SNAPSHOT+976");
        assertThat(installed.getLocalMetadata().getKnownRemoteVersionId()).isEqualTo("24490");
        assertThat(installed.getLocalMetadata().getUpdateState()).isEqualTo(ExtensionUpdateState.UPDATE_AVAILABLE);
        assertThat(installed.getLocalMetadata().getUpdateMessage()).contains("mas reciente");
        assertThat(installed.getLocalMetadata().getLastCheckedForUpdatesAtEpochMillis()).isNotNull();
    }

    @Test
    void shouldPreferModsTomlLogoFileNameBeforeGenericPngFallbacks() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Path forgeDir = tempDir.resolve("forge-logo-server");
        createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);

        Path sourceJar = tempDir.resolve("konkrete.jar");
        createJar(
                sourceJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader="javafml"
                        loaderVersion="[47,)"
                        [[mods]]
                        modId="konkrete"
                        version="1.0.0"
                        displayName="Konkrete"
                        logoFile="konkrete.png"
                        description="Logo lookup test"
                        """,
                        "assets/random/icon.png",
                        "wrong generic icon",
                        "assets/konkrete/konkrete.png",
                        "declared icon"
                )
        );

        ServerExtension installed = service.installManualJar(server, sourceJar);

        assertThat(installed.getLocalMetadata().getLocalIconPath()).isEqualTo("assets/konkrete/konkrete.png");
        assertThat(installed.getSource().getIconUrl()).endsWith("!/assets/konkrete/konkrete.png");
    }

    @Test
    void shouldNotUseGenericInternalPngWhenDeclaredModsTomlLogoIsMissing() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Path forgeDir = tempDir.resolve("forge-missing-logo-server");
        createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.19.2\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);

        Path sourceJar = tempDir.resolve("konkrete-missing-logo.jar");
        createJar(
                sourceJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader="javafml"
                        loaderVersion="[41,)"
                        [[mods]]
                        modId="konkrete"
                        version="1.8.0"
                        displayName="Konkrete"
                        logoFile="konkrete.png"
                        description="Missing declared logo test"
                        """,
                        "assets/keksuccino/filechooser/back_icon.png",
                        "generic ui icon"
                )
        );

        ServerExtension installed = service.installManualJar(server, sourceJar);

        assertThat(installed.getLocalMetadata().getLocalIconPath()).isNull();
        assertThat(installed.getSource().getIconUrl()).isNull();
    }

    @Test
    void shouldParseForgeTomlInlineCommentsAndIgnoreUnsafeInternalTextureIcons() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Path forgeDir = tempDir.resolve("forge-jei-style-server");
        createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.19\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);

        Path sourceJar = tempDir.resolve("jei-style.jar");
        createJar(
                sourceJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader="javafml" #mandatory
                        loaderVersion="[41,)" #mandatory
                        license="The MIT License (MIT)"
                        #logoFile="examplemod.png" #optional
                        [[mods]]
                        modId="jei" #mandatory
                        version="11.1.1.239" #mandatory
                        displayName="Just Enough Items" #mandatory
                        displayURL="https://www.curseforge.com/minecraft/mc-mods/jei" #optional
                        authors="mezz" #optional
                        description='''
                        JEI is an item and recipe viewing mod.
                        '''
                        [[dependencies.jei]]
                        modId="minecraft" #mandatory
                        mandatory=true #mandatory
                        versionRange="[1.19, 1.20)" #mandatory
                        """,
                        "assets/jei/textures/gui/icons/info.png",
                        "generic ui icon"
                )
        );

        ServerExtension installed = service.installManualJar(server, sourceJar);

        assertThat(installed.getDisplayName()).isEqualTo("Just Enough Items");
        assertThat(installed.getDescription()).contains("JEI is an item");
        assertThat(installed.getLocalMetadata().getLocalIconPath()).isNull();
        assertThat(installed.getSource().getIconUrl()).isNull();
    }

    @Test
    void shouldDetectExactInstalledCatalogExtension() {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        ServerExtension installed = trackedExtension("ext-1", "ViaVersion", "ViaVersion.jar", "5.2.1", "hangar", "31", "24490");
        server.setExtensions(List.of(installed));

        ExtensionCatalogEntry entry = new ExtensionCatalogEntry(
                "hangar",
                "31",
                "24490",
                "ViaVersion",
                "PaperMC",
                "5.2.1",
                "Protocol compatibility",
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                java.util.Set.of(ServerPlatform.PAPER),
                java.util.Set.of("1.21.1"),
                null,
                null,
                null,
                0L
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, entry);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.INSTALLED_EXACT);
        assertThat(resolution.alreadyInstalled()).isTrue();
        assertThat(resolution.exactVersionInstalled()).isTrue();
        assertThat(resolution.blocksInstall()).isTrue();
    }

    @Test
    void shouldDetectAvailableUpdateForTrackedExtension() {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        ServerExtension installed = trackedExtension("ext-1", "ViaVersion", "ViaVersion.jar", "5.2.1", "hangar", "31", "old");
        server.setExtensions(List.of(installed));

        ExtensionCatalogEntry entry = new ExtensionCatalogEntry(
                "hangar",
                "31",
                "24490",
                "ViaVersion",
                "PaperMC",
                "5.9.0",
                "Protocol compatibility",
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                java.util.Set.of(ServerPlatform.PAPER),
                java.util.Set.of("1.21.1"),
                null,
                null,
                null,
                0L
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, entry);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.UPDATE_AVAILABLE);
        assertThat(resolution.updateAvailable()).isTrue();
        assertThat(resolution.message()).contains("5.2.1").contains("5.9.0");
    }

    @Test
    void shouldDetectFileNameConflictForDifferentExtension() {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        ServerExtension installed = trackedExtension("ext-1", "OtherPlugin", "shared.jar", "1.0.0", "hangar", "999", "old");
        server.setExtensions(List.of(installed));

        ExtensionDownloadPlan plan = new ExtensionDownloadPlan(
                "modrinth",
                "abc",
                "v2",
                "2.0.0",
                null,
                "shared.jar",
                "https://example.test/shared.jar",
                ExtensionSourceType.MODRINTH,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.21.1",
                true,
                "Ready"
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, plan);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.FILE_NAME_CONFLICT);
        assertThat(resolution.fileNameConflict()).isTrue();
        assertThat(resolution.message()).contains("ocupa ese archivo");
    }

    @Test
    void shouldDetectManualInstallWithIncompleteMetadata() {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        ServerExtension installed = new ServerExtension();
        installed.setId("ext-1");
        installed.setDisplayName("ViaVersion");
        installed.setVersion("5.2.1");
        installed.setFileName("ViaVersion.jar");
        installed.setExtensionType(ServerExtensionType.PLUGIN);
        installed.setPlatform(ServerPlatform.PAPER);

        ExtensionSource source = new ExtensionSource();
        source.setType(ExtensionSourceType.MANUAL);
        installed.setSource(source);

        ExtensionLocalMetadata metadata = new ExtensionLocalMetadata();
        metadata.setFileName("ViaVersion.jar");
        metadata.setInstalledVersion("5.2.1");
        installed.setLocalMetadata(metadata);

        server.setExtensions(List.of(installed));

        ExtensionCatalogEntry entry = new ExtensionCatalogEntry(
                "hangar",
                "31",
                "24490",
                "ViaVersion",
                "PaperMC",
                "5.9.0",
                "Protocol compatibility",
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                java.util.Set.of(ServerPlatform.PAPER),
                java.util.Set.of("1.21.1"),
                null,
                null,
                null,
                0L
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, entry);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.INSTALLED_WITH_INCOMPLETE_METADATA);
        assertThat(resolution.incompleteMetadataMatch()).isTrue();
        assertThat(resolution.message()).contains("metadata local").contains("manual");
    }

    @Test
    void shouldScanForgeModDescriptorWithStructuredDependencies() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("forge-scan", ServerPlatform.FORGE);
        Path modJar = serverDir(server).resolve("mods").resolve("example-forge.jar");
        createJar(modJar, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                [[mods]]
                modId="example"
                version="1.2.3"
                displayName="Example Forge Mod"
                authors="Forge Author"
                description="Forge descriptor"
                [[dependencies.example]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.20.1,1.21)"
                [[dependencies.example]]
                modId="jei"
                mandatory=true
                versionRange="[15,)"
                """));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).singleElement().satisfies(extension -> {
            assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.MOD);
            assertThat(extension.getPlatform()).isEqualTo(ServerPlatform.FORGE);
            assertThat(extension.getDisplayName()).isEqualTo("Example Forge Mod");
            assertThat(extension.getLocalMetadata().getSupportedLoaders()).contains("Forge", "javafml");
            assertThat(extension.getLocalMetadata().getSupportedMinecraftVersions()).contains("[1.20.1,1.21)");
            assertDependency(extension, "jei", true, "required");
            assertThat(extension.getLocalMetadata().getDependencies())
                    .extracting(ExtensionRemoteDependency::getProjectId)
                    .doesNotContain("minecraft");
        });
    }

    @Test
    void shouldReleaseJarAfterDetectingInstalledExtension() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("forge-release-after-scan", ServerPlatform.FORGE);
        Path modJar = serverDir(server).resolve("mods").resolve("release-me.jar");
        createJar(modJar, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                [[mods]]
                modId="release_me"
                version="1.0.0"
                displayName="Release Me"
                """));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);
        Files.delete(modJar);
        List<ServerExtension> afterExternalDelete = service.detectInstalledExtensions(server);

        assertThat(detected).singleElement()
                .extracting(ServerExtension::getDisplayName)
                .isEqualTo("Release Me");
        assertThat(modJar).doesNotExist();
        assertThat(afterExternalDelete).isEmpty();
    }

    @Test
    void shouldScanFabricModDescriptorWithDependencies() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("fabric-scan", ServerPlatform.FABRIC);
        Path modJar = serverDir(server).resolve("mods").resolve("fabric-example.jar");
        createJar(modJar, Map.of("fabric.mod.json", """
                {
                  "schemaVersion": 1,
                  "id": "fabric_example",
                  "version": "2.0.0",
                  "name": "Fabric Example",
                  "description": "Fabric descriptor",
                  "authors": ["Fabric Author"],
                  "environment": "server",
                  "depends": {
                    "fabricloader": ">=0.15.0",
                    "minecraft": ">=1.20",
                    "cloth-config": "*"
                  },
                  "recommends": {
                    "modmenu": "*"
                  }
                }
                """));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).singleElement().satisfies(extension -> {
            assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.MOD);
            assertThat(extension.getPlatform()).isEqualTo(ServerPlatform.FABRIC);
            assertThat(extension.getLocalMetadata().getSupportedLoaders()).contains("Fabric");
            assertThat(extension.getLocalMetadata().getServerSide()).isEqualTo("server");
            assertDependency(extension, "cloth-config", true, "depends");
            assertDependency(extension, "modmenu", false, "recommends");
        });
    }

    @Test
    void shouldScanQuiltModDescriptorWithDependencies() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("quilt-scan", ServerPlatform.QUILT);
        Path modJar = serverDir(server).resolve("mods").resolve("quilt-example.jar");
        createJar(modJar, Map.of("quilt.mod.json", """
                {
                  "quilt_loader": {
                    "id": "quilt_example",
                    "version": "3.0.0",
                    "metadata": {
                      "name": "Quilt Example",
                      "description": "Quilt descriptor",
                      "contributors": {"Quilt Author": "Owner"}
                    },
                    "depends": [
                      {"id": "minecraft", "versions": ">=1.20"},
                      {"id": "qsl", "versions": "*"}
                    ]
                  }
                }
                """));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).singleElement().satisfies(extension -> {
            assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.MOD);
            assertThat(extension.getPlatform()).isEqualTo(ServerPlatform.QUILT);
            assertThat(extension.getDisplayName()).isEqualTo("Quilt Example");
            assertThat(extension.getLocalMetadata().getSupportedLoaders()).contains("Quilt");
            assertDependency(extension, "qsl", true, "depends");
        });
    }

    @Test
    void shouldScanBukkitPluginDescriptorWithDependencies() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("bukkit-scan", ServerPlatform.PAPER);
        Path pluginJar = serverDir(server).resolve("plugins").resolve("ViaVersion.jar");
        createJar(pluginJar, Map.of("plugin.yml", """
                name: ViaVersion
                version: 5.2.1
                description: Protocol compatibility plugin
                author: Via
                api-version: '1.21'
                depend: [ProtocolLib]
                softdepend:
                  - LuckPerms
                  - Vault
                loadbefore:
                  - Geyser-Spigot
                """));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).singleElement().satisfies(extension -> {
            assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.PLUGIN);
            assertThat(extension.getPlatform()).isEqualTo(ServerPlatform.PAPER);
            assertThat(extension.getDisplayName()).isEqualTo("ViaVersion");
            assertThat(extension.getLocalMetadata().getSupportedLoaders()).contains("Bukkit");
            assertDependency(extension, "ProtocolLib", true, "depend");
            assertDependency(extension, "LuckPerms", false, "softdepend");
            assertDependency(extension, "Geyser-Spigot", false, "loadbefore");
        });
    }

    @Test
    void shouldScanPaperPluginDescriptorWithModernDependencies() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("paper-scan", ServerPlatform.PAPER);
        Path pluginJar = serverDir(server).resolve("plugins").resolve("PaperOnly.jar");
        createJar(pluginJar, Map.of("paper-plugin.yml", """
                name: PaperOnly
                version: 1.0.0
                main: test.Plugin
                description: Paper plugin
                authors: [Alice, Bob]
                api-version: '1.21'
                dependencies:
                  server:
                    LuckPerms:
                      load: BEFORE
                      required: true
                    Vault:
                      required: false
                """));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).singleElement().satisfies(extension -> {
            assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.PLUGIN);
            assertThat(extension.getPlatform()).isEqualTo(ServerPlatform.PAPER);
            assertThat(extension.getLocalMetadata().getSupportedLoaders()).contains("Paper");
            assertThat(extension.getLocalMetadata().getAuthors()).contains("Alice", "Bob");
            assertDependency(extension, "LuckPerms", true, "server:before");
            assertDependency(extension, "Vault", false, "server");
        });
    }

    @Test
    void shouldInstallModsIntoModsDirectory() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-mods", ServerPlatform.FORGE);
        Path sourceJar = tempDir.resolve("manual-forge.jar");
        createJar(sourceJar, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                [[mods]]
                modId="manualforge"
                version="1.0.0"
                displayName="Manual Forge"
                """));

        ServerExtension installed = service.installManualJar(server, sourceJar);

        assertThat(serverDir(server).resolve("mods").resolve("manual-forge.jar")).isRegularFile();
        assertThat(serverDir(server).resolve("plugins").resolve("manual-forge.jar")).doesNotExist();
        assertThat(installed.getLocalMetadata().getRelativePath()).isEqualTo("mods/manual-forge.jar");
    }

    @Test
    void shouldInstallPluginsIntoPluginsDirectory() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-plugins", ServerPlatform.PAPER);
        Path sourceJar = tempDir.resolve("manual-plugin.jar");
        createJar(sourceJar, Map.of("plugin.yml", """
                name: ManualPlugin
                version: 1.0.0
                main: test.Plugin
                api-version: '1.21'
                """));

        ServerExtension installed = service.installManualJar(server, sourceJar);

        assertThat(serverDir(server).resolve("plugins").resolve("manual-plugin.jar")).isRegularFile();
        assertThat(serverDir(server).resolve("mods").resolve("manual-plugin.jar")).doesNotExist();
        assertThat(installed.getExtensionType()).isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(installed.getLocalMetadata().getRelativePath()).isEqualTo("plugins/manual-plugin.jar");
    }

    @Test
    void shouldTreatBukkitApiVersionAsMinimumServerVersion() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-legacy-api-plugin", ServerPlatform.PAPER);
        server.setVersion("1.21.1");
        Path sourceJar = tempDir.resolve("legacy-api-plugin.jar");
        createJar(sourceJar, Map.of("plugin.yml", """
                name: LegacyApiPlugin
                version: 1.0.0
                main: test.Plugin
                api-version: '1.13'
                """));

        ExtensionCompatibilityReport report = service.validateCompatibility(server, sourceJar);
        ServerExtension installed = service.installManualJar(server, sourceJar);

        assertThat(report.status()).isNotEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        assertThat(installed.getLocalMetadata().getMinecraftVersionConstraint()).isEqualTo(">=1.13");
        assertThat(serverDir(server).resolve("plugins").resolve("legacy-api-plugin.jar")).isRegularFile();
    }

    @Test
    void shouldIgnoreNonMinecraftServerVersionDuringJarCompatibilityCheck() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("invalid-server-version", ServerPlatform.PAPER);
        server.setVersion("custom-build");
        Path sourceJar = tempDir.resolve("legacy-api-plugin-invalid-server-version.jar");
        createJar(sourceJar, Map.of("plugin.yml", """
                name: LegacyApiPlugin
                version: 1.0.0
                main: test.Plugin
                api-version: '1.13'
                """));

        ExtensionCompatibilityReport report = service.validateCompatibility(server, sourceJar);

        assertThat(report.status()).isNotEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        assertThat(report.reasons()).anyMatch(detail -> detail.contains("No se conoce"));
    }

    @Test
    void shouldBlockCatalogModsOnPluginServersBeforeDownload() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("block-mod-on-plugin", ServerPlatform.PAPER);
        ExtensionDownloadPlan plan = new ExtensionDownloadPlan(
                "modrinth",
                "fabric-api",
                "v1",
                "1.0.0",
                null,
                "fabric-api.jar",
                "https://example.test/fabric-api.jar",
                ExtensionSourceType.MODRINTH,
                ServerExtensionType.MOD,
                ServerPlatform.FABRIC,
                "1.21.1",
                true,
                "Ready"
        );
        AtomicBoolean downloaderCalled = new AtomicBoolean(false);

        assertThat(service.evaluateCatalogInstallation(server, plan).state())
                .isEqualTo(ExtensionInstallResolutionState.INCOMPATIBLE);
        assertThatThrownBy(() -> service.installCatalogDownload(server, plan, (url, destination) -> downloaderCalled.set(true)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("plugins");
        assertThat(downloaderCalled).isFalse();
    }

    @Test
    void shouldBlockCatalogPluginsOnModServersBeforeDownload() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("block-plugin-on-mod", ServerPlatform.FORGE);
        ExtensionDownloadPlan plan = new ExtensionDownloadPlan(
                "hangar",
                "viaversion",
                "v1",
                "5.0.0",
                null,
                "ViaVersion.jar",
                "https://example.test/ViaVersion.jar",
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.21.1",
                true,
                "Ready"
        );
        AtomicBoolean downloaderCalled = new AtomicBoolean(false);

        assertThat(service.evaluateCatalogInstallation(server, plan).state())
                .isEqualTo(ExtensionInstallResolutionState.INCOMPATIBLE);
        assertThatThrownBy(() -> service.installCatalogDownload(server, plan, (url, destination) -> downloaderCalled.set(true)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("mods");
        assertThat(downloaderCalled).isFalse();
    }

    @Test
    void shouldBlockManualModsAndPluginsOnVanillaServers() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("vanilla-extension-block", ServerPlatform.VANILLA);
        Path sourceJar = tempDir.resolve("manual-forge.jar");
        createJar(sourceJar, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                [[mods]]
                modId="manualforge"
                version="1.0.0"
                """));

        assertThat(service.validateCompatibility(server, sourceJar).status())
                .isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        assertThatThrownBy(() -> service.installManualJar(server, sourceJar))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Vanilla");
    }

    @Test
    void shouldBlockManualInstallsWhenServerEcosystemIsUnknown() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Path serverDir = tempDir.resolve("unknown-extension-block");
        Files.createDirectories(serverDir);
        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setPlatform(ServerPlatform.UNKNOWN);
        server.setVersion("1.21.1");
        Path sourceJar = tempDir.resolve("manual-plugin.jar");
        createJar(sourceJar, Map.of("plugin.yml", """
                name: ManualPlugin
                version: 1.0.0
                main: test.Plugin
                api-version: '1.21'
                """));

        assertThat(service.validateCompatibility(server, sourceJar).status())
                .isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        assertThatThrownBy(() -> service.installManualJar(server, sourceJar))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ecosistema");
    }

    @Test
    void shouldMarkPluginCatalogPlanIncompatibleWhenMinecraftVersionDoesNotMatch() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("plugin-version-mismatch", ServerPlatform.PAPER);
        server.setVersion("1.21.1");
        ExtensionDownloadPlan plan = new ExtensionDownloadPlan(
                "hangar",
                "paperonly",
                "v1",
                "1.0.0",
                null,
                "PaperOnly.jar",
                "https://example.test/PaperOnly.jar",
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.20.4",
                true,
                "Ready"
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, plan);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.INCOMPATIBLE);
        assertThat(resolution.message()).contains("Minecraft 1.21.1");
    }

    @Test
    void shouldMarkCatalogPlanIncompatibleWhenModernNumericMinecraftVersionDoesNotMatch() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("plugin-modern-version-mismatch", ServerPlatform.PAPER);
        server.setVersion("26.1.1");
        ExtensionDownloadPlan plan = new ExtensionDownloadPlan(
                "modrinth",
                "nova-framework",
                "nova-2612",
                "0.23.0",
                null,
                "Nova-0.23.0+MC-26.1.2.jar",
                "https://example.test/Nova.jar",
                ExtensionSourceType.MODRINTH,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "26.1.2",
                true,
                "Ready"
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, plan);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.INCOMPATIBLE);
        assertThat(resolution.message()).contains("Minecraft 26.1.1");
    }

    @Test
    void shouldNotBlockCatalogPlanForOptionalPluginDependencies() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("plugin-optional-dependency", ServerPlatform.PAPER);
        ExtensionDependency optionalDependency = new ExtensionDependency(
                "hangar",
                "Vault",
                null,
                "Vault",
                "optional",
                false
        );
        ExtensionDownloadPlan plan = new ExtensionDownloadPlan(
                "hangar",
                "economy",
                "v1",
                "Economy",
                "Paper",
                "Economy plugin",
                "1.0.0",
                null,
                "Economy.jar",
                "https://example.test/Economy.jar",
                null,
                null,
                null,
                null,
                0L,
                "unsupported",
                "required",
                java.util.Set.of(),
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.21.1",
                true,
                "Ready",
                List.of(optionalDependency)
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, plan);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.AVAILABLE);
    }

    @Test
    void shouldRefreshStaleRemoteDependencyMetadataFromDetectedPluginDescriptor() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("plugin-ignore-viafabric-status", ServerPlatform.PAPER);
        Files.createDirectories(serverDir(server).resolve("plugins"));
        createJar(serverDir(server).resolve("plugins").resolve("ViaVersion.jar"), Map.of("plugin.yml", """
                name: ViaVersion
                version: 5.0.0
                main: com.viaversion.viaversion.bukkit.BukkitPlugin
                api-version: '1.21'
                depend: [ViaBackwards]
                """));

        ServerExtension extension = new ServerExtension();
        extension.setDisplayName("ViaVersion");
        extension.setFileName("ViaVersion.jar");
        extension.setInstallState(ExtensionInstallState.INSTALLED);
        ExtensionLocalMetadata metadata = new ExtensionLocalMetadata();
        metadata.setRelativePath("plugins/ViaVersion.jar");
        metadata.setFileName("ViaVersion.jar");
        ExtensionRemoteDependency viaFabric = new ExtensionRemoteDependency();
        viaFabric.setProviderId("modrinth");
        viaFabric.setProjectId("viafabric");
        viaFabric.setDisplayName("VIAFABRIC");
        viaFabric.setDependencyType("required");
        viaFabric.setRequired(true);
        metadata.setDependencies(List.of(viaFabric));
        extension.setLocalMetadata(metadata);
        server.setExtensions(List.of(extension));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).hasSize(1);
        assertThat(detected.getFirst().getLocalMetadata().getDependencies())
                .extracting(ExtensionRemoteDependency::getProjectId)
                .containsExactly("viafabric", "ViaBackwards");
    }

    @Test
    void shouldRecognizeCatalogDependencyAfterDescriptorRescanUsesLocalId() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("dependency-local-id-match", ServerPlatform.FABRIC);
        createJar(serverDir(server).resolve("mods").resolve("ParentMod.jar"), Map.of("fabric.mod.json", """
                {
                  "schemaVersion": 1,
                  "id": "parent-mod",
                  "version": "1.0.0",
                  "name": "Parent Mod"
                }
                """));
        createJar(serverDir(server).resolve("mods").resolve("fabric-api.jar"), Map.of("fabric.mod.json", """
                {
                  "schemaVersion": 1,
                  "id": "fabric-api",
                  "version": "1.0.0",
                  "name": "Fabric API"
                }
                """));

        ServerExtension parent = new ServerExtension();
        parent.setDisplayName("Parent Mod");
        parent.setFileName("ParentMod.jar");
        parent.setInstallState(ExtensionInstallState.INSTALLED);
        parent.setExtensionType(ServerExtensionType.MOD);
        parent.setPlatform(ServerPlatform.FABRIC);
        ExtensionLocalMetadata parentMetadata = new ExtensionLocalMetadata();
        parentMetadata.setRelativePath("mods/ParentMod.jar");
        parentMetadata.setFileName("ParentMod.jar");
        ExtensionRemoteDependency fabricApi = new ExtensionRemoteDependency();
        fabricApi.setProviderId("modrinth");
        fabricApi.setProjectId("FabricAPI");
        fabricApi.setDisplayName("Fabric API");
        fabricApi.setDependencyType("required");
        fabricApi.setRequired(true);
        parentMetadata.setDependencies(List.of(fabricApi));
        parent.setLocalMetadata(parentMetadata);

        ServerExtension dependency = service.detectInstalledExtensions(server).stream()
                .filter(extension -> "fabric-api.jar".equals(extension.getFileName()))
                .findFirst()
                .orElseThrow();
        server.setExtensions(List.of(parent, dependency));

        InstalledExtensionStatus status = service.assessInstalledExtension(server, parent);

        assertThat(status.missingDependencies()).isEmpty();
    }

    @Test
    void shouldPreserveCatalogAndDescriptorDependenciesAcrossRescan() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("merge-catalog-and-local-dependencies", ServerPlatform.FORGE);
        Path modJar = serverDir(server).resolve("mods").resolve("ParentMod.jar");
        createJar(modJar, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[47,)"
                [[mods]]
                modId="parentmod"
                version="1.0.0"
                displayName="Parent Mod"
                [[dependencies.parentmod]]
                modId="fabric-api"
                mandatory=true
                type="required"
                """));

        ServerExtension extension = new ServerExtension();
        extension.setDisplayName("Parent Mod");
        extension.setFileName("ParentMod.jar");
        extension.setInstallState(ExtensionInstallState.INSTALLED);
        ExtensionLocalMetadata metadata = new ExtensionLocalMetadata();
        metadata.setRelativePath("mods/ParentMod.jar");
        metadata.setFileName("ParentMod.jar");
        ExtensionRemoteDependency catalogDependency = new ExtensionRemoteDependency();
        catalogDependency.setProviderId("modrinth");
        catalogDependency.setProjectId("fabric-api");
        catalogDependency.setDisplayName("Fabric API");
        catalogDependency.setDependencyType("required");
        catalogDependency.setRequired(true);
        metadata.setDependencies(List.of(catalogDependency));
        extension.setLocalMetadata(metadata);
        server.setExtensions(List.of(extension));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).hasSize(1);
        assertThat(detected.getFirst().getLocalMetadata().getDependencies())
                .extracting(ExtensionRemoteDependency::getProviderId, ExtensionRemoteDependency::getProjectId)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("modrinth", "fabric-api"),
                        org.assertj.core.groups.Tuple.tuple("forge", "fabric-api")
                );
    }

    @Test
    void shouldInstallModrinthPluginPlanUsingDownloadedJar() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-modrinth-plugin", ServerPlatform.PAPER);
        Path fakeDownload = tempDir.resolve("downloads").resolve("modrinth-plugin.jar");
        createJar(fakeDownload, Map.of("plugin.yml", """
                name: ModrinthPlugin
                version: 2.0.0
                main: test.Plugin
                api-version: '1.21'
                """));
        ExtensionDownloadPlan plan = catalogPluginPlan(
                "modrinth",
                "geyser",
                "gv1",
                "ModrinthPlugin.jar",
                "https://cdn.example.test/download"
        );

        ServerExtension installed = service.installCatalogDownload(server, plan,
                (url, destination) -> Files.copy(fakeDownload, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING));

        assertThat(installed.getDisplayName()).isEqualTo("ModrinthPlugin");
        assertThat(installed.getSource().getProvider()).isEqualTo("modrinth");
        assertThat(installed.getSource().getProjectId()).isEqualTo("geyser");
        assertThat(serverDir(server).resolve("plugins").resolve("ModrinthPlugin.jar")).isRegularFile();
    }

    @Test
    void shouldInstallHangarPluginPlanUsingDownloadedJar() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-hangar-plugin", ServerPlatform.PAPER);
        Path fakeDownload = tempDir.resolve("downloads").resolve("hangar-plugin.jar");
        createJar(fakeDownload, Map.of("plugin.yml", """
                name: HangarPlugin
                version: 5.9.0
                main: test.Plugin
                api-version: '1.21'
                """));
        ExtensionDownloadPlan plan = catalogPluginPlan(
                "hangar",
                "31",
                "24490",
                "HangarPlugin.jar",
                "https://hangar.example.test/download"
        );

        ServerExtension installed = service.installCatalogDownload(server, plan,
                (url, destination) -> Files.copy(fakeDownload, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING));

        assertThat(installed.getDisplayName()).isEqualTo("HangarPlugin");
        assertThat(installed.getSource().getProvider()).isEqualTo("hangar");
        assertThat(installed.getSource().getVersionId()).isEqualTo("24490");
        assertThat(serverDir(server).resolve("plugins").resolve("HangarPlugin.jar")).isRegularFile();
    }

    @Test
    void shouldPreserveCatalogPluginTypeWhenDetectedJarMetadataDisagrees() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-hangar-plugin-with-mod-marker", ServerPlatform.PAPER);
        Path fakeDownload = tempDir.resolve("downloads").resolve("hangar-plugin-mod-marker.jar");
        createJar(fakeDownload, Map.of(
                "META-INF/mods.toml", """
                        modLoader="javafml"
                        loaderVersion="[1,)"
                        license="MIT"
                        [[mods]]
                        modId="viabackwards"
                        version="5.0.0"
                        displayName="ViaBackwards"
                        """,
                "plugin.yml", """
                        name: ViaBackwards
                        version: 5.0.0
                        main: com.viaversion.viabackwards.BukkitPlugin
                        api-version: '1.21'
                        """
        ));
        ExtensionDownloadPlan plan = catalogPluginPlan(
                "hangar",
                "ViaBackwards",
                "vb1",
                "ViaBackwards.jar",
                "https://hangar.example.test/viabackwards"
        );

        ServerExtension installed = service.installCatalogDownload(server, plan,
                (url, destination) -> Files.copy(fakeDownload, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING));

        assertThat(installed.getExtensionType()).isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(installed.getPlatform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(installed.getSource().getProvider()).isEqualTo("hangar");
    }

    @Test
    void shouldPreferPluginDescriptorForHybridViaJarOnPaperServer() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("hybrid-via-plugin", ServerPlatform.PAPER);
        Files.createDirectories(serverDir(server).resolve("plugins"));
        Path pluginJar = serverDir(server).resolve("plugins").resolve("ViaBackwards.jar");
        createJar(pluginJar, Map.of(
                "META-INF/mods.toml", """
                        modLoader="javafml"
                        loaderVersion="[1,)"
                        [[mods]]
                        modId="viabackwards"
                        version="5.0.0"
                        displayName="ViaBackwards"
                        """,
                "fabric.mod.json", """
                        {
                          "id": "viabackwards",
                          "version": "5.0.0",
                          "name": "ViaBackwards"
                        }
                        """,
                "plugin.yml", """
                        name: ViaBackwards
                        version: 5.0.0
                        main: com.viaversion.viabackwards.BukkitPlugin
                        api-version: '1.21'
                        """
        ));

        List<ServerExtension> detected = service.detectInstalledExtensions(server);

        assertThat(detected).hasSize(1);
        assertThat(detected.getFirst().getDisplayName()).isEqualTo("ViaBackwards");
        assertThat(detected.getFirst().getExtensionType()).isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(detected.getFirst().getPlatform()).isEqualTo(ServerPlatform.PAPER);
    }

    @Test
    void shouldRejectHtmlJsonOrErrorDownloadBodiesBeforeInstall() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("reject-text-download", ServerPlatform.PAPER);
        ExtensionDownloadPlan plan = catalogPluginPlan(
                "modrinth",
                "bad",
                "v1",
                "BadPlugin.jar",
                "https://cdn.example.test/bad.jar"
        );

        assertThatThrownBy(() -> service.installCatalogDownload(server, plan,
                (url, destination) -> Files.writeString(destination.toPath(), "{\"error\":\"not found\"}", StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("JAR");
        assertThat(serverDir(server).resolve("plugins").resolve("BadPlugin.jar")).doesNotExist();
    }

    @Test
    void shouldFollowRedirectedDownloadUrlAndInstallJar() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-redirected-plugin", ServerPlatform.PAPER);
        Path fakeJar = tempDir.resolve("redirected-source.jar");
        createJar(fakeJar, Map.of("plugin.yml", """
                name: RedirectedPlugin
                version: 1.0.0
                main: test.Plugin
                api-version: '1.21'
                """));
        byte[] jarBytes = Files.readAllBytes(fakeJar);
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/artifact");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/artifact", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/java-archive");
            exchange.sendResponseHeaders(200, jarBytes.length);
            exchange.getResponseBody().write(jarBytes);
            exchange.close();
        });
        httpServer.start();
        try {
            ExtensionDownloadPlan plan = catalogPluginPlan(
                    "modrinth",
                    "redirect",
                    "v1",
                    "RedirectedPlugin.jar",
                    "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/redirect"
            );

            ServerExtension installed = service.installCatalogDownload(server, plan, new ExtensionArtifactDownloader());

            assertThat(installed.getDisplayName()).isEqualTo("RedirectedPlugin");
            assertThat(serverDir(server).resolve("plugins").resolve("RedirectedPlugin.jar")).isRegularFile();
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void shouldInstallValidJarFromNonJarUrlWhenProviderFilenameIsJar() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("install-nonjar-url-plugin", ServerPlatform.PAPER);
        Path fakeDownload = tempDir.resolve("downloads").resolve("opaque-download");
        createJar(fakeDownload, Map.of("plugin.yml", """
                name: OpaqueDownloadPlugin
                version: 1.0.0
                main: test.Plugin
                api-version: '1.21'
                """));
        ExtensionDownloadPlan plan = catalogPluginPlan(
                "hangar",
                "opaque",
                "v1",
                "OpaqueDownloadPlugin.jar",
                "https://example.test/api/download?platform=PAPER"
        );

        ServerExtension installed = service.installCatalogDownload(server, plan,
                (url, destination) -> Files.copy(fakeDownload, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING));

        assertThat(installed.getDisplayName()).isEqualTo("OpaqueDownloadPlugin");
        assertThat(serverDir(server).resolve("plugins").resolve("OpaqueDownloadPlugin.jar")).isRegularFile();
    }

    @Test
    void shouldUninstallPluginFromPluginsDirectory() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("uninstall-plugin", ServerPlatform.PAPER);
        Path pluginJar = serverDir(server).resolve("plugins").resolve("ViaVersion.jar");
        createJar(pluginJar, Map.of("plugin.yml", """
                name: ViaVersion
                version: 5.2.1
                main: test.Plugin
                api-version: '1.21'
                """));
        server.setExtensions(service.detectInstalledExtensions(server));

        boolean removed = service.removeExtension(server, server.getExtensions().getFirst());

        assertThat(removed).isTrue();
        assertThat(pluginJar).doesNotExist();
        assertThat(serverDir(server).resolve("mods").resolve("ViaVersion.jar")).doesNotExist();
    }

    @Test
    void shouldKeepPreviousEcosystemExtensionsVisibleAsIncompatibleAfterConversion() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = extensionServer("converted-mod-to-plugin", ServerPlatform.FORGE);
        Path modJar = serverDir(server).resolve("mods").resolve("ExampleMod.jar");
        createJar(modJar, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                [[mods]]
                modId="examplemod"
                version="1.0.0"
                displayName="Example Mod"
                """));
        server.setExtensions(service.detectInstalledExtensions(server));

        server.setPlatform(ServerPlatform.PAPER);
        List<ServerExtension> detectedAfterConversion = service.detectInstalledExtensions(server);

        assertThat(detectedAfterConversion)
                .anySatisfy(extension -> {
                    assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.MOD);
                    assertThat(extension.getLocalMetadata().getRelativePath()).isEqualTo("mods/ExampleMod.jar");
                    assertThat(service.assessInstalledExtension(server, extension).severity())
                            .isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
                });
        assertThat(modJar).isRegularFile();
    }

    private ServerExtension trackedExtension(String id,
                                             String displayName,
                                             String fileName,
                                             String version,
                                             String provider,
                                             String projectId,
                                             String versionId) {
        ServerExtension installed = new ServerExtension();
        installed.setId(id);
        installed.setDisplayName(displayName);
        installed.setVersion(version);
        installed.setFileName(fileName);
        installed.setExtensionType(ServerExtensionType.PLUGIN);
        installed.setPlatform(ServerPlatform.PAPER);

        ExtensionSource source = new ExtensionSource();
        source.setType(ExtensionSourceType.HANGAR);
        source.setProvider(provider);
        source.setProjectId(projectId);
        source.setVersionId(versionId);
        installed.setSource(source);

        ExtensionLocalMetadata metadata = new ExtensionLocalMetadata();
        metadata.setInstalledVersion(version);
        metadata.setKnownRemoteVersion(version);
        metadata.setKnownRemoteVersionId(versionId);
        metadata.setFileName(fileName);
        installed.setLocalMetadata(metadata);
        return installed;
    }

    private Server extensionServer(String name, ServerPlatform platform) throws Exception {
        Path serverDir = tempDir.resolve(name);
        Files.createDirectories(serverDir.resolve("mods"));
        Files.createDirectories(serverDir.resolve("plugins"));
        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setVersion("1.21.1");
        server.setPlatform(platform);
        return server;
    }

    private ExtensionDownloadPlan catalogPluginPlan(String providerId,
                                                    String projectId,
                                                    String versionId,
                                                    String fileName,
                                                    String downloadUrl) {
        return new ExtensionDownloadPlan(
                providerId,
                projectId,
                versionId,
                "1.0.0",
                null,
                fileName,
                downloadUrl,
                "hangar".equals(providerId) ? ExtensionSourceType.HANGAR : ExtensionSourceType.MODRINTH,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.21.1",
                true,
                "Ready"
        );
    }

    private Path serverDir(Server server) {
        return Path.of(server.getServerDir());
    }

    private void assertDependency(ServerExtension extension, String projectId, boolean required, String dependencyType) {
        assertThat(extension.getLocalMetadata().getDependencies())
                .anySatisfy(dependency -> {
                    assertThat(dependency.getProjectId()).isEqualTo(projectId);
                    assertThat(dependency.getDisplayName()).isEqualTo(projectId);
                    assertThat(dependency.getRequired()).isEqualTo(required);
                    assertThat(dependency.getDependencyType()).isEqualTo(dependencyType);
                });
    }

    private static Path createValidServerJar(Path serverDir,
                                             String jarName,
                                             String versionJson,
                                             String... extraEntries) throws Exception {
        Files.createDirectories(serverDir);
        Path jarPath = serverDir.resolve(jarName);
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jarOut.putNextEntry(new JarEntry("version.json"));
            jarOut.write((versionJson == null ? "{\"id\":\"test\"}" : versionJson).getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
            if (extraEntries != null) {
                for (String entryName : extraEntries) {
                    if (entryName == null || entryName.isBlank()) {
                        continue;
                    }
                    jarOut.putNextEntry(new JarEntry(entryName));
                    jarOut.write(new byte[0]);
                    jarOut.closeEntry();
                }
            }
        }
        return jarPath;
    }

    private static Path createJar(Path targetJar, Map<String, String> textEntries) throws Exception {
        if (targetJar.getParent() != null) {
            Files.createDirectories(targetJar.getParent());
        }
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(targetJar))) {
            for (Map.Entry<String, String> entry : textEntries.entrySet()) {
                jarOut.putNextEntry(new JarEntry(entry.getKey()));
                jarOut.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jarOut.closeEntry();
            }
        }
        return targetJar;
    }
}
