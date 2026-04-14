package controlador;

import controlador.world.PreviewRenderPreferences;
import modelo.World;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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

class MCARendererRealWorldComparisonTest {
    private static final int WARMUP_ITERATIONS = 1;
    private static final int MEASURE_ITERATIONS = 3;
    private static final int DEFAULT_LIMIT_BLOCKS = 1024;

    @Test
    void generarMetricasYArtefactosParaMundoReal() throws Exception {
        String rawWorldPath = System.getProperty("easymc.realWorldPath");
        Assumptions.assumeTrue(rawWorldPath != null && !rawWorldPath.isBlank(), "Falta -Deasymc.realWorldPath");

        Path worldPath = Path.of(rawWorldPath);
        Assumptions.assumeTrue(Files.isDirectory(worldPath), "La ruta del mundo real no existe");

        World world = new World(worldPath.toString(), worldPath.getFileName().toString());
        List<Path> regions = selectRegionsForPreview(worldPath, resolveCenter(world), DEFAULT_LIMIT_BLOCKS);
        Assumptions.assumeFalse(regions.isEmpty(), "No se han encontrado regiones para el benchmark real");

        MCARenderer.WorldPoint center = resolveCenter(world);
        Path outputDir = Path.of("target", "real-world-preview");
        Files.createDirectories(outputDir);

        MCARenderer renderer = new MCARenderer();
        Map<String, PreviewRenderPreferences> presets = new LinkedHashMap<>();
        presets.put("quality", PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.QUALITY));
        presets.put("balanced", PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.BALANCED));
        presets.put("performance", PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.PERFORMANCE));
        presets.put("ultra-performance", PreviewRenderPreferences.presetDefaults(PreviewRenderPreferences.PreviewRenderPreset.ULTRA_PERFORMANCE));

        Map<String, ScenarioResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, PreviewRenderPreferences> entry : presets.entrySet()) {
            ScenarioResult result = benchmarkScenario(renderer, regions, center, entry.getValue());
            results.put(entry.getKey(), result);
            ImageIO.write(result.image(), "png", outputDir.resolve(entry.getKey() + ".png").toFile());
        }

        writeArtifacts(outputDir, "quality_vs_balanced", results.get("quality").image(), results.get("balanced").image());
        writeArtifacts(outputDir, "balanced_vs_performance", results.get("balanced").image(), results.get("performance").image());
        writeArtifacts(outputDir, "performance_vs_ultra_performance", results.get("performance").image(), results.get("ultra-performance").image());
        ImageIO.write(
                createTriptych(results.get("quality").image(), results.get("balanced").image(), results.get("performance").image()),
                "png",
                outputDir.resolve("quality_balanced_performance-triptych.png").toFile()
        );
        Files.writeString(outputDir.resolve("benchmark.txt"), renderReport(worldPath, regions.size(), center, results));
        Files.writeString(outputDir.resolve("benchmark.md"), renderMarkdownReport(worldPath, regions.size(), center, results));
        Files.writeString(outputDir.resolve("benchmark.csv"), renderCsvReport(results));

        assertThat(results.get("performance").medianMillis()).isLessThan(results.get("quality").medianMillis());
        assertThat(results.get("ultra-performance").medianMillis()).isLessThan(results.get("performance").medianMillis());
        assertThat(Files.isRegularFile(outputDir.resolve("balanced_vs_performance-side-by-side.png"))).isTrue();
        assertThat(Files.isRegularFile(outputDir.resolve("quality_balanced_performance-triptych.png"))).isTrue();
    }

    private ScenarioResult benchmarkScenario(
            MCARenderer renderer,
            List<Path> regions,
            MCARenderer.WorldPoint center,
            PreviewRenderPreferences preferences
    ) throws Exception {
        MCARenderer.RenderOptions options = buildWorldOptions(preferences, center, DEFAULT_LIMIT_BLOCKS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            renderer.renderWorld(regions, options);
        }

        List<Double> samples = new ArrayList<>();
        List<Double> dataLoadSamples = new ArrayList<>();
        List<Double> blockSamples = new ArrayList<>();
        List<Double> biomeSamples = new ArrayList<>();
        List<Double> shadeSamples = new ArrayList<>();
        List<Double> regionComposeSamples = new ArrayList<>();
        List<Double> worldComposeSamples = new ArrayList<>();
        List<Double> cropSamples = new ArrayList<>();
        BufferedImage lastImage = null;
        int threadCount = 1;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            long start = System.nanoTime();
            MCARenderer.RenderedWorld renderedWorld = renderer.renderWorldWithMetadata(regions, options);
            lastImage = renderedWorld.image();
            samples.add((System.nanoTime() - start) / 1_000_000.0d);
            MCARenderer.RenderPhases phases = renderedWorld.stats().phases();
            dataLoadSamples.add(nanosToMillis(phases.dataLoadNanos()));
            blockSamples.add(nanosToMillis(phases.blockSampleNanos()));
            biomeSamples.add(nanosToMillis(phases.biomeSampleNanos()));
            shadeSamples.add(nanosToMillis(phases.shadeColorNanos()));
            regionComposeSamples.add(nanosToMillis(phases.regionComposeNanos()));
            worldComposeSamples.add(nanosToMillis(phases.worldComposeNanos()));
            cropSamples.add(nanosToMillis(phases.cropNanos()));
            threadCount = renderedWorld.stats().threadCount();
        }
        return new ScenarioResult(
                median(samples),
                average(samples),
                lastImage,
                threadCount,
                summary(dataLoadSamples),
                summary(blockSamples),
                summary(biomeSamples),
                summary(shadeSamples),
                summary(regionComposeSamples),
                summary(worldComposeSamples),
                summary(cropSamples)
        );
    }

    private MCARenderer.RenderOptions buildWorldOptions(
            PreviewRenderPreferences preferences,
            MCARenderer.WorldPoint center,
            int limitBlocks
    ) {
        int half = limitBlocks / 2;
        return preferences.toRenderOptions()
                .withPreferSquareCrop(true)
                .withWorldBounds(center.x() - half, center.x() + half - 1, center.z() - half, center.z() + half - 1);
    }

    private MCARenderer.WorldPoint resolveCenter(World world) {
        WorldDataReader.SpawnPoint spawnPoint = WorldDataReader.getSpawnPoint(world);
        if (spawnPoint == null) {
            return new MCARenderer.WorldPoint(0, 0);
        }
        return new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z());
    }

    private List<Path> selectRegionsForPreview(Path worldPath, MCARenderer.WorldPoint center, int limitBlocks) throws IOException {
        Path regionDir = worldPath.resolve("region");
        if (!Files.isDirectory(regionDir)) {
            return List.of();
        }
        int minBlockX = center.x() - (limitBlocks / 2);
        int maxBlockX = center.x() + (limitBlocks / 2) - 1;
        int minBlockZ = center.z() - (limitBlocks / 2);
        int maxBlockZ = center.z() + (limitBlocks / 2) - 1;
        int minRegionX = Math.floorDiv(minBlockX, MCARenderer.REGION_BLOCK_SIDE);
        int maxRegionX = Math.floorDiv(maxBlockX, MCARenderer.REGION_BLOCK_SIDE);
        int minRegionZ = Math.floorDiv(minBlockZ, MCARenderer.REGION_BLOCK_SIDE);
        int maxRegionZ = Math.floorDiv(maxBlockZ, MCARenderer.REGION_BLOCK_SIDE);

        try (var stream = Files.list(regionDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .filter(path -> {
                        int[] coords = parseRegionCoords(path.getFileName().toString());
                        if (coords == null) {
                            return false;
                        }
                        return coords[0] >= minRegionX && coords[0] <= maxRegionX && coords[1] >= minRegionZ && coords[1] <= maxRegionZ;
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private int[] parseRegionCoords(String fileName) {
        String[] parts = fileName.split("\\.");
        if (parts.length != 4 || !"r".equals(parts[0]) || !"mca".equals(parts[3])) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void writeArtifacts(Path outputDir, String prefix, BufferedImage left, BufferedImage right) throws IOException {
        ImageIO.write(createDiff(left, right), "png", outputDir.resolve(prefix + "-diff.png").toFile());
        ImageIO.write(createSideBySide(left, right), "png", outputDir.resolve(prefix + "-side-by-side.png").toFile());
    }

    private BufferedImage createSideBySide(BufferedImage left, BufferedImage right) {
        int width = left.getWidth() + right.getWidth();
        int height = Math.max(left.getHeight(), right.getHeight());
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combined.createGraphics();
        try {
            g2.setColor(new Color(24, 24, 24));
            g2.fillRect(0, 0, width, height);
            g2.drawImage(left, 0, 0, null);
            g2.drawImage(right, left.getWidth(), 0, null);
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawLine(left.getWidth(), 0, left.getWidth(), height);
        } finally {
            g2.dispose();
        }
        return combined;
    }

    private BufferedImage createTriptych(BufferedImage left, BufferedImage center, BufferedImage right) {
        int width = left.getWidth() + center.getWidth() + right.getWidth();
        int height = Math.max(left.getHeight(), Math.max(center.getHeight(), right.getHeight()));
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combined.createGraphics();
        try {
            g2.setColor(new Color(24, 24, 24));
            g2.fillRect(0, 0, width, height);
            int x = 0;
            g2.drawImage(left, x, 0, null);
            x += left.getWidth();
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawLine(x, 0, x, height);
            g2.drawImage(center, x, 0, null);
            x += center.getWidth();
            g2.drawLine(x, 0, x, height);
            g2.drawImage(right, x, 0, null);
        } finally {
            g2.dispose();
        }
        return combined;
    }

    private BufferedImage createDiff(BufferedImage left, BufferedImage right) {
        int width = Math.min(left.getWidth(), right.getWidth());
        int height = Math.min(left.getHeight(), right.getHeight());
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color a = new Color(left.getRGB(x, y), true);
                Color b = new Color(right.getRGB(x, y), true);
                int dr = Math.abs(a.getRed() - b.getRed());
                int dg = Math.abs(a.getGreen() - b.getGreen());
                int db = Math.abs(a.getBlue() - b.getBlue());
                int intensity = Math.min(255, dr + dg + db);
                diff.setRGB(x, y, new Color(intensity, dg, db, 255).getRGB());
            }
        }
        return diff;
    }

    private String renderReport(
            Path worldPath,
            int regionCount,
            MCARenderer.WorldPoint center,
            Map<String, ScenarioResult> results
    ) {
        StringBuilder report = new StringBuilder();
        report.append("world=").append(worldPath).append('\n');
        report.append("regions=").append(regionCount).append('\n');
        report.append("center=").append(center.x()).append(',').append(center.z()).append('\n');
        report.append("limitBlocks=").append(DEFAULT_LIMIT_BLOCKS).append('\n');
        for (Map.Entry<String, ScenarioResult> entry : results.entrySet()) {
            report.append(entry.getKey())
                    .append(".medianMs=")
                    .append(String.format(Locale.US, "%.3f", entry.getValue().medianMillis()))
                    .append('\n');
            report.append(entry.getKey())
                    .append(".avgMs=")
                    .append(String.format(Locale.US, "%.3f", entry.getValue().averageMillis()))
                    .append('\n');
            report.append(entry.getKey()).append(".threads=").append(entry.getValue().threadCount()).append('\n');
            appendPhase(report, entry.getKey(), "dataLoad", entry.getValue().dataLoad());
            appendPhase(report, entry.getKey(), "blockSample", entry.getValue().blockSample());
            appendPhase(report, entry.getKey(), "biomeSample", entry.getValue().biomeSample());
            appendPhase(report, entry.getKey(), "shadeColor", entry.getValue().shadeColor());
            appendPhase(report, entry.getKey(), "regionCompose", entry.getValue().regionCompose());
            appendPhase(report, entry.getKey(), "worldCompose", entry.getValue().worldCompose());
            appendPhase(report, entry.getKey(), "crop", entry.getValue().crop());
        }
        return report.toString();
    }

    private String renderMarkdownReport(
            Path worldPath,
            int regionCount,
            MCARenderer.WorldPoint center,
            Map<String, ScenarioResult> results
    ) {
        StringBuilder report = new StringBuilder();
        report.append("# Real World Preview Benchmark").append('\n').append('\n');
        report.append("- world: ").append(worldPath).append('\n');
        report.append("- regions: ").append(regionCount).append('\n');
        report.append("- center: ").append(center.x()).append(',').append(center.z()).append('\n');
        report.append("- limitBlocks: ").append(DEFAULT_LIMIT_BLOCKS).append('\n').append('\n');
        report.append("| preset | median ms | avg ms | data load | blocks | biomes | shade/color | region compose | world compose | crop | threads |").append('\n');
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |").append('\n');
        for(Map.Entry<String, ScenarioResult> entry : results.entrySet()) {
            ScenarioResult result = entry.getValue();
            report.append("| ")
                    .append(entry.getKey())
                    .append(" | ")
                    .append(formatMillis(result.medianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.averageMillis()))
                    .append(" | ")
                    .append(formatMillis(result.dataLoad().medianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.blockSample().medianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.biomeSample().medianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.shadeColor().medianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.regionCompose().medianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.worldCompose().medianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.crop().medianMillis()))
                    .append(" | ")
                    .append(result.threadCount())
                    .append(" |")
                    .append('\n');
        }
        return report.toString();
    }

    private String renderCsvReport(Map<String, ScenarioResult> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("preset,median_ms,avg_ms,data_load_ms,block_sample_ms,biome_sample_ms,shade_color_ms,region_compose_ms,world_compose_ms,crop_ms,threads").append('\n');
        for(Map.Entry<String, ScenarioResult> entry : results.entrySet()) {
            ScenarioResult result = entry.getValue();
            csv.append(entry.getKey()).append(',')
                    .append(formatMillis(result.medianMillis())).append(',')
                    .append(formatMillis(result.averageMillis())).append(',')
                    .append(formatMillis(result.dataLoad().medianMillis())).append(',')
                    .append(formatMillis(result.blockSample().medianMillis())).append(',')
                    .append(formatMillis(result.biomeSample().medianMillis())).append(',')
                    .append(formatMillis(result.shadeColor().medianMillis())).append(',')
                    .append(formatMillis(result.regionCompose().medianMillis())).append(',')
                    .append(formatMillis(result.worldCompose().medianMillis())).append(',')
                    .append(formatMillis(result.crop().medianMillis())).append(',')
                    .append(result.threadCount())
                    .append('\n');
        }
        return csv.toString();
    }

    private double median(List<Double> samples) {
        List<Double> ordered = new ArrayList<>(samples);
        ordered.sort(Double::compareTo);
        if (ordered.isEmpty()) {
            return 0.0d;
        }
        int mid = ordered.size() / 2;
        if ((ordered.size() & 1) == 1) {
            return ordered.get(mid);
        }
        return (ordered.get(mid - 1) + ordered.get(mid)) / 2.0d;
    }

    private double average(List<Double> samples) {
        if (samples.isEmpty()) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (double sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
    }

    private MetricSummary summary(List<Double> values) {
        if(values.isEmpty()) {
            return new MetricSummary(0d, 0d, 0d, 0d, 0d);
        }
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

    private void appendPhase(StringBuilder report, String preset, String phaseName, MetricSummary summary) {
        report.append(preset)
                .append('.')
                .append(phaseName)
                .append(".medianMs=")
                .append(formatMillis(summary.medianMillis()))
                .append('\n');
    }

    private String formatMillis(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private record ScenarioResult(double medianMillis,
                                  double averageMillis,
                                  BufferedImage image,
                                  int threadCount,
                                  MetricSummary dataLoad,
                                  MetricSummary blockSample,
                                  MetricSummary biomeSample,
                                  MetricSummary shadeColor,
                                  MetricSummary regionCompose,
                                  MetricSummary worldCompose,
                                  MetricSummary crop) {
    }

    private record MetricSummary(double averageMillis, double medianMillis, double minMillis, double maxMillis, double stddevMillis) {
    }
}
