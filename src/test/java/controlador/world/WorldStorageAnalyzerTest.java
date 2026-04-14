package controlador.world;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorldStorageAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    @Tag("smoke")
    void analyze_debeSepararMundoYPlayerDataCuandoExistenDirectoriosConocidos() throws Exception {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir.resolve("region"));
        Files.createDirectories(worldDir.resolve("playerdata"));
        Files.createDirectories(worldDir.resolve("stats"));
        Files.write(worldDir.resolve("region/r.0.0.mca"), new byte[15]);
        Files.write(worldDir.resolve("playerdata/player.dat"), new byte[6]);
        Files.write(worldDir.resolve("stats/stats.json"), new byte[4]);

        WorldStorageStats stats = WorldStorageAnalyzer.analyze(TestWorldFixtures.world(worldDir, "world"));

        assertThat(stats.worldBytes()).isEqualTo(15L);
        assertThat(stats.playerAndStatsBytes()).isEqualTo(10L);
        assertThat(stats.totalBytes()).isEqualTo(25L);
    }

    @Test
    void analyze_debeUsarFallbackCuandoNoHayDirectoriosDeRegionClasicos() throws Exception {
        Path worldDir = tempDir.resolve("world-fallback");
        Files.createDirectories(worldDir.resolve("custom"));
        Files.createDirectories(worldDir.resolve("playerdata"));
        Files.write(worldDir.resolve("custom/map.bin"), new byte[20]);
        Files.write(worldDir.resolve("playerdata/player.dat"), new byte[5]);

        WorldStorageStats stats = WorldStorageAnalyzer.analyze(TestWorldFixtures.world(worldDir, "world-fallback"));

        assertThat(stats.worldBytes()).isEqualTo(20L);
        assertThat(stats.playerAndStatsBytes()).isEqualTo(5L);
        assertThat(stats.totalBytes()).isEqualTo(25L);
    }
}
