package controlador;

import java.nio.file.Path;

public final class AppPaths {
    static final String APP_ROOT_PROPERTY = "dora.appRoot";

    private static final String APP_DIRECTORY_NAME = ".dora";

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
}
