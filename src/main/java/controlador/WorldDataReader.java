package controlador;

import static controlador.Utilidades.fromMStoDateString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import modelo.World;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.Tag;

public class WorldDataReader {
    // Lee desde level.dat el tiempo acumulado del mundo indicado.
    private static Path getDataFile(World mundo){
        if(mundo == null || mundo.getWorldName() == null || mundo.getWorldName().isBlank()) return null;
        Path levelDat = Path.of(mundo.getWorldDir()).resolve("level.dat");
        if(!Files.isRegularFile(levelDat)) return null;
        return levelDat;
    }
    public static long getActiveTicks(World mundo) {
        Path levelDat = getDataFile(mundo);
        try{
            NamedTag named = NBTUtil.read(levelDat.toFile());
            if(named == null || !(named.getTag() instanceof CompoundTag root)) return 0L;
            
            CompoundTag data = root.getCompoundTag("Data");
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
        Path levelDat = getDataFile(mundo);

        try{
            NamedTag named = NBTUtil.read(levelDat.toFile());
            if(named == null || !(named.getTag() instanceof CompoundTag root)) return null;

            CompoundTag data = root.getCompoundTag("Data");
            if(data == null) return null;

            LongTag timeTag = data.getLongTag("LastPlayed");
            if(timeTag == null) return null;
            return fromMStoDateString(timeTag.asLong());
        } catch (Exception ex){
            System.out.println("Ha ocurrido un error accediendo a LastPlayed en el mundo: "+mundo.getWorldDir());
            return null;
        }
    }

    public static void showAllTags(World mundo){
        Path levelDat = getDataFile(mundo);
        try{
            NamedTag named = NBTUtil.read(levelDat.toFile());
            if (named.getTag() instanceof CompoundTag) {
                CompoundTag root = (CompoundTag) named.getTag();
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
}
