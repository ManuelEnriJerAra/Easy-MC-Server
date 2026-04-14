package controlador;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MCARendererImageComparisonTest {
    @TempDir
    Path tempDir;

    @Test
    void generarArtefactosSideBySideYDiff_paraCompararPerfiles() throws Exception {
        List<Path> regions = MCARendererBenchmarkTest.createBenchmarkRegions(tempDir.resolve("visual-bench"));
        MCARenderer renderer = new MCARenderer();

        BufferedImage baseline = renderer.renderWorld(regions, MCARendererBenchmarkTest.originalBaselineOptions());
        BufferedImage degraded = renderer.renderWorld(regions, MCARendererBenchmarkTest.degradedBaselineOptions());
        BufferedImage balanced = renderer.renderWorld(regions, MCARendererBenchmarkTest.balancedOptions());
        BufferedImage quality = renderer.renderWorld(regions, MCARendererBenchmarkTest.qualityOptions());
        BufferedImage performance = renderer.renderWorld(regions, MCARendererBenchmarkTest.performanceOptions());

        Path outputDir = Path.of("target", "visual-regressions");
        Files.createDirectories(outputDir);
        writeArtifacts(outputDir, "baseline_vs_degraded", baseline, degraded);
        writeArtifacts(outputDir, "baseline_vs_balanced", baseline, balanced);
        writeArtifacts(outputDir, "balanced_vs_quality", balanced, quality);
        writeArtifacts(outputDir, "performance_vs_balanced", performance, balanced);
        ImageIO.write(createTriptych(quality, balanced, performance), "png", outputDir.resolve("quality_balanced_performance-triptych.png").toFile());

        assertThat(Files.isRegularFile(outputDir.resolve("baseline_vs_balanced-side-by-side.png"))).isTrue();
        assertThat(Files.isRegularFile(outputDir.resolve("baseline_vs_balanced-diff.png"))).isTrue();
        assertThat(Files.isRegularFile(outputDir.resolve("quality_balanced_performance-triptych.png"))).isTrue();
        assertThat(perceptualDelta(baseline, degraded)).isGreaterThan(0);
        assertThat(perceptualDelta(balanced, quality)).isGreaterThan(0);
    }

    private void writeArtifacts(Path outputDir, String prefix, BufferedImage left, BufferedImage right) throws IOException {
        ImageIO.write(left, "png", outputDir.resolve(prefix + "-left.png").toFile());
        ImageIO.write(right, "png", outputDir.resolve(prefix + "-right.png").toFile());
        ImageIO.write(createDiff(left, right), "png", outputDir.resolve(prefix + "-diff.png").toFile());
        ImageIO.write(createSideBySide(left, right), "png", outputDir.resolve(prefix + "-side-by-side.png").toFile());
    }

    private BufferedImage createDiff(BufferedImage left, BufferedImage right) {
        int width = Math.min(left.getWidth(), right.getWidth());
        int height = Math.min(left.getHeight(), right.getHeight());
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
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

    private long perceptualDelta(BufferedImage left, BufferedImage right) {
        int width = Math.min(left.getWidth(), right.getWidth());
        int height = Math.min(left.getHeight(), right.getHeight());
        long delta = 0L;
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
                int a = left.getRGB(x, y);
                int b = right.getRGB(x, y);
                delta += Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF));
                delta += Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF));
                delta += Math.abs((a & 0xFF) - (b & 0xFF));
            }
        }
        return delta;
    }
}
