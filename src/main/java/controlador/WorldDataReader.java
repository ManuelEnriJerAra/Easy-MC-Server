package controlador;

import static controlador.Utilidades.fromMStoDateString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import modelo.World;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

public class WorldDataReader {
    private static final Path LEGACY_OVERWORLD_REGION_DIR = Path.of("region");
    private static final Path NAMESPACED_OVERWORLD_REGION_DIR = Path.of("dimensions", "minecraft", "overworld", "region");

    // Minecraft no ha guardado siempre la seed en el mismo sitio.
    // Este reader intenta ocultar esa diferencia para que la UI no tenga que
    // saber si el mundo es legacy o moderno.

    // Lee desde level.dat el tiempo acumulado del mundo indicado.
    private static Path getDataFile(World mundo){
        if(mundo == null || mundo.getWorldName() == null || mundo.getWorldName().isBlank()) return null;
        Path levelDat = Path.of(mundo.getWorldDir()).resolve("level.dat");
        if(!Files.isRegularFile(levelDat)) return null;
        return levelDat;
    }

    // Punto centralizado para abrir level.dat y devolver la raiz NBT.
    // Asi evitamos repetir la misma lectura en todos los getters.
    private static CompoundTag getRootTag(World mundo) {
        Path levelDat = getDataFile(mundo);
        if(levelDat == null) return null;

        try {
            NamedTag named = NBTUtil.read(levelDat.toFile());
            if(named == null || !(named.getTag() instanceof CompoundTag root)) return null;
            return root;
        } catch (Exception ex) {
            return null;
        }
    }

    // Dentro de level.dat, casi todos los metadatos del mundo viven bajo Data.
    private static CompoundTag getDataTag(World mundo) {
        CompoundTag root = getRootTag(mundo);
        return root == null ? null : root.getCompoundTag("Data");
    }

    public static String getVersionName(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            CompoundTag versionTag = data.getCompoundTag("Version");
            if(versionTag == null) return null;

            String versionName = versionTag.getString("Name");
            return versionName == null || versionName.isBlank() ? null : versionName;
        } catch (Exception ex) {
            return null;
        }
    }

    public static WorldStorageLayout detectOverworldStorageLayout(World mundo) {
        Path worldDir = getWorldDirectory(mundo);
        if(worldDir == null) return WorldStorageLayout.LEGACY;

        Path legacyDir = worldDir.resolve(LEGACY_OVERWORLD_REGION_DIR);
        Path namespacedDir = worldDir.resolve(NAMESPACED_OVERWORLD_REGION_DIR);

        boolean hasLegacyDir = Files.isDirectory(legacyDir);
        boolean hasNamespacedDir = Files.isDirectory(namespacedDir);
        if(hasNamespacedDir && !hasLegacyDir) {
            return WorldStorageLayout.NAMESPACED;
        }
        if(hasLegacyDir && !hasNamespacedDir) {
            return WorldStorageLayout.LEGACY;
        }

        // Desde la rama 26.x Mojang movio el Overworld a dimensions/minecraft/overworld.
        // Si aun no existe la carpeta (por ejemplo, mundo nuevo sin chunks), usamos el
        // nombre de version de level.dat para decidir que layout esperar.
        String versionName = getVersionName(mundo);
        if(usesNamespacedStorage(versionName)) {
            return WorldStorageLayout.NAMESPACED;
        }

        return WorldStorageLayout.LEGACY;
    }

    public static Path getOverworldRegionDirectory(World mundo) {
        Path worldDir = getWorldDirectory(mundo);
        if(worldDir == null) return null;

        Path legacyDir = worldDir.resolve(LEGACY_OVERWORLD_REGION_DIR);
        Path namespacedDir = worldDir.resolve(NAMESPACED_OVERWORLD_REGION_DIR);

        WorldStorageLayout layout = detectOverworldStorageLayout(mundo);
        if(layout == WorldStorageLayout.NAMESPACED) {
            return Files.isDirectory(namespacedDir) || !Files.isDirectory(legacyDir) ? namespacedDir : legacyDir;
        }
        return Files.isDirectory(legacyDir) || !Files.isDirectory(namespacedDir) ? legacyDir : namespacedDir;
    }

    private static Path getWorldDirectory(World mundo) {
        if(mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) return null;
        return Path.of(mundo.getWorldDir());
    }

    private static boolean usesNamespacedStorage(String versionName) {
        if(versionName == null || versionName.isBlank()) return false;

        String normalized = versionName.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("26.")
                || normalized.startsWith("26w")
                || normalized.contains("26.1 snapshot")
                || normalized.contains("snapshot 26");
    }

    public static long getActiveTicks(World mundo) {
        try{
            CompoundTag data = getDataTag(mundo);
            if(data == null) return 0L;

            LongTag timeTag = data.getLongTag("Time");
            if(timeTag == null) return 0L;
            return Math.max(0L, timeTag.asLong());
        } catch (Exception ex){
            System.out.println("Ha ocurrido un error intentando leer los ticks del mundo: "+mundo.getWorldDir());
            return 0L;
        }
    }

    public static String getLastPlayed(World mundo){
        try{
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            LongTag timeTag = data.getLongTag("LastPlayed");
            if(timeTag == null) return null;
            return fromMStoDateString(timeTag.asLong());
        } catch (Exception ex){
            System.out.println("Ha ocurrido un error accediendo a LastPlayed en el mundo: "+mundo.getWorldDir());
            return null;
        }
    }

    public static String getSeed(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return "-";

            // Mundos legacy y muchas versiones intermedias guardan la seed en Data.RandomSeed.
            LongTag randomSeedTag = data.getLongTag("RandomSeed");
            if(randomSeedTag != null) {
                return Long.toString(randomSeedTag.asLong());
            }

            // En mundos modernos la seed suele vivir en Data.WorldGenSettings.seed.
            CompoundTag worldGenSettings = data.getCompoundTag("WorldGenSettings");
            if(worldGenSettings != null) {
                LongTag longSeedTag = worldGenSettings.getLongTag("seed");
                if(longSeedTag != null) {
                    return Long.toString(longSeedTag.asLong());
                }

                // Algunas lecturas pueden devolver el tag como entero o string, asi que dejamos
                // estos fallbacks para poder comparar la seed sin depender del tipo exacto.
                IntTag intSeedTag = worldGenSettings.getIntTag("seed");
                if(intSeedTag != null) {
                    return Integer.toString(intSeedTag.asInt());
                }

                StringTag stringSeedTag = worldGenSettings.getStringTag("seed");
                if(stringSeedTag != null) {
                    return stringSeedTag.getValue();
                }
            }

            return "-";
        } catch (Exception ex) {
            System.out.println("Ha ocurrido un error accediendo a la seed del mundo: " + mundo.getWorldDir());
            return "-";
        }
    }

    public static SpawnPoint getSpawnPoint(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            IntTag spawnXTag = data.getIntTag("SpawnX");
            IntTag spawnZTag = data.getIntTag("SpawnZ");
            if(spawnXTag != null && spawnZTag != null) {
                return new SpawnPoint(spawnXTag.asInt(), spawnZTag.asInt());
            }

            CompoundTag spawnTag = data.getCompoundTag("spawn");
            if(spawnTag == null) return null;

            int[] spawnPos = spawnTag.getIntArray("pos");
            if(spawnPos != null && spawnPos.length >= 3) {
                return new SpawnPoint(spawnPos[0], spawnPos[2]);
            }

            ListTag<?> spawnPosList = spawnTag.getListTag("pos");
            if(spawnPosList != null && spawnPosList.size() >= 3) {
                Tag<?> xTag = spawnPosList.get(0);
                Tag<?> zTag = spawnPosList.get(2);
                if(xTag instanceof net.querz.nbt.tag.NumberTag<?> xNumber
                        && zTag instanceof net.querz.nbt.tag.NumberTag<?> zNumber) {
                    return new SpawnPoint(xNumber.asInt(), zNumber.asInt());
                }
            }

            return null;
        } catch (Exception ex) {
            System.out.println("Ha ocurrido un error accediendo al spawn del mundo: " + mundo.getWorldDir());
            return null;
        }
    }

    public static void showAllTags(World mundo){
        try{
            CompoundTag root = getRootTag(mundo);
            if (root != null) {
                CompoundTag data = root.getCompoundTag("Data");
                for (Map.Entry<String, Tag<?>> entry : data.entrySet()) {
                    var tag = entry.getValue();
                    String shownValue;

                    if (tag instanceof CompoundTag) {
                        shownValue = "{...}";
                    } else if (tag instanceof ListTag<?>) {
                        shownValue = "[...]";
                    } else {
                        shownValue = tag.valueToString();
                    }

                    System.out.println(
                        entry.getKey()
                        + " -> " + shownValue
                        + " -> " + tag.getClass().getSimpleName()
                    );
                }
            }
        } catch (Exception ex) {
            System.out.println("Ha ocurrido un error leyendo los tags del mundo: "+mundo.getWorldDir());
        }
    }   

    // Solo necesitamos X y Z para una vista cenital.
    public record SpawnPoint(int x, int z) {}

    public enum WorldStorageLayout {
        LEGACY,
        NAMESPACED
    }
}
