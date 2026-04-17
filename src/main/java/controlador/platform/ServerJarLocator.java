package controlador.platform;

import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ServerJarLocator {
    private ServerJarLocator() {
    }

    static Path findExecutableJar(Path serverDir, ServerPlatform platform) throws IOException {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return null;
        }
        try (var stream = Files.list(serverDir)) {
            List<Path> jars = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .toList();
            if (jars.isEmpty()) {
                return null;
            }
            if (jars.size() == 1) {
                return jars.getFirst();
            }
            return jars.stream()
                    .max(Comparator
                            .comparingInt((Path jar) -> scoreJar(jar, platform))
                            .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .orElse(null);
        }
    }

    private static int scoreJar(Path jar, ServerPlatform platform) {
        String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
        int score = 0;

        if (name.contains("installer")) score -= 100;
        if (name.contains("client")) score -= 80;
        if (name.contains("api")) score -= 40;
        if (name.contains("sources")) score -= 40;
        if (name.contains("javadoc")) score -= 40;
        if (name.contains("server")) score += 20;

        switch (platform == null ? ServerPlatform.UNKNOWN : platform) {
            case FORGE, NEOFORGE -> {
                if (name.contains("forge")) score += 50;
                if (name.contains("neoforge")) score += 50;
            }
            case PAPER, SPIGOT, BUKKIT, PURPUR, PUFFERFISH -> {
                if (name.contains("paper")) score += 50;
                if (name.contains("spigot")) score += 40;
                if (name.contains("bukkit")) score += 40;
                if (name.contains("purpur")) score += 40;
                if (name.contains("pufferfish")) score += 40;
            }
            case FABRIC -> {
                if (name.contains("fabric")) score += 50;
            }
            case QUILT -> {
                if (name.contains("quilt")) score += 50;
            }
            case VANILLA, UNKNOWN -> {
                if (name.contains("minecraft")) score += 30;
                if (name.contains("paper") || name.contains("forge") || name.contains("fabric") || name.contains("quilt")) {
                    score -= 30;
                }
            }
            default -> {
            }
        }

        if (MinecraftServerJarInspector.looksLikeMinecraftServerJar(jar)) {
            score += 25;
        }
        return score;
    }
}
