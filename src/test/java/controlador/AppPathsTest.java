package controlador;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppPathsTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearPathOverrides() {
        System.clearProperty(AppPaths.APP_ROOT_PROPERTY);
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
}
