package controlador.world;

import controlador.MCARenderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorldPreviewOverlayServiceTest {
    @TempDir
    Path tempDir;

    @Test
    @Tag("smoke")
    void saveAndLoadOverlayData_debePersistirSoloJugadoresValidos() throws Exception {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        var world = TestWorldFixtures.world(worldDir, "world");
        PreviewOverlayData overlayData = new PreviewOverlayData(
                10,
                -20,
                2,
                new MCARenderer.WorldPoint(4, 5),
                List.of(
                        new PreviewPlayerPoint("Alex", new MCARenderer.WorldPoint(1, 2)),
                        new PreviewPlayerPoint(" ", new MCARenderer.WorldPoint(7, 8)),
                        new PreviewPlayerPoint("Steve", new MCARenderer.WorldPoint(-3, 6))
                )
        );

        WorldPreviewOverlayService.saveOverlayData(world, overlayData);
        PreviewOverlayData loaded = WorldPreviewOverlayService.loadOverlayData(world);

        assertThat(loaded.originBlockX()).isEqualTo(10);
        assertThat(loaded.originBlockZ()).isEqualTo(-20);
        assertThat(loaded.pixelsPerBlock()).isEqualTo(2);
        assertThat(loaded.spawnPoint()).isEqualTo(new MCARenderer.WorldPoint(4, 5));
        assertThat(loaded.playerPoints()).containsExactly(
                new PreviewPlayerPoint("Alex", new MCARenderer.WorldPoint(1, 2)),
                new PreviewPlayerPoint("Steve", new MCARenderer.WorldPoint(-3, 6))
        );
    }

    @Test
    void loadOverlayData_debeDevolverNullSiElArchivoEsInvalido() throws Exception {
        Path worldDir = tempDir.resolve("broken-world");
        Files.createDirectories(worldDir);
        Files.writeString(worldDir.resolve(".preview-overlay.properties"), "originBlockX=invalid");

        assertThat(WorldPreviewOverlayService.loadOverlayData(TestWorldFixtures.world(worldDir, "broken-world")))
                .isNull();
    }
}
