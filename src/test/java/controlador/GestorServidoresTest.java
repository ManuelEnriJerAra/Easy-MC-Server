package controlador;

import modelo.Server;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;
import controlador.platform.ServerCreationOption;
import controlador.platform.ServerInstallationRequest;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformProfile;
import controlador.platform.ServerValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GestorServidoresTest {
    @TempDir
    Path tempDir;

    @Test
    void guardarServidor_debeCompletarMetadatosYPersistirEnJsonAislado() throws Exception {
        Path jsonPath = tempDir.resolve("easy-mc-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Server server = new Server();
        server.setId(null);
        server.setDisplayName("Alpha");
        server.setServerConfig(null);

        gestor.guardarServidor(server);

        List<Server> persisted = new ObjectMapper().readValue(jsonPath.toFile(),
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().getId()).isNotBlank();
        assertThat(persisted.getFirst().getServerConfig()).isNotNull();
        assertThat(persisted.getFirst().getOrdenLista()).isZero();
        assertThat(persisted.getFirst().getPreviewRenderProfileId()).isEqualTo("quality");
        assertThat(persisted.getFirst().getPreviewRenderRealtime()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowSpawn()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowPlayers()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowChunkGrid()).isFalse();
        assertThat(persisted.getFirst().getPreviewUseWholeMap()).isFalse();
        assertThat(persisted.getFirst().getPreviewRenderLimitPixels()).isEqualTo(256);
        assertThat(persisted.getFirst().getPreviewRenderCenterId()).isEqualTo("spawn");
        assertThat(persisted.getFirst().getEstadisticasCpuActiva()).isTrue();
        assertThat(persisted.getFirst().getEstadisticasCpuHistorial()).isTrue();
        assertThat(persisted.getFirst().getPlatform()).isEqualTo(ServerPlatform.UNKNOWN);
        assertThat(persisted.getFirst().getLoader()).isEqualTo(ServerLoader.UNKNOWN);
        assertThat(persisted.getFirst().getEcosystemType()).isEqualTo(ServerEcosystemType.UNKNOWN);
        assertThat(persisted.getFirst().getCapabilities()).contains(ServerCapability.CORE_SERVER);
        assertThat(persisted.getFirst().getExtensions()).isNotNull().isEmpty();
    }

    @Test
    void constructor_debeCompletarPreferenciasPreviewFaltantes() throws Exception {
        Path jsonPath = tempDir.resolve("easy-mc-server-list.json");
        Path serverDir = tempDir.resolve("previewless-server");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar");
        Server server = new Server();
        server.setDisplayName("Previewless");
        server.setServerDir(serverDir.toString());
        server.setPreviewRenderProfileId(null);
        server.setPreviewRenderRealtime(null);
        server.setPreviewShowSpawn(null);
        server.setPreviewShowPlayers(null);
        server.setPreviewShowChunkGrid(null);
        server.setPreviewUseWholeMap(null);
        server.setPreviewRenderLimitPixels(null);
        server.setPreviewRenderCenterId(null);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), List.of(server));

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        Server persisted = gestor.getListaServidores().getFirst();
        assertThat(persisted.getPreviewRenderProfileId()).isEqualTo("quality");
        assertThat(persisted.getPreviewRenderRealtime()).isFalse();
        assertThat(persisted.getPreviewShowSpawn()).isFalse();
        assertThat(persisted.getPreviewShowPlayers()).isFalse();
        assertThat(persisted.getPreviewShowChunkGrid()).isFalse();
        assertThat(persisted.getPreviewUseWholeMap()).isFalse();
        assertThat(persisted.getPreviewRenderLimitPixels()).isEqualTo(256);
        assertThat(persisted.getPreviewRenderCenterId()).isEqualTo("spawn");
        assertThat(persisted.getEstadisticasCpuActiva()).isTrue();
        assertThat(persisted.getEstadisticasCpuHistorial()).isTrue();
        assertThat(persisted.getPlatform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(persisted.getLoader()).isEqualTo(ServerLoader.VANILLA);
        assertThat(persisted.getEcosystemType()).isEqualTo(ServerEcosystemType.NONE);
        assertThat(persisted.getExtensions()).isNotNull().isEmpty();
    }

    @Test
    void constructor_debeMigrarTipoLegacyAPlataformaLoaderYCapacidades() throws Exception {
        Path jsonPath = tempDir.resolve("ServerList.json");
        Path forgeDir = tempDir.resolve("legacy-forge-server");
        TestWorldFixtures.createValidServerJar(forgeDir, "server.jar");
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        String legacyJson = """
                [
                  {
                    "id": "legacy-1",
                    "displayName": "Legacy Forge",
                    "tipo": "FORGE",
                    "serverDir": "%s"
                  }
                ]
                """.formatted(forgeDir.toString().replace("\\", "\\\\"));
        Files.writeString(jsonPath, legacyJson, StandardCharsets.UTF_8);

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        Server persisted = gestor.getListaServidores().getFirst();
        assertThat(persisted.getTipo()).isEqualTo("FORGE");
        assertThat(persisted.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(persisted.getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(persisted.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(persisted.getCapabilities()).contains(
                ServerCapability.CORE_SERVER,
                ServerCapability.EXTENSIONS,
                ServerCapability.MOD_EXTENSIONS
        );
        assertThat(persisted.getExtensions()).isNotNull().isEmpty();

        List<Server> reloaded = new ObjectMapper().readValue(jsonPath.toFile(),
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(reloaded.getFirst().getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(reloaded.getFirst().getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(reloaded.getFirst().getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
    }

    @Test
    void importarServidorDesdeDirectorio_debeDetectarForgeYActivarExperienciaMods() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path forgeDir = tempDir.resolve("import-forge");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "launch.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));

        Server imported = gestor.importarServidorDesdeDirectorio(forgeDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getTipo()).isEqualTo("FORGE");
        assertThat(imported.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(imported.getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(imported.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(imported.getCapabilities()).contains(ServerCapability.MOD_EXTENSIONS);
    }

    @Test
    void importarServidorDesdeDirectorio_debeDetectarPaperYActivarExperienciaPlugins() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path paperDir = tempDir.resolve("import-paper");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "custom-runtime.jar",
                "{\"id\":\"1.21.1\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );

        Server imported = gestor.importarServidorDesdeDirectorio(paperDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getTipo()).isEqualTo("PAPER");
        assertThat(imported.getPlatform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(imported.getLoader()).isEqualTo(ServerLoader.PAPER);
        assertThat(imported.getEcosystemType()).isEqualTo(ServerEcosystemType.PLUGINS);
        assertThat(imported.getCapabilities()).contains(ServerCapability.PLUGIN_EXTENSIONS);
    }

    @Test
    void puedeConvertirseAPlataformaCompatible_soloDebeAceptarServidoresVanillaDetectables() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());

        Path vanillaDir = tempDir.resolve("convertible-vanilla");
        TestWorldFixtures.createValidServerJar(vanillaDir, "launch-anything.jar", "{\"id\":\"1.20.1\"}");
        Server vanilla = new Server();
        vanilla.setServerDir(vanillaDir.toString());
        vanilla.setPlatform(ServerPlatform.UNKNOWN);

        Path paperDir = tempDir.resolve("non-convertible-paper");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "runtime.jar",
                "{\"id\":\"1.20.1\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Server paper = new Server();
        paper.setServerDir(paperDir.toString());

        assertThat(gestor.puedeConvertirseAPlataformaCompatible(vanilla)).isTrue();
        assertThat(vanilla.getPlatform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(gestor.puedeConvertirseAPlataformaCompatible(paper)).isFalse();
    }

    @Test
    void convertirServidorExistente_debeCrearBackupYPreservarConfiguracionYMundo() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path serverDir = tempDir.resolve("vanilla-to-forge");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar", "{\"id\":\"1.20.1\"}");
        Files.createDirectories(serverDir.resolve("world"));
        Files.writeString(serverDir.resolve("world").resolve("level.dat"), "world-data");
        Properties props = new Properties();
        props.setProperty("motd", "Vanilla MOTD");
        props.setProperty("level-name", "world");
        TestWorldFixtures.writeServerProperties(serverDir, props);

        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setDisplayName("Convertible");
        server.setVersion("1.20.1");
        server.setPlatform(ServerPlatform.VANILLA);
        gestor.guardarServidor(server);

        ServerPlatformAdapter forgeInstaller = new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return ServerPlatform.FORGE;
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server transientServer, ServerInstallationRequest request) throws java.io.IOException {
                Files.deleteIfExists(request.targetDirectory().resolve("server.jar"));
                TestWorldFixtures.createValidServerJar(
                        request.targetDirectory(),
                        "runtime.jar",
                        "{\"id\":\"1.20.1\"}",
                        "net/minecraftforge/server/ServerMain.class"
                );
                Files.createDirectories(request.targetDirectory().resolve("libraries"));
                Files.createDirectories(request.targetDirectory().resolve("mods"));
                Files.writeString(request.targetDirectory().resolve("run.bat"), "@echo off");
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("runtime.jar");
            }
        };

        Server converted = gestor.convertirServidorExistente(
                server,
                forgeInstaller,
                new ServerCreationOption(ServerPlatform.FORGE, "1.20.1", "1.20.1-47.4.18", "Forge 1.20.1", "ignored"),
                (url, destination) -> {}
        );

        assertThat(converted).isNotNull();
        assertThat(converted.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(converted.getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(converted.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(converted.getCapabilities()).contains(ServerCapability.MOD_EXTENSIONS);
        assertThat(converted.getLoaderVersion()).isEqualTo("1.20.1-47.4.18");
        assertThat(
                Files.exists(serverDir.resolve("world").resolve("level.dat"))
                        || Files.exists(serverDir.resolve(GestorMundos.DIRECTORIO_MUNDOS).resolve("world").resolve("level.dat"))
        ).isTrue();
        assertThat(Files.exists(serverDir.resolve("mods"))).isTrue();
        assertThat(Utilidades.leerMotdDesdeProperties(serverDir)).isEqualTo("Vanilla MOTD");

        try (var backups = Files.list(tempDir)) {
            assertThat(backups.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("backup_before_forge"))
                    .toList()).isNotEmpty();
        }
    }

    @Test
    void establecerFavoritoYReordenarServidores_debenMantenerOrdenVisualEstable() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("easy-mc-server-list.json").toFile());
        Server alpha = new Server();
        alpha.setDisplayName("Alpha");
        Server beta = new Server();
        beta.setDisplayName("Beta");
        Server gamma = new Server();
        gamma.setDisplayName("Gamma");
        gestor.guardarServidor(alpha);
        gestor.guardarServidor(beta);
        gestor.guardarServidor(gamma);

        gestor.establecerFavorito(beta, true);
        gestor.reordenarServidores(List.of(gamma.getId(), alpha.getId(), beta.getId()));

        List<Server> ordered = gestor.getListaServidores();
        assertThat(ordered.getFirst().getId()).isEqualTo(beta.getId());
        assertThat(ordered).extracting(Server::getId).containsExactly(beta.getId(), gamma.getId(), alpha.getId());
    }

    @Test
    void constructor_debeEliminarServidoresPersistidosNoCargables() throws Exception {
        Path jsonPath = tempDir.resolve("easy-mc-server-list.json");
        Path validDir = tempDir.resolve("valid-server");
        Path invalidDir = tempDir.resolve("invalid-server");
        Files.createDirectories(invalidDir);
        TestWorldFixtures.createValidServerJar(validDir, "server.jar");

        Server valid = new Server();
        valid.setDisplayName("Valid");
        valid.setServerDir(validDir.toString());

        Server invalid = new Server();
        invalid.setDisplayName("Invalid");
        invalid.setServerDir(invalidDir.toString());

        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), List.of(valid, invalid));

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        assertThat(gestor.getListaServidores()).extracting(Server::getDisplayName).containsExactly("Valid");
        assertThat(gestor.getAvisoServidoresNoCargados()).contains("No se han podido cargar 1 servidores");
    }

    @Test
    void eliminarServidor_debeFallarSiProcesoSigueVivo() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("easy-mc-server-list.json").toFile());
        Server server = new Server();
        server.setDisplayName("Busy");
        gestor.guardarServidor(server);
        server.setServerProcess(new FakeProcess(true));

        boolean deleted = gestor.eliminarServidor(server);

        assertThat(deleted).isFalse();
        assertThat(gestor.getListaServidores()).extracting(Server::getId).contains(server.getId());
    }

    private static final class FakeProcess extends Process {
        private final boolean alive;

        private FakeProcess(boolean alive) {
            this.alive = alive;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            return !alive;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
