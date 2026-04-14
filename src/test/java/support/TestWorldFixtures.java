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
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class TestWorldFixtures {
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
        Files.createDirectories(serverDir);
        Path jarPath = serverDir.resolve(jarName);
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jarOut.putNextEntry(new JarEntry("version.json"));
            jarOut.write("{\"id\":\"test\"}".getBytes());
            jarOut.closeEntry();
        }
        return jarPath;
    }

    public static Path createSimpleRegion(Path regionDir, int regionX, int regionZ, int blockY, String blockName) throws IOException {
        Files.createDirectories(regionDir);
        Path regionFile = regionDir.resolve(MCAUtil.createNameFromRegionLocation(regionX, regionZ));
        MCAFile mcaFile = new MCAFile(regionX, regionZ);
        Chunk chunk = Chunk.newChunk();
        chunk.setDataVersion(3578);
        chunk.setStatus("minecraft:full");

        CompoundTag block = new CompoundTag();
        block.putString("Name", blockName);
        chunk.setBlockStateAt(0, blockY, 0, block, false);
        mcaFile.setChunk(0, 0, chunk);
        MCAUtil.write(mcaFile, regionFile.toFile());
        return regionFile;
    }
}
