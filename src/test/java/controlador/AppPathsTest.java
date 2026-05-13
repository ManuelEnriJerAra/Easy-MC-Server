package controlador;

import modelo.EasyMCConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppPathsTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearPathOverrides() {
        System.clearProperty(AppPaths.APP_ROOT_PROPERTY);
        System.clearProperty(AppPaths.LEGACY_ROOT_PROPERTY);
    }

    @Test
    void appPaths_debeResolverSubdirectoriosDesdeRaizDeDatosDeUsuario() {
        Path appRoot = tempDir.resolve("app-data");
        System.setProperty(AppPaths.APP_ROOT_PROPERTY, appRoot.toString());

        assertThat(AppPaths.rootDirectory()).isEqualTo(appRoot.toAbsolutePath().normalize());
        assertThat(AppPaths.configDirectory()).isEqualTo(appRoot.toAbsolutePath().normalize().resolve("config"));
        assertThat(AppPaths.cacheDirectory()).isEqualTo(appRoot.toAbsolutePath().normalize().resolve("cache"));
        assertThat(AppPaths.locksDirectory()).isEqualTo(appRoot.toAbsolutePath().normalize().resolve("locks"));
        assertThat(AppPaths.statsDirectory()).isEqualTo(appRoot.toAbsolutePath().normalize().resolve("stats"));
    }

    @Test
    void gestorConfiguracion_debeMigrarConfigDesdeDirectorioLegado() throws Exception {
        Path appRoot = tempDir.resolve("app-data");
        Path legacyRoot = tempDir.resolve("legacy-install");
        System.setProperty(AppPaths.APP_ROOT_PROPERTY, appRoot.toString());
        System.setProperty(AppPaths.LEGACY_ROOT_PROPERTY, legacyRoot.toString());

        Files.createDirectories(legacyRoot);
        Path legacyConfig = legacyRoot.resolve("easy-mc-config.json");
        Files.writeString(legacyConfig, "{\"temaClassName\":\"legacy.Theme\"}", StandardCharsets.UTF_8);

        EasyMCConfig config = GestorConfiguracion.cargarConfiguracion();

        Path migratedConfig = appRoot.resolve("config").resolve("easy-mc-config.json");
        assertThat(config.getTemaClassName()).isEqualTo("legacy.Theme");
        assertThat(migratedConfig).exists();
        assertThat(legacyConfig).doesNotExist();
    }

    @Test
    void gestorServidores_debeMigrarListaDesdeDirectorioLegado() throws Exception {
        Path appRoot = tempDir.resolve("app-data");
        Path legacyRoot = tempDir.resolve("legacy-install");
        System.setProperty(AppPaths.APP_ROOT_PROPERTY, appRoot.toString());
        System.setProperty(AppPaths.LEGACY_ROOT_PROPERTY, legacyRoot.toString());

        Files.createDirectories(legacyRoot);
        Path legacyServerList = legacyRoot.resolve("easy-mc-server-list.json");
        Files.writeString(legacyServerList, "[]", StandardCharsets.UTF_8);

        GestorServidores gestor = new GestorServidores();

        Path migratedServerList = appRoot.resolve("config").resolve("easy-mc-server-list.json");
        assertThat(gestor.getListaServidores()).isEmpty();
        assertThat(migratedServerList).exists();
        assertThat(legacyServerList).doesNotExist();
    }
}
