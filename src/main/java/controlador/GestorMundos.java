package controlador;

import modelo.Server;
import modelo.ServerProperties;

import javax.swing.*;
import java.awt.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public final class GestorMundos {
    public static final String DIRECTORIO_MUNDOS = "Easy-MC-Worlds";
    private static final String META_ARCHIVO = ".emw-world.properties";
    private static final String SUFIJO_NETHER = "_nether";
    private static final String SUFIJO_END = "_the_end";

    private GestorMundos() {
    }

    public static boolean sincronizarMundosServidor(Server server) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) return false;

        try {
            Path serverDir = Path.of(server.getServerDir());
            if(!Files.isDirectory(serverDir)) return false;

            Files.createDirectories(getDirectorioMundos(server));
            Properties props = cargarServerProperties(serverDir, true);

            String levelNameBruto = props.getProperty("level-name", "world").trim();
            if(levelNameBruto.isBlank()) levelNameBruto = "world";

            String mundoActivo = normalizarNombreMundo(levelNameBruto);
            if(mundoActivo.isBlank()) mundoActivo = "world";

            boolean cambios = false;
            if(!esRutaGestionada(levelNameBruto)) {
                cambios |= migrarMundoRaizASistemaGestionado(serverDir, mundoActivo);
                props.setProperty("level-name", construirLevelNameGestionado(mundoActivo));
                cambios = true;
            }

            Path mundoActivoDir = getDirectorioMundo(server, mundoActivo);
            if(!Files.exists(mundoActivoDir)) {
                Files.createDirectories(mundoActivoDir);
                cambios = true;
            }

            if(listarMundos(server).isEmpty()) {
                Files.createDirectories(getDirectorioMundo(server, "world"));
                props.setProperty("level-name", construirLevelNameGestionado("world"));
                cambios = true;
            }

            if(cambios) {
                guardarServerProperties(serverDir, props);
            }
            return cambios;
        } catch (IOException e) {
            throw new RuntimeException("No se ha podido sincronizar la carpeta de mundos: " + e.getMessage(), e);
        }
    }

    public static List<String> listarMundos(Server server) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) return List.of();

        Path worldsDir = getDirectorioMundos(server);
        if(!Files.isDirectory(worldsDir)) return List.of();

        try(Stream<Path> paths = Files.list(worldsDir)) {
            List<String> mundos = paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(nombre -> !nombre.endsWith(SUFIJO_NETHER) && !nombre.endsWith(SUFIJO_END))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            return new ArrayList<>(mundos);
        } catch (IOException e) {
            throw new RuntimeException("No se han podido listar los mundos: " + e.getMessage(), e);
        }
    }

    public static String getNombreMundoActivo(Server server) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) return "world";

        try {
            Properties props = cargarServerProperties(Path.of(server.getServerDir()), false);
            String levelName = props.getProperty("level-name", "world");
            String normalizado = normalizarNombreMundo(levelName);
            return normalizado.isBlank() ? "world" : normalizado;
        } catch (IOException e) {
            return "world";
        }
    }

    public static boolean importarMundo(Server server, Component parent) {
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona la carpeta del mundo");
        if(chooser.showDialog(parent, "Importar") != JFileChooser.APPROVE_OPTION) return false;

        Path origen = chooser.getSelectedFile().toPath();
        if(!esMundoValido(origen)) {
            JOptionPane.showMessageDialog(parent,
                    "La carpeta seleccionada no parece ser un mundo v?lido de Minecraft. Debe contener level.dat.",
                    "Importar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        String nombreDestino = resolverNombreDisponible(parent, server, sanitizarNombreMundo(origen.getFileName().toString()));
        if(nombreDestino == null) return false;

        try {
            copiarConDimensiones(origen, getDirectorioMundo(server, nombreDestino));
            JOptionPane.showMessageDialog(parent,
                    "Mundo importado en " + DIRECTORIO_MUNDOS + ": " + nombreDestino,
                    "Importar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido importar el mundo: " + e.getMessage(),
                    "Importar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public static boolean exportarMundo(Server server, String nombreMundo, Component parent) {
        if(server == null || nombreMundo == null || nombreMundo.isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "No hay un mundo seleccionado para exportar.",
                    "Exportar mundo",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        Path origen = getDirectorioMundo(server, nombreMundo);
        if(!Files.isDirectory(origen)) {
            JOptionPane.showMessageDialog(parent,
                    "El mundo seleccionado no existe en " + DIRECTORIO_MUNDOS + ".",
                    "Exportar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona la carpeta de destino");
        if(chooser.showDialog(parent, "Exportar") != JFileChooser.APPROVE_OPTION) return false;

        Path destinoBase = chooser.getSelectedFile().toPath();
        Path destino = destinoBase.resolve(nombreMundo);
        if(Files.exists(destino)) {
            JOptionPane.showMessageDialog(parent,
                    "Ya existe una carpeta con ese nombre en el destino seleccionado.",
                    "Exportar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            copiarConDimensiones(origen, destino);
            JOptionPane.showMessageDialog(parent,
                    "Mundo exportado en: " + destino,
                    "Exportar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido exportar el mundo: " + e.getMessage(),
                    "Exportar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public static boolean cambiarMundo(Server server, String nombreMundo, Component parent) {
        if(server == null || nombreMundo == null || nombreMundo.isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "Selecciona un mundo v?lido.",
                    "Cambiar mundo",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        Path mundoDir = getDirectorioMundo(server, nombreMundo);
        if(!Files.isDirectory(mundoDir)) {
            JOptionPane.showMessageDialog(parent,
                    "El mundo seleccionado no existe en " + DIRECTORIO_MUNDOS + ".",
                    "Cambiar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            Path serverDir = Path.of(server.getServerDir());
            Properties props = cargarServerProperties(serverDir, true);
            props.setProperty("level-name", construirLevelNameGestionado(nombreMundo));
            aplicarMetadataMundo(mundoDir, props);
            guardarServerProperties(serverDir, props);
            JOptionPane.showMessageDialog(parent,
                    "El mundo activo ahora es: " + nombreMundo + ".\nSe aplicará al iniciar el servidor.",
                    "Cambiar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido cambiar el mundo: " + e.getMessage(),
                    "Cambiar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public static boolean generarNuevoMundo(Server server, WorldGenerationSettings settings, Component parent) {
        if(settings == null) return false;
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        String nombre = sanitizarNombreMundo(settings.getNombre());
        if(nombre.isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "El nombre del mundo no puede estar vac?o.",
                    "Generar mundo",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        Path mundoDir = getDirectorioMundo(server, nombre);
        if(Files.exists(mundoDir)) {
            JOptionPane.showMessageDialog(parent,
                    "Ya existe un mundo con ese nombre.",
                    "Generar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            Files.createDirectories(mundoDir);
            guardarMetadataMundo(mundoDir, settings);
            if(settings.isActivarAlCrear()) {
                return cambiarMundo(server, nombre, parent);
            }
            JOptionPane.showMessageDialog(parent,
                    "Mundo preparado en " + DIRECTORIO_MUNDOS + ": " + nombre + ". Se generar? al activarlo e iniciar el servidor.",
                    "Generar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido preparar el nuevo mundo: " + e.getMessage(),
                    "Generar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public static Path getDirectorioMundos(Server server) {
        return Path.of(server.getServerDir()).resolve(DIRECTORIO_MUNDOS);
    }

    public static Path getDirectorioMundo(Server server, String nombreMundo) {
        return getDirectorioMundos(server).resolve(nombreMundo);
    }

    private static boolean puedeModificarMundos(Server server, Component parent) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "No hay un servidor seleccionado v?lido.",
                    "Mundos",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if(server.getServerProcess() != null && server.getServerProcess().isAlive()) {
            JOptionPane.showMessageDialog(parent,
                    "Det?n el servidor antes de importar, exportar, generar o cambiar mundos.",
                    "Mundos",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private static boolean esMundoValido(Path directorio) {
        return directorio != null
                && Files.isDirectory(directorio)
                && Files.isRegularFile(directorio.resolve("level.dat"));
    }

    private static String resolverNombreDisponible(Component parent, Server server, String nombreBase) {
        String candidato = nombreBase == null || nombreBase.isBlank() ? "world" : nombreBase;
        Set<String> existentes = new LinkedHashSet<>(listarMundos(server));
        while(existentes.contains(candidato)) {
            String nuevoNombre = JOptionPane.showInputDialog(parent,
                    "Ya existe un mundo con ese nombre. Escribe uno nuevo:",
                    candidato + "-importado");
            if(nuevoNombre == null) return null;
            candidato = sanitizarNombreMundo(nuevoNombre);
            if(candidato.isBlank()) {
                JOptionPane.showMessageDialog(parent,
                        "El nombre indicado no es v?lido.",
                        "Importar mundo",
                        JOptionPane.WARNING_MESSAGE);
                candidato = nombreBase;
            }
        }
        return candidato;
    }

    private static void copiarConDimensiones(Path origenBase, Path destinoBase) throws IOException {
        Utilidades.copiarDirectorio(origenBase, destinoBase);
        copiarSiExiste(origenBase.resolveSibling(origenBase.getFileName() + SUFIJO_NETHER), destinoBase.resolveSibling(destinoBase.getFileName() + SUFIJO_NETHER));
        copiarSiExiste(origenBase.resolveSibling(origenBase.getFileName() + SUFIJO_END), destinoBase.resolveSibling(destinoBase.getFileName() + SUFIJO_END));
    }

    private static void copiarSiExiste(Path origen, Path destino) throws IOException {
        if(Files.isDirectory(origen)) {
            Utilidades.copiarDirectorio(origen, destino);
        }
    }

    private static boolean migrarMundoRaizASistemaGestionado(Path serverDir, String nombreMundo) throws IOException {
        Path origen = serverDir.resolve(nombreMundo);
        Path destino = serverDir.resolve(DIRECTORIO_MUNDOS).resolve(nombreMundo);
        boolean cambios = false;

        if(Files.isDirectory(origen) && !Files.exists(destino)) {
            Utilidades.moverDirectorio(origen, destino);
            cambios = true;
        } else if(!Files.exists(destino)) {
            Files.createDirectories(destino);
            cambios = true;
        }

        cambios |= moverDimensionSiHaceFalta(serverDir.resolve(nombreMundo + SUFIJO_NETHER), serverDir.resolve(DIRECTORIO_MUNDOS).resolve(nombreMundo + SUFIJO_NETHER));
        cambios |= moverDimensionSiHaceFalta(serverDir.resolve(nombreMundo + SUFIJO_END), serverDir.resolve(DIRECTORIO_MUNDOS).resolve(nombreMundo + SUFIJO_END));
        return cambios;
    }

    private static boolean moverDimensionSiHaceFalta(Path origen, Path destino) throws IOException {
        if(!Files.isDirectory(origen) || Files.exists(destino)) return false;
        Utilidades.moverDirectorio(origen, destino);
        return true;
    }

    private static Properties cargarServerProperties(Path serverDir, boolean crearSiNoExiste) throws IOException {
        Path propertiesPath = serverDir.resolve("server.properties");
        if(!Files.exists(propertiesPath) && crearSiNoExiste) {
            try {
                new ServerProperties().escribePropiedades(serverDir.toFile());
            } catch (IllegalAccessException e) {
                throw new IOException("No se ha podido crear server.properties", e);
            }
        }

        Properties props = new Properties();
        if(Files.exists(propertiesPath)) {
            try(FileInputStream fis = new FileInputStream(propertiesPath.toFile())) {
                props.load(fis);
            }
        }
        return props;
    }

    private static void guardarServerProperties(Path serverDir, Properties props) throws IOException {
        Path propertiesPath = serverDir.resolve("server.properties");
        try(FileOutputStream fos = new FileOutputStream(propertiesPath.toFile())) {
            props.store(fos, null);
        }
    }

    private static void guardarMetadataMundo(Path mundoDir, WorldGenerationSettings settings) throws IOException {
        Properties metadata = new Properties();
        metadata.setProperty("display-name", settings.getNombre());
        metadata.setProperty("level-seed", Objects.toString(settings.getSemilla(), ""));
        metadata.setProperty("level-type", settings.getTipoMundo());
        metadata.setProperty("generate-structures", String.valueOf(settings.isGenerarEstructuras()));
        metadata.setProperty("hardcore", String.valueOf(settings.isHardcore()));
        metadata.setProperty("gamemode", settings.getGamemode());
        metadata.setProperty("difficulty", settings.getDificultad());
        metadata.setProperty("allow-nether", String.valueOf(settings.isPermitirNether()));

        try(FileOutputStream fos = new FileOutputStream(mundoDir.resolve(META_ARCHIVO).toFile())) {
            metadata.store(fos, "Easy MC Server managed world metadata");
        }
    }

    private static void aplicarMetadataMundo(Path mundoDir, Properties props) throws IOException {
        Path metadataPath = mundoDir.resolve(META_ARCHIVO);
        if(!Files.exists(metadataPath)) return;

        Properties metadata = new Properties();
        try(FileInputStream fis = new FileInputStream(metadataPath.toFile())) {
            metadata.load(fis);
        }

        copiarPropiedad(metadata, props, "level-seed");
        copiarPropiedad(metadata, props, "level-type");
        copiarPropiedad(metadata, props, "generate-structures");
        copiarPropiedad(metadata, props, "hardcore");
        copiarPropiedad(metadata, props, "gamemode");
        copiarPropiedad(metadata, props, "difficulty");
        copiarPropiedad(metadata, props, "allow-nether");
    }

    private static void copiarPropiedad(Properties origen, Properties destino, String key) {
        String value = origen.getProperty(key);
        if(value != null) {
            destino.setProperty(key, value);
        }
    }

    private static boolean esRutaGestionada(String levelName) {
        if(levelName == null) return false;
        String normalizado = levelName.replace('\\', '/');
        return normalizado.startsWith(DIRECTORIO_MUNDOS + "/");
    }

    private static String construirLevelNameGestionado(String nombreMundo) {
        return DIRECTORIO_MUNDOS + "/" + nombreMundo;
    }

    private static String normalizarNombreMundo(String levelName) {
        if(levelName == null) return "world";
        String normalizado = levelName.replace('\\', '/').trim();
        if(normalizado.startsWith(DIRECTORIO_MUNDOS + "/")) {
            normalizado = normalizado.substring((DIRECTORIO_MUNDOS + "/").length());
        }
        int ultimaBarra = normalizado.lastIndexOf('/');
        if(ultimaBarra >= 0 && ultimaBarra + 1 < normalizado.length()) {
            normalizado = normalizado.substring(ultimaBarra + 1);
        }
        return sanitizarNombreMundo(normalizado);
    }

    private static String sanitizarNombreMundo(String nombre) {
        if(nombre == null) return "";
        return nombre.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ");
    }

    public static final class WorldGenerationSettings {
        private final String nombre;
        private final String semilla;
        private final String tipoMundo;
        private final boolean generarEstructuras;
        private final boolean hardcore;
        private final String gamemode;
        private final String dificultad;
        private final boolean permitirNether;
        private final boolean activarAlCrear;

        public WorldGenerationSettings(String nombre,
                                       String semilla,
                                       String tipoMundo,
                                       boolean generarEstructuras,
                                       boolean hardcore,
                                       String gamemode,
                                       String dificultad,
                                       boolean permitirNether,
                                       boolean activarAlCrear) {
            this.nombre = nombre;
            this.semilla = semilla;
            this.tipoMundo = tipoMundo;
            this.generarEstructuras = generarEstructuras;
            this.hardcore = hardcore;
            this.gamemode = gamemode;
            this.dificultad = dificultad;
            this.permitirNether = permitirNether;
            this.activarAlCrear = activarAlCrear;
        }

        public String getNombre() {
            return nombre;
        }

        public String getSemilla() {
            return semilla;
        }

        public String getTipoMundo() {
            return tipoMundo;
        }

        public boolean isGenerarEstructuras() {
            return generarEstructuras;
        }

        public boolean isHardcore() {
            return hardcore;
        }

        public String getGamemode() {
            return gamemode;
        }

        public String getDificultad() {
            return dificultad;
        }

        public boolean isPermitirNether() {
            return permitirNether;
        }

        public boolean isActivarAlCrear() {
            return activarAlCrear;
        }
    }
}
