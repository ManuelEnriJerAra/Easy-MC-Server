package controlador.platform;

import controlador.MojangAPI;
import modelo.Server;
import modelo.ServerConfig;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerPlatformAdaptersTest {
    @TempDir
    Path tempDir;

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
    void install_vanillaDebeEncapsularDescargaEulaYMetadatos() throws Exception {
        Path sourceJar = tempDir.resolve("downloads").resolve("source.jar");
        TestWorldFixtures.createValidServerJar(sourceJar.getParent(), sourceJar.getFileName().toString());
        Path icon = tempDir.resolve("default_image.png");
        Files.write(icon, new byte[]{0, 1, 2, 3});
        Path installDir = tempDir.resolve("vanilla-server");

        Server server = new Server();
        VanillaServerPlatformAdapter adapter = new VanillaServerPlatformAdapter();
        adapter.install(server, new ServerInstallationRequest(
                installDir,
                "1.21.5",
                true,
                icon,
                new FakeMojangApi(sourceJar)
        ));

        assertThat(server.getPlatform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(server.getVersion()).isEqualTo("1.21.5");
        assertThat(server.getServerDir()).isEqualTo(installDir.toString());
        assertThat(Files.exists(installDir.resolve("1.21.5_server.jar"))).isTrue();
        assertThat(Files.exists(installDir.resolve("eula.txt"))).isTrue();
        assertThat(Files.exists(installDir.resolve("server-icon.png"))).isTrue();

        ServerPlatformProfile profile = adapter.detect(installDir);
        assertThat(profile).isNotNull();
        assertThat(profile.platform()).isEqualTo(ServerPlatform.VANILLA);
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
}
