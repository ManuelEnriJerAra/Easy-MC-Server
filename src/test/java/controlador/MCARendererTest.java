package controlador;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MCARendererTest {
    @TempDir
    Path tempDir;

    @Test
    void renderRegion_debeRechazarArchivosVacios() throws Exception {
        Path emptyRegion = Files.createFile(tempDir.resolve("r.0.0.mca"));

        MCARenderer renderer = new MCARenderer();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> renderer.renderRegion(emptyRegion))
                .withMessageContaining("esta vacio");
    }

    @Test
    void renderWorldWithMetadata_debeRechazarListaVacia() {
        MCARenderer renderer = new MCARenderer();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> renderer.renderWorldWithMetadata(List.of(), MCARenderer.RenderOptions.defaults()))
                .withMessageContaining("No hay regiones");
    }

    @Test
    @Tag("smoke")
    void renderRegionYHasVisibleBlocks_debenProcesarRegionSimpleValida() throws Exception {
        Path regionFile = TestWorldFixtures.createSimpleRegion(tempDir.resolve("region"), 0, 0, 64, "minecraft:grass_block");
        MCARenderer renderer = new MCARenderer();

        boolean visible = renderer.hasVisibleBlocks(regionFile);
        BufferedImage image = renderer.renderRegion(regionFile, MCARenderer.RenderOptions.defaults().withPreferSquareCrop(false));

        assertThat(visible).isTrue();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);
    }
}
