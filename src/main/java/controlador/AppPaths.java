package controlador;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {
    static final String APP_ROOT_PROPERTY = "easy.mc.appRoot";
    static final String LEGACY_ROOT_PROPERTY = "easy.mc.legacyAppRoot";

    private static final String APP_DIRECTORY_NAME = ".easy-mc-server";

    private AppPaths() {
    }

    public static Path rootDirectory() {
        String override = System.getProperty(APP_ROOT_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home", "."), APP_DIRECTORY_NAME).toAbsolutePath().normalize();
    }

    public static Path configDirectory() {
        return rootDirectory().resolve("config");
    }

    public static Path cacheDirectory() {
        return rootDirectory().resolve("cache");
    }

    public static Path locksDirectory() {
        return rootDirectory().resolve("locks");
    }

    public static Path statsDirectory() {
        return rootDirectory().resolve("stats");
    }

    public static Path legacyBaseDirectory() {
        String override = System.getProperty(LEGACY_ROOT_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        try {
            Path baseDir = Path.of(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(baseDir)) {
                baseDir = baseDir.getParent();
            }

            String normalized = baseDir.toString().replace('\\', '/');
            if (normalized.endsWith("/target/classes")) {
                baseDir = baseDir.getParent().getParent();
            } else if (normalized.endsWith("/build/classes/java/main")) {
                baseDir = baseDir.getParent().getParent().getParent().getParent();
            } else if (normalized.endsWith("/bin/main")) {
                baseDir = baseDir.getParent().getParent();
            }

            return baseDir.toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException e) {
            return Path.of(".").toAbsolutePath().normalize();
        }
    }

    static void migrateLegacyFileIfNeeded(Path legacyPath, Path targetPath) {
        if (legacyPath == null || targetPath == null || Files.exists(targetPath) || !Files.exists(legacyPath)) {
            return;
        }
        if (legacyPath.toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize())) {
            return;
        }
        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.move(legacyPath, targetPath);
        } catch (IOException e) {
            System.err.println("No se ha podido migrar " + legacyPath.getFileName() + " a " + targetPath + ": " + e.getMessage());
        }
    }
}
