package controlador;

import modelo.EasyMCConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GestorConfiguracion {
    private static final String JSON_FILE = "easy-mc-config.json";
    private static final String LEGACY_JSON_FILE = "EasyMCConfig.json";
    private static final String DEFAULT_THEME_CLASS =
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedLightIJTheme";
    private static final int DEFAULT_STATS_RANGE_SECONDS = 60;
    private static final boolean DEFAULT_STATS_PERSISTENCE_ENABLED = true;
    private static final int DEFAULT_STATS_RECENT_WINDOW_SECONDS = 24 * 60 * 60;
    private static final int DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS = 60;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GestorConfiguracion() {
    }

    public static EasyMCConfig cargarConfiguracion() {
        File file = getJsonFile();
        if (!file.exists()) {
            EasyMCConfig config = crearConfiguracionPorDefecto();
            guardarConfiguracion(config);
            return config;
        }

        try {
            EasyMCConfig config = MAPPER.readValue(file, EasyMCConfig.class);
            if (config == null || config.getTemaClassName() == null || config.getTemaClassName().isBlank()) {
                config = crearConfiguracionPorDefecto();
                guardarConfiguracion(config);
            } else if (normalizarConfiguracion(config)) {
                guardarConfiguracion(config);
            }
            return config;
        } catch (JacksonException e) {
            System.err.println("Error al cargar " + JSON_FILE + ": " + e.getMessage());
            EasyMCConfig config = crearConfiguracionPorDefecto();
            guardarConfiguracion(config);
            return config;
        }
    }

    public static void guardarConfiguracion(EasyMCConfig config) {
        if (config == null) return;
        if (config.getTemaClassName() == null || config.getTemaClassName().isBlank()) {
            config.setTemaClassName(DEFAULT_THEME_CLASS);
        }
        if (config.getEstadisticasRangoSegundos() == null || config.getEstadisticasRangoSegundos() <= 0) {
            config.setEstadisticasRangoSegundos(DEFAULT_STATS_RANGE_SECONDS);
        }
        if (config.getEstadisticasPersistenciaActiva() == null) {
            config.setEstadisticasPersistenciaActiva(DEFAULT_STATS_PERSISTENCE_ENABLED);
        }
        if (config.getEstadisticasVentanaRecienteSegundos() == null || config.getEstadisticasVentanaRecienteSegundos() <= 0) {
            config.setEstadisticasVentanaRecienteSegundos(DEFAULT_STATS_RECENT_WINDOW_SECONDS);
        }
        if (config.getEstadisticasResolucionHistoricaSegundos() == null || config.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            config.setEstadisticasResolucionHistoricaSegundos(DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS);
        }

        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(getJsonFile(), config);
        } catch (JacksonException e) {
            System.err.println("Error al guardar " + JSON_FILE + ": " + e.getMessage());
        }
    }

    public static void guardarTema(String temaClassName) {
        EasyMCConfig config = cargarConfiguracion();
        config.setTemaClassName(temaClassName);
        guardarConfiguracion(config);
    }

    public static String getTemaGuardado() {
        return cargarConfiguracion().getTemaClassName();
    }

    public static String getTemaPorDefecto() {
        return DEFAULT_THEME_CLASS;
    }

    public static int getEstadisticasRangoSegundos() {
        Integer seconds = cargarConfiguracion().getEstadisticasRangoSegundos();
        return seconds == null || seconds <= 0 ? DEFAULT_STATS_RANGE_SECONDS : seconds;
    }

    public static void guardarEstadisticasRangoSegundos(int seconds) {
        EasyMCConfig config = cargarConfiguracion();
        config.setEstadisticasRangoSegundos(seconds > 0 ? seconds : DEFAULT_STATS_RANGE_SECONDS);
        guardarConfiguracion(config);
    }

    public static boolean isEstadisticasPersistenciaActiva() {
        Boolean enabled = cargarConfiguracion().getEstadisticasPersistenciaActiva();
        return enabled == null ? DEFAULT_STATS_PERSISTENCE_ENABLED : enabled;
    }

    public static int getEstadisticasVentanaRecienteSegundos() {
        Integer seconds = cargarConfiguracion().getEstadisticasVentanaRecienteSegundos();
        return seconds == null || seconds <= 0 ? DEFAULT_STATS_RECENT_WINDOW_SECONDS : seconds;
    }

    public static int getEstadisticasResolucionHistoricaSegundos() {
        Integer seconds = cargarConfiguracion().getEstadisticasResolucionHistoricaSegundos();
        return seconds == null || seconds <= 0 ? DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS : seconds;
    }

    public static void guardarEstadisticasPersistenciaActiva(boolean enabled) {
        EasyMCConfig config = cargarConfiguracion();
        config.setEstadisticasPersistenciaActiva(enabled);
        guardarConfiguracion(config);
    }

    public static void guardarEstadisticasVentanaRecienteSegundos(int seconds) {
        EasyMCConfig config = cargarConfiguracion();
        config.setEstadisticasVentanaRecienteSegundos(seconds > 0 ? seconds : DEFAULT_STATS_RECENT_WINDOW_SECONDS);
        guardarConfiguracion(config);
    }

    public static void guardarEstadisticasResolucionHistoricaSegundos(int seconds) {
        EasyMCConfig config = cargarConfiguracion();
        config.setEstadisticasResolucionHistoricaSegundos(seconds > 0 ? seconds : DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS);
        guardarConfiguracion(config);
    }

    public static Path getBaseDirectory() {
        try {
            Path baseDir = Path.of(GestorConfiguracion.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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

            return baseDir;
        } catch (URISyntaxException | RuntimeException e) {
            return Path.of(".");
        }
    }

    private static EasyMCConfig crearConfiguracionPorDefecto() {
        return new EasyMCConfig(
                DEFAULT_THEME_CLASS,
                DEFAULT_STATS_RANGE_SECONDS,
                DEFAULT_STATS_PERSISTENCE_ENABLED,
                DEFAULT_STATS_RECENT_WINDOW_SECONDS,
                DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS
        );
    }

    private static File getJsonFile() {
        Path baseDir = getBaseDirectory();
        Path jsonPath = baseDir.resolve(JSON_FILE);
        migrateLegacyFileIfNeeded(baseDir.resolve(LEGACY_JSON_FILE), jsonPath);
        return jsonPath.toFile();
    }

    private static void migrateLegacyFileIfNeeded(Path legacyPath, Path targetPath) {
        if (legacyPath == null || targetPath == null || Files.exists(targetPath) || !Files.exists(legacyPath)) {
            return;
        }
        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.move(legacyPath, targetPath);
        } catch (Exception e) {
            System.err.println("No se ha podido migrar " + legacyPath.getFileName() + " a " + targetPath.getFileName() + ": " + e.getMessage());
        }
    }

    private static boolean normalizarConfiguracion(EasyMCConfig config) {
        boolean changed = false;
        if (config.getEstadisticasRangoSegundos() == null || config.getEstadisticasRangoSegundos() <= 0) {
            config.setEstadisticasRangoSegundos(DEFAULT_STATS_RANGE_SECONDS);
            changed = true;
        }
        if (config.getEstadisticasPersistenciaActiva() == null) {
            config.setEstadisticasPersistenciaActiva(DEFAULT_STATS_PERSISTENCE_ENABLED);
            changed = true;
        }
        if (config.getEstadisticasVentanaRecienteSegundos() == null || config.getEstadisticasVentanaRecienteSegundos() <= 0) {
            config.setEstadisticasVentanaRecienteSegundos(DEFAULT_STATS_RECENT_WINDOW_SECONDS);
            changed = true;
        }
        if (config.getEstadisticasResolucionHistoricaSegundos() == null || config.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            config.setEstadisticasResolucionHistoricaSegundos(DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS);
            changed = true;
        }
        return changed;
    }
}
