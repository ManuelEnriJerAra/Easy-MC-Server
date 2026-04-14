package controlador;

import net.querz.mca.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MCARendererBenchmarkTest {
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 8;

    @TempDir
    Path tempDir;

    @Test
    void benchmark_renderPipeline_debeGenerarMetricasComparables() throws Exception {
        List<Path> regions = createBenchmarkRegions(tempDir.resolve("bench"));
        MCARenderer renderer = new MCARenderer();

        Map<String, MCARenderer.RenderOptions> scenarios = new LinkedHashMap<>();
        scenarios.put("baseline_original", originalBaselineOptions());
        scenarios.put("baseline_degradado", degradedBaselineOptions());
        scenarios.put("final_quality", qualityOptions());
        scenarios.put("final_balanced", balancedOptions());
        scenarios.put("final_performance", performanceOptions());
        scenarios.put("final_ultra_performance", ultraPerformanceOptions());
        scenarios.put("coste_base_pipeline", MCARenderer.RenderOptions.ultraPerformance().withPreferSquareCrop(false));
        scenarios.put("coste_basic_shading", MCARenderer.RenderOptions.ultraPerformance().withPreferSquareCrop(false).withShadeByHeight(true));
        scenarios.put("coste_water_fast", MCARenderer.RenderOptions.ultraPerformance().withPreferSquareCrop(false).withShadeByHeight(true).withWaterSubsurfaceShading(true));
        scenarios.put("coste_water_advanced", MCARenderer.RenderOptions.ultraPerformance().withPreferSquareCrop(false).withShadeByHeight(true).withWaterSubsurfaceShading(true).withAdvancedWaterColoring(true));
        scenarios.put("coste_material_shading", MCARenderer.RenderOptions.ultraPerformance().withPreferSquareCrop(false).withShadeByHeight(true).withWaterSubsurfaceShading(true).withAdvancedWaterColoring(true).withAdvancedMaterialShading(true));
        scenarios.put("coste_biome_fast", MCARenderer.RenderOptions.ultraPerformance().withPreferSquareCrop(false).withShadeByHeight(true).withWaterSubsurfaceShading(true).withAdvancedWaterColoring(true).withBiomeColoring(true));
        scenarios.put("coste_biome_advanced", MCARenderer.RenderOptions.ultraPerformance().withPreferSquareCrop(false).withShadeByHeight(true).withWaterSubsurfaceShading(true).withAdvancedWaterColoring(true).withAdvancedMaterialShading(true).withBiomeColoring(true).withAdvancedBiomeColoring(true));

        Map<String, BenchmarkResult> results = new LinkedHashMap<>();
        for(Map.Entry<String, MCARenderer.RenderOptions> entry : scenarios.entrySet()) {
            results.put(entry.getKey(), runScenario(renderer, regions, entry.getValue()));
        }

        Path outputDir = Path.of("target", "benchmarks");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("mca-render-benchmark.txt"), renderBenchmarkReport(results));

        BenchmarkResult baselineOriginal = results.get("baseline_original");
        BenchmarkResult baselineDegraded = results.get("baseline_degradado");
        BenchmarkResult finalQuality = results.get("final_quality");
        BenchmarkResult finalBalanced = results.get("final_balanced");
        BenchmarkResult finalPerformance = results.get("final_performance");
        BenchmarkResult finalUltraPerformance = results.get("final_ultra_performance");

        assertThat(baselineOriginal).isNotNull();
        assertThat(baselineDegraded).isNotNull();
        assertThat(finalQuality).isNotNull();
        assertThat(finalBalanced).isNotNull();
        assertThat(finalPerformance).isNotNull();
        assertThat(finalUltraPerformance).isNotNull();
        assertThat(Files.isRegularFile(outputDir.resolve("mca-render-benchmark.txt"))).isTrue();
    }

    static MCARenderer.RenderOptions originalBaselineOptions() {
        return MCARenderer.RenderOptions.performance()
                .withPreferSquareCrop(false)
                .withWaterSubsurfaceShading(true)
                .withShadeByHeight(true);
    }

    static MCARenderer.RenderOptions degradedBaselineOptions() {
        return MCARenderer.RenderOptions.performance()
                .withPreferSquareCrop(false)
                .withWaterSubsurfaceShading(true)
                .withAdvancedWaterColoring(true)
                .withAdvancedMaterialShading(true);
    }

    static MCARenderer.RenderOptions balancedOptions() {
        return MCARenderer.RenderOptions.balanced()
                .withPreferSquareCrop(false);
    }

    static MCARenderer.RenderOptions qualityOptions() {
        return MCARenderer.RenderOptions.quality()
                .withPreferSquareCrop(false);
    }

    static MCARenderer.RenderOptions performanceOptions() {
        return MCARenderer.RenderOptions.performance()
                .withPreferSquareCrop(false);
    }

    static MCARenderer.RenderOptions ultraPerformanceOptions() {
        return MCARenderer.RenderOptions.ultraPerformance()
                .withPreferSquareCrop(false);
    }

    static List<Path> createBenchmarkRegions(Path baseDir) throws IOException {
        List<Path> regions = new ArrayList<>();
        regions.add(createBenchmarkRegion(baseDir, 0, 0, 0));
        regions.add(createBenchmarkRegion(baseDir, 1, 0, 1));
        regions.add(createBenchmarkRegion(baseDir, 0, 1, 2));
        regions.add(createBenchmarkRegion(baseDir, 1, 1, 3));
        return regions;
    }

    private static Path createBenchmarkRegion(Path baseDir, int regionX, int regionZ, int variant) throws IOException {
        return TestWorldFixtures.createRegion(baseDir, regionX, regionZ, mcaFile -> {
            for(int chunkZ = 0; chunkZ < 8; chunkZ++) {
                for(int chunkX = 0; chunkX < 8; chunkX++) {
                    Chunk chunk = TestWorldFixtures.createFullChunk();
                    populateChunk(chunk, chunkX, chunkZ, variant);
                    mcaFile.setChunk(chunkX, chunkZ, chunk);
                }
            }
        });
    }

    private static void populateChunk(Chunk chunk, int chunkX, int chunkZ, int variant) {
        for(int localZ = 0; localZ < 16; localZ++) {
            for(int localX = 0; localX < 16; localX++) {
                int globalX = (chunkX * 16) + localX + (variant * 7);
                int globalZ = (chunkZ * 16) + localZ + (variant * 11);
                int terrainHeight = 58 + Math.floorMod((globalX * 5) + (globalZ * 3), 20);
                String topBlock = selectSurfaceBlock(globalX, globalZ, terrainHeight);
                TestWorldFixtures.fillColumn(chunk, localX, localZ, 30, terrainHeight - 1, terrainHeight < 62 ? "minecraft:stone" : "minecraft:dirt");
                TestWorldFixtures.setBlock(chunk, localX, terrainHeight, localZ, topBlock);
                if((globalX + globalZ) % 7 == 0) {
                    TestWorldFixtures.setBlock(chunk, localX, terrainHeight + 1, localZ, "minecraft:oak_leaves");
                }
                if(terrainHeight < 63) {
                    for(int waterY = terrainHeight + 1; waterY <= 63; waterY++) {
                        TestWorldFixtures.setBlock(chunk, localX, waterY, localZ, "minecraft:water");
                    }
                }
            }
        }
    }

    private static String selectSurfaceBlock(int globalX, int globalZ, int terrainHeight) {
        if(terrainHeight >= 73) {
            return "minecraft:snow_block";
        }
        if(terrainHeight < 63) {
            return (globalX + globalZ) % 2 == 0 ? "minecraft:sand" : "minecraft:clay";
        }
        int selector = Math.floorMod(globalX * 13 + globalZ * 17, 9);
        return switch (selector) {
            case 0, 1 -> "minecraft:grass_block";
            case 2 -> "minecraft:stone";
            case 3 -> "minecraft:gravel";
            case 4 -> "minecraft:moss_block";
            case 5 -> "minecraft:oak_planks";
            case 6 -> "minecraft:terracotta";
            case 7 -> "minecraft:oak_log";
            default -> "minecraft:dirt";
        };
    }

    private BenchmarkResult runScenario(MCARenderer renderer, List<Path> regions, MCARenderer.RenderOptions options) throws Exception {
        for(int i = 0; i < WARMUP_ITERATIONS; i++) {
            renderer.renderRegion(regions.getFirst(), options);
            renderer.renderWorldWithMetadata(regions, options);
        }

        List<Double> regionMillis = new ArrayList<>();
        List<Double> worldMillis = new ArrayList<>();
        List<Double> trackedMillis = new ArrayList<>();
        List<Double> dataLoadMillis = new ArrayList<>();
        List<Double> blockSampleMillis = new ArrayList<>();
        List<Double> biomeSampleMillis = new ArrayList<>();
        List<Double> shadeColorMillis = new ArrayList<>();
        List<Double> regionComposeMillis = new ArrayList<>();
        List<Double> worldComposeMillis = new ArrayList<>();
        List<Double> cropMillis = new ArrayList<>();
        int threadCount = 1;
        for(int i = 0; i < MEASURE_ITERATIONS; i++) {
            long regionStart = System.nanoTime();
            renderer.renderRegion(regions.getFirst(), options);
            regionMillis.add(nanosToMillis(System.nanoTime() - regionStart));

            long worldStart = System.nanoTime();
            MCARenderer.RenderedWorld renderedWorld = renderer.renderWorldWithMetadata(regions, options);
            worldMillis.add(nanosToMillis(System.nanoTime() - worldStart));
            trackedMillis.add(nanosToMillis(renderedWorld.stats().totalTrackedNanos()));
            MCARenderer.RenderPhases phases = renderedWorld.stats().phases();
            dataLoadMillis.add(nanosToMillis(phases.dataLoadNanos()));
            blockSampleMillis.add(nanosToMillis(phases.blockSampleNanos()));
            biomeSampleMillis.add(nanosToMillis(phases.biomeSampleNanos()));
            shadeColorMillis.add(nanosToMillis(phases.shadeColorNanos()));
            regionComposeMillis.add(nanosToMillis(phases.regionComposeNanos()));
            worldComposeMillis.add(nanosToMillis(phases.worldComposeNanos()));
            cropMillis.add(nanosToMillis(phases.cropNanos()));
            threadCount = renderedWorld.stats().threadCount();
        }
        return new BenchmarkResult(
                summary(regionMillis),
                summary(worldMillis),
                summary(trackedMillis),
                threadCount,
                summary(dataLoadMillis),
                summary(blockSampleMillis),
                summary(biomeSampleMillis),
                summary(shadeColorMillis),
                summary(regionComposeMillis),
                summary(worldComposeMillis),
                summary(cropMillis)
        );
    }

    private String renderBenchmarkReport(Map<String, BenchmarkResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("iterations=").append(MEASURE_ITERATIONS).append(System.lineSeparator());
        builder.append("warmups=").append(WARMUP_ITERATIONS).append(System.lineSeparator());
        for(Map.Entry<String, BenchmarkResult> entry : results.entrySet()) {
            String label = entry.getKey();
            BenchmarkResult result = entry.getValue();
            builder.append(System.lineSeparator()).append("[").append(label).append("]").append(System.lineSeparator());
            builder.append(formatStats("region", result.region())).append(System.lineSeparator());
            builder.append(formatStats("world", result.world())).append(System.lineSeparator());
            builder.append(formatStats("tracked", result.tracked())).append(System.lineSeparator());
            builder.append("threads=").append(result.threadCount()).append(System.lineSeparator());
            builder.append(formatStats("phase.dataLoad", result.dataLoad())).append(System.lineSeparator());
            builder.append(formatStats("phase.blockSample", result.blockSample())).append(System.lineSeparator());
            builder.append(formatStats("phase.biomeSample", result.biomeSample())).append(System.lineSeparator());
            builder.append(formatStats("phase.shadeColor", result.shadeColor())).append(System.lineSeparator());
            builder.append(formatStats("phase.regionCompose", result.regionCompose())).append(System.lineSeparator());
            builder.append(formatStats("phase.worldCompose", result.worldCompose())).append(System.lineSeparator());
            builder.append(formatStats("phase.crop", result.crop())).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String formatStats(String prefix, MetricSummary summary) {
        return String.format(
                Locale.ROOT,
                "%s.avg.ms=%.3f %s.median.ms=%.3f %s.min.ms=%.3f %s.max.ms=%.3f %s.stddev.ms=%.3f",
                prefix,
                summary.averageMillis(),
                prefix,
                summary.medianMillis(),
                prefix,
                summary.minMillis(),
                prefix,
                summary.maxMillis(),
                prefix,
                summary.stddevMillis()
        );
    }

    private MetricSummary summary(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        DoubleSummaryStatistics stats = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        double average = stats.getAverage();
        double median = sorted.size() % 2 == 0
                ? (sorted.get((sorted.size() / 2) - 1) + sorted.get(sorted.size() / 2)) / 2.0d
                : sorted.get(sorted.size() / 2);
        double variance = values.stream()
                .mapToDouble(value -> {
                    double delta = value - average;
                    return delta * delta;
                })
                .average()
                .orElse(0d);
        return new MetricSummary(average, median, stats.getMin(), stats.getMax(), Math.sqrt(variance));
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    record BenchmarkResult(MetricSummary region,
                           MetricSummary world,
                           MetricSummary tracked,
                           int threadCount,
                           MetricSummary dataLoad,
                           MetricSummary blockSample,
                           MetricSummary biomeSample,
                           MetricSummary shadeColor,
                           MetricSummary regionCompose,
                           MetricSummary worldCompose,
                           MetricSummary crop) {
        double worldMedianMillis() {
            return world.medianMillis();
        }
    }

    record MetricSummary(double averageMillis, double medianMillis, double minMillis, double maxMillis, double stddevMillis) {
    }
}
