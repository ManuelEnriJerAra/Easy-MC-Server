/*
 * Fichero: GestorServidores.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Esta clase lleva a cabo toda la gestión de los servidores como conjunto algunas funciones son crear, borrar, listar,
 * importar, inicializar, etc.
 * */

package controlador;

import controlador.extensions.ExtensionCompatibilityReport;
import controlador.extensions.ExtensionCatalogDetails;
import controlador.extensions.ExtensionCatalogEntry;
import controlador.extensions.ExtensionCatalogProviderDescriptor;
import controlador.extensions.ExtensionCatalogQuery;
import controlador.extensions.ExtensionCatalogService;
import controlador.extensions.ExtensionDownloadPlan;
import controlador.extensions.ExtensionInstallResolution;
import controlador.extensions.ExtensionUpdateCandidate;
import controlador.extensions.ServerExtensionsService;
import modelo.Server;
import modelo.ServerConfig;
import controlador.platform.ServerInstallationRequest;
import controlador.platform.ServerCreationOption;
import controlador.platform.FileDownloader;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import controlador.platform.ServerValidationResult;
import com.formdev.flatlaf.extras.components.FlatProgressBar;
import lombok.Getter;
import lombok.Setter;
import modelo.extensions.ServerExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static controlador.Utilidades.copiarArchivo;
import static controlador.Utilidades.rellenaEULA;
import static java.lang.Math.max;
import static java.lang.Math.min;

@Getter
@Setter

public class GestorServidores {
    private static final List<String> ARCHIVOS_CONFIG_PRESERVABLES = List.of(
            "server.properties",
            "eula.txt",
            "server-icon.png",
            "whitelist.json",
            "whitelist.txt",
            "ops.json",
            "ops.txt",
            "banned-ips.json",
            "banned-ips.txt",
            "banned-players.json",
            "banned-players.txt",
            "permissions.json",
            "usercache.json"
    );
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // ===== ATRIBUTOS =====
    private static final MojangAPI MOJANG_API = new MojangAPI();

    // AtomicInteger nos elimina el riesgo de condición de carrera si varios servidores intentan acceder a la vez
    private static final AtomicInteger NEXT_PORT_SESION = new AtomicInteger(25565);
    private static final int PUERTO_MINECRAFT_DEFECTO = 25565;
    private static final int PUERTO_MAXIMO = 65535;

    private static final String JSON_FILE = "easy-mc-server-list.json";
    private static final String LEGACY_JSON_FILE = "ServerList.json";

    private static File getJsonFile() {
        try {
            Path baseDir = Path.of(GestorServidores.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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

            Path jsonPath = baseDir.resolve(JSON_FILE);
            migrateLegacyFileIfNeeded(baseDir.resolve(LEGACY_JSON_FILE), jsonPath);
            return jsonPath.toFile();
        } catch (URISyntaxException | RuntimeException e) {
            return new File(JSON_FILE);
        }
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
        } catch (IOException e) {
            System.err.println("No se ha podido migrar " + legacyPath.getFileName() + " a " + targetPath.getFileName() + ": " + e.getMessage());
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final File jsonFile;
    private final ServerExtensionsService serverExtensionsService = new ServerExtensionsService();
    private final ExtensionCatalogService extensionCatalogService = new ExtensionCatalogService();
    private final FileDownloader extensionDownloader = (url, destination) -> {
        java.net.URI uri = java.net.URI.create(url);
        try (InputStream in = uri.toURL().openStream()) {
            Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    };

    private String avisoServidoresNoCargados;

    // Lista Principal (runtime + persistencia)
    private List<Server> listaServidores;
    private Server servidorSeleccionado;

    // Esto gestiona los listeners
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // ===== CONSTRUCTORES =====

    // Constructor por defecto
    public GestorServidores() {
        this(getJsonFile());
    }

    GestorServidores(File jsonFile) {
        this.jsonFile = jsonFile == null ? getJsonFile() : jsonFile;
        this.listaServidores = cargarServidores();
        boolean cambiosOrden = normalizarMetadatosOrden(true);
        validarYLimpiarServidoresPersistidos();
        boolean cambiosMundos = sincronizarMundosServidoresCargados();
        boolean cambiosExtensiones = sincronizarExtensionesServidoresCargados();
        if (cambiosOrden || cambiosMundos || cambiosExtensiones) {
            guardarServidores();
        }
    }

    private boolean sincronizarMundosServidoresCargados() {
        if (listaServidores == null || listaServidores.isEmpty()) return false;

        boolean cambios = false;
        for (Server server : listaServidores) {
            if (server == null) continue;
            try {
                if (GestorMundos.sincronizarMundosServidor(server)) {
                    cambios = true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return cambios;
    }

    private boolean sincronizarExtensionesServidoresCargados() {
        if (listaServidores == null || listaServidores.isEmpty()) return false;

        boolean cambios = false;
        for (Server server : listaServidores) {
            if (server == null) continue;
            try {
                if (sincronizarExtensionesServidor(server)) {
                    cambios = true;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return cambios;
    }

    // cargamos todos los servidores del JSON
    private List<Server> cargarServidores(){
        File file = jsonFile;
        if (!file.exists()) {
            List<Server> servidores = new ArrayList<>();
            try{
                mapper.writerWithDefaultPrettyPrinter().writeValue(file, servidores);
            } catch (JacksonException e) {
                System.err.println("Error al inicializar servidores: " + e.getMessage());
            }
            return servidores;
        }
        try{
            return mapper.readValue(file, new TypeReference<>(){});
        } catch (JacksonException e) {
            System.err.println("Error al cargar servidores: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean normalizarMetadatosOrden(boolean reordenarListaInterna) {
        if (listaServidores == null) {
            listaServidores = new ArrayList<>();
            return false;
        }

        boolean cambios = false;
        List<Server> depurada = new ArrayList<>();
        for (Server server : listaServidores) {
            if (server == null) {
                cambios = true;
                continue;
            }
            if (asegurarMetadatosPersistentes(server)) {
                cambios = true;
            }
            depurada.add(server);
        }
        listaServidores = depurada;

        List<Server> porOrdenBase = new ArrayList<>(listaServidores);
        porOrdenBase.sort(Comparator.comparingInt(this::valorOrdenListaParaNormalizar));
        for (int i = 0; i < porOrdenBase.size(); i++) {
            Server server = porOrdenBase.get(i);
            if (!Objects.equals(server.getOrdenLista(), i)) {
                server.setOrdenLista(i);
                cambios = true;
            }
        }

        int siguienteOrdenFavorito = obtenerSiguienteOrdenFavorito();
        for (Server server : listaServidores) {
            if (!Boolean.TRUE.equals(server.getFavorito())) {
                continue;
            }
            if (server.getOrdenFavorito() != null) {
                continue;
            }
            server.setOrdenFavorito(siguienteOrdenFavorito++);
            cambios = true;
        }

        if (reordenarListaInterna) {
            listaServidores = copiarListaServidoresOrdenada();
        }
        return cambios;
    }

    private boolean asegurarMetadatosPersistentes(Server server) {
        boolean cambios = false;
        if (server.migrarModeloLegacy()) {
            cambios = true;
        }
        if (server.getId() == null || server.getId().isBlank()) {
            server.setId(UUID.randomUUID().toString());
            cambios = true;
        }
        if (server.getServerConfig() == null) {
            server.setServerConfig(new ServerConfig());
            cambios = true;
        }
        if (sincronizarPuertoConfigDesdeProperties(server)) {
            cambios = true;
        }
        if (server.getFavorito() == null) {
            server.setFavorito(Boolean.FALSE);
            cambios = true;
        }
        if (server.getEstadisticasRangoSegundos() == null || server.getEstadisticasRangoSegundos() <= 0) {
            server.setEstadisticasRangoSegundos(300);
            cambios = true;
        }
        if (server.getEstadisticasPersistenciaActiva() == null) {
            server.setEstadisticasPersistenciaActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasVentanaRecienteSegundos() == null || server.getEstadisticasVentanaRecienteSegundos() <= 0) {
            server.setEstadisticasVentanaRecienteSegundos(30 * 24 * 60 * 60);
            cambios = true;
        }
        if (server.getEstadisticasResolucionHistoricaSegundos() == null || server.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            server.setEstadisticasResolucionHistoricaSegundos(60);
            cambios = true;
        }
        if (server.getEstadisticasCpuActiva() == null) {
            server.setEstadisticasCpuActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasCpuHistorial() == null) {
            server.setEstadisticasCpuHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasRamActiva() == null) {
            server.setEstadisticasRamActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasRamHistorial() == null) {
            server.setEstadisticasRamHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasDiscoActiva() == null) {
            server.setEstadisticasDiscoActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasDiscoHistorial() == null) {
            server.setEstadisticasDiscoHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasJugadoresActiva() == null) {
            server.setEstadisticasJugadoresActiva(Boolean.FALSE);
            cambios = true;
        }
        if (server.getEstadisticasJugadoresHistorial() == null) {
            server.setEstadisticasJugadoresHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getPreviewRenderProfileId() == null || server.getPreviewRenderProfileId().isBlank()) {
            server.setPreviewRenderProfileId("quality");
            cambios = true;
        }
        if (server.getPreviewRenderRealtime() == null) {
            server.setPreviewRenderRealtime(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewShowSpawn() == null) {
            server.setPreviewShowSpawn(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewShowPlayers() == null) {
            server.setPreviewShowPlayers(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewShowChunkGrid() == null) {
            server.setPreviewShowChunkGrid(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewUseWholeMap() == null) {
            server.setPreviewUseWholeMap(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewRenderLimitPixels() == null || server.getPreviewRenderLimitPixels() < 0) {
            server.setPreviewRenderLimitPixels(256);
            cambios = true;
        }
        if (server.getPreviewRenderCenterId() == null || server.getPreviewRenderCenterId().isBlank()) {
            server.setPreviewRenderCenterId("spawn");
            cambios = true;
        }
        return cambios;
    }

    private boolean sincronizarPuertoConfigDesdeProperties(Server server) {
        if (server == null || server.getServerConfig() == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return false;
        }
        Path propertiesPath;
        try {
            propertiesPath = Path.of(server.getServerDir()).resolve("server.properties");
        } catch (RuntimeException e) {
            return false;
        }
        if (!Files.isRegularFile(propertiesPath)) {
            return false;
        }
        try {
            Properties properties = Utilidades.cargarPropertiesUtf8(propertiesPath);
            Integer propertiesPort = parsePort(properties.getProperty("server-port"));
            if (propertiesPort == null || propertiesPort == server.getServerConfig().getPuerto()) {
                return false;
            }
            server.getServerConfig().setPuerto(propertiesPort);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean sincronizarExtensionesServidor(Server server) throws IOException {
        if (server == null) {
            return false;
        }
        List<ServerExtension> actuales = server.getExtensions() == null ? List.of() : List.copyOf(server.getExtensions());
        List<ServerExtension> detectadas = serverExtensionsService.detectInstalledExtensions(server);
        if (actuales.equals(detectadas)) {
            return false;
        }
        server.setExtensions(new ArrayList<>(detectadas));
        return true;
    }

    private void copiarMetadatosOrdenSiFaltan(Server origen, Server destino) {
        if (origen == null || destino == null) return;
        if (destino.getOrdenLista() == null) {
            destino.setOrdenLista(origen.getOrdenLista());
        }
        if (destino.getFavorito() == null) {
            destino.setFavorito(origen.getFavorito());
        }
        if (destino.getOrdenFavorito() == null) {
            destino.setOrdenFavorito(origen.getOrdenFavorito());
        }
    }

    private int valorOrdenListaParaNormalizar(Server server) {
        if (server == null || server.getOrdenLista() == null) {
            return Integer.MAX_VALUE;
        }
        return server.getOrdenLista();
    }

    private int valorOrdenVisual(Integer orden) {
        return orden == null ? Integer.MAX_VALUE : orden;
    }

    private int obtenerSiguienteOrdenLista() {
        int maximo = -1;
        for (Server server : listaServidores) {
            if (server == null || server.getOrdenLista() == null) continue;
            maximo = Math.max(maximo, server.getOrdenLista());
        }
        return maximo + 1;
    }

    private int obtenerSiguienteOrdenFavorito() {
        int maximo = -1;
        for (Server server : listaServidores) {
            if (server == null || server.getOrdenFavorito() == null) continue;
            maximo = Math.max(maximo, server.getOrdenFavorito());
        }
        return maximo + 1;
    }

    private Comparator<Server> comparadorVisualServidores() {
        return Comparator
                .comparing((Server server) -> !Boolean.TRUE.equals(server.getFavorito()))
                .thenComparingInt(server -> Boolean.TRUE.equals(server.getFavorito())
                        ? valorOrdenVisual(server.getOrdenFavorito())
                        : Integer.MAX_VALUE)
                .thenComparingInt(server -> valorOrdenVisual(server.getOrdenLista()))
                .thenComparing(server -> textoOrdenable(server.getDisplayName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(server -> textoOrdenable(server.getServerDir()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(server -> textoOrdenable(server.getId()), String.CASE_INSENSITIVE_ORDER);
    }

    private List<Server> copiarListaServidoresOrdenada() {
        List<Server> ordenados = new ArrayList<>(listaServidores);
        ordenados.sort(comparadorVisualServidores());
        return ordenados;
    }

    private String textoOrdenable(String texto) {
        return texto == null ? "" : texto;
    }

    private ServerPlatformProfile inspeccionarServidor(Path serverDir) {
        return ServerPlatformAdapters.detect(serverDir);
    }

    public boolean puedeConvertirseAPlataformaCompatible(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return false;
        }

        Path serverDir;
        try {
            serverDir = Path.of(server.getServerDir());
        } catch (RuntimeException ex) {
            return false;
        }

        if (!Files.isDirectory(serverDir)) {
            return false;
        }

        ServerPlatformProfile profile = inspeccionarServidor(serverDir);
        if (profile != null) {
            aplicarPerfilPlataforma(server, profile);
        }

        return profile != null && profile.platform() == modelo.extensions.ServerPlatform.VANILLA;
    }

    public boolean admiteGestionDeExtensiones(Server server) {
        return serverExtensionsService.supportsManagedExtensions(server);
    }

    public List<Path> obtenerDirectoriosExtensiones(Server server) {
        return serverExtensionsService.getManagedExtensionDirectories(server);
    }

    public List<ServerExtension> detectarExtensionesInstaladas(Server server) throws IOException {
        if (server == null) {
            return List.of();
        }
        return serverExtensionsService.detectInstalledExtensions(server);
    }

    public List<ServerExtension> sincronizarExtensionesInstaladas(Server server) throws IOException {
        if (server == null) {
            return List.of();
        }
        sincronizarExtensionesServidor(server);
        guardarServidor(server);
        return server.getExtensions() == null ? List.of() : List.copyOf(server.getExtensions());
    }

    public ServerExtension instalarExtensionManual(Server server, Path extensionJar) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        ServerExtension installed = serverExtensionsService.installManualJar(server, extensionJar);
        guardarServidor(server);
        return installed;
    }

    public ExtensionCompatibilityReport validarCompatibilidadExtension(Server server, Path extensionJar) throws IOException {
        return serverExtensionsService.validateCompatibility(server, extensionJar);
    }

    public ExtensionCompatibilityReport validarCompatibilidadExtension(Server server, ServerExtension extension) {
        return serverExtensionsService.validateCompatibility(server, extension);
    }

    public boolean eliminarExtensionLocal(Server server, ServerExtension extension) throws IOException {
        if (server == null || extension == null) {
            return false;
        }
        boolean removed = serverExtensionsService.removeExtension(server, extension);
        guardarServidor(server);
        return removed;
    }

    public List<ExtensionCatalogProviderDescriptor> obtenerRepositoriosExtensiones() {
        return extensionCatalogService.getAvailableProviders();
    }

    public List<ExtensionCatalogEntry> buscarExtensionesExternas(ExtensionCatalogQuery query) throws IOException {
        return extensionCatalogService.search(query);
    }

    public List<ExtensionCatalogEntry> buscarExtensionesExternas(Server server, String queryText, int limit) throws IOException {
        return extensionCatalogService.search(extensionCatalogService.buildQueryForServer(server, queryText, limit));
    }

    public ExtensionCatalogDetails obtenerDetalleExtensionExterna(String providerId,
                                                                  String projectId,
                                                                  ExtensionCatalogQuery query) throws IOException {
        return extensionCatalogService.getDetails(providerId, projectId, query).orElse(null);
    }

    public ExtensionDownloadPlan prepararDescargaExtensionExterna(String providerId,
                                                                  String projectId,
                                                                  String versionId,
                                                                  Server server) throws IOException {
        return extensionCatalogService.resolveDownload(providerId, projectId, versionId, server).orElse(null);
    }

    public ExtensionInstallResolution evaluarInstalacionExterna(Server server,
                                                                ExtensionCatalogEntry entry) {
        return serverExtensionsService.evaluateCatalogInstallation(server, entry);
    }

    public ExtensionInstallResolution evaluarInstalacionExterna(Server server,
                                                                ExtensionDownloadPlan downloadPlan) {
        return serverExtensionsService.evaluateCatalogInstallation(server, downloadPlan);
    }

    public List<ExtensionUpdateCandidate> buscarActualizacionesExtensiones(Server server) throws IOException {
        List<ExtensionUpdateCandidate> updates = extensionCatalogService.findUpdates(server);
        if (serverExtensionsService.applyUpdateMetadata(server, updates)) {
            guardarServidor(server);
        }
        return updates;
    }

    public ServerExtension instalarExtensionExterna(Server server,
                                                    String providerId,
                                                    String projectId,
                                                    String versionId) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        ExtensionDownloadPlan downloadPlan = prepararDescargaExtensionExterna(providerId, projectId, versionId, server);
        if (downloadPlan == null || !downloadPlan.ready()) {
            throw new IOException("No se ha podido resolver una descarga compatible para la extension externa.");
        }
        ServerExtension installed = serverExtensionsService.installCatalogDownload(server, downloadPlan, extensionDownloader);
        guardarServidor(server);
        return installed;
    }

    private void aplicarPerfilPlataforma(Server server, ServerPlatformProfile profile) {
        if (server == null || profile == null) return;
        server.setPlatform(profile.platform());
        server.setLoader(profile.loader());
        server.setLoaderVersion(profile.loaderVersion());
        server.setEcosystemType(profile.ecosystemType());
        server.setCapabilities(profile.capabilities());
        if (profile.minecraftVersion() != null && !profile.minecraftVersion().isBlank()) {
            server.setVersion(profile.minecraftVersion());
        }
    }

    private ServerPlatformAdapter resolverAdaptador(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return ServerPlatformAdapters.forPlatform(null);
        }

        Path serverDir = Path.of(server.getServerDir());
        ServerPlatformProfile profile = inspeccionarServidor(serverDir);
        if (profile != null) {
            aplicarPerfilPlataforma(server, profile);
            return ServerPlatformAdapters.forPlatform(profile.platform());
        }
        return ServerPlatformAdapters.forPlatform(server.getPlatform());
    }

    // esta función de encarga de comprobar si los servidores almacenados son correctos, si no lo son se eliminan
    private void validarYLimpiarServidoresPersistidos() {
        if (listaServidores == null || listaServidores.isEmpty()) return;

        List<Server> cargables = new ArrayList<>(); // aquí almacenamos los correctos
        List<Server> noCargables = new ArrayList<>(); // aquí almacenamos los que vamos a borrar

        // si han ocurrido cambios se los notificaremos al usuario
        boolean cambios = false;

        for (Server server : listaServidores) {
            // si el servidor no es cargable lo ignoramos y añadimos a noCargables
            if (esServidorCargable(server)) {
                try {
                    ServerPlatformProfile profile = inspeccionarServidor(Path.of(server.getServerDir()));
                    if (profile != null) {
                        aplicarPerfilPlataforma(server, profile);
                        cambios = true;
                    }
                } catch (RuntimeException ignored) {
                    // si no se puede detectar, no bloqueamos la carga
                }
                cargables.add(server);
            } else {
                noCargables.add(server);
            }
        }

        // si todos los servidores eran cargables ignoramos la revisión y no notificamos nada
        listaServidores = cargables;
        if (normalizarMetadatosOrden(true)) {
            cambios = true;
        }

        if (noCargables.isEmpty() && !cambios) return;

        guardarServidores();
        notificarCambio();

        if (noCargables.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("No se han podido cargar ")
                .append(noCargables.size())
                .append(" servidores guardados (carpeta inexistente o servidor invalido).")
                .append("\nNo se mostraran y se han eliminado de " + JSON_FILE + ".");

        // mostramos un máximo de 8 servidores que no se han podido cargar
        int maxDetalles = 8;
        int mostrados = 0;
        for (Server server : noCargables) {
            if (server == null) continue;
            if (mostrados >= maxDetalles) break;
            String nombre = server.getDisplayName() == null ? "(sin nombre)" : server.getDisplayName();
            String dir = server.getServerDir() == null ? "(sin carpeta)" : server.getServerDir();
            sb.append("\n- ").append(nombre).append(" [").append(dir).append("]");
            mostrados++;
        }
        if (noCargables.size() > maxDetalles) {
            sb.append("\n... y ").append(noCargables.size() - maxDetalles).append(" mas.");
        }

        avisoServidoresNoCargados = sb.toString();
    }

    // Si hay servidores que no han podido ser cargados se notifica, si no, pasamos directamente al programa
    public void mostrarAvisoArranqueSiProcede(Component parent) {
        if (avisoServidoresNoCargados == null || avisoServidoresNoCargados.isBlank()) return;
        String mensaje = avisoServidoresNoCargados;
        avisoServidoresNoCargados = null;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                parent,
                mensaje,
                "Servidores no cargados",
                JOptionPane.WARNING_MESSAGE
        ));
    }

    // Consideramos "cargable" si hay exactamente un .jar y parece un server jar de Minecraft
    private boolean esServidorCargable(Server server) {
        if (server == null) return false;
        if (server.getServerDir() == null || server.getServerDir().isBlank()) return false;

        Path dir = Path.of(server.getServerDir());
        if (!Files.isDirectory(dir)) return false;
        ServerPlatformProfile profile = inspeccionarServidor(dir);
        ServerPlatformAdapter adapter = ServerPlatformAdapters.forPlatform(profile == null ? server.getPlatform() : profile.platform());
        ServerValidationResult validation = adapter.validate(dir);
        if (validation.valid() && profile != null) {
            aplicarPerfilPlataforma(server, profile);
        }
        return validation.valid();
    }
    // ===== LISTENERS Y CAMBIOS =====

    // Esto permite que otros escuchen
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }


    // Añade un listener SOLO para una propiedad concreta (ej: "serverState")
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    // Esto permite que dejen de escuchar
    public void removePropertyChangeListener(PropertyChangeListener listener){
        pcs.removePropertyChangeListener(listener);
    }

    // Quita el listener de una propiedad concreta
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    // este método notifica a los oyentes de que ha ocurrido un cambio en la lista de servidores
    private void notificarCambio(){
        pcs.firePropertyChange("listaServidores", null, getListaServidores());
    }

    // este método notifica a los oyentes de que ha ocurrido un cambio en el estado del servidor
    public void notificarEstadoServidor(Server server){
        pcs.firePropertyChange("estadoServidor", null, server);
    }

    public void notificarConfiguracionServidor(Server server){
        pcs.firePropertyChange("configuracionServidor", null, server);
    }

    // ===== FUNCIONES Y MÉTODOS =====

    public Server crearServidor(){
        ServerPlatformAdapter adapter = seleccionarAdaptadorCreacion();
        if (adapter == null) return null;

        List<ServerCreationOption> options = cargarOpcionesCreacion(adapter);
        if (options == null || options.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null,
                    "No hay versiones disponibles para " + adapter.getCreationDisplayName() + ".",
                    "Crear servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        ServerCreationOption selectedOption = seleccionarOpcionCreacion(adapter, options);
        if (selectedOption == null) return null;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona donde se creará la carpeta del servidor");
        if(chooser.showDialog(chooser,"Seleccionar") != JFileChooser.APPROVE_OPTION){
            return null;
        }

        int eula = JOptionPane.showConfirmDialog(null, "¿Aceptas el EULA de Mojang (https://aka.ms/MinecraftEULA)?", "EULA",  JOptionPane.YES_NO_OPTION);
        if(eula != JOptionPane.YES_OPTION){
            return null;
        }

        File carpetaSeleccionada = chooser.getSelectedFile();
        if(!carpetaSeleccionada.isDirectory()){
            carpetaSeleccionada = carpetaSeleccionada.getParentFile();
        }

        File targetFolder = resolverDirectorioServidorDisponible(
                carpetaSeleccionada.getAbsoluteFile(),
                selectedOption.directoryName()
        );
        return crearServidorAutomatizado(adapter, selectedOption, targetFolder.toPath());
    }

    public Server convertirServidorAPlataformaCompatible(Server server) {
        if (server == null) return null;
        if (server.getServerProcess() != null && server.getServerProcess().isAlive()) {
            JOptionPane.showMessageDialog(
                    null,
                    "Deten el servidor antes de iniciar una conversion de plataforma.",
                    "Convertir servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        ServerPlatformProfile sourceProfile = inspeccionarServidor(Path.of(server.getServerDir()));
        if (sourceProfile == null || sourceProfile.platform() != modelo.extensions.ServerPlatform.VANILLA) {
            JOptionPane.showMessageDialog(
                    null,
                    "En esta primera fase solo se admite la conversion de servidores Vanilla a Forge.",
                    "Convertir servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        ServerPlatformAdapter forgeAdapter = ServerPlatformAdapters.forPlatform(modelo.extensions.ServerPlatform.FORGE);
        List<ServerCreationOption> forgeOptions = cargarOpcionesConversionForge(server, forgeAdapter);
        if (forgeOptions.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha encontrado una version de Forge compatible con Minecraft " + server.getVersion() + ".",
                    "Convertir servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        ServerCreationOption selectedOption = seleccionarOpcionConversion(
                "Selecciona una version de Forge",
                forgeOptions
        );
        if (selectedOption == null) return null;

        int confirm = JOptionPane.showConfirmDialog(
                null,
                """
                Se va a convertir el servidor seleccionado de Vanilla a Forge.

                Riesgos:
                - Algunos mods pueden requerir pasos manuales despues.
                - La instalacion puede anadir o reemplazar archivos de arranque.
                - Aunque se intentara preservar configuracion y mundos, conviene revisar el resultado.

                Easy-MC-Server creara un backup completo antes de continuar.
                """,
                "Convertir servidor",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return null;
        }

        return convertirServidorExistente(
                server,
                forgeAdapter,
                selectedOption,
                (url, destination) -> MOJANG_API.descargar(url, destination, null)
        );
    }

    // esta función descarga de una url a un destino dándole un nombre y mostrando una barra de carga
    private boolean descargarConBarra(String url, File destino, String titulo){
        final JDialog dialog = new JDialog((Frame) null, titulo, true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel label = new JLabel("Descargando...");
        FlatProgressBar progreso = new FlatProgressBar();
        progreso.setMinimum(0);
        progreso.setMaximum(100);
        progreso.setIndeterminate(true);
        progreso.setStringPainted(true);
        progreso.setString("...");

        // texto arriba y barra de progreso abajo
        contenido.add(label, BorderLayout.NORTH);
        contenido.add(progreso, BorderLayout.CENTER);
        dialog.setContentPane(contenido);
        dialog.setSize(420, 120);
        dialog.setLocationRelativeTo(null);

        // SwingWorker permite hacer un trabajo pesado y actualizar la interfaz de forma segura
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                final long[] ultimoPorcentaje = { -1 };
                // ejecutamos la descarga
                MOJANG_API.descargar(url, destino, (leidos, total) -> {
                    if(isCancelled()) return;
                    if(total <= 0) return;
                    long porcentaje = (leidos * 100L) / total;
                    if(porcentaje == ultimoPorcentaje[0]) return; // si no se ha actualizado el porcentaje no actualizamos
                    ultimoPorcentaje[0] = porcentaje;
                    setProgress((int)max(0, min(100, porcentaje)));
                });
                return null;
            }

            @Override
            protected void done() {
                dialog.dispose();
            }
        };

        // worker escucha cambios en la propiedad "progress"
        worker.addPropertyChangeListener(evt -> {
            if(!"progress".equals(evt.getPropertyName())) return;
            Object valorPropiedad = evt.getNewValue();
            if(!(valorPropiedad instanceof Integer p)) return;
            if(progreso.isIndeterminate()) progreso.setIndeterminate(false);
            progreso.setValue(p);
            progreso.setString(p + "%");
        });

        worker.execute();
        dialog.setVisible(true);

        try{
            worker.get(); // bloquea el programa hasta que worker termina
            return true;
        } catch (Exception e){
            try{
                if(destino.exists()) destino.delete();
            } catch (RuntimeException ignored){}
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido descargar el servidor: " + e.getMessage(),
                    "Descarga",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
    }

    private ServerPlatformAdapter seleccionarAdaptadorCreacion() {
        List<ServerPlatformAdapter> creatableAdapters = ServerPlatformAdapters.creatable();
        if (creatableAdapters.isEmpty()) {
            return null;
        }
        JComboBox<ServerPlatformAdapter> platformBox = new JComboBox<>(creatableAdapters.toArray(ServerPlatformAdapter[]::new));
        platformBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getCreationDisplayName());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });

        int option = JOptionPane.showConfirmDialog(
                null,
                platformBox,
                "Selecciona una plataforma",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (option != JOptionPane.OK_OPTION) {
            return null;
        }
        return (ServerPlatformAdapter) platformBox.getSelectedItem();
    }

    private List<ServerCreationOption> cargarOpcionesCreacion(ServerPlatformAdapter adapter) {
        try {
            return ejecutarTareaConDialogo(
                    "Cargando versiones",
                    "Consultando versiones disponibles de " + adapter.getCreationDisplayName() + "...",
                    adapter::listCreationOptions
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se han podido cargar las versiones de " + adapter.getCreationDisplayName() + ": " + e.getMessage(),
                    "Crear servidor",
                    JOptionPane.ERROR_MESSAGE
            );
            return List.of();
        }
    }

    private List<ServerCreationOption> cargarOpcionesConversionForge(Server server, ServerPlatformAdapter adapter) {
        List<ServerCreationOption> options = cargarOpcionesCreacion(adapter);
        if (server == null || server.getVersion() == null || server.getVersion().isBlank()) {
            return options;
        }
        return options.stream()
                .filter(option -> Objects.equals(option.minecraftVersion(), server.getVersion()))
                .toList();
    }

    private ServerCreationOption seleccionarOpcionCreacion(ServerPlatformAdapter adapter, List<ServerCreationOption> options) {
        return seleccionarOpcionConversion(
                "Selecciona una versión de " + adapter.getCreationDisplayName(),
                options
        );
    }

    private ServerCreationOption seleccionarOpcionConversion(String title, List<ServerCreationOption> options) {
        JComboBox<ServerCreationOption> versionBox = new JComboBox<>(options.toArray(ServerCreationOption[]::new));
        versionBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.displayName());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });

        int option = JOptionPane.showConfirmDialog(
                null,
                versionBox,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (option != JOptionPane.OK_OPTION) {
            return null;
        }
        return (ServerCreationOption) versionBox.getSelectedItem();
    }

    Server crearServidorAutomatizado(ServerPlatformAdapter adapter, ServerCreationOption option, Path targetDirectory) {
        if (adapter == null || option == null || targetDirectory == null) {
            return null;
        }

        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido crear la carpeta del servidor: " + e.getMessage(),
                    "Instalacion",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }

        try {
            ejecutarTareaConDialogo(
                    "Instalando " + adapter.getCreationDisplayName(),
                    "Preparando servidor " + option.displayName() + "...",
                    () -> {
                        Server transientServer = new Server();
                        adapter.install(transientServer, new ServerInstallationRequest(
                                targetDirectory,
                                option.minecraftVersion(),
                                option.platformVersion(),
                                true,
                                resolverIconoPorDefecto(),
                                MOJANG_API,
                                (url, destination) -> MOJANG_API.descargar(url, destination, null)
                        ));
                        return transientServer;
                    }
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido instalar el servidor: " + e.getMessage(),
                    "Instalacion",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }

        Server server = new Server();
        server.setServerDir(targetDirectory.toAbsolutePath().toString());
        aplicarPerfilPlataforma(server, inspeccionarServidor(targetDirectory));
        if (server.getVersion() == null || server.getVersion().isBlank()) {
            server.setVersion(option.minecraftVersion());
        }
        if (server.getPlatform() == null || server.getPlatform() == modelo.extensions.ServerPlatform.UNKNOWN) {
            server.setPlatform(option.platform());
        }
        server.setDisplayName(construirNombreServidorImportado(server.getVersion(), server.getServerDir(), false));
        try {
            sincronizarExtensionesServidor(server);
        } catch (IOException ignored) {
        }
        guardarServidor(server);
        GestorMundos.sincronizarMundosServidor(server);
        return server;
    }

    Server convertirServidorExistente(Server server,
                                      ServerPlatformAdapter targetAdapter,
                                      ServerCreationOption option,
                                      controlador.platform.FileDownloader downloader) {
        if (server == null || targetAdapter == null || option == null) return null;
        if (server.getServerDir() == null || server.getServerDir().isBlank()) return null;
        if (server.getServerProcess() != null && server.getServerProcess().isAlive()) return null;

        Path serverDir = Path.of(server.getServerDir());
        if (!Files.isDirectory(serverDir)) return null;

        try {
            Path backupDir = crearBackupConversion(serverDir, option.platform());
            Properties preservedProperties = cargarPropertiesSilenciosamente(serverDir.resolve("server.properties"));

            ejecutarTareaConDialogo(
                    "Convirtiendo servidor",
                    "Instalando " + option.displayName() + " sobre el servidor actual...",
                    () -> {
                        targetAdapter.install(new Server(), new ServerInstallationRequest(
                                serverDir,
                                option.minecraftVersion(),
                                option.platformVersion(),
                                true,
                                resolverIconoPorDefecto(),
                                MOJANG_API,
                                downloader
                        ));
                        return null;
                    }
            );

            restaurarDatosTrasConversion(backupDir, serverDir, preservedProperties);
            GestorMundos.sincronizarMundosServidor(server);

            ServerPlatformProfile targetProfile = inspeccionarServidor(serverDir);
            if (targetProfile != null) {
                aplicarPerfilPlataforma(server, targetProfile);
            } else {
                server.setPlatform(option.platform());
                server.setVersion(option.minecraftVersion());
            }
            if (server.getLoaderVersion() == null || server.getLoaderVersion().isBlank()) {
                server.setLoaderVersion(option.platformVersion());
            }

            sincronizarExtensionesServidor(server);
            guardarServidor(server);
            return server;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido convertir el servidor: " + e.getMessage(),
                    "Convertir servidor",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private Path crearBackupConversion(Path serverDir, modelo.extensions.ServerPlatform targetPlatform) throws IOException {
        Path parent = serverDir.getParent();
        if (parent == null) {
            parent = serverDir.toAbsolutePath().getParent();
        }
        if (parent == null) {
            throw new IOException("No se ha podido resolver una carpeta para el backup.");
        }

        String platformName = targetPlatform == null ? "conversion" : targetPlatform.getLegacyTypeName().toLowerCase();
        String backupName = serverDir.getFileName() + "_backup_before_" + platformName + "_" + BACKUP_TIMESTAMP_FORMAT.format(LocalDateTime.now());
        Path backupDir = parent.resolve(backupName);
        Utilidades.copiarDirectorio(serverDir, backupDir);
        return backupDir;
    }

    private void restaurarDatosTrasConversion(Path backupDir, Path targetDir, Properties preservedProperties) throws IOException {
        if (backupDir == null || targetDir == null) return;

        for (String fileName : ARCHIVOS_CONFIG_PRESERVABLES) {
            Path backupFile = backupDir.resolve(fileName);
            Path targetFile = targetDir.resolve(fileName);
            if (Files.isRegularFile(backupFile) && !Files.exists(targetFile)) {
                Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (preservedProperties != null && !preservedProperties.isEmpty()) {
            Properties current = cargarPropertiesSilenciosamente(targetDir.resolve("server.properties"));
            current.putAll(preservedProperties);
            Utilidades.guardarPropertiesUtf8(targetDir.resolve("server.properties"), current, null);
        }

        try (Stream<Path> children = Files.list(backupDir)) {
            for (Path backupChild : children.toList()) {
                if (!Files.isDirectory(backupChild)) continue;
                String name = backupChild.getFileName().toString();
                if (!debePreservarseDirectorioEnConversion(backupChild, name)) continue;
                Path targetChild = targetDir.resolve(name);
                if (!Files.exists(targetChild)) {
                    Utilidades.copiarDirectorio(backupChild, targetChild);
                }
            }
        }
    }

    private boolean debePreservarseDirectorioEnConversion(Path backupChild, String name) {
        if (name == null || name.isBlank()) return false;
        if (GestorMundos.DIRECTORIO_MUNDOS.equals(name)) return true;
        if (Files.isRegularFile(backupChild.resolve("level.dat"))) return true;
        return name.endsWith("_nether") || name.endsWith("_the_end");
    }

    private Properties cargarPropertiesSilenciosamente(Path propertiesPath) {
        try {
            return Utilidades.cargarPropertiesUtf8(propertiesPath);
        } catch (IOException e) {
            return new Properties();
        }
    }

    private <T> T ejecutarTareaConDialogo(String titulo, String descripcion, Callable<T> task) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            try {
                return task.call();
            } catch (Exception e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException(cause.getMessage(), cause);
            }
        }

        final JDialog dialog = new JDialog((Frame) null, titulo, true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel label = new JLabel(descripcion == null ? "Procesando..." : descripcion);
        FlatProgressBar progreso = new FlatProgressBar();
        progreso.setIndeterminate(true);
        progreso.setStringPainted(true);
        progreso.setString("...");
        contenido.add(label, BorderLayout.NORTH);
        contenido.add(progreso, BorderLayout.CENTER);
        dialog.setContentPane(contenido);
        dialog.setSize(460, 120);
        dialog.setLocationRelativeTo(null);

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                dialog.dispose();
            }
        };

        worker.execute();
        dialog.setVisible(true);

        try {
            return worker.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(cause.getMessage(), cause);
        }
    }

    private Path resolverIconoPorDefecto() {
        Path local = Path.of("default_image.png");
        if (Files.isRegularFile(local)) {
            return local;
        }
        try (InputStream in = GestorServidores.class.getResourceAsStream("/default_image.png")) {
            if (in == null) {
                return null;
            }
            Path tempFile = Files.createTempFile("easymc-default-icon", ".png");
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            return null;
        }
    }

    // Esta función es la encargada de importar un servidor a partir de una carpeta
    public Server importarServidor(){
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona el directorio del servidor");

        // pedimos al usuario que indique la carpeta
        if(chooser.showDialog(chooser,"Seleccionar") == JFileChooser.APPROVE_OPTION){
            return importarServidorDesdeDirectorio(chooser.getSelectedFile().toPath());
        }
        return null;
    }

    Server importarServidorDesdeDirectorio(Path directorio) {
        if (directorio == null) return null;

        String directorioNormalizado = directorio.toAbsolutePath().toString();
        for (Server existente : listaServidores) {
            if (existente == null || existente.getServerDir() == null) continue;
            if (existente.getServerDir().equals(directorioNormalizado)) {
                System.out.println("El servidor ya está importado.");
                return existente;
            }
        }

        ServerPlatformProfile profile = inspeccionarServidor(directorio);
        if (profile == null) {
            System.out.println("No se ha podido detectar una plataforma valida para el servidor importado.");
            return null;
        }

        Server server = new Server();
        server.setServerDir(directorioNormalizado);
        aplicarPerfilPlataforma(server, profile);
        if (server.getVersion() == null || server.getVersion().isBlank()) {
            server.setVersion(DetectorVersionServidor.detectarVersionVanilla(server));
        }
        server.setDisplayName(construirNombreServidorImportado(server.getVersion(), server.getServerDir(), false));

        guardarServidor(server);
        GestorMundos.sincronizarMundosServidor(server);
        try {
            sincronizarExtensionesServidor(server);
            guardarServidor(server);
        } catch (IOException ignored) {
        }
        return server;
    }

    // Guarda la lista de servidores en el JSON
    public void guardarServidores(){
        normalizarMetadatosOrden(true);
        try{
            mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, copiarListaServidoresOrdenada());
        } catch (JacksonException e) {
            System.err.println("Error al guardar servidores: " + e.getMessage());
        }
    }

    public List<Server> getListaServidores() {
        normalizarMetadatosOrden(false);
        return copiarListaServidoresOrdenada();
    }

    public void setListaServidores(List<Server> listaServidores) {
        this.listaServidores = listaServidores == null ? new ArrayList<>() : new ArrayList<>(listaServidores);
        normalizarMetadatosOrden(true);
    }

    // Guarda un sólo servidor en la lista de servidores y luego lo guarda en el JSON
    public void guardarServidor(Server server){
        if (server == null) return;
        if (listaServidores == null) {
            listaServidores = new ArrayList<>();
        }

        asegurarMetadatosPersistentes(server);

        int indiceExistente = -1;
        for (int i = 0; i < listaServidores.size(); i++) {
            Server actual = listaServidores.get(i);
            if (actual == null) continue;
            if (Objects.equals(actual.getId(), server.getId())) {
                indiceExistente = i;
                copiarMetadatosOrdenSiFaltan(actual, server);
                break;
            }
        }

        if (indiceExistente >= 0) {
            listaServidores.set(indiceExistente, server);
        } else {
            if (server.getOrdenLista() == null) {
                server.setOrdenLista(obtenerSiguienteOrdenLista());
            }
            listaServidores.add(server);
        }

        if (Boolean.TRUE.equals(server.getFavorito()) && server.getOrdenFavorito() == null) {
            server.setOrdenFavorito(obtenerSiguienteOrdenFavorito());
        }

        guardarServidores();
        notificarCambio();
    }

    public void establecerFavorito(Server server, boolean favorito) {
        if (server == null) return;

        Server serverPersistido = getServerById(server.getId());
        if (serverPersistido == null) return;
        if (Boolean.valueOf(favorito).equals(serverPersistido.getFavorito())) return;

        serverPersistido.setFavorito(favorito);
        if (favorito && serverPersistido.getOrdenFavorito() == null) {
            serverPersistido.setOrdenFavorito(obtenerSiguienteOrdenFavorito());
        }

        guardarServidor(serverPersistido);
    }

    public void reordenarServidores(List<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) return;
        normalizarMetadatosOrden(false);

        Set<String> idsAplicados = new HashSet<>();
        int orden = 0;
        for (String id : serverIds) {
            Server server = getServerById(id);
            if (server == null) continue;
            if (!idsAplicados.add(id)) continue;
            server.setOrdenLista(orden++);
        }

        List<Server> resto = new ArrayList<>(listaServidores);
        resto.sort(Comparator.comparingInt(server -> valorOrdenVisual(server.getOrdenLista())));
        for (Server server : resto) {
            if (server == null) continue;
            if (!idsAplicados.add(server.getId())) continue;
            server.setOrdenLista(orden++);
        }

        guardarServidores();
        notificarCambio();
    }

    // Elimina un servidor de la lista de servidores y del JSON, no toca los archivos del servidor
    public boolean eliminarServidor(Server server){
        if(server == null) return false;
        if(server.getServerProcess() != null && server.getServerProcess().isAlive()){
            return false; // no podemos eliminarlo mientras está en ejecución para no dejar procesos abiertos
        }

        boolean removed = listaServidores.removeIf(s -> Objects.equals(s.getId(), server.getId()));
        if(!removed) return false;

        normalizarMetadatosOrden(true);

        if(servidorSeleccionado != null && Objects.equals(servidorSeleccionado.getId(), server.getId())){
            servidorSeleccionado = null;
        }
        // Una vez eliminado de la lista de servidores guardamos el JSON
        guardarServidores();
        notificarCambio();
        return true;
    }

    // Obtener una lista de todos los servidores activos
    public List<Server> getServidoresActivos() {
        if (listaServidores == null || listaServidores.isEmpty()) return List.of();
        return listaServidores.stream()
                .filter(s -> s != null && s.getServerProcess() != null && s.getServerProcess().isAlive())
                .toList();
    }

    // Detener todos los servidores activos para salir del programa
    public void detenerServidoresActivosParaSalir() {
        List<Server> activos = getServidoresActivos();
        if (activos.isEmpty()) return;

        for (Server server : activos) {
            try {
                safePararServidor(server); // hacemos una parada segura en cada uno de ellos
            } catch (RuntimeException ignored) {
            }
        }

        long cuentaAtras = System.currentTimeMillis() + 7_000;
        // contamos 7 segundos
        for (Server server : activos) {
            Process proceso = server.getServerProcess();
            if (proceso == null) continue;
            if (!proceso.isAlive()) continue;
            // si el proceso está vivo restamos los segundos transcurridos
            long restante = cuentaAtras - System.currentTimeMillis();
            if (restante <= 0) break;
            try {
                proceso.waitFor(restante, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // si no se han podido parar los servidores de forma segura forzamos su cierre
        for (Server server : activos) {
            Process proceso = server.getServerProcess();
            if (proceso == null) continue;
            if (!proceso.isAlive()) continue;
            try {
                forzarPararServidor(server);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // Eliminar la carpeta que contiene el servidor, debe comprobar que sea un servidor cargable
    public int eliminarServidorCompleto(Server server){
        return 0; // POR IMPLEMENTAR
    }

    // ===== MÉTODOS DE CONTROL DEL SERVIDOR =====

    public synchronized void iniciarServidor(Server server) throws IOException {
        // Compruebo si está en marcha, si no, prosigo
        if(server.getServerProcess()!=null && server.getServerProcess().isAlive()) {
            server.appendConsoleLinea("[INFO] El servidor ya está iniciado. ");
            return;
        }
        Path dir = Path.of(server.getServerDir());
        try {
            GestorMundos.sincronizarMundosServidor(server);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[WARN] No se ha podido preparar easy-mc-worlds: " + e.getMessage());
        }

        ServerPlatformAdapter adapter = resolverAdaptador(server);
        Path jar;
        try {
            jar = adapter.resolveExecutableJar(dir);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido encontrar el .jar del servidor: " + e.getMessage());
            return;
        } catch (IOException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido resolver el .jar del servidor: " + e.getMessage());
            return;
        }
        if (jar == null && adapter.requiresExecutableJarForStart()) {
            server.appendConsoleLinea("[ERROR] No se ha podido encontrar el .jar del servidor.");
            return;
        }

        // Creo un proceso con la dirección del servidor y la RAM elegida y en el primer puerto que esté libre desde 25565
        ServerConfig serverConfig = asegurarServerConfig(server);
        sincronizarPuertoConfigDesdeProperties(server);
        Integer puertoSeleccionado = resolverPuertoParaInicio(server, serverConfig);
        if (puertoSeleccionado == null) {
            server.appendConsoleLinea("[INFO] Inicio cancelado por seleccion de puerto.");
            return;
        }
        int puerto = puertoSeleccionado;
        if(puerto != serverConfig.getPuerto()){
            // si el puerto no está libre pasamos al siguiente
            serverConfig.setPuerto(puerto);
        }
        try{
            // escribimos el puerto nuevo en las propiedades del servidor
            Utilidades.escribirPuertoEnProperties(dir, puerto);
            guardarServidor(server);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido escribir el puerto en server.properties: " + e.getMessage());
        }

        ProcessBuilder pb;
        try {
            pb = adapter.buildStartProcess(server, jar);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido preparar el arranque del servidor: " + e.getMessage());
            return;
        }
        server.appendConsoleLinea("[INFO] Iniciando servidor con "+serverConfig.getRamInit()+"M y "+serverConfig.getRamMax()+"M de RAM.");

        try{
            // creo el proceso y se lo asigno al servidor
            Process proceso = pb.start();
            server.setServerProcess(proceso);
            server.setIniciando(true);
            notificarEstadoServidor(server);

            // comienzo a leer la consola
            startLogReader(server);
            notificarEstadoServidor(server);

            // detecto cuando el proceso finaliza
            proceso.onExit().thenRun(()->{
               if(server.getServerProcess() == proceso){
                   server.setServerProcess(null);
               }
               server.setIniciando(false);
               server.setLogReaderIniciado(false);
               server.appendConsoleLinea("[INFO] El servidor se ha detenido.");
               notificarEstadoServidor(server);
               // si el usuario ha pedido un reinicio y hemos parado el servidor entonces lo iniciamos de nuevo
               if(server.getRestartPending()){
                   server.setRestartPending(false);
                   try {
                       iniciarServidor(server);
                   } catch (IOException e) {
                       server.appendConsoleLinea("[ERROR] Error al reiniciar el servidor: " + e.getMessage());
                   }
               }
            });

        } catch (IOException e) {
            server.setIniciando(false);
            server.appendConsoleLinea("[ERROR] El servidor no se pudo iniciar"+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // Eliminar el proceso de servidor, a evitar, puede provocar corrupción de mundos
    public void forzarPararServidor(Server server){
        Process proceso = server.getServerProcess();

        if(proceso==null || !proceso.isAlive()) {
            server.appendConsoleLinea("[INFO] El servidor no está en ejecución");
            server.setServerProcess(null);
            server.setIniciando(false);
            notificarEstadoServidor(server);
            return;
        }
        server.appendConsoleLinea("[INFO] Forzando cierre del servidor...");

        // 1er Intento
        proceso.destroy();
        try{
            // esperamos a ver si lo hemos destruido
            boolean destruido = proceso.waitFor(3, TimeUnit.SECONDS);
            if(!destruido){ // 2do intento, ahora es personal
                proceso.destroyForcibly();
                destruido = proceso.waitFor(3, TimeUnit.SECONDS);

            }
            if(destruido){
                server.appendConsoleLinea("[INFO] Se ha cerrado el servidor de forma forzada, no se han guardado los cambios.");
            }
            else{
                server.appendConsoleLinea("[ERROR] No se ha podido cerrar el servidor.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally{
            // Si no está vivo limpiamos referencia
            if(!proceso.isAlive()) server.setServerProcess(null);
            if(!proceso.isAlive()) server.setIniciando(false);
            notificarEstadoServidor(server);
        }
    }

    public void safePararServidor(Server server){
        mandarComando(server, "stop");
    }

    public Server getServerById(String id){
        if (listaServidores == null || id == null) return null;
        return listaServidores.stream()
                .filter(Objects::nonNull)
                .filter(servidor -> Objects.equals(servidor.getId(), id))
                .findFirst()
                .orElse(null);
    }

    public void mandarComando(Server server, String comando){
        mandarComando(server, comando, true);
    }

    public void mandarComando(Server server, String comando, boolean mostrarEnConsola){
        if(server.getServerProcess()==null || !server.getServerProcess().isAlive()) {
            server.appendConsoleLinea("[INFO] No has iniciado el servidor.");
            return;
        }
        try{
            OutputStream os = server.getServerProcess().getOutputStream();
            PrintWriter pw = new PrintWriter(os, true);
            pw.println(comando);
            if(mostrarEnConsola){
                server.appendConsoleLinea("[INFO] Enviado comando: '"+comando+"'.");
            }
        } catch (Exception e) {
            server.appendConsoleLinea("[ERROR] Error mandando '"+comando+"' "+e.getMessage());
        }
    }

    // ===== LECTURA DE LOGS =====

    private void startLogReader(Server server){
        if (server.getLogReaderIniciado()) return;
        if (server.getServerProcess() == null) return;

        server.setLogReaderIniciado(true);

        Thread lector = new Thread(
            new ServerLogReader(
                    server,
                    server.getServerProcess().getInputStream(),
                    () -> SwingUtilities.invokeLater(() -> notificarEstadoServidor(server))
            ),
            "log-reader-"+server.getId()
        );
        lector.setDaemon(true);
        lector.start();
    }

    private ServerConfig asegurarServerConfig(Server server) {
        ServerConfig serverConfig = server == null ? null : server.getServerConfig();
        if(serverConfig == null){
            serverConfig = new ServerConfig();
            if (server != null) {
                server.setServerConfig(serverConfig);
            }
        }
        return serverConfig;
    }

    private Integer resolverPuertoParaInicio(Server server, ServerConfig serverConfig) {
        int puertoGuardado = puertoConfigurado(serverConfig);
        String bindHost = resolveServerBindHost(server);

        if (isPortAvailable(bindHost, puertoGuardado)) {
            probarSiguientePuerto(puertoGuardado);
            return puertoGuardado;
        }

        Integer siguientePuerto = buscarSiguientePuertoDisponible(bindHost, puertoGuardado + 1);
        if (siguientePuerto == null) {
            throw new RuntimeException("No hay puertos disponibles entre " + Math.max(1, puertoGuardado + 1) + " y 65535");
        }

        Integer puertoElegido = solicitarPuertoAlternativo(
                server,
                bindHost,
                puertoGuardado,
                isPortAvailable(bindHost, PUERTO_MINECRAFT_DEFECTO),
                siguientePuerto
        );
        if (puertoElegido != null) {
            probarSiguientePuerto(puertoElegido);
        }
        return puertoElegido;
    }

    private Integer resolverPuertoParaInicio(Server server) {
        return resolverPuertoParaInicio(server, asegurarServerConfig(server));
    }

    private int puertoConfigurado(ServerConfig serverConfig) {
        if (serverConfig == null || !isValidPortNumber(serverConfig.getPuerto())) {
            return PUERTO_MINECRAFT_DEFECTO;
        }
        return serverConfig.getPuerto();
    }

    private String resolveServerBindHost(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        try {
            Properties properties = Utilidades.cargarPropertiesUtf8(Path.of(server.getServerDir()).resolve("server.properties"));
            String serverIp = properties.getProperty("server-ip");
            return serverIp == null || serverIp.isBlank() ? null : serverIp.trim();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isValidPortNumber(int port) {
        return port > 0 && port <= PUERTO_MAXIMO;
    }

    private Integer buscarSiguientePuertoDisponible(String bindHost, int desde) {
        for(int p = Math.max(1, desde); p <= PUERTO_MAXIMO; p++){
            if(isPortAvailable(bindHost, p)){
                return p;
            }
        }
        return null;
    }

        // Política: siempre intentar 25565, luego 25566, 25567...
    // comprobamos si hay libre un puerto
    private static boolean isPortAvailable(int port){
        return isPortAvailable(null, port);
    }

    private static boolean isPortAvailable(String bindHost, int port){
        if(port <= 0 || port > PUERTO_MAXIMO) return false;
        try(ServerSocket socket = new ServerSocket()){
            socket.setReuseAddress(false);
            String host = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost.trim();
            socket.bind(new InetSocketAddress(host, port));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // Usar siguiente puerto
    private static void probarSiguientePuerto(int puertoUsado){
        int objetivo = puertoUsado + 1;
        while(true){
            int actual = NEXT_PORT_SESION.get();
            if(actual >= objetivo) return;
            if(NEXT_PORT_SESION.compareAndSet(actual, objetivo)) return;
        }
    }

    private Integer solicitarPuertoAlternativo(Server server,
                                               String bindHost,
                                               int puertoOcupado,
                                               boolean puertoDefectoDisponible,
                                               int puertoRecomendado) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        AtomicReference<Integer> resultado = new AtomicReference<>();
        Runnable dialogTask = () -> resultado.set(mostrarDialogoPuertoOcupado(
                server,
                bindHost,
                puertoOcupado,
                puertoDefectoDisponible,
                puertoRecomendado
        ));

        if (SwingUtilities.isEventDispatchThread()) {
            dialogTask.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(dialogTask);
            } catch (Exception e) {
                return null;
            }
        }
        return resultado.get();
    }

    private Integer mostrarDialogoPuertoOcupado(Server server,
                                                String bindHost,
                                                int puertoOcupado,
                                                boolean puertoDefectoDisponible,
                                                int puertoRecomendadoInicial) {
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JDialog dialog = new JDialog(owner, "Puerto del servidor ocupado", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));

        String nombreServidor = server == null || server.getDisplayName() == null || server.getDisplayName().isBlank()
                ? "este servidor"
                : server.getDisplayName();
        JLabel message = new JLabel("<html><body style='width:560px'>No se ha podido usar el puerto guardado "
                + puertoOcupado + " para " + escapeHtml(nombreServidor)
                + ". Puedes cancelar el inicio o guardar otro puerto antes de arrancar.</body></html>");
        root.add(message, BorderLayout.NORTH);

        ButtonGroup group = new ButtonGroup();
        JRadioButton defaultRadio = new JRadioButton(
                puertoDefectoDisponible ? "Puerto por defecto (recomendado)" : "Puerto por defecto",
                puertoDefectoDisponible
        );
        JRadioButton recommendedRadio = new JRadioButton(
                puertoDefectoDisponible ? "Siguiente puerto disponible" : "Siguiente puerto disponible (recomendado)",
                !puertoDefectoDisponible
        );
        JRadioButton customRadio = new JRadioButton("Puerto personalizado");
        defaultRadio.setEnabled(puertoDefectoDisponible);
        group.add(defaultRadio);
        group.add(recommendedRadio);
        group.add(customRadio);

        JLabel defaultValueLabel = new JLabel(puertoDefectoDisponible ? String.valueOf(PUERTO_MINECRAFT_DEFECTO) : "No disponible");
        defaultValueLabel.setFont(defaultValueLabel.getFont().deriveFont(Font.BOLD, 22f));
        JLabel recommendedValueLabel = new JLabel(String.valueOf(puertoRecomendadoInicial));
        recommendedValueLabel.setFont(recommendedValueLabel.getFont().deriveFont(Font.BOLD, 22f));
        JTextField customPortField = new JTextField(String.valueOf(puertoRecomendadoInicial), 8);
        customPortField.setEnabled(false);
        JLabel statusLabel = new JLabel(" ");

        JPanel defaultPanel = new JPanel(new BorderLayout(0, 8));
        defaultPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 14));
        defaultPanel.add(defaultRadio, BorderLayout.NORTH);
        defaultPanel.add(new JLabel("Puerto 25565:"), BorderLayout.CENTER);
        defaultPanel.add(defaultValueLabel, BorderLayout.SOUTH);

        JPanel recommendedPanel = new JPanel(new BorderLayout(0, 8));
        recommendedPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 14));
        recommendedPanel.add(recommendedRadio, BorderLayout.NORTH);
        recommendedPanel.add(new JLabel("Puerto sugerido:"), BorderLayout.CENTER);
        recommendedPanel.add(recommendedValueLabel, BorderLayout.SOUTH);

        JPanel customPanel = new JPanel(new BorderLayout(0, 8));
        customPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        customPanel.add(customRadio, BorderLayout.NORTH);
        customPanel.add(new JLabel("Elige un puerto entre 1 y " + PUERTO_MAXIMO + ":"), BorderLayout.CENTER);
        customPanel.add(customPortField, BorderLayout.SOUTH);

        JPanel optionsPanel = new JPanel(new GridLayout(1, 3, 0, 0));
        optionsPanel.add(defaultPanel);
        optionsPanel.add(recommendedPanel);
        optionsPanel.add(customPanel);
        Color dividerColor = UIManager.getColor("Component.borderColor");
        optionsPanel.setBorder(BorderFactory.createLineBorder(dividerColor == null ? Color.GRAY : dividerColor));
        root.add(optionsPanel, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancelar inicio");
        JButton saveButton = new JButton("Guardar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);

        AtomicReference<Integer> selectedPort = new AtomicReference<>();
        AtomicInteger recommendedPort = new AtomicInteger(puertoRecomendadoInicial);

        defaultRadio.addActionListener(e -> customPortField.setEnabled(false));
        recommendedRadio.addActionListener(e -> customPortField.setEnabled(false));
        customRadio.addActionListener(e -> {
            customPortField.setEnabled(true);
            customPortField.requestFocusInWindow();
            customPortField.selectAll();
        });

        cancelButton.addActionListener(e -> {
            selectedPort.set(null);
            dialog.dispose();
        });
        saveButton.addActionListener(e -> {
            if (defaultRadio.isSelected()) {
                if (isPortAvailable(bindHost, PUERTO_MINECRAFT_DEFECTO)) {
                    selectedPort.set(PUERTO_MINECRAFT_DEFECTO);
                    dialog.dispose();
                    return;
                }
                defaultRadio.setEnabled(false);
                recommendedRadio.setSelected(true);
                statusLabel.setText("El puerto por defecto ya no esta disponible.");
                return;
            }
            if (recommendedRadio.isSelected()) {
                int candidate = recommendedPort.get();
                if (isPortAvailable(bindHost, candidate)) {
                    selectedPort.set(candidate);
                    dialog.dispose();
                    return;
                }
                Integer refreshed = buscarSiguientePuertoDisponible(bindHost, candidate + 1);
                if (refreshed == null) {
                    statusLabel.setText("No hay puertos disponibles.");
                    return;
                }
                recommendedPort.set(refreshed);
                recommendedValueLabel.setText(String.valueOf(refreshed));
                statusLabel.setText("El puerto sugerido se ha ocupado. Nueva sugerencia: " + refreshed + ".");
                return;
            }

            Integer customPort = parsePort(customPortField.getText());
            if (customPort == null) {
                statusLabel.setText("Introduce un puerto valido entre 1 y " + PUERTO_MAXIMO + ".");
                customRadio.setSelected(true);
                customPortField.setEnabled(true);
                customPortField.requestFocusInWindow();
                customPortField.selectAll();
                return;
            }
            if (!isPortAvailable(bindHost, customPort)) {
                statusLabel.setText("El puerto " + customPort + " ya esta ocupado por otro proceso.");
                customRadio.setSelected(true);
                customPortField.setEnabled(true);
                customPortField.requestFocusInWindow();
                customPortField.selectAll();
                return;
            }
            selectedPort.set(customPort);
            dialog.dispose();
        });

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(640, dialog.getHeight()));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return selectedPort.get();
    }

    private Integer parsePort(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            int port = Integer.parseInt(text.trim());
            return port > 0 && port <= PUERTO_MAXIMO ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private File resolverDirectorioServidorDisponible(File directorioPadre, String nombreBase) {
        File padre = directorioPadre != null ? directorioPadre : new File(".");
        String base = (nombreBase == null || nombreBase.isBlank()) ? "server" : nombreBase;
        File candidato = new File(padre, base);
        int copia = 1;
        while(candidato.exists()){
            candidato = new File(padre, base + "_" + copia);
            copia++;
        }
        return candidato;
    }

    private String construirNombreServidorImportado(String version, String serverDir, boolean forzarSufijoCopia) {
        String versionNormalizada = (version == null || version.isBlank()) ? "sin versión" : version.trim();
        String base = "Servidor " + versionNormalizada;

        int duplicadosExistentes = 0;
        if (listaServidores != null) {
            for (Server existente : listaServidores) {
                if (existente == null) continue;
                if (serverDir != null && serverDir.equals(existente.getServerDir())) continue;
                if (!Objects.equals(versionNormalizada, normalizarVersionNombre(existente.getVersion()))) continue;
                duplicadosExistentes++;
            }
        }

        if (!forzarSufijoCopia && duplicadosExistentes == 0) {
            return base;
        }
        return base + " (" + duplicadosExistentes + ")";
    }

    private String normalizarVersionNombre(String version) {
        return (version == null || version.isBlank()) ? "sin versión" : version.trim();
    }
}

