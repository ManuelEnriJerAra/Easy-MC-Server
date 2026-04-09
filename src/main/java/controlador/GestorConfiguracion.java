package controlador;

import modelo.EasyMCConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GestorConfiguracion {
    private static final String JSON_FILE = "EasyMCConfig.json";
    private static final String DEFAULT_THEME_CLASS =
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedLightIJTheme";

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
            }
            return config;
        } catch (JacksonException e) {
            System.err.println("Error al cargar EasyMCConfig.json: " + e.getMessage());
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

        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(getJsonFile(), config);
        } catch (JacksonException e) {
            System.err.println("Error al guardar EasyMCConfig.json: " + e.getMessage());
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

    private static EasyMCConfig crearConfiguracionPorDefecto() {
        return new EasyMCConfig(DEFAULT_THEME_CLASS);
    }

    private static File getJsonFile() {
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

            return baseDir.resolve(JSON_FILE).toFile();
        } catch (URISyntaxException | RuntimeException e) {
            return new File(JSON_FILE);
        }
    }
}
