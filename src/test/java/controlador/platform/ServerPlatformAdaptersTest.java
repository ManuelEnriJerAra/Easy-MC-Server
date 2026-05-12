package controlador.platform;

import controlador.MojangAPI;
import controlador.Utilidades;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import modelo.Server;
import modelo.ServerConfig;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServerPlatformAdaptersTest {
    @TempDir
    Path tempDir;

    @Test
    void registry_debeExponerTodasLasPlataformasYCrearSoloLasAutomatizables() {
        assertThat(ServerPlatformAdapters.all())
                .extracting(ServerPlatformAdapter::getPlatform)
                .containsExactlyInAnyOrder(
                        ServerPlatform.VANILLA,
                        ServerPlatform.FORGE,
                        ServerPlatform.NEOFORGE,
                        ServerPlatform.FABRIC,
                        ServerPlatform.QUILT,
                        ServerPlatform.PAPER,
                        ServerPlatform.SPIGOT,
                        ServerPlatform.BUKKIT,
                        ServerPlatform.PURPUR,
                        ServerPlatform.PUFFERFISH
                );

        assertThat(ServerPlatformAdapters.creatable())
                .extracting(ServerPlatformAdapter::getPlatform)
                .containsExactlyInAnyOrder(
                        ServerPlatform.VANILLA,
                        ServerPlatform.FORGE,
                        ServerPlatform.NEOFORGE,
                        ServerPlatform.FABRIC,
                        ServerPlatform.QUILT,
                        ServerPlatform.PAPER,
                        ServerPlatform.PURPUR
                );

        assertThat(ServerPlatformAdapters.forPlatform(ServerPlatform.SPIGOT).getCreationUnavailableReason())
                .contains("BuildTools");
        assertThat(ServerPlatformAdapters.forPlatform(ServerPlatform.PUFFERFISH).getCreationUnavailableReason())
                .contains("endpoint publico estable");
    }

    @Test
    void detect_debeResolverForgeConCapacidadesYRutaDeMods() throws Exception {
        Path forgeDir = tempDir.resolve("forge-server");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "runtime.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(forgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(profile.extensionDirectories()).containsExactly(forgeDir.resolve("mods"));
        assertThat(profile.capabilities()).contains(ServerCapability.MOD_EXTENSIONS);
        assertThat(new ForgeServerPlatformAdapter().validate(forgeDir).valid()).isTrue();
    }

    @Test
    void detect_debeResolverNeoForgeFabricQuiltYPlataformasDePlugins() throws Exception {
        Path neoForgeDir = tempDir.resolve("neoforge-server");
        TestWorldFixtures.createValidServerJar(
                neoForgeDir,
                "neoforge-runtime.jar",
                "{\"id\":\"1.21.1\"}",
                "net/neoforged/neoforge/NeoForge.class"
        );
        Files.createDirectories(neoForgeDir.resolve("mods"));

        Path fabricDir = tempDir.resolve("fabric-server");
        TestWorldFixtures.createValidServerJar(
                fabricDir,
                "fabric-server.jar",
                "{\"id\":\"1.21.1\"}",
                "net/fabricmc/loader/impl/launch/server/FabricServerLauncher.class"
        );
        Files.createDirectories(fabricDir.resolve("mods"));

        Path quiltDir = tempDir.resolve("quilt-server");
        TestWorldFixtures.createValidServerJar(
                quiltDir,
                "quilt-server-launch.jar",
                "{\"id\":\"1.21.1\"}",
                "org/quiltmc/loader/impl/launch/server/QuiltServerLauncher.class"
        );
        Files.createDirectories(quiltDir.resolve("mods"));

        Path purpurDir = tempDir.resolve("purpur-server");
        TestWorldFixtures.createValidServerJar(
                purpurDir,
                "purpur.jar",
                "{\"id\":\"1.21.1\"}",
                "org/purpurmc/purpur/PurpurConfig.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Files.writeString(purpurDir.resolve("purpur.yml"), "");

        Path pufferfishDir = tempDir.resolve("pufferfish-server");
        TestWorldFixtures.createValidServerJar(
                pufferfishDir,
                "pufferfish.jar",
                "{\"id\":\"1.21.1\"}",
                "gg/pufferfish/pufferfish/PufferfishConfig.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Files.writeString(pufferfishDir.resolve("pufferfish.yml"), "");

        Path spigotDir = tempDir.resolve("spigot-server");
        TestWorldFixtures.createValidServerJar(
                spigotDir,
                "spigot.jar",
                "{\"id\":\"1.21.1\"}",
                "org/spigotmc/SpigotConfig.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Files.writeString(spigotDir.resolve("spigot.yml"), "");

        Path bukkitDir = tempDir.resolve("bukkit-server");
        TestWorldFixtures.createValidServerJar(
                bukkitDir,
                "craftbukkit.jar",
                "{\"id\":\"1.21.1\"}",
                "org/bukkit/craftbukkit/Main.class"
        );
        Files.createDirectories(bukkitDir.resolve("plugins"));

        assertThat(ServerPlatformAdapters.detect(neoForgeDir).platform()).isEqualTo(ServerPlatform.NEOFORGE);
        assertThat(ServerPlatformAdapters.detect(fabricDir).platform()).isEqualTo(ServerPlatform.FABRIC);
        assertThat(ServerPlatformAdapters.detect(quiltDir).platform()).isEqualTo(ServerPlatform.QUILT);
        assertThat(ServerPlatformAdapters.detect(purpurDir).platform()).isEqualTo(ServerPlatform.PURPUR);
        assertThat(ServerPlatformAdapters.detect(pufferfishDir).platform()).isEqualTo(ServerPlatform.PUFFERFISH);
        assertThat(ServerPlatformAdapters.detect(spigotDir).platform()).isEqualTo(ServerPlatform.SPIGOT);
        assertThat(ServerPlatformAdapters.detect(bukkitDir).platform()).isEqualTo(ServerPlatform.BUKKIT);
    }

    @Test
    void adapters_debenSeleccionarCarpetaDeExtensionesSegunEcosistema() {
        Path serverDir = tempDir.resolve("server");

        for (ServerPlatform platform : Set.of(ServerPlatform.FORGE, ServerPlatform.NEOFORGE, ServerPlatform.FABRIC, ServerPlatform.QUILT)) {
            assertThat(ServerPlatformAdapters.forPlatform(platform).getExtensionDirectories(serverDir))
                    .containsExactly(serverDir.resolve("mods"));
        }
        for (ServerPlatform platform : Set.of(ServerPlatform.PAPER, ServerPlatform.SPIGOT, ServerPlatform.BUKKIT, ServerPlatform.PURPUR, ServerPlatform.PUFFERFISH)) {
            assertThat(ServerPlatformAdapters.forPlatform(platform).getExtensionDirectories(serverDir))
                    .containsExactly(serverDir.resolve("plugins"));
        }
        assertThat(ServerPlatformAdapters.forPlatform(ServerPlatform.VANILLA).getExtensionDirectories(serverDir)).isEmpty();
    }

    @Test
    void detect_debeResolverPaperConPluginsYComandoDeArranque() throws Exception {
        Path paperDir = tempDir.resolve("paper-server");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "server-launch.jar",
                "{\"id\":\"1.21.1\"}",
                "io/papermc/paper/PaperBootstrap.class"
        );
        Files.createDirectories(paperDir.resolve("plugins"));

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(paperDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(profile.extensionDirectories()).containsExactly(paperDir.resolve("plugins"));
        assertThat(profile.capabilities()).contains(ServerCapability.PLUGIN_EXTENSIONS);

        Server server = new Server();
        server.setServerDir(paperDir.toString());
        server.setServerConfig(new ServerConfig());
        ProcessBuilder processBuilder = new PaperServerPlatformAdapter().buildStartProcess(server, profile.executableJar());
        assertThat(processBuilder.command()).contains("java", "-jar", "nogui");
        assertThat(processBuilder.command()).contains(profile.executableJar().toString());
    }

    @Test
    void detect_debeInferirVersionDesdeArgsDeForgeModernoSinJarEjecutable() throws Exception {
        Path forgeDir = tempDir.resolve("forge-script-version");
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries/net/minecraftforge/forge/1.20.1-47.2.0"));
        Files.writeString(forgeDir.resolve("run.bat"), "@echo off\njava @libraries/net/minecraftforge/forge/1.20.1-47.2.0/win_args.txt nogui\n");
        Files.writeString(forgeDir.resolve("unix_args.txt"), "--fml.mcVersion 1.20.1\n--fml.forgeVersion 47.2.0\n");

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(forgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(profile.minecraftVersion()).isEqualTo("1.20.1");
    }

    @Test
    void detect_debeInferirVersionDesdeManifestDeModpackForge() throws Exception {
        Path forgeDir = tempDir.resolve("26.1.2_server_1");
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("config"));
        Files.createDirectories(forgeDir.resolve("libraries"));
        Files.writeString(forgeDir.resolve("manifest.json"), """
                {
                  "minecraft": {
                    "version": "1.14.4",
                    "modLoaders": [
                      { "id": "forge-26.1.2", "primary": true }
                    ]
                  }
                }
                """);

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(forgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(profile.minecraftVersion()).isEqualTo("1.14.4");
    }

    @Test
    void detect_noDebeUsarVersionDeLoaderForgeComoVersionMinecraft() throws Exception {
        Path forgeDir = tempDir.resolve("forge-loader-only");
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("config"));
        Files.createDirectories(forgeDir.resolve("libraries"));
        Files.writeString(forgeDir.resolve("manifest.json"), """
                {
                  "minecraft": {
                    "modLoaders": [
                      { "id": "forge-26.1.2", "primary": true }
                    ]
                  }
                }
                """);

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(forgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(profile.minecraftVersion()).isNull();
    }

    @Test
    void detect_noDebeUsarNombreDeCarpetaNiJarComoVersion() throws Exception {
        Path forgeDir = tempDir.resolve("1.20.1_server_1");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "forge-1.20.1-server.jar",
                "{\"id\":\"forge\"}",
                "net/minecraftforge/common/MinecraftForge.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(forgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(profile.minecraftVersion()).isNull();
    }

    @Test
    void detect_debeInferirVersionDesdeLauncherPropertiesDeFabric() throws Exception {
        Path fabricDir = tempDir.resolve("fabric-launcher-version");
        Files.createDirectories(fabricDir.resolve("mods"));
        Files.writeString(fabricDir.resolve("fabric-server-launcher.properties"), "minecraftVersion=1.19.4\nserverJar=server.jar\n");
        Files.writeString(fabricDir.resolve("fabric-server-launcher.jar"), "");

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(fabricDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.FABRIC);
        assertThat(profile.minecraftVersion()).isEqualTo("1.19.4");
    }

    @Test
    void detect_debeInferirVersionDesdeJarInternoAunqueNoSeaElEjecutableElegido() throws Exception {
        Path paperDir = tempDir.resolve("paper-multi-jar");
        TestWorldFixtures.createJar(
                paperDir.resolve("launcher.jar"),
                Map.of("version.json", "{\"id\":\"launcher\"}"),
                "io/papermc/paper/PaperBootstrap.class"
        );
        TestWorldFixtures.createJar(
                paperDir.resolve("server-runtime.jar"),
                Map.of("version.json", "{\"id\":\"1.21.4\"}")
        );
        Files.createDirectories(paperDir.resolve("plugins"));

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(paperDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(profile.minecraftVersion()).isEqualTo("1.21.4");
    }

    @Test
    void detect_debeInferirVersionDesdeLogDelServidor() throws Exception {
        Path paperDir = tempDir.resolve("paper-log-version");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "server.jar",
                "{\"id\":\"git-Paper-123\"}",
                "io/papermc/paper/PaperBootstrap.class"
        );
        Files.createDirectories(paperDir.resolve("plugins"));
        Files.createDirectories(paperDir.resolve("logs"));
        Files.writeString(paperDir.resolve("logs").resolve("latest.log"), "[Server thread/INFO]: Starting minecraft server version 1.20.6\n");

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(paperDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(profile.minecraftVersion()).isEqualTo("1.20.6");
    }

    @Test
    void detect_debeInferirVersionSnapshotDesdeJar() throws Exception {
        Path vanillaDir = tempDir.resolve("vanilla-snapshot");
        TestWorldFixtures.createValidServerJar(vanillaDir, "server.jar", "{\"id\":\"24w14a\"}");

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(vanillaDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(profile.minecraftVersion()).isEqualTo("24w14a");
    }

    @Test
    void detect_debeInferirVersionDesdeMmcPack() throws Exception {
        Path fabricDir = tempDir.resolve("fabric-mmc-pack");
        Files.createDirectories(fabricDir.resolve("mods"));
        Files.writeString(fabricDir.resolve("fabric-server-launcher.jar"), "");
        Files.writeString(fabricDir.resolve("mmc-pack.json"), """
                {
                  "components": [
                    { "uid": "net.fabricmc.fabric-loader", "version": "0.15.11" },
                    { "uid": "net.minecraft", "version": "1.20.4" }
                  ]
                }
                """);

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(fabricDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.FABRIC);
        assertThat(profile.minecraftVersion()).isEqualTo("1.20.4");
    }

    @Test
    void detect_debeInferirVersionMinecraftDesdeCoordenadaNeoForge() throws Exception {
        Path neoForgeDir = tempDir.resolve("neoforge-runtime-version");
        Files.createDirectories(neoForgeDir.resolve("mods"));
        Files.createDirectories(neoForgeDir.resolve("libraries/net/neoforged/neoforge/21.1.172"));
        Files.writeString(neoForgeDir.resolve("run.bat"), "@echo off\njava @libraries/net/neoforged/neoforge/21.1.172/win_args.txt nogui\n");

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(neoForgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.NEOFORGE);
        assertThat(profile.minecraftVersion()).isEqualTo("1.21.1");
    }

    @Test
    void detect_debePreferirMcVersionExplicitaDeNeoForgeSemantico() throws Exception {
        Path neoForgeDir = tempDir.resolve("neoforge-semantic-version");
        Files.createDirectories(neoForgeDir.resolve("mods"));
        Files.createDirectories(neoForgeDir.resolve("libraries/net/neoforged/neoforge/26.1.2.48-beta"));
        Files.writeString(
                neoForgeDir.resolve("libraries/net/neoforged/neoforge/26.1.2.48-beta/win_args.txt"),
                """
                --fml.neoForgeVersion 26.1.2.48-beta
                --fml.mcVersion 26.1.2
                --fml.neoFormVersion 1
                """
        );
        Files.writeString(neoForgeDir.resolve("run.bat"), "@echo off\njava @libraries/net/neoforged/neoforge/26.1.2.48-beta/win_args.txt nogui\n");

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(neoForgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.NEOFORGE);
        assertThat(profile.minecraftVersion()).isEqualTo("26.1.2");
    }

    @Test
    void detect_debeInferirVersionMinecraftDesdeCoordenadaNeoForgeSemantica() throws Exception {
        Path neoForgeDir = tempDir.resolve("neoforge-semantic-coordinate-version");
        Files.createDirectories(neoForgeDir.resolve("mods"));
        Files.createDirectories(neoForgeDir.resolve("libraries/net/neoforged/neoforge/26.1.2.48-beta"));
        Files.writeString(neoForgeDir.resolve("run.bat"), "@echo off\njava @libraries/net/neoforged/neoforge/26.1.2.48-beta/win_args.txt nogui\n");

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(neoForgeDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.NEOFORGE);
        assertThat(profile.minecraftVersion()).isEqualTo("26.1.2");
    }

    @Test
    void install_vanillaDebeEncapsularDescargaEulaYMetadatos() throws Exception {
        Path sourceJar = tempDir.resolve("downloads").resolve("source.jar");
        TestWorldFixtures.createValidServerJar(sourceJar.getParent(), sourceJar.getFileName().toString());
        Path icon = tempDir.resolve("default_image.png");
        Files.write(icon, new byte[]{0, 1, 2, 3});
        Path installDir = tempDir.resolve("vanilla-server");

        Server server = new Server();
        VanillaServerPlatformAdapter adapter = new VanillaServerPlatformAdapter(new FakeMojangApi(sourceJar));
        adapter.install(server, new ServerInstallationRequest(
                installDir,
                "1.21.5",
                "1.21.5",
                true,
                icon,
                null,
                (url, destination) -> Files.copy(sourceJar, destination.toPath())
        ));

        assertThat(server.getPlatform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(server.getVersion()).isEqualTo("1.21.5");
        assertThat(server.getServerDir()).isEqualTo(installDir.toString());
        assertThat(Files.exists(installDir.resolve("1.21.5_server.jar"))).isTrue();
        assertThat(Files.exists(installDir.resolve("eula.txt"))).isTrue();
        assertThat(Files.exists(installDir.resolve("server-icon.png"))).isTrue();
        assertFullServerProperties(installDir, "Easy-MC Vanilla 1.21.5");

        ServerPlatformProfile profile = adapter.detect(installDir);
        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.VANILLA);
    }

    @Test
    void install_forgeDebePrepararServidorYMetadatosBase() throws Exception {
        Path icon = tempDir.resolve("forge-icon.png");
        Files.write(icon, new byte[]{7, 8, 9});
        Path installDir = tempDir.resolve("forge-created");
        Path fakeInstaller = tempDir.resolve("downloads").resolve("forge-installer.jar");
        TestWorldFixtures.createValidServerJar(fakeInstaller.getParent(), fakeInstaller.getFileName().toString(), "{\"id\":\"installer\"}");

        ForgeRepositoryClient repositoryClient = new ForgeRepositoryClient() {
            @Override
            public String getInstallerUrl(String artifactVersion) {
                return "https://maven.minecraftforge.net/fake/" + artifactVersion + "/installer.jar";
            }
        };
        ForgeInstallerRunner installerRunner = (installerJar, targetDirectory) -> {
            Files.createDirectories(targetDirectory.resolve("libraries"));
            Files.createDirectories(targetDirectory.resolve("mods"));
            Files.writeString(targetDirectory.resolve("run.bat"), "@echo off");
            TestWorldFixtures.createValidServerJar(
                    targetDirectory,
                    "runtime.jar",
                    "{\"id\":\"1.20.1\"}",
                    "net/minecraftforge/server/ServerMain.class"
            );
        };

        ForgeServerPlatformAdapter adapter = new ForgeServerPlatformAdapter(repositoryClient, installerRunner);
        Server server = new Server();
        adapter.install(server, new ServerInstallationRequest(
                installDir,
                "1.20.1",
                "1.20.1-47.4.18",
                true,
                icon,
                null,
                (url, destination) -> Files.copy(fakeInstaller, destination.toPath())
        ));

        assertThat(server.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(server.getVersion()).isEqualTo("1.20.1");
        assertThat(server.getLoaderVersion()).isEqualTo("1.20.1-47.4.18");
        assertThat(Files.exists(installDir.resolve("forge-1.20.1-47.4.18-installer.jar"))).isTrue();
        assertThat(Files.exists(installDir.resolve("run.bat"))).isTrue();
        assertThat(Files.exists(installDir.resolve("mods"))).isTrue();
        assertThat(Files.exists(installDir.resolve("eula.txt"))).isTrue();
        assertThat(Files.exists(installDir.resolve("server-icon.png"))).isTrue();
        assertFullServerProperties(installDir, "Easy-MC Forge 1.20.1");
        assertThat(adapter.detect(installDir)).isNotNull();
        assertThat(adapter.detect(installDir).platform()).isEqualTo(ServerPlatform.FORGE);
    }

    @Test
    void install_neoforgeDebeUsarInstaladorYPrepararMods() throws Exception {
        Path installDir = tempDir.resolve("neoforge-created");
        Path fakeInstaller = tempDir.resolve("downloads").resolve("neoforge-installer.jar");
        TestWorldFixtures.createValidServerJar(fakeInstaller.getParent(), fakeInstaller.getFileName().toString(), "{\"id\":\"installer\"}");
        NeoForgeRepositoryClient repositoryClient = new NeoForgeRepositoryClient() {
            @Override
            String getInstallerUrl(String artifactVersion) {
                return "https://maven.neoforged.net/fake/" + artifactVersion + "/installer.jar";
            }
        };
        ForgeInstallerRunner installerRunner = (installerJar, targetDirectory) -> {
            Files.createDirectories(targetDirectory.resolve("libraries"));
            TestWorldFixtures.createValidServerJar(
                    targetDirectory,
                    "neoforge-runtime.jar",
                    "{\"id\":\"1.21.1\"}",
                    "net/neoforged/neoforge/NeoForge.class"
            );
        };

        Server server = new Server();
        NeoForgeServerPlatformAdapter adapter = new NeoForgeServerPlatformAdapter(repositoryClient, installerRunner);
        adapter.install(server, new ServerInstallationRequest(
                installDir,
                "1.21.1",
                "21.1.200",
                true,
                null,
                null,
                (url, destination) -> Files.copy(fakeInstaller, destination.toPath())
        ));

        assertThat(server.getPlatform()).isEqualTo(ServerPlatform.NEOFORGE);
        assertThat(server.getLoaderVersion()).isEqualTo("21.1.200");
        assertThat(Files.exists(installDir.resolve("mods"))).isTrue();
        assertThat(Files.exists(installDir.resolve("config"))).isTrue();
        assertFullServerProperties(installDir, "Easy-MC NeoForge 1.21.1");
        assertThat(adapter.detect(installDir).platform()).isEqualTo(ServerPlatform.NEOFORGE);
    }

    @Test
    void install_paperPurpurFabricYQuiltPreparanCarpetasCorrectas() throws Exception {
        Path sourceJar = tempDir.resolve("downloads").resolve("runtime.jar");
        TestWorldFixtures.createValidServerJar(sourceJar.getParent(), sourceJar.getFileName().toString(), "{\"id\":\"1.21.1\"}");

        Server paperServer = new Server();
        PaperServerPlatformAdapter paperAdapter = new PaperServerPlatformAdapter(new PaperDownloadsClient(new FakePlatformHttpClient(Map.of(
                "https://fill.papermc.io/v3/projects/paper/versions/1.21.1/builds",
                """
                [
                  {"id":"42","channel":"STABLE","downloads":{"server:default":{"url":"https://example.test/paper.jar"}}}
                ]
                """
        ))));
        paperAdapter.install(paperServer, request(tempDir.resolve("paper-created"), "1.21.1", "42", sourceJar));
        assertThat(paperServer.getPlatform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(Files.exists(tempDir.resolve("paper-created").resolve("plugins"))).isTrue();
        assertFullServerProperties(tempDir.resolve("paper-created"), "Easy-MC Paper 1.21.1");

        Server purpurServer = new Server();
        PurpurServerPlatformAdapter purpurAdapter = new PurpurServerPlatformAdapter(new PurpurDownloadsClient(new FakePlatformHttpClient(Map.of())));
        purpurAdapter.install(purpurServer, request(tempDir.resolve("purpur-created"), "1.21.1", "99", sourceJar));
        assertThat(purpurServer.getPlatform()).isEqualTo(ServerPlatform.PURPUR);
        assertThat(Files.exists(tempDir.resolve("purpur-created").resolve("plugins"))).isTrue();
        assertFullServerProperties(tempDir.resolve("purpur-created"), "Easy-MC Purpur 1.21.1");

        Server fabricServer = new Server();
        FabricServerPlatformAdapter fabricAdapter = new FabricServerPlatformAdapter(new FabricMetaClient(new FakePlatformHttpClient(Map.of())));
        fabricAdapter.install(fabricServer, request(
                tempDir.resolve("fabric-created"),
                "1.21.1",
                FabricMetaClient.encodePlatformVersion("0.19.2", "1.1.1"),
                sourceJar
        ));
        assertThat(fabricServer.getPlatform()).isEqualTo(ServerPlatform.FABRIC);
        assertThat(fabricServer.getLoaderVersion()).isEqualTo("0.19.2");
        assertThat(Files.exists(tempDir.resolve("fabric-created").resolve("mods"))).isTrue();
        assertFullServerProperties(tempDir.resolve("fabric-created"), "Easy-MC Fabric 1.21.1");

        Server quiltServer = new Server();
        QuiltServerPlatformAdapter quiltAdapter = new QuiltServerPlatformAdapter(
                new QuiltMetaClient(new FakePlatformHttpClient(Map.of(
                        "https://meta.quiltmc.org/v3/versions/installer",
                        """
                        [
                          {"version":"0.12.1","url":"https://example.test/quilt-installer.jar"}
                        ]
                        """
                ))),
                (installerJar, targetDirectory, installerArguments) -> TestWorldFixtures.createValidServerJar(
                        targetDirectory,
                        "quilt-server-launch.jar",
                        "{\"id\":\"1.21.1\"}",
                        "org/quiltmc/loader/impl/launch/server/QuiltServerLauncher.class"
                )
        );
        quiltAdapter.install(quiltServer, request(
                tempDir.resolve("quilt-created"),
                "1.21.1",
                QuiltMetaClient.encodePlatformVersion("0.29.2", "0.12.1"),
                sourceJar
        ));
        assertThat(quiltServer.getPlatform()).isEqualTo(ServerPlatform.QUILT);
        assertThat(quiltServer.getLoaderVersion()).isEqualTo("0.29.2");
        assertThat(Files.exists(tempDir.resolve("quilt-created").resolve("mods"))).isTrue();
        assertFullServerProperties(tempDir.resolve("quilt-created"), "Easy-MC Quilt 1.21.1");
    }

    @Test
    void creationClients_debenParsearOpcionesYDirectorios() throws Exception {
        PaperDownloadsClient paperClient = new PaperDownloadsClient(new FakePlatformHttpClient(Map.of(
                "https://fill.papermc.io/v3/projects/paper",
                """
                {"versions":{"1.21":["1.21.1","1.21.2"]}}
                """,
                "https://fill.papermc.io/v3/projects/paper/versions/1.21.2/builds",
                """
                [{"id":"55","channel":"STABLE","downloads":{"server:default":{"url":"https://example.test/paper-55.jar"}}}]
                """,
                "https://fill.papermc.io/v3/projects/paper/versions/1.21.1/builds",
                """
                [{"id":"44","channel":"EXPERIMENTAL"},{"id":"43","channel":"STABLE","downloads":{"server:default":{"url":"https://example.test/paper-43.jar"}}}]
                """
        )));
        assertThat(paperClient.listCreationOptions())
                .extracting(ServerCreationOption::directoryName)
                .contains("paper-1.21.2-server", "paper-1.21.1-server");

        FabricMetaClient fabricClient = new FabricMetaClient(new FakePlatformHttpClient(Map.of(
                "https://meta.fabricmc.net/v2/versions/loader",
                """
                [{"version":"0.19.2","stable":true}]
                """,
                "https://meta.fabricmc.net/v2/versions/installer",
                """
                [{"version":"1.1.1","stable":true}]
                """,
                "https://meta.fabricmc.net/v2/versions/game",
                """
                [{"version":"1.21.2","stable":true},{"version":"1.21.1","stable":true},{"version":"25w01a","stable":false}]
                """
        )));
        assertThat(fabricClient.listCreationOptions().getFirst().directoryName()).isEqualTo("fabric-1.21.2-server");
        assertThat(fabricClient.listCreationOptions().getFirst().platformVersion()).isEqualTo("0.19.2|1.1.1");

        assertThat(NeoForgeRepositoryClient.inferMinecraftVersion("21.1.200")).isEqualTo("1.21.1");
        assertThat(NeoForgeRepositoryClient.inferMinecraftVersion("20.6.120")).isEqualTo("1.20.6");
        assertThat(NeoForgeRepositoryClient.inferMinecraftVersion("26.1.2.48-beta")).isEqualTo("26.1.2");
        assertThat(NeoForgeRepositoryClient.inferMinecraftVersion("26.1.2")).isEqualTo("26.1.2");
    }

    @Test
    void forgeRepositoryClient_debeAgruparUltimaVersionPorMinecraft() throws Exception {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.20.1-47.4.17</version>
                      <version>1.20.1-47.4.18</version>
                      <version>1.21.1-52.1.13</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> versions = ForgeRepositoryClient.parseArtifactVersions(
                new ByteArrayInputStream(metadata.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(versions).containsExactly("1.20.1-47.4.17", "1.20.1-47.4.18", "1.21.1-52.1.13");
    }

    @Test
    void detect_debeResolverPaperAunqueElNombreDelJarNoAyude() throws Exception {
        Path paperDir = tempDir.resolve("paper-heuristics");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "bootstrap.jar",
                "{\"id\":\"1.21.4\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(paperDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(profile.minecraftVersion()).isEqualTo("1.21.4");
    }

    @Test
    void detect_debeAceptarVersionSemanticaFuturaDesdeVersionJson() throws Exception {
        Path paperDir = tempDir.resolve("paper-future-version-json");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "bootstrap.jar",
                "{\"id\":\"26.1.2\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );

        ServerPlatformProfile profile = ServerPlatformAdapters.detect(paperDir);

        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(profile.minecraftVersion()).isEqualTo("26.1.2");
    }

    private static final class FakeMojangApi extends MojangAPI {
        private final Path sourceJar;

        private FakeMojangApi(Path sourceJar) {
            this.sourceJar = sourceJar;
        }

        @Override
        public String obtenerUrlServerJar(String versionId) {
            return "https://example.test/" + versionId + "/server.jar";
        }

        @Override
        public void descargar(String url, File destino, DownloadProgressListener listener) {
            try {
                Files.createDirectories(destino.toPath().getParent());
                Files.copy(sourceJar, destino.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ServerInstallationRequest request(Path installDir, String minecraftVersion, String platformVersion, Path sourceJar) {
        return new ServerInstallationRequest(
                installDir,
                minecraftVersion,
                platformVersion,
                true,
                null,
                null,
                (url, destination) -> Files.copy(sourceJar, destination.toPath())
        );
    }

    private void assertFullServerProperties(Path installDir, String expectedMotd) throws IOException {
        Properties properties = Utilidades.cargarPropertiesUtf8(installDir.resolve("server.properties"));
        assertThat(properties.getProperty("motd")).isEqualTo(expectedMotd);
        assertThat(properties.getProperty("server-port")).isEqualTo("25565");
        assertThat(properties.getProperty("online-mode")).isEqualTo("true");
        assertThat(properties.getProperty("level-name")).isEqualTo("world");
        assertThat(properties.getProperty("gamemode")).isEqualTo("survival");
        assertThat(properties.size()).isGreaterThan(20);
    }

    private static final class FakePlatformHttpClient implements PlatformHttpClient {
        private final Map<String, String> responses;

        private FakePlatformHttpClient(Map<String, String> responses) {
            this.responses = responses == null ? Map.of() : responses;
        }

        @Override
        public JsonElement getJson(String url) throws IOException {
            String response = responses.get(url);
            if (response == null) {
                throw new IOException("No fixture for " + url);
            }
            return JsonParser.parseString(response);
        }
    }
}
