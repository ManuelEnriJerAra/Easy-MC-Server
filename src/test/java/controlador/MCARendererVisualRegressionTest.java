package controlador;

import net.querz.mca.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MCARendererVisualRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void renderRegion_debeSerDeterministaParaLaMismaEntrada() throws Exception {
        Path regionFile = TestWorldFixtures.createRegion(tempDir.resolve("world"), 0, 0, mcaFile -> {
            Chunk chunk = TestWorldFixtures.createFullChunk();
            for(int z = 0; z < 16; z++) {
                for(int x = 0; x < 16; x++) {
                    TestWorldFixtures.fillColumn(chunk, x, z, 50, 56 + ((x + z) % 5), (x + z) % 3 == 0 ? "minecraft:stone" : "minecraft:grass_block");
                }
            }
            mcaFile.setChunk(0, 0, chunk);
        });
        MCARenderer renderer = new MCARenderer();
        MCARenderer.RenderOptions options = MCARenderer.RenderOptions.defaults()
                .withPreferSquareCrop(false)
                .withWaterSubsurfaceShading(true);

        BufferedImage first = renderer.renderRegion(regionFile, options);
        BufferedImage second = renderer.renderRegion(regionFile, options);

        assertThat(imageSignature(first)).isEqualTo(imageSignature(second));
    }

    @Test
    void renderRegion_debeIgnorarDecoracionTransparenteDeFormaConsistente() throws Exception {
        Path regionFile = TestWorldFixtures.createRegion(tempDir.resolve("transparent"), 0, 0, mcaFile -> {
            Chunk chunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(chunk, 0, 60, 0, "minecraft:stone");
            TestWorldFixtures.setBlock(chunk, 0, 61, 0, "minecraft:tall_grass");
            mcaFile.setChunk(0, 0, chunk);
        });
        MCARenderer renderer = new MCARenderer();

        BufferedImage image = renderer.renderRegion(
                regionFile,
                MCARenderer.RenderOptions.defaults().withPreferSquareCrop(false)
        );

        assertThat(image.getRGB(0, 0)).isNotEqualTo(MCARenderer.RenderOptions.defaults().defaultArgb());
    }

    @Test
    void renderRegion_noDebeTratarElAireComoSuperficieAunqueNoSeIgnorenTransparentes() throws Exception {
        Path regionFile = TestWorldFixtures.createRegion(tempDir.resolve("air-top"), 0, 0, mcaFile -> {
            Chunk chunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(chunk, 0, 60, 0, "minecraft:stone");
            mcaFile.setChunk(0, 0, chunk);
        });
        MCARenderer renderer = new MCARenderer();

        BufferedImage image = renderer.renderRegion(
                regionFile,
                MCARenderer.RenderOptions.performance().withPreferSquareCrop(false)
        );

        assertThat(image.getRGB(0, 0)).isNotEqualTo(MCARenderer.RenderOptions.performance().defaultArgb());
    }

    @Test
    void renderRegion_debeTenirElAguaConElFondoCuandoHaySubsurfaceShading() throws Exception {
        Path regionFile = TestWorldFixtures.createRegion(tempDir.resolve("water"), 0, 0, mcaFile -> {
            Chunk chunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(chunk, 0, 60, 0, "minecraft:sand");
            TestWorldFixtures.setBlock(chunk, 0, 61, 0, "minecraft:water");
            TestWorldFixtures.setBlock(chunk, 0, 62, 0, "minecraft:water");
            mcaFile.setChunk(0, 0, chunk);
        });
        MCARenderer renderer = new MCARenderer();

        BufferedImage plainWater = renderer.renderRegion(
                regionFile,
                MCARenderer.RenderOptions.defaults().withPreferSquareCrop(false).withShadeByHeight(false).withWaterSubsurfaceShading(false)
        );
        BufferedImage shadedWater = renderer.renderRegion(
                regionFile,
                MCARenderer.RenderOptions.defaults().withPreferSquareCrop(false).withShadeByHeight(false).withWaterSubsurfaceShading(true)
        );

        assertThat(shadedWater.getRGB(0, 0)).isNotEqualTo(plainWater.getRGB(0, 0));
    }

    @Test
    void renderRegion_debeEscalarCadaBloqueComoAreaUniforme() throws Exception {
        Path regionFile = TestWorldFixtures.createSimpleRegion(tempDir.resolve("scaled"), 0, 0, 64, "minecraft:grass_block");
        MCARenderer renderer = new MCARenderer();

        BufferedImage scaled = renderer.renderRegion(
                regionFile,
                MCARenderer.RenderOptions.defaults().withPreferSquareCrop(false).withShadeByHeight(false).withPixelsPerBlock(3)
        );

        assertThat(scaled.getWidth()).isEqualTo(3);
        assertThat(scaled.getHeight()).isEqualTo(3);
        int expected = scaled.getRGB(0, 0);
        for(int y = 0; y < scaled.getHeight(); y++) {
            for(int x = 0; x < scaled.getWidth(); x++) {
                assertThat(scaled.getRGB(x, y)).isEqualTo(expected);
            }
        }
    }

    @Test
    void renderWorld_debeResolverBloquesEnLimitesEntreRegiones() throws Exception {
        Path regionDir = tempDir.resolve("world-bounds");
        Path westRegion = TestWorldFixtures.createRegion(regionDir, 0, 0, mcaFile -> {
            Chunk edgeChunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(edgeChunk, 15, 70, 0, "minecraft:stone");
            mcaFile.setChunk(31, 0, edgeChunk);
        });
        Path eastRegion = TestWorldFixtures.createRegion(regionDir, 1, 0, mcaFile -> {
            Chunk edgeChunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(edgeChunk, 0, 71, 0, "minecraft:grass_block");
            mcaFile.setChunk(0, 0, edgeChunk);
        });

        MCARenderer renderer = new MCARenderer();
        BufferedImage image = renderer.renderWorld(
                java.util.List.of(westRegion, eastRegion),
                MCARenderer.RenderOptions.balanced()
                        .withPreferSquareCrop(false)
                        .withWorldBounds(511, 512, 0, 0)
        );

        assertThat(image.getWidth()).isEqualTo(2);
        assertThat(image.getHeight()).isEqualTo(1);
        assertThat(image.getRGB(0, 0)).isNotEqualTo(MCARenderer.RenderOptions.balanced().defaultArgb());
        assertThat(image.getRGB(1, 0)).isNotEqualTo(MCARenderer.RenderOptions.balanced().defaultArgb());
    }

    @Test
    void renderWorld_debeResolverEsquinasEnRecortesDePreview() throws Exception {
        Path regionDir = tempDir.resolve("world-corners");
        Path northWest = TestWorldFixtures.createRegion(regionDir, 0, 0, mcaFile -> {
            Chunk edgeChunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(edgeChunk, 15, 70, 15, "minecraft:stone");
            mcaFile.setChunk(31, 31, edgeChunk);
        });
        Path northEast = TestWorldFixtures.createRegion(regionDir, 1, 0, mcaFile -> {
            Chunk edgeChunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(edgeChunk, 0, 71, 15, "minecraft:grass_block");
            mcaFile.setChunk(0, 31, edgeChunk);
        });
        Path southWest = TestWorldFixtures.createRegion(regionDir, 0, 1, mcaFile -> {
            Chunk edgeChunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(edgeChunk, 15, 72, 0, "minecraft:sand");
            mcaFile.setChunk(31, 0, edgeChunk);
        });
        Path southEast = TestWorldFixtures.createRegion(regionDir, 1, 1, mcaFile -> {
            Chunk edgeChunk = TestWorldFixtures.createFullChunk();
            TestWorldFixtures.setBlock(edgeChunk, 0, 73, 0, "minecraft:snow_block");
            mcaFile.setChunk(0, 0, edgeChunk);
        });

        MCARenderer renderer = new MCARenderer();
        BufferedImage image = renderer.renderWorld(
                java.util.List.of(northWest, northEast, southWest, southEast),
                MCARenderer.RenderOptions.balanced()
                        .withPreferSquareCrop(false)
                        .withWorldBounds(511, 512, 511, 512)
        );

        assertThat(image.getWidth()).isEqualTo(2);
        assertThat(image.getHeight()).isEqualTo(2);
        assertThat(image.getRGB(0, 0)).isNotEqualTo(MCARenderer.RenderOptions.balanced().defaultArgb());
        assertThat(image.getRGB(1, 0)).isNotEqualTo(MCARenderer.RenderOptions.balanced().defaultArgb());
        assertThat(image.getRGB(0, 1)).isNotEqualTo(MCARenderer.RenderOptions.balanced().defaultArgb());
        assertThat(image.getRGB(1, 1)).isNotEqualTo(MCARenderer.RenderOptions.balanced().defaultArgb());
    }

    @Test
    void colorMath_debeOscurecerAguaProfundaMasQueAguaSomera() throws Exception {
        MCARenderer renderer = new MCARenderer();
        Method resolveBaseColor = MCARenderer.class.getDeclaredMethod("resolveBaseColor", String.class);
        Method resolveWaterSurfaceColor = MCARenderer.class.getDeclaredMethod("resolveWaterSurfaceColor", Color.class, String.class, int.class);
        resolveBaseColor.setAccessible(true);
        resolveWaterSurfaceColor.setAccessible(true);

        Color baseWater = (Color) resolveBaseColor.invoke(renderer, "minecraft:water");
        Color shallow = (Color) resolveWaterSurfaceColor.invoke(renderer, baseWater, "minecraft:sand", 1);
        Color deep = (Color) resolveWaterSurfaceColor.invoke(renderer, baseWater, "minecraft:sand", 8);

        int shallowDistanceToSand = colorDistance(shallow, new Color(218, 205, 135));
        int deepDistanceToSand = colorDistance(deep, new Color(218, 205, 135));
        assertThat(deepDistanceToSand).isGreaterThan(shallowDistanceToSand);
    }

    @Test
    void colorMath_debeAplicarContrasteDeSombreadoEnPendientes() throws Exception {
        MCARenderer renderer = new MCARenderer();
        Method calculateHeightShade = MCARenderer.class.getDeclaredMethod("calculateHeightShade", int.class, int.class, int.class, int.class, double.class, double.class, double.class);
        calculateHeightShade.setAccessible(true);

        int flatShade = (int) calculateHeightShade.invoke(renderer, 80, 80, 80, 80, 0.38d, 0.62d, 0.38d);
        int slopeShade = (int) calculateHeightShade.invoke(renderer, 70, 90, 75, 85, 0.38d, 0.62d, 0.38d);

        assertThat(Math.abs(slopeShade)).isGreaterThan(Math.abs(flatShade));
    }

    @Test
    void colorMath_debeDiferenciarTintesDeBiomaEnVegetacion() throws Exception {
        MCARenderer renderer = new MCARenderer();
        Method applyBiomeTint = MCARenderer.class.getDeclaredMethod("applyBiomeTint", Color.class, String.class, String.class);
        applyBiomeTint.setAccessible(true);

        Color base = new Color(100, 164, 76);
        Color plains = (Color) applyBiomeTint.invoke(renderer, base, "minecraft:grass_block", "minecraft:plains");
        Color swamp = (Color) applyBiomeTint.invoke(renderer, base, "minecraft:grass_block", "minecraft:swamp");

        assertThat(plains.getRGB()).isNotEqualTo(swamp.getRGB());
    }

    @Test
    void colorMath_debeSuavizarElSombreadoDeNieveFrenteAVegetacion() throws Exception {
        MCARenderer renderer = new MCARenderer();
        Method refineShadeForMaterial = MCARenderer.class.getDeclaredMethod(
                "refineShadeForMaterial",
                String.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class
        );
        refineShadeForMaterial.setAccessible(true);

        int snowShade = (int) refineShadeForMaterial.invoke(renderer, "minecraft:snow_block", 20, 70, 90, 75, 85, 0);
        int grassShade = (int) refineShadeForMaterial.invoke(renderer, "minecraft:grass_block", 20, 70, 90, 75, 85, 0);

        assertThat(Math.abs(snowShade)).isLessThan(Math.abs(grassShade));
    }

    @Test
    void renderOptions_presetsDebenSepararCalidadEquilibradoYRendimiento() {
        MCARenderer.RenderOptions quality = MCARenderer.RenderOptions.quality();
        MCARenderer.RenderOptions balanced = MCARenderer.RenderOptions.balanced();
        MCARenderer.RenderOptions performance = MCARenderer.RenderOptions.performance();
        MCARenderer.RenderOptions ultraPerformance = MCARenderer.RenderOptions.ultraPerformance();

        assertThat(quality.advancedMaterialShading()).isTrue();
        assertThat(quality.advancedWaterColoring()).isTrue();
        assertThat(quality.advancedBiomeColoring()).isTrue();
        assertThat(quality.waterSubsurfaceShading()).isTrue();
        assertThat(quality.biomeColoring()).isTrue();
        assertThat(balanced.advancedMaterialShading()).isFalse();
        assertThat(balanced.advancedWaterColoring()).isFalse();
        assertThat(balanced.advancedBiomeColoring()).isFalse();
        assertThat(balanced.waterSubsurfaceShading()).isTrue();
        assertThat(balanced.biomeColoring()).isTrue();
        assertThat(performance.advancedMaterialShading()).isFalse();
        assertThat(performance.advancedWaterColoring()).isFalse();
        assertThat(performance.advancedBiomeColoring()).isFalse();
        assertThat(performance.shadeByHeight()).isTrue();
        assertThat(performance.waterSubsurfaceShading()).isFalse();
        assertThat(performance.biomeColoring()).isTrue();
        assertThat(ultraPerformance.shadeByHeight()).isFalse();
        assertThat(ultraPerformance.waterSubsurfaceShading()).isFalse();
        assertThat(ultraPerformance.biomeColoring()).isFalse();
        assertThat(MCARenderer.RenderOptions.defaults().advancedMaterialShading()).isFalse();
    }

    private long imageSignature(BufferedImage image) {
        long signature = 1_125_899_906_842_597L;
        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                signature = (signature * 1_099_511_628_211L) ^ image.getRGB(x, y);
            }
        }
        signature ^= image.getWidth();
        signature ^= ((long) image.getHeight()) << 32;
        return signature;
    }

    private int colorDistance(Color a, Color b) {
        int dr = a.getRed() - b.getRed();
        int dg = a.getGreen() - b.getGreen();
        int db = a.getBlue() - b.getBlue();
        return (dr * dr) + (dg * dg) + (db * db);
    }
}
