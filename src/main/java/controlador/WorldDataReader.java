package controlador;

import static controlador.Utilidades.fromMStoDateString;

import java.nio.file.Files;
import java.nio.file.Path;
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
            if(spawnXTag == null || spawnZTag == null) {
                return null;
            }
            return new SpawnPoint(spawnXTag.asInt(), spawnZTag.asInt());
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
}
