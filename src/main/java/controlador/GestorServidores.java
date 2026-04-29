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
import vista.AppTheme;
import vista.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
        ServerCreationWizardResult wizardResult = mostrarAsistenteCreacionServidor();
        if (wizardResult == null) return null;
        return crearServidorAutomatizado(
                wizardResult.adapter(),
                wizardResult.option(),
                wizardResult.targetDirectory().toPath()
        );
    }

    /*
    private Server crearServidorLegacyDialogos(){
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

        File carpetaSeleccionada = chooser.getSelectedFile();
        if(!carpetaSeleccionada.isDirectory()){
            carpetaSeleccionada = carpetaSeleccionada.getParentFile();
        }

        File carpetaPadre = carpetaSeleccionada.getAbsoluteFile();
        File targetFolderSugerido = resolverDirectorioServidorDisponible(
                carpetaPadre,
                selectedOption.directoryName()
        );
        String nombreCarpeta = solicitarNombreCarpetaServidor(
                carpetaPadre,
                targetFolderSugerido.getName()
        );
        if (nombreCarpeta == null) {
            return null;
        }

        File targetFolder = new File(
                carpetaPadre,
                nombreCarpeta
        );

        int eula = JOptionPane.showConfirmDialog(null, "¿Aceptas el EULA de Mojang (https://aka.ms/MinecraftEULA)?", "EULA",  JOptionPane.YES_NO_OPTION);
        if(eula != JOptionPane.YES_OPTION){
            return null;
        }

        return crearServidorAutomatizado(adapter, selectedOption, targetFolder.toPath());
    }
    */

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

    private String solicitarNombreCarpetaServidor(File carpetaPadre, String nombreSugerido) {
        if (GraphicsEnvironment.isHeadless()) {
            String nombre = normalizarNombreCarpeta(nombreSugerido);
            return validarNombreCarpetaServidor(nombre) == null ? nombre : null;
        }

        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JDialog dialog = new JDialog(owner, "Nombre de la carpeta", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));

        JTextField nameField = new JTextField(normalizarNombreCarpeta(nombreSugerido), 24);
        String defaultFolderName = nameField.getText();
        JLabel parentLabel = new JLabel(formatearPrefijoCarpetaPadre(carpetaPadre));
        Font pathFont = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        parentLabel.setFont(pathFont);
        nameField.setFont(pathFont);

        JButton resetButton = new JButton();
        AppTheme.applyHeaderIconButtonStyle(resetButton);
        resetButton.setToolTipText("Restablecer nombre");
        resetButton.setPreferredSize(new Dimension(40, 40));
        resetButton.setMinimumSize(new Dimension(40, 40));
        resetButton.setMaximumSize(new Dimension(40, 40));
        SvgIconFactory.RotatingIcon resetIcon = SvgIconFactory.createRotating(
                "easymcicons/reset.svg",
                26,
                26,
                AppTheme::getForeground
        );
        resetButton.setIcon(resetIcon);

        JPanel pathPanel = new JPanel(new BorderLayout(0, 0));
        Color folderNameBorderColor = UIManager.getColor("Component.borderColor") == null
                ? Color.GRAY
                : UIManager.getColor("Component.borderColor");
        pathPanel.setBorder(AppTheme.createRoundedBorder(new Insets(6, 8, 6, 6), folderNameBorderColor, 1f));
        pathPanel.setBackground(UIManager.getColor("TextField.background"));
        pathPanel.setOpaque(true);
        parentLabel.setOpaque(false);
        nameField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        nameField.setOpaque(false);
        pathPanel.add(parentLabel, BorderLayout.WEST);
        pathPanel.add(nameField, BorderLayout.CENTER);
        pathPanel.add(resetButton, BorderLayout.EAST);

        JLabel statusLabel = new JLabel(" ");
        JPanel form = new JPanel(new BorderLayout(0, 6));
        form.add(pathPanel, BorderLayout.CENTER);
        form.add(statusLabel, BorderLayout.SOUTH);
        root.add(form, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancelar");
        JButton continueButton = new JButton("Continuar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(continueButton);
        root.add(buttons, BorderLayout.SOUTH);

        AtomicReference<String> resultado = new AtomicReference<>();
        Runnable validar = () -> {
            String nombre = nameField.getText();
            String error = validarNombreCarpetaServidor(nombre);
            if (error == null && existeCarpetaConNombreNoPortable(carpetaPadre, nombre)) {
                error = "Ya existe una carpeta con ese nombre.";
            }
            statusLabel.setText(error == null ? " " : error);
            statusLabel.setForeground(error == null ? UIManager.getColor("Label.foreground") : Color.RED);
            continueButton.setEnabled(error == null);
        };
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                validar.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                validar.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                validar.run();
            }
        });
        resetButton.addActionListener(e -> {
            nameField.setText(defaultFolderName);
            nameField.requestFocusInWindow();
            nameField.selectAll();
            animarIconoReset(resetIcon, resetButton);
        });
        cancelButton.addActionListener(e -> {
            resultado.set(null);
            dialog.dispose();
        });
        continueButton.addActionListener(e -> {
            String nombre = nameField.getText();
            if (validarNombreCarpetaServidor(nombre) != null || existeCarpetaConNombreNoPortable(carpetaPadre, nombre)) {
                validar.run();
                return;
            }
            resultado.set(normalizarNombreCarpeta(nombre));
            dialog.dispose();
        });

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(640, dialog.getHeight()));
        dialog.setLocationRelativeTo(null);
        SwingUtilities.invokeLater(() -> {
            nameField.requestFocusInWindow();
            nameField.selectAll();
        });
        validar.run();
        dialog.setVisible(true);
        return resultado.get();
    }

    private static String formatearPrefijoCarpetaPadre(File carpetaPadre) {
        if (carpetaPadre == null) {
            return "";
        }
        String path = carpetaPadre.getAbsolutePath();
        return path.endsWith(File.separator) ? path : path + File.separator;
    }

    private static void animarIconoReset(SvgIconFactory.RotatingIcon icon, AbstractButton button) {
        if (icon == null || button == null) {
            return;
        }
        Timer timer = new Timer(16, null);
        final int frames = 18;
        final int[] frame = {0};
        timer.addActionListener(e -> {
            frame[0]++;
            double progress = Math.min(1d, frame[0] / (double) frames);
            icon.setAngleRadians(-progress * Math.PI * 2d);
            button.repaint();
            if (frame[0] >= frames) {
                icon.setAngleRadians(0d);
                button.repaint();
                ((Timer) e.getSource()).stop();
            }
        });
        timer.start();
    }

    static String validarNombreCarpetaServidor(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "El nombre de la carpeta no puede estar vacio.";
        }
        if (!Objects.equals(nombre, nombre.trim())) {
            return "El nombre no puede empezar ni terminar con espacios.";
        }
        if (nombre.equals(".") || nombre.equals("..")) {
            return "Ese nombre esta reservado.";
        }
        if (nombre.endsWith(".")) {
            return "El nombre no puede terminar en punto.";
        }
        if (nombre.getBytes(StandardCharsets.UTF_8).length > 255) {
            return "El nombre es demasiado largo.";
        }
        for (int i = 0; i < nombre.length(); i++) {
            char c = nombre.charAt(i);
            if (c < 32 || c == 127) {
                return "El nombre no puede contener caracteres de control.";
            }
            if ("<>:\"/\\|?*".indexOf(c) >= 0) {
                return "El nombre contiene caracteres no validos para una carpeta.";
            }
        }
        String baseName = nombre;
        int dotIndex = baseName.indexOf('.');
        if (dotIndex >= 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        String upperBaseName = baseName.toUpperCase();
        Set<String> reservedWindowsNames = Set.of(
                "CON", "PRN", "AUX", "NUL",
                "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        );
        if (reservedWindowsNames.contains(upperBaseName)) {
            return "Ese nombre esta reservado por Windows.";
        }
        return null;
    }

    private static String normalizarNombreCarpeta(String nombre) {
        return nombre == null ? "" : nombre.trim();
    }

    private static boolean existeCarpetaConNombreNoPortable(File carpetaPadre, String nombre) {
        if (carpetaPadre == null || nombre == null || nombre.isBlank()) {
            return false;
        }
        if (new File(carpetaPadre, nombre).exists()) {
            return true;
        }
        String[] siblings = carpetaPadre.list();
        if (siblings == null) {
            return false;
        }
        for (String sibling : siblings) {
            if (nombre.equalsIgnoreCase(sibling)) {
                return true;
            }
        }
        return false;
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

    private ServerCreationWizardResult mostrarAsistenteCreacionServidor() {
        List<ServerPlatformAdapter> adapters = ServerPlatformAdapters.creatable();
        if (adapters.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null,
                    "No hay plataformas disponibles para crear servidores.",
                    "Crear servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        ServerCreationWizardState state = new ServerCreationWizardState();
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JDialog dialog = new JDialog(owner, "Crear servidor", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        CardLayout cardLayout = new CardLayout();
        JPanel cards = new JPanel(cardLayout);
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);

        JComboBox<ServerPlatformAdapter> platformBox = new JComboBox<>(adapters.toArray(ServerPlatformAdapter[]::new));
        platformBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> createWizardComboLabel(
                list,
                value == null ? "" : value.getCreationDisplayName(),
                isSelected
        ));
        cards.add(createWizardStepPanel("Plataforma", platformBox), "platform");

        DefaultListModel<ServerCreationOption> versionListModel = new DefaultListModel<>();
        JList<ServerCreationOption> versionList = new JList<>(versionListModel);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setVisibleRowCount(12);
        versionList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> createWizardComboLabel(
                list,
                value == null ? "" : value.displayName(),
                isSelected
        ));
        JScrollPane versionScroll = new JScrollPane(versionList);
        versionScroll.setPreferredSize(new Dimension(620, 320));
        JCheckBox includeSnapshotsCheck = new JCheckBox("Snapshots", false);
        JCheckBox includeReleasesCheck = new JCheckBox("Releases", true);
        JCheckBox eulaCheck = new JCheckBox();
        JPanel versionFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        versionFilters.add(new JLabel("Incluir:"));
        versionFilters.add(includeSnapshotsCheck);
        versionFilters.add(includeReleasesCheck);
        JPanel eulaPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JLabel eulaLabel = new JLabel("Acepto el EULA de Mojang (https://aka.ms/MinecraftEULA)");
        Font eulaFont = eulaLabel.getFont();
        eulaLabel.setFont(eulaFont.deriveFont(Font.BOLD));
        eulaPanel.add(eulaLabel);
        eulaPanel.add(eulaCheck);
        JPanel versionControls = new JPanel(new BorderLayout(0, 0));
        versionControls.add(versionFilters, BorderLayout.WEST);
        versionControls.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.CENTER);
        versionControls.add(eulaPanel, BorderLayout.EAST);
        JPanel versionStep = new JPanel(new BorderLayout(0, 8));
        versionStep.add(versionScroll, BorderLayout.CENTER);
        versionStep.add(versionControls, BorderLayout.SOUTH);
        cards.add(createWizardStepPanel("Version", versionStep), "version");

        JTextField parentField = new JTextField(32);
        JFileChooser parentChooser = new JFileChooser();
        parentChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        parentChooser.setAcceptAllFileFilterUsed(false);
        parentChooser.setControlButtonsAreShown(false);
        parentChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f != null && f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Carpetas";
            }
        });
        parentChooser.setPreferredSize(new Dimension(620, 320));
        File initialParentDirectory = parentChooser.getCurrentDirectory();
        if (initialParentDirectory != null) {
            parentField.setText(initialParentDirectory.getAbsolutePath());
        }
        JLabel parentHint = new JLabel("Se creará una nueva carpeta dentro de la carpeta seleccionada.");
        JPanel parentStep = new JPanel(new BorderLayout(0, 8));
        parentStep.add(parentHint, BorderLayout.NORTH);
        parentStep.add(parentChooser, BorderLayout.CENTER);
        cards.add(createWizardStepPanel("Carpeta de destino", parentStep), "parent");

        JTextField folderNameField = new JTextField(24);
        JLabel parentPrefixLabel = new JLabel();
        JButton resetFolderButton = new JButton();
        AppTheme.applyHeaderIconButtonStyle(resetFolderButton);
        resetFolderButton.setToolTipText("Restablecer nombre");
        resetFolderButton.setPreferredSize(new Dimension(40, 40));
        SvgIconFactory.RotatingIcon resetIcon = SvgIconFactory.createRotating(
                "easymcicons/reset.svg",
                26,
                26,
                AppTheme::getForeground
        );
        resetFolderButton.setIcon(resetIcon);
        Font pathFont = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        parentPrefixLabel.setFont(pathFont);
        folderNameField.setFont(pathFont);
        JPanel folderPathPanel = new JPanel(new BorderLayout(0, 0));
        Color folderNameBorderColor = UIManager.getColor("Component.borderColor") == null
                ? Color.GRAY
                : UIManager.getColor("Component.borderColor");
        folderPathPanel.setBorder(AppTheme.createRoundedBorder(new Insets(6, 8, 6, 6), folderNameBorderColor, 1f));
        folderPathPanel.setBackground(UIManager.getColor("TextField.background"));
        folderPathPanel.setOpaque(true);
        folderNameField.setOpaque(false);
        folderNameField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        folderPathPanel.add(parentPrefixLabel, BorderLayout.WEST);
        folderPathPanel.add(folderNameField, BorderLayout.CENTER);
        folderPathPanel.add(resetFolderButton, BorderLayout.EAST);
        folderPathPanel.setPreferredSize(new Dimension(620, 42));
        JPanel folderNameWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        folderNameWrap.add(folderPathPanel);
        cards.add(createWizardStepPanel("Nombre de carpeta", folderNameWrap), "folderName");

        JButton backButton = new JButton();
        JButton nextButton = new JButton();
        AppTheme.applyHeaderIconButtonStyle(backButton);
        AppTheme.applyHeaderIconButtonStyle(nextButton);
        backButton.setIcon(SvgIconFactory.create("easymcicons/arrow-left.svg", 28, 28, AppTheme::getForeground));
        nextButton.setIcon(SvgIconFactory.create("easymcicons/arrow-right.svg", 28, 28, AppTheme::getForeground));
        backButton.setToolTipText("Anterior");
        nextButton.setToolTipText("Siguiente");
        Dimension arrowButtonSize = new Dimension(44, 44);
        backButton.setPreferredSize(arrowButtonSize);
        nextButton.setPreferredSize(arrowButtonSize);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        nav.add(backButton);
        nav.add(nextButton);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(nav, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        root.add(cards, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        String[] stepNames = {"platform", "version", "parent", "folderName"};
        int[] stepIndex = {0};
        AtomicReference<ServerCreationWizardResult> result = new AtomicReference<>();
        AtomicInteger versionDownloadCheckGeneration = new AtomicInteger();
        Runnable[] refreshNextState = new Runnable[1];
        Runnable[] checkSelectedVersionDownload = new Runnable[1];
        java.util.function.Consumer<AbstractButton>[] refreshVersionFilters = new java.util.function.Consumer[1];
        Runnable refreshEulaState = () -> {
            boolean accepted = eulaCheck.isSelected();
            eulaLabel.setFont(eulaFont.deriveFont(accepted ? Font.PLAIN : Font.BOLD));
            if (refreshNextState[0] != null) {
                refreshNextState[0].run();
            }
        };
        refreshNextState[0] = () -> nextButton.setEnabled(puedeAvanzarPasoCreacionServidor(
                stepIndex[0],
                state,
                platformBox,
                versionList,
                parentField,
                folderNameField,
                eulaCheck
        ));
        Runnable refreshNav = () -> {
            backButton.setEnabled(stepIndex[0] > 0);
            nextButton.setToolTipText(stepIndex[0] == stepNames.length - 1 ? "Crear servidor" : "Siguiente");
            statusLabel.setText(" ");
            cardLayout.show(cards, stepNames[stepIndex[0]]);
            refreshEulaState.run();
            if (stepIndex[0] == 3) {
                actualizarVistaNombreCarpeta(state, parentPrefixLabel, folderNameField);
            }
            refreshNextState[0].run();
        };
        checkSelectedVersionDownload[0] = () -> {
            ServerCreationOption selected = state.option;
            if (state.adapter == null
                    || state.adapter.getPlatform() != modelo.extensions.ServerPlatform.VANILLA
                    || selected == null
                    || selected.minecraftVersion() == null
                    || state.serverJarAvailableVersions.contains(selected.minecraftVersion())
                    || state.serverJarUnavailableVersions.contains(selected.minecraftVersion())) {
                state.pendingServerJarVersion = null;
                refreshNextState[0].run();
                return;
            }

            String versionId = selected.minecraftVersion();
            if (Objects.equals(state.pendingServerJarVersion, versionId)) {
                refreshNextState[0].run();
                return;
            }
            state.pendingServerJarVersion = versionId;
            int generation = versionDownloadCheckGeneration.incrementAndGet();
            refreshNextState[0].run();
            MojangAPI.runBackgroundRequest(() -> {
                boolean available;
                try {
                    String url = MOJANG_API.obtenerUrlServerJar(versionId);
                    available = url != null && !url.isBlank();
                } catch (RuntimeException ex) {
                    available = false;
                }
                boolean finalAvailable = available;
                SwingUtilities.invokeLater(() -> {
                    if (generation != versionDownloadCheckGeneration.get()) {
                        return;
                    }
                    if (finalAvailable) {
                        state.serverJarAvailableVersions.add(versionId);
                    } else {
                        state.serverJarUnavailableVersions.add(versionId);
                    }
                    if (Objects.equals(state.pendingServerJarVersion, versionId)) {
                        state.pendingServerJarVersion = null;
                    }
                    refreshNextState[0].run();
                });
            });
        };

        platformBox.addActionListener(e -> {
            ServerPlatformAdapter selected = (ServerPlatformAdapter) platformBox.getSelectedItem();
            if (selected != state.adapter) {
                state.adapter = selected;
                state.options = null;
                state.option = null;
                versionListModel.clear();
                boolean snapshotsDisponibles = soportaSnapshotsCreacion(selected);
                includeSnapshotsCheck.setEnabled(snapshotsDisponibles);
                if (!snapshotsDisponibles) {
                    includeSnapshotsCheck.setSelected(false);
                }
                state.includeSnapshots = snapshotsDisponibles && includeSnapshotsCheck.isSelected();
                state.includeReleases = includeReleasesCheck.isSelected();
                actualizarSugerenciaNombreCarpeta(state, folderNameField, true);
                refreshNextState[0].run();
            }
        });
        versionList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || state.updatingVersionList) {
                return;
            }
            ServerCreationOption selected = versionList.getSelectedValue();
            if (!Objects.equals(selected, state.option)) {
                state.option = selected;
                SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
            }
            refreshNextState[0].run();
        });
        refreshVersionFilters[0] = changedCheck -> {
            if (state.updatingVersionFilters) {
                return;
            }
            state.updatingVersionFilters = true;
            try {
                if (!includeSnapshotsCheck.isSelected() && !includeReleasesCheck.isSelected()) {
                    changedCheck.setSelected(true);
                }
                state.includeSnapshots = includeSnapshotsCheck.isEnabled() && includeSnapshotsCheck.isSelected();
                state.includeReleases = includeReleasesCheck.isSelected();
                actualizarListadoVersiones(state, versionListModel, versionList);
            } finally {
                state.updatingVersionFilters = false;
            }
            refreshNextState[0].run();
            SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
        };
        includeSnapshotsCheck.addActionListener(e -> refreshVersionFilters[0].accept(includeSnapshotsCheck));
        includeReleasesCheck.addActionListener(e -> refreshVersionFilters[0].accept(includeReleasesCheck));
        parentField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                actualizarPadre();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                actualizarPadre();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                actualizarPadre();
            }

            private void actualizarPadre() {
                state.parentDirectory = parentField.getText() == null || parentField.getText().isBlank()
                        ? null
                        : new File(parentField.getText().trim());
                actualizarSugerenciaNombreCarpeta(state, folderNameField, false);
                refreshNextState[0].run();
            }
        });
        folderNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                state.folderName = folderNameField.getText();
                state.folderNameEdited = !Objects.equals(normalizarNombreCarpeta(state.folderName), state.suggestedFolderName);
                refreshNextState[0].run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                state.folderName = folderNameField.getText();
                state.folderNameEdited = !Objects.equals(normalizarNombreCarpeta(state.folderName), state.suggestedFolderName);
                refreshNextState[0].run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                state.folderName = folderNameField.getText();
                state.folderNameEdited = !Objects.equals(normalizarNombreCarpeta(state.folderName), state.suggestedFolderName);
                refreshNextState[0].run();
            }
        });
        parentChooser.addPropertyChangeListener(evt -> {
            File selected = null;
            if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())
                    && evt.getNewValue() instanceof File selectedFile) {
                selected = selectedFile;
            } else if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(evt.getPropertyName())
                    && evt.getNewValue() instanceof File currentDirectory) {
                selected = currentDirectory;
            }
            if (selected == null) return;
            if (!selected.isDirectory()) {
                selected = selected.getParentFile();
            }
            if (selected != null && !Objects.equals(parentField.getText(), selected.getAbsolutePath())) {
                parentField.setText(selected.getAbsolutePath());
            }
        });
        resetFolderButton.addActionListener(e -> {
            folderNameField.setText(state.suggestedFolderName == null ? "" : state.suggestedFolderName);
            folderNameField.requestFocusInWindow();
            folderNameField.selectAll();
            animarIconoReset(resetIcon, resetFolderButton);
        });
        eulaCheck.addActionListener(e -> {
            state.eulaAccepted = eulaCheck.isSelected();
            refreshEulaState.run();
        });

        backButton.addActionListener(e -> {
            if (stepIndex[0] <= 0) return;
            stepIndex[0]--;
            refreshNav.run();
        });
        nextButton.addActionListener(e -> {
            String error = validarPasoCreacionServidor(
                    stepIndex[0],
                    state,
                    platformBox,
                    versionList,
                    parentField,
                    folderNameField,
                    eulaCheck
            );
            if (error != null) {
                statusLabel.setText(" ");
                refreshNextState[0].run();
                return;
            }
            if (stepIndex[0] == 1 && !esOpcionCreacionDescargable(state)) {
                SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
                statusLabel.setText(" ");
                refreshNextState[0].run();
                return;
            }
            if (stepIndex[0] == 0 && !cargarOpcionesCreacionWizard(dialog, state, versionListModel, versionList)) {
                statusLabel.setText(" ");
                refreshNextState[0].run();
                return;
            }
            if (stepIndex[0] == 0) {
                SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
            }
            if (stepIndex[0] == stepNames.length - 1) {
                result.set(new ServerCreationWizardResult(
                        state.adapter,
                        state.option,
                        new File(state.parentDirectory, normalizarNombreCarpeta(state.folderName))
                ));
                dialog.dispose();
                return;
            }
            stepIndex[0]++;
            refreshNav.run();
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (confirmarCancelacionAsistenteCreacion(dialog)) {
                    result.set(null);
                    dialog.dispose();
                }
            }
        });

        state.adapter = adapters.getFirst();
        includeSnapshotsCheck.setEnabled(soportaSnapshotsCreacion(state.adapter));
        state.includeSnapshots = includeSnapshotsCheck.isSelected();
        state.includeReleases = includeReleasesCheck.isSelected();
        state.parentDirectory = initialParentDirectory == null ? null : initialParentDirectory.getAbsoluteFile();
        platformBox.setSelectedItem(state.adapter);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(760, Math.max(520, dialog.getHeight())));
        dialog.setLocationRelativeTo(null);
        refreshNav.run();
        dialog.setVisible(true);
        return result.get();
    }

    private JLabel createWizardComboLabel(JList<?> list, String text, boolean isSelected) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        if (isSelected) {
            label.setBackground(list.getSelectionBackground());
            label.setForeground(list.getSelectionForeground());
        } else {
            label.setBackground(list.getBackground());
            label.setForeground(list.getForeground());
        }
        return label;
    }

    private JPanel createWizardStepPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private boolean cargarOpcionesCreacionWizard(Component parent,
                                                 ServerCreationWizardState state,
                                                 DefaultListModel<ServerCreationOption> versionListModel,
                                                 JList<ServerCreationOption> versionList) {
        if (state == null || state.adapter == null) {
            return false;
        }
        if (state.options != null && !state.options.isEmpty()) {
            actualizarListadoVersiones(state, versionListModel, versionList);
            return true;
        }
        List<ServerCreationOption> options = cargarOpcionesCreacion(state.adapter);
        if (options == null || options.isEmpty()) {
            return false;
        }
        state.options = options;
        actualizarListadoVersiones(state, versionListModel, versionList);
        return !versionListModel.isEmpty();
    }

    private void actualizarListadoVersiones(ServerCreationWizardState state,
                                            DefaultListModel<ServerCreationOption> versionListModel,
                                            JList<ServerCreationOption> versionList) {
        if (state == null || versionListModel == null || versionList == null) {
            return;
        }
        ServerCreationOption previousSelection = state.option;
        state.updatingVersionList = true;
        try {
            versionList.setValueIsAdjusting(true);
            if (state.options == null || state.options.isEmpty()) {
                versionListModel.clear();
                state.option = null;
                return;
            }
            List<ServerCreationOption> filteredOptions = state.options.stream()
                    .filter(option -> debeMostrarOpcionCreacion(option, state.includeSnapshots, state.includeReleases))
                    .toList();
            versionListModel.clear();
            versionListModel.addAll(filteredOptions);
            if (filteredOptions.isEmpty()) {
                state.option = null;
                return;
            }
            int selectedIndex = -1;
            if (previousSelection != null) {
                for (int i = 0; i < filteredOptions.size(); i++) {
                    if (Objects.equals(filteredOptions.get(i), previousSelection)) {
                        selectedIndex = i;
                        break;
                    }
                }
            }
            if (selectedIndex < 0) {
                selectedIndex = 0;
            }
            versionList.setSelectedIndex(selectedIndex);
            versionList.ensureIndexIsVisible(selectedIndex);
            state.option = filteredOptions.get(selectedIndex);
        } finally {
            versionList.setValueIsAdjusting(false);
            state.updatingVersionList = false;
        }
    }

    private boolean debeMostrarOpcionCreacion(ServerCreationOption option, boolean includeSnapshots, boolean includeReleases) {
        if (option == null) {
            return false;
        }
        boolean snapshot = esSnapshot(option);
        return snapshot ? includeSnapshots : includeReleases;
    }

    private void asegurarAlMenosUnFiltroVersionActivo(JCheckBox snapshotsCheck,
                                                      JCheckBox releasesCheck,
                                                      JCheckBox changedCheck) {
        if (snapshotsCheck == null || releasesCheck == null) {
            return;
        }
        if (snapshotsCheck.isSelected() || releasesCheck.isSelected()) {
            return;
        }
        if (changedCheck != null && changedCheck.isEnabled()) {
            changedCheck.setSelected(true);
            return;
        }
        releasesCheck.setSelected(true);
    }

    private boolean soportaSnapshotsCreacion(ServerPlatformAdapter adapter) {
        return adapter != null && adapter.getPlatform() == modelo.extensions.ServerPlatform.VANILLA;
    }

    private String validarOpcionCreacionDescargable(ServerCreationWizardState state,
                                                    DefaultListModel<ServerCreationOption> versionListModel,
                                                    JList<ServerCreationOption> versionList) {
        if (state == null || state.option == null || state.adapter == null) {
            return "Selecciona una version.";
        }
        if (state.adapter.getPlatform() != modelo.extensions.ServerPlatform.VANILLA) {
            return null;
        }
        String versionId = state.option.minecraftVersion();
        if (state.serverJarAvailableVersions.contains(versionId)) {
            return null;
        }
        String url;
        try {
            url = MOJANG_API.obtenerUrlServerJar(versionId);
        } catch (RuntimeException e) {
            url = null;
        }
        if (url != null && !url.isBlank()) {
            state.serverJarAvailableVersions.add(versionId);
            return null;
        }
        if (state.options != null) {
            state.options = state.options.stream()
                    .filter(option -> !Objects.equals(option, state.option))
                    .toList();
        }
        state.option = null;
        actualizarListadoVersiones(state, versionListModel, versionList);
        return "Esa version no tiene descarga de servidor disponible. Se ha retirado de la lista.";
    }

    private boolean puedeAvanzarPasoCreacionServidor(int stepIndex,
                                                     ServerCreationWizardState state,
                                                     JComboBox<ServerPlatformAdapter> platformBox,
                                                     JList<ServerCreationOption> versionList,
                                                     JTextField parentField,
                                                     JTextField folderNameField,
                                                     JCheckBox eulaCheck) {
        if (state == null) {
            return false;
        }
        return switch (stepIndex) {
            case 0 -> platformBox.getSelectedItem() instanceof ServerPlatformAdapter;
            case 1 -> {
                ServerCreationOption selected = versionList.getSelectedValue();
                boolean hasEnabledFilter = state.includeSnapshots || state.includeReleases;
                boolean downloadable = selected != null
                        && Objects.equals(selected, state.option)
                        && esOpcionCreacionDescargable(state);
                yield hasEnabledFilter
                        && versionList.getModel().getSize() > 0
                        && selected != null
                        && eulaCheck.isSelected()
                        && downloadable;
            }
            case 2 -> {
                String parentText = parentField.getText();
                yield parentText != null && !parentText.isBlank() && new File(parentText.trim()).isDirectory();
            }
            case 3 -> {
                String folderName = folderNameField.getText();
                yield validarNombreCarpetaServidor(folderName) == null
                        && state.parentDirectory != null
                        && !existeCarpetaConNombreNoPortable(state.parentDirectory, folderName);
            }
            default -> false;
        };
    }

    private boolean esOpcionCreacionDescargable(ServerCreationWizardState state) {
        if (state == null || state.option == null || state.adapter == null) {
            return false;
        }
        if (state.adapter.getPlatform() != modelo.extensions.ServerPlatform.VANILLA) {
            return true;
        }
        String versionId = state.option.minecraftVersion();
        if (versionId == null || versionId.isBlank()) {
            return false;
        }
        if (Objects.equals(state.pendingServerJarVersion, versionId)) {
            return false;
        }
        return state.serverJarAvailableVersions.contains(versionId);
    }

    private boolean esSnapshot(ServerCreationOption option) {
        if (option == null) {
            return false;
        }
        if (option.versionType() != null && !option.versionType().isBlank()) {
            return option.isSnapshot();
        }
        if (option.minecraftVersion() == null) {
            return false;
        }
        String version = option.minecraftVersion().toLowerCase();
        return version.contains("snapshot")
                || version.contains("pre")
                || version.contains("rc")
                || version.matches("\\d{2}w\\d{2}[a-z]");
    }

    private String validarPasoCreacionServidor(int stepIndex,
                                               ServerCreationWizardState state,
                                               JComboBox<ServerPlatformAdapter> platformBox,
                                               JList<ServerCreationOption> versionList,
                                               JTextField parentField,
                                               JTextField folderNameField,
                                               JCheckBox eulaCheck) {
        if (state == null) {
            return "No se ha podido preparar la creacion.";
        }
        return switch (stepIndex) {
            case 0 -> {
                state.adapter = (ServerPlatformAdapter) platformBox.getSelectedItem();
                yield state.adapter == null ? "Selecciona una plataforma." : null;
            }
            case 1 -> {
                if (!state.includeSnapshots && !state.includeReleases) {
                    yield "Selecciona al menos un tipo de version.";
                }
                if (versionList.getModel().getSize() <= 0) {
                    yield "No hay versiones disponibles con los filtros seleccionados.";
                }
                state.option = versionList.getSelectedValue();
                if (state.option == null) {
                    yield "Selecciona una version.";
                }
                state.eulaAccepted = eulaCheck.isSelected();
                yield state.eulaAccepted ? null : "Debes aceptar el EULA para crear el servidor.";
            }
            case 2 -> {
                String parentText = parentField.getText();
                if (parentText == null || parentText.isBlank()) {
                    yield "Selecciona una carpeta de destino.";
                }
                File parent = new File(parentText.trim());
                if (!parent.isDirectory()) {
                    yield "La carpeta de destino no existe.";
                }
                state.parentDirectory = parent.getAbsoluteFile();
                yield null;
            }
            case 3 -> {
                String folderName = folderNameField.getText();
                String error = validarNombreCarpetaServidor(folderName);
                if (error != null) {
                    yield error;
                }
                if (existeCarpetaConNombreNoPortable(state.parentDirectory, folderName)) {
                    yield "Ya existe una carpeta con ese nombre.";
                }
                state.folderName = normalizarNombreCarpeta(folderName);
                yield null;
            }
            default -> null;
        };
    }

    private void actualizarVistaNombreCarpeta(ServerCreationWizardState state,
                                              JLabel parentPrefixLabel,
                                              JTextField folderNameField) {
        if (state == null) {
            return;
        }
        parentPrefixLabel.setText(formatearPrefijoCarpetaPadre(state.parentDirectory));
        actualizarSugerenciaNombreCarpeta(state, folderNameField, false);
    }

    private void actualizarSugerenciaNombreCarpeta(ServerCreationWizardState state,
                                                   JTextField folderNameField,
                                                   boolean forceReplace) {
        if (state == null || state.option == null || state.parentDirectory == null) {
            return;
        }
        File suggestedFolder = resolverDirectorioServidorDisponible(
                state.parentDirectory.getAbsoluteFile(),
                state.option.directoryName()
        );
        String previousSuggestion = state.suggestedFolderName;
        state.suggestedFolderName = suggestedFolder.getName();
        boolean shouldReplace = forceReplace
                || !state.folderNameEdited
                || state.folderName == null
                || state.folderName.isBlank()
                || Objects.equals(normalizarNombreCarpeta(state.folderName), previousSuggestion);
        if (!shouldReplace) {
            return;
        }
        state.folderName = state.suggestedFolderName;
        state.folderNameEdited = false;
        if (folderNameField != null && !Objects.equals(folderNameField.getText(), state.folderName)) {
            folderNameField.setText(state.folderName);
        }
    }

    private boolean confirmarCancelacionAsistenteCreacion(Component parent) {
        int option = JOptionPane.showConfirmDialog(
                parent,
                "¿Quieres cancelar la creación del servidor?",
                "Cancelar creación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return option == JOptionPane.YES_OPTION;
    }

    private static final class ServerCreationWizardState {
        private ServerPlatformAdapter adapter;
        private List<ServerCreationOption> options;
        private ServerCreationOption option;
        private File parentDirectory;
        private String suggestedFolderName;
        private String folderName;
        private boolean folderNameEdited;
        private boolean includeSnapshots;
        private boolean includeReleases = true;
        private boolean eulaAccepted;
        private boolean updatingVersionList;
        private boolean updatingVersionFilters;
        private String pendingServerJarVersion;
        private final Set<String> serverJarAvailableVersions = new HashSet<>();
        private final Set<String> serverJarUnavailableVersions = new HashSet<>();
    }

    private record ServerCreationWizardResult(
            ServerPlatformAdapter adapter,
            ServerCreationOption option,
            File targetDirectory
    ) {
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

