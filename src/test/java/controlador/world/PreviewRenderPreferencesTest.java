package controlador.world;

import controlador.MCARenderer;
import modelo.Server;
import modelo.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewRenderPreferencesTest {
    @TempDir
    Path tempDir;

    @Test
    void presets_debenAplicarLaConfiguracionEsperada() {
        PreviewRenderPreferences quality = PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.QUALITY);
        PreviewRenderPreferences balanced = PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.BALANCED);
        PreviewRenderPreferences performance = PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.PERFORMANCE);
        PreviewRenderPreferences ultraPerformance = PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.ULTRA_PERFORMANCE);

        assertThat(quality.isEnabled(PreviewRenderPreferences.RenderToggle.ADVANCED_WATER_COLORING)).isTrue();
        assertThat(quality.isEnabled(PreviewRenderPreferences.RenderToggle.ADVANCED_BIOME_COLORING)).isTrue();

        assertThat(balanced.isEnabled(PreviewRenderPreferences.RenderToggle.SHADE_BY_HEIGHT)).isTrue();
        assertThat(balanced.isEnabled(PreviewRenderPreferences.RenderToggle.WATER_SUBSURFACE_SHADING)).isTrue();
        assertThat(balanced.isEnabled(PreviewRenderPreferences.RenderToggle.BIOME_COLORING)).isTrue();
        assertThat(balanced.isEnabled(PreviewRenderPreferences.RenderToggle.ADVANCED_MATERIAL_SHADING)).isFalse();

        assertThat(performance.isEnabled(PreviewRenderPreferences.RenderToggle.SHADE_BY_HEIGHT)).isTrue();
        assertThat(performance.isEnabled(PreviewRenderPreferences.RenderToggle.WATER_SUBSURFACE_SHADING)).isFalse();
        assertThat(performance.isEnabled(PreviewRenderPreferences.RenderToggle.BIOME_COLORING)).isTrue();
        assertThat(ultraPerformance.isEnabled(PreviewRenderPreferences.RenderToggle.SHADE_BY_HEIGHT)).isFalse();
        assertThat(ultraPerformance.isEnabled(PreviewRenderPreferences.RenderToggle.WATER_SUBSURFACE_SHADING)).isFalse();
        assertThat(ultraPerformance.isEnabled(PreviewRenderPreferences.RenderToggle.BIOME_COLORING)).isFalse();
    }

    @Test
    void modificarUnToggle_debePasarAPersonalizado() {
        PreviewRenderPreferences balanced = PreviewRenderPreferences.defaults();

        PreviewRenderPreferences custom = balanced.withRenderToggle(PreviewRenderPreferences.RenderToggle.ADVANCED_WATER_COLORING, true);

        assertThat(custom.preset()).isEqualTo(PreviewRenderPreferences.PreviewRenderPreset.CUSTOM);
        assertThat(custom.isEnabled(PreviewRenderPreferences.RenderToggle.ADVANCED_WATER_COLORING)).isTrue();
    }

    @Test
    void persistencia_debeConservarPresetYTogglesEnMetadataDelMundo() throws Exception {
        World world = new World(tempDir.resolve("world").toString(), "world");
        Files.createDirectories(Path.of(world.getWorldDir()));
        PreviewRenderPreferences preferences = PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.QUALITY)
                .withRenderToggle(PreviewRenderPreferences.RenderToggle.ADVANCED_MATERIAL_SHADING, false)
                .withRenderLimitPixels(1024)
                .withRenderCenterId("player:Steve")
                .withShowChunkGrid(true);

        Properties metadata = new Properties();
        metadata.setProperty("level-type", "default");
        preferences.writeTo(metadata);
        WorldFilesService.writeWorldMetadata(world, metadata);

        Properties reloaded = WorldFilesService.readWorldMetadata(world);
        PreviewRenderPreferences restored = PreviewRenderPreferences.fromProperties(reloaded, PreviewRenderPreferences.defaults());

        assertThat(restored.preset()).isEqualTo(PreviewRenderPreferences.PreviewRenderPreset.CUSTOM);
        assertThat(restored.isEnabled(PreviewRenderPreferences.RenderToggle.ADVANCED_MATERIAL_SHADING)).isFalse();
        assertThat(restored.renderLimitPixels()).isEqualTo(1024);
        assertThat(restored.renderCenterId()).isEqualTo("player:Steve");
        assertThat(restored.showChunkGrid()).isTrue();
        assertThat(reloaded.getProperty("level-type")).isEqualTo("default");
    }

    @Test
    void legacyServer_debeMigrarPresetPerformanceAntiguoAUltraRendimiento() {
        Server server = new Server();
        server.setPreviewRenderProfileId("performance");
        server.setPreviewRenderRealtime(true);
        server.setPreviewShowSpawn(true);
        server.setPreviewRenderLimitPixels(512);
        server.setPreviewRenderCenterId("spawn");

        PreviewRenderPreferences restored = PreviewRenderPreferences.fromLegacyServer(server);

        assertThat(restored.preset()).isEqualTo(PreviewRenderPreferences.PreviewRenderPreset.ULTRA_PERFORMANCE);
        assertThat(restored.renderRealtime()).isTrue();
        assertThat(restored.showSpawn()).isTrue();
        assertThat(restored.renderLimitPixels()).isEqualTo(512);
    }

    @Test
    void persistencia_debeIgnorarTogglesRetiradosSinRomperLaCarga() {
        Properties properties = new Properties();
        properties.setProperty("preview.menu.preset", "performance");
        properties.setProperty("preview.menu.toggle.neighborHeightHints", "true");
        properties.setProperty("preview.menu.toggle.ignoreTransparentBlocks", "false");

        PreviewRenderPreferences restored = PreviewRenderPreferences.fromProperties(properties, PreviewRenderPreferences.defaults());

        assertThat(restored.preset()).isEqualTo(PreviewRenderPreferences.PreviewRenderPreset.ULTRA_PERFORMANCE);
        assertThat(restored.isEnabled(PreviewRenderPreferences.RenderToggle.SHADE_BY_HEIGHT)).isFalse();
        assertThat(restored.isEnabled(PreviewRenderPreferences.RenderToggle.BIOME_COLORING)).isFalse();
    }

    @Test
    void conversionARenderOptions_debeReflejarTodosLosFlagsVisibles() {
        PreviewRenderPreferences preferences = PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.BALANCED)
                .withRenderToggle(PreviewRenderPreferences.RenderToggle.ADVANCED_MATERIAL_SHADING, true);

        MCARenderer.RenderOptions options = preferences.toRenderOptions();

        assertThat(options.shadeByHeight()).isTrue();
        assertThat(options.waterSubsurfaceShading()).isTrue();
        assertThat(options.biomeColoring()).isTrue();
        assertThat(options.advancedMaterialShading()).isTrue();
    }
}
