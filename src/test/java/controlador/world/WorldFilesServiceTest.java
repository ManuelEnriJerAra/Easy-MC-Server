package controlador.world;

import modelo.Server;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class WorldFilesServiceTest {
    @TempDir
    Path tempDir;

    @Test
    @Tag("smoke")
    void readWriteServerProperties_debeMantenerRoundTrip() throws Exception {
        Server server = TestWorldFixtures.server(tempDir.resolve("server"));
        Properties properties = new Properties();
        properties.setProperty("motd", "Easy MC");

        WorldFilesService.writeServerProperties(server, properties);

        assertThat(WorldFilesService.readServerProperties(server)).containsEntry("motd", "Easy MC");
    }

    @Test
    void readWorldMetadata_debeDevolverVacioSiNoExisteArchivo() {
        assertThat(WorldFilesService.readWorldMetadata(TestWorldFixtures.world(tempDir.resolve("missing"), "missing")))
                .isEmpty();
    }

    @Test
    void getPreviewPathYMetadataPath_debenResolverSobreElMundo() throws Exception {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);

        assertThat(WorldFilesService.getPreviewPath(TestWorldFixtures.world(worldDir, "world")))
                .isEqualTo(worldDir.resolve("preview.png"));
        assertThat(WorldFilesService.getPreviewOverlayMetadataPath(TestWorldFixtures.world(worldDir, "world")))
                .isEqualTo(worldDir.resolve("easy-mc-preview-overlay.properties"));
    }

    @Test
    void getWorldMetadataPath_debeMigrarNombreLegacy() throws Exception {
        Path worldDir = tempDir.resolve("legacy-world");
        Files.createDirectories(worldDir);
        Path legacyPath = worldDir.resolve(".emw-world.properties");
        Files.writeString(legacyPath, "display-name=Legacy");

        Path metadataPath = WorldFilesService.getWorldMetadataPath(TestWorldFixtures.world(worldDir, "legacy-world"));

        assertThat(metadataPath).isEqualTo(worldDir.resolve("easy-mc-world-config.properties"));
        assertThat(metadataPath).exists();
        assertThat(legacyPath).doesNotExist();
    }

    @Test
    void getPreviewOverlayMetadataPath_debeMigrarNombreLegacy() throws Exception {
        Path worldDir = tempDir.resolve("legacy-preview");
        Files.createDirectories(worldDir);
        Path legacyPath = worldDir.resolve(".preview-overlay.properties");
        Files.writeString(legacyPath, "originBlockX=0");

        Path metadataPath = WorldFilesService.getPreviewOverlayMetadataPath(TestWorldFixtures.world(worldDir, "legacy-preview"));

        assertThat(metadataPath).isEqualTo(worldDir.resolve("easy-mc-preview-overlay.properties"));
        assertThat(metadataPath).exists();
        assertThat(legacyPath).doesNotExist();
    }
}
