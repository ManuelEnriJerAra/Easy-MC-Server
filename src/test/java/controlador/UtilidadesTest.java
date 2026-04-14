package controlador;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class UtilidadesTest {
    @TempDir
    Path tempDir;

    @Test
    @Tag("smoke")
    void copiarDirectorio_debeCopiarArbolCompleto() throws Exception {
        Path origen = tempDir.resolve("origen");
        Path destino = tempDir.resolve("destino");
        Files.createDirectories(origen.resolve("sub"));
        Files.writeString(origen.resolve("root.txt"), "root");
        Files.writeString(origen.resolve("sub/data.txt"), "nested");

        Utilidades.copiarDirectorio(origen, destino);

        assertThat(destino.resolve("root.txt")).hasContent("root");
        assertThat(destino.resolve("sub/data.txt")).hasContent("nested");
    }

    @Test
    void moverDirectorio_debeMoverContenidoYEliminarOrigen() throws Exception {
        Path origen = tempDir.resolve("world");
        Path destino = tempDir.resolve("migrated/world");
        Files.createDirectories(origen);
        Files.writeString(origen.resolve("level.dat"), "data");

        Utilidades.moverDirectorio(origen, destino);

        assertThat(origen).doesNotExist();
        assertThat(destino.resolve("level.dat")).hasContent("data");
    }

    @Test
    void eliminarDirectorio_debeBorrarArbolCompleto() throws Exception {
        Path directorio = tempDir.resolve("delete-me");
        Files.createDirectories(directorio.resolve("nested"));
        Files.writeString(directorio.resolve("nested/file.txt"), "bye");

        Utilidades.eliminarDirectorio(directorio);

        assertThat(directorio).doesNotExist();
    }

    @Test
    @Tag("smoke")
    void escribirMotdYPuerto_debePersistirValoresEnProperties() throws Exception {
        Path serverDir = tempDir.resolve("server");

        Utilidades.escribirPuertoEnProperties(serverDir, 25570);
        Utilidades.escribirMotdEnProperties(serverDir, "Hello world");

        assertThat(Utilidades.leerMotdDesdeProperties(serverDir)).isEqualTo("Hello world");
        assertThat(Files.readString(serverDir.resolve("server.properties"))).contains("server-port=25570");
    }

    @Test
    void encontrarEjecutableJar_debeDevolverElUnicoJarDisponible() throws Exception {
        Path serverDir = tempDir.resolve("server");
        Files.createDirectories(serverDir);
        Path jar = Files.writeString(serverDir.resolve("paper.jar"), "jar");

        assertThat(Utilidades.encontrarEjecutableJar(serverDir)).isEqualTo(jar);
    }

    @Test
    void encontrarEjecutableJar_debeFallarSiHayMasDeUnJar() throws Exception {
        Path serverDir = tempDir.resolve("server");
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("a.jar"), "a");
        Files.writeString(serverDir.resolve("b.jar"), "b");

        assertThatIllegalStateException()
                .isThrownBy(() -> Utilidades.encontrarEjecutableJar(serverDir))
                .withMessageContaining("m\u00E1s de un jar");
    }

    @Test
    @Tag("smoke")
    void extraerPorcentaje_debeLeerNumeroAntesDelSimboloPorcentaje() {
        assertThat(Utilidades.extraerPorcentaje("Downloading libraries 83%")).isEqualTo(83);
    }

    @Test
    void resolveSystemPicturesDirectory_debePriorizarPicturesDeWindows() throws Exception {
        Path home = tempDir.resolve("home");
        Path pictures = home.resolve("Pictures");
        Files.createDirectories(pictures);

        assertThat(Utilidades.resolveSystemPicturesDirectory("Windows 11", home.toString(), Map.of("USERPROFILE", home.toString())))
                .isEqualTo(pictures.toFile());
    }

    @Test
    void resolveSystemPicturesDirectory_debeLeerXdgPicturesDir() throws Exception {
        Path home = tempDir.resolve("home");
        Path configDir = tempDir.resolve("config");
        Path pictures = home.resolve("Media").resolve("Capturas");
        Files.createDirectories(pictures);
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("user-dirs.dirs"), "XDG_PICTURES_DIR=\"$HOME/Media/Capturas\"");

        assertThat(Utilidades.resolveSystemPicturesDirectory(
                "Linux",
                home.toString(),
                Map.of("XDG_CONFIG_HOME", configDir.toString())
        )).isEqualTo(pictures.toFile());
    }
}
