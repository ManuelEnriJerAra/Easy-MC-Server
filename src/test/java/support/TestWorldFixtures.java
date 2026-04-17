package support;

import modelo.Server;
import modelo.World;
import net.querz.mca.Chunk;
import net.querz.mca.MCAFile;
import net.querz.mca.MCAUtil;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class TestWorldFixtures {
    @FunctionalInterface
    public interface RegionWriter {
        void write(MCAFile mcaFile) throws IOException;
    }

    private TestWorldFixtures() {
    }

    public static World world(Path worldDir, String worldName) {
        return new World(worldDir.toString(), worldName);
    }

    public static Server server(Path serverDir) {
        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setDisplayName(serverDir.getFileName().toString());
        return server;
    }

    public static Path writeLevelDat(Path worldDir, CompoundTag data) throws IOException {
        Files.createDirectories(worldDir);
        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        Path levelDat = worldDir.resolve("level.dat");
        NBTUtil.write(new NamedTag("", root), levelDat.toFile());
        return levelDat;
    }

    public static Path writeServerProperties(Path serverDir, Properties properties) throws IOException {
        Files.createDirectories(serverDir);
        Path propertiesPath = serverDir.resolve("server.properties");
        try (OutputStream out = Files.newOutputStream(propertiesPath)) {
            properties.store(out, null);
        }
        return propertiesPath;
    }

    public static Path createValidServerJar(Path serverDir, String jarName) throws IOException {
        return createValidServerJar(serverDir, jarName, "{\"id\":\"test\"}");
    }

    public static Path createValidServerJar(Path serverDir, String jarName, String versionJson, String... extraEntries) throws IOException {
        Files.createDirectories(serverDir);
        Path jarPath = serverDir.resolve(jarName);
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jarOut.putNextEntry(new JarEntry("version.json"));
            jarOut.write((versionJson == null ? "{\"id\":\"test\"}" : versionJson).getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
            if (extraEntries != null) {
                for (String entryName : extraEntries) {
                    if (entryName == null || entryName.isBlank()) {
                        continue;
                    }
                    JarEntry entry = new JarEntry(entryName);
                    jarOut.putNextEntry(entry);
                    jarOut.write(new byte[0]);
                    jarOut.closeEntry();
                }
            }
        }
        return jarPath;
    }

    public static Path createJar(Path targetJar, Map<String, String> textEntries, String... emptyEntries) throws IOException {
        if (targetJar.getParent() != null) {
            Files.createDirectories(targetJar.getParent());
        }
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(targetJar))) {
            if (textEntries != null) {
                for (Map.Entry<String, String> entry : textEntries.entrySet()) {
                    if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                        continue;
                    }
                    jarOut.putNextEntry(new JarEntry(entry.getKey()));
                    jarOut.write((entry.getValue() == null ? "" : entry.getValue()).getBytes(StandardCharsets.UTF_8));
                    jarOut.closeEntry();
                }
            }
            if (emptyEntries != null) {
                for (String entryName : emptyEntries) {
                    if (entryName == null || entryName.isBlank()) {
                        continue;
                    }
                    jarOut.putNextEntry(new JarEntry(entryName));
                    jarOut.write(new byte[0]);
                    jarOut.closeEntry();
                }
            }
        }
        return targetJar;
    }

    public static Path createSimpleRegion(Path regionDir, int regionX, int regionZ, int blockY, String blockName) throws IOException {
        return createRegion(regionDir, regionX, regionZ, mcaFile -> {
            Chunk chunk = createFullChunk();
            setBlock(chunk, 0, blockY, 0, blockName);
            mcaFile.setChunk(0, 0, chunk);
        });
    }

    public static Path createRegion(Path regionDir, int regionX, int regionZ, RegionWriter writer) throws IOException {
        Files.createDirectories(regionDir);
        Path regionFile = regionDir.resolve(MCAUtil.createNameFromRegionLocation(regionX, regionZ));
        MCAFile mcaFile = new MCAFile(regionX, regionZ);
        if(writer != null) {
            writer.write(mcaFile);
        }
        MCAUtil.write(mcaFile, regionFile.toFile());
        return regionFile;
    }

    public static Chunk createFullChunk() {
        Chunk chunk = Chunk.newChunk();
        chunk.setDataVersion(3578);
        chunk.setStatus("minecraft:full");
        return chunk;
    }

    public static void setBlock(Chunk chunk, int x, int y, int z, String blockName) {
        CompoundTag block = new CompoundTag();
        block.putString("Name", blockName);
        chunk.setBlockStateAt(x, y, z, block, false);
    }

    public static void fillColumn(Chunk chunk, int x, int z, int minY, int maxYInclusive, String blockName) {
        for(int y = minY; y <= maxYInclusive; y++) {
            setBlock(chunk, x, y, z, blockName);
        }
    }
}
