package controlador;

import modelo.World;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GestorMundosTest {
    @TempDir
    Path tempDir;

    @Test
    @Tag("smoke")
    void sincronizarMundosServidor_debeMigrarMundoRaizYActualizarLevelName() throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path rootWorld = serverDir.resolve("world");
        Files.createDirectories(rootWorld);
        Files.writeString(rootWorld.resolve("level.dat"), "data");
        Files.createDirectories(serverDir.resolve("world_nether"));
        Files.createDirectories(serverDir.resolve("world_the_end"));

        Properties properties = new Properties();
        properties.setProperty("level-name", "world");
        TestWorldFixtures.writeServerProperties(serverDir, properties);

        boolean changed = GestorMundos.sincronizarMundosServidor(TestWorldFixtures.server(serverDir));

        assertThat(changed).isTrue();
        assertThat(serverDir.resolve("Easy-MC-Worlds/world")).isDirectory();
        assertThat(serverDir.resolve("Easy-MC-Worlds/world_nether")).isDirectory();
        assertThat(serverDir.resolve("Easy-MC-Worlds/world_the_end")).isDirectory();
        assertThat(rootWorld).doesNotExist();
        assertThat(Files.readString(serverDir.resolve("server.properties"))).contains("level-name=Easy-MC-Worlds/world");
    }

    @Test
    @Tag("smoke")
    void listarMundos_debeExcluirDimensionesAuxiliares() throws Exception {
        Path serverDir = tempDir.resolve("server-list");
        Path managedDir = serverDir.resolve(GestorMundos.DIRECTORIO_MUNDOS);
        Files.createDirectories(managedDir.resolve("world"));
        Files.createDirectories(managedDir.resolve("adventure"));
        Files.createDirectories(managedDir.resolve("world_nether"));
        Files.createDirectories(managedDir.resolve("world_the_end"));

        List<World> worlds = GestorMundos.listarMundos(TestWorldFixtures.server(serverDir));

        assertThat(worlds).extracting(World::getWorldName).containsExactly("adventure", "world");
    }

    @Test
    void getMundoActivo_debeResolverLevelNameGestionado() throws Exception {
        Path serverDir = tempDir.resolve("server-active");
        Path managedDir = serverDir.resolve(GestorMundos.DIRECTORIO_MUNDOS);
        Files.createDirectories(managedDir.resolve("creative"));
        Properties properties = new Properties();
        properties.setProperty("level-name", "Easy-MC-Worlds/creative");
        TestWorldFixtures.writeServerProperties(serverDir, properties);

        World activeWorld = GestorMundos.getMundoActivo(TestWorldFixtures.server(serverDir));

        assertThat(activeWorld.getWorldName()).isEqualTo("creative");
        assertThat(activeWorld.getWorldDir()).isEqualTo(managedDir.resolve("creative").toString());
    }

    @Test
    void sincronizarMundosServidor_debeCrearDirectorioGestionadoAunqueNoExistanMundos() throws Exception {
        Path serverDir = tempDir.resolve("empty-server");
        TestWorldFixtures.writeServerProperties(serverDir, new Properties());

        GestorMundos.sincronizarMundosServidor(TestWorldFixtures.server(serverDir));

        assertThat(serverDir.resolve(GestorMundos.DIRECTORIO_MUNDOS).resolve("world")).isDirectory();
    }
}
