package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerExtensionsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldPersistUntrackedMetadataForManualInstalls() throws Exception {
        ServerExtensionsService service = new ServerExtensionsService();
        Path forgeDir = tempDir.resolve("forge-server");
        TestWorldFixtures.createValidServerJar(
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
        TestWorldFixtures.createJar(
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
                        authors="Easy MC"
                        description="Forge test mod"
                        """,
                        "META-INF/MANIFEST.MF",
                        """
                        Manifest-Version: 1.0
                        """
                )
        );

        ServerExtension installed = service.installManualJar(server, sourceJar);
        ExtensionLocalMetadata metadata = installed.getLocalMetadata();

        assertThat(metadata.getInstalledVersion()).isEqualTo("1.0.0");
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
    void shouldDetectExactInstalledCatalogExtension() {
        ServerExtensionsService service = new ServerExtensionsService();
        Server server = new Server();
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
                null
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
                null
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
                null
        );

        ExtensionInstallResolution resolution = service.evaluateCatalogInstallation(server, entry);

        assertThat(resolution.state()).isEqualTo(ExtensionInstallResolutionState.INSTALLED_WITH_INCOMPLETE_METADATA);
        assertThat(resolution.incompleteMetadataMatch()).isTrue();
        assertThat(resolution.message()).contains("metadata local").contains("manual");
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
}
