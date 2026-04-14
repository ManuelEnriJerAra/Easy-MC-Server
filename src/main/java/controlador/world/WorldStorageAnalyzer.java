package controlador.world;

import modelo.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class WorldStorageAnalyzer {
    private WorldStorageAnalyzer() {
    }

    public static WorldStorageStats analyze(World world) {
        Path worldDir = WorldFilesService.getWorldDirectory(world);
        if (worldDir == null || !Files.isDirectory(worldDir)) {
            return new WorldStorageStats(0L, 0L, 0L);
        }

        long total = calculateDirectorySize(worldDir);
        long playerAndStats = calculateDirectorySize(worldDir.resolve("playerdata"))
                + calculateDirectorySize(worldDir.resolve("stats"))
                + calculateDirectorySize(worldDir.resolve("advancements"))
                + calculateDirectorySize(worldDir.resolve("data"));

        long worldBytes = calculateDirectorySize(worldDir.resolve("region"))
                + calculateDirectorySize(worldDir.resolve("entities"))
                + calculateDirectorySize(worldDir.resolve("poi"))
                + calculateDirectorySize(worldDir.resolve("DIM-1"))
                + calculateDirectorySize(worldDir.resolve("DIM1"))
                + calculateDirectorySize(worldDir.resolve("dimensions"));

        if (worldBytes <= 0L) {
            worldBytes = Math.max(0L, total - playerAndStats);
        }

        return new WorldStorageStats(worldBytes, playerAndStats, total);
    }

    private static long calculateDirectorySize(Path path) {
        if (path == null || !Files.exists(path)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(current -> {
                        try {
                            return Files.size(current);
                        } catch (IOException ex) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ex) {
            return 0L;
        }
    }
}
