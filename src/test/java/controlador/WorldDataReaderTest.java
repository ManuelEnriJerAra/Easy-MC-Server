package controlador;

import modelo.World;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.StringTag;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorldDataReaderTest {
    @TempDir
    Path tempDir;

    @Test
    @Tag("smoke")
    void detectOverworldStorageLayout_debeUsarDirectorioExistenteConPrioridad() throws Exception {
        Path worldDir = tempDir.resolve("world-layout");
        CompoundTag data = baseWorldData();
        data.put("Version", versionTag("1.21.4"));
        TestWorldFixtures.writeLevelDat(worldDir, data);
        Files.createDirectories(worldDir.resolve("dimensions/minecraft/overworld/region"));

        World world = TestWorldFixtures.world(worldDir, "world-layout");

        assertThat(WorldDataReader.detectOverworldStorageLayout(world))
                .isEqualTo(WorldDataReader.WorldStorageLayout.NAMESPACED);
        assertThat(WorldDataReader.getOverworldRegionDirectory(world))
                .isEqualTo(worldDir.resolve("dimensions/minecraft/overworld/region"));
    }

    @Test
    @Tag("smoke")
    void detectOverworldStorageLayout_debeInferirSnapshots26ComoNamespaced() throws Exception {
        Path worldDir = tempDir.resolve("world-26w");
        CompoundTag data = baseWorldData();
        data.put("Version", versionTag("26w10a"));
        TestWorldFixtures.writeLevelDat(worldDir, data);

        World world = TestWorldFixtures.world(worldDir, "world-26w");

        assertThat(WorldDataReader.detectOverworldStorageLayout(world))
                .isEqualTo(WorldDataReader.WorldStorageLayout.NAMESPACED);
        assertThat(WorldDataReader.getOverworldRegionDirectory(world))
                .isEqualTo(worldDir.resolve("dimensions/minecraft/overworld/region"));
    }

    @Test
    @Tag("smoke")
    void getSeed_debeSoportarLegacyYWorldGenSettings() throws Exception {
        Path legacyDir = tempDir.resolve("legacy");
        CompoundTag legacyData = baseWorldData();
        legacyData.putLong("RandomSeed", 123456789L);
        TestWorldFixtures.writeLevelDat(legacyDir, legacyData);

        Path modernDir = tempDir.resolve("modern");
        CompoundTag modernData = baseWorldData();
        CompoundTag worldGenSettings = new CompoundTag();
        worldGenSettings.putInt("seed", 987654321);
        modernData.put("WorldGenSettings", worldGenSettings);
        TestWorldFixtures.writeLevelDat(modernDir, modernData);

        assertThat(WorldDataReader.getSeed(TestWorldFixtures.world(legacyDir, "legacy"))).isEqualTo("123456789");
        assertThat(WorldDataReader.getSeed(TestWorldFixtures.world(modernDir, "modern"))).isEqualTo("987654321");
    }

    @Test
    void getSpawnPoint_debeLeerFormatoClasicoYFormatoModerno() throws Exception {
        Path classicDir = tempDir.resolve("classic-spawn");
        CompoundTag classicData = baseWorldData();
        classicData.putInt("SpawnX", 12);
        classicData.putInt("SpawnY", 70);
        classicData.putInt("SpawnZ", -4);
        classicData.putFloat("SpawnAngle", 90.5f);
        TestWorldFixtures.writeLevelDat(classicDir, classicData);

        Path modernDir = tempDir.resolve("modern-spawn");
        CompoundTag modernData = baseWorldData();
        CompoundTag spawn = new CompoundTag();
        spawn.putIntArray("pos", new int[]{100, 65, -30});
        spawn.putFloat("angle", 45.0f);
        modernData.put("spawn", spawn);
        TestWorldFixtures.writeLevelDat(modernDir, modernData);

        assertThat(WorldDataReader.getSpawnPoint(TestWorldFixtures.world(classicDir, "classic-spawn")))
                .isEqualTo(new WorldDataReader.SpawnPoint(12, 70, -4, 90.5f));
        assertThat(WorldDataReader.getSpawnPoint(TestWorldFixtures.world(modernDir, "modern-spawn")))
                .isEqualTo(new WorldDataReader.SpawnPoint(100, 65, -30, 45.0f));
    }

    @Test
    @Tag("smoke")
    void getDayTime_debeFormatearDiaYHoraMinecraft() throws Exception {
        Path worldDir = tempDir.resolve("daytime");
        CompoundTag data = baseWorldData();
        data.putLong("DayTime", 6000L);
        TestWorldFixtures.writeLevelDat(worldDir, data);

        assertThat(WorldDataReader.getDayTime(TestWorldFixtures.world(worldDir, "daytime")))
                .isEqualTo("Dia 1 - 12:00");
    }

    @Test
    void getWeatherSummaryYDataPacksSummary_debenReflejarEstadoGuardado() throws Exception {
        Path worldDir = tempDir.resolve("weather");
        CompoundTag data = baseWorldData();
        data.putInt("raining", 1);
        CompoundTag dataPacks = new CompoundTag();
        ListTag<StringTag> enabled = new ListTag<>(StringTag.class);
        enabled.addString("vanilla");
        enabled.addString("bundle");
        ListTag<StringTag> disabled = new ListTag<>(StringTag.class);
        disabled.addString("legacy");
        dataPacks.put("Enabled", enabled);
        dataPacks.put("Disabled", disabled);
        data.put("DataPacks", dataPacks);
        TestWorldFixtures.writeLevelDat(worldDir, data);

        World world = TestWorldFixtures.world(worldDir, "weather");

        assertThat(WorldDataReader.getWeatherSummary(world)).isEqualTo("Lluvia");
        assertThat(WorldDataReader.getDataPacksSummary(world)).isEqualTo("2 activos / 1 desactivados");
    }

    @Test
    void getGameRules_debeOrdenarYNormalizarValores() throws Exception {
        Path worldDir = tempDir.resolve("rules");
        CompoundTag data = baseWorldData();
        CompoundTag gameRules = new CompoundTag();
        gameRules.putString("mobGriefing", "false");
        gameRules.putString("keepInventory", "true");
        gameRules.putString("randomTickSpeed", "3");
        data.put("GameRules", gameRules);
        TestWorldFixtures.writeLevelDat(worldDir, data);

        Map<String, String> rules = WorldDataReader.getGameRules(TestWorldFixtures.world(worldDir, "rules"));

        assertThat(rules).containsEntry("keepInventory", "✓");
        assertThat(rules).containsEntry("mobGriefing", "✗");
        assertThat(rules).containsEntry("randomTickSpeed", "3");
        assertThat(rules.keySet()).containsExactly("keepInventory", "mobGriefing", "randomTickSpeed");
    }

    private static CompoundTag baseWorldData() {
        CompoundTag data = new CompoundTag();
        data.putLong("Time", 1200L);
        data.putLong("LastPlayed", 1_700_000_000_000L);
        data.putInt("GameType", 0);
        data.putInt("Difficulty", 2);
        data.put("DataVersion", new IntTag(3700));
        return data;
    }

    private static CompoundTag versionTag(String versionName) {
        CompoundTag version = new CompoundTag();
        version.putString("Name", versionName);
        version.put("DataVersion", new LongTag(3700L));
        return version;
    }
}
