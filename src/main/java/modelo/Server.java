/*
* Fichero: Server.java
*
* Autor: Manuel Enrique Jerónimo Aragón
* Fecha: 16/01/2026
*
* Descripción:
* Define la clase Server, que representa un servidor de Minecraft. Desde el objeto servidor podemos acceder a todos
* sus datos. Tiene los atributos ServerConfig y ServerProperties, que son otras clases de este mismo proyecto.
* El servidor contiene toda la información del servidor, sin embargo, no realiza ninguna gestión, de eso se encarga la
* clase GestorServidores.
* */


package modelo;

import java.io.*;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Setter;
import lombok.Getter;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;

import javax.swing.*;
import javax.imageio.ImageIO;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)

public class Server {
    // ===== DATOS PERSISTENTES =====
    private String id; // identificador único del servidor, es persistente, lo guardamos en el JSON
    private String displayName; // nombre que el usuario le da al servidor, es persistente, lo guardamos en el JSON
    private String version; // versión del servidor
    private String tipo; // tipo del servidor (Vanilla, Forge, Fabric, etc.)
    private ServerPlatform platform; // plataforma interna del servidor
    private ServerLoader loader; // loader/ecosistema técnico principal
    private String loaderVersion; // versión del loader, si se conoce
    private Set<ServerCapability> capabilities; // capacidades detectadas o soportadas por la app
    private ServerEcosystemType ecosystemType; // agrupa el servidor en vanilla/mods/plugins
    private List<ServerExtension> extensions; // extensiones detectadas o gestionadas localmente
    private String serverDir; // carpeta del servidor
    private ServerConfig serverConfig; // contiene XMS RAM, XMX RAM y el puerto
    private Integer ordenLista; // orden manual/base de la lista, preparado para drag and drop futuro
    private Boolean favorito; // indica si el servidor se muestra en el bloque superior de favoritos
    private Integer ordenFavorito; // orden estable entre favoritos, segun cuando se marcaron por primera vez
    private Integer estadisticasRangoSegundos; // rango visible preferido en el panel de estadísticas
    private Boolean estadisticasPersistenciaActiva; // si el histórico se guarda en disco para este servidor
    private Integer estadisticasVentanaRecienteSegundos; // muestras recientes conservadas a resolución de 1 segundo
    private Integer estadisticasResolucionHistoricaSegundos; // bucket para compactar histórico antiguo
    private Boolean estadisticasCpuActiva;
    private Boolean estadisticasCpuHistorial;
    private Boolean estadisticasRamActiva;
    private Boolean estadisticasRamHistorial;
    private Boolean estadisticasDiscoActiva;
    private Boolean estadisticasDiscoHistorial;
    private Boolean estadisticasJugadoresActiva;
    private Boolean estadisticasJugadoresHistorial;
    private String previewRenderProfileId;
    private Boolean previewRenderRealtime;
    private Boolean previewShowSpawn;
    private Boolean previewShowPlayers;
    private Boolean previewShowChunkGrid;
    private Boolean previewUseWholeMap;
    private Integer previewRenderLimitPixels;
    private String previewRenderCenterId;

    // ===== DATOS DE EJECUCIÓN =====
    // lo que sea transient no se va a guardar en el JSON
    @JsonIgnore private transient Process serverProcess; // proceso del servidor NUNCA DEBE GUARDARSE, ES DE TIPO RUNTIME
    @JsonIgnore transient StringBuilder consoleBuffer = new StringBuilder(); // buffer de consola NO LO GUARDO
    @JsonIgnore transient List<Consumer<String>> consoleListeners = new ArrayList<>(); // es transient, no se guarda
    @JsonIgnore transient Boolean logReaderIniciado = false; // NO LO GUARDO
    @JsonIgnore transient Boolean restartPending = false; // reinicio pendiente
    @JsonIgnore transient List<String> rawLogLines = new ArrayList<>(); // líneas de logs sin traducir
    @JsonIgnore transient Boolean iniciando = false; // arranque en progreso

    // ===== CONSTRUCTORES =====

    // constructor por defecto
    public Server(){
        id = UUID.randomUUID().toString();
        this.platform = ServerPlatform.UNKNOWN;
        this.loader = ServerLoader.UNKNOWN;
        this.loaderVersion = null;
        this.capabilities = EnumSet.noneOf(ServerCapability.class);
        this.ecosystemType = ServerEcosystemType.UNKNOWN;
        this.extensions = new ArrayList<>();
        this.serverConfig = new ServerConfig();
        this.estadisticasRangoSegundos = 300;
        this.estadisticasPersistenciaActiva = true;
        this.estadisticasVentanaRecienteSegundos = 30 * 24 * 60 * 60;
        this.estadisticasResolucionHistoricaSegundos = 60;
        this.estadisticasCpuActiva = true;
        this.estadisticasCpuHistorial = true;
        this.estadisticasRamActiva = true;
        this.estadisticasRamHistorial = true;
        this.estadisticasDiscoActiva = true;
        this.estadisticasDiscoHistorial = true;
        this.estadisticasJugadoresActiva = false;
        this.estadisticasJugadoresHistorial = true;
        this.previewRenderProfileId = "quality";
        this.previewRenderRealtime = false;
        this.previewShowSpawn = false;
        this.previewShowPlayers = false;
        this.previewShowChunkGrid = false;
        this.previewUseWholeMap = false;
        this.previewRenderLimitPixels = 256;
        this.previewRenderCenterId = "spawn";
    }

    public String getTipo() {
        if (tipo != null && !tipo.isBlank()) {
            return tipo;
        }
        if (platform != null && platform != ServerPlatform.UNKNOWN) {
            return platform.getLegacyTypeName();
        }
        return null;
    }

    public void setTipo(String tipo) {
        this.tipo = normalizarTexto(tipo);
        ServerPlatform migratedPlatform = ServerPlatform.fromLegacyType(this.tipo);
        if (migratedPlatform != ServerPlatform.UNKNOWN || this.platform == null || this.platform == ServerPlatform.UNKNOWN) {
            this.platform = migratedPlatform;
        }
        aplicarMetadatosCompatiblesConPlataforma(false);
    }

    public void setPlatform(ServerPlatform platform) {
        this.platform = platform == null ? ServerPlatform.UNKNOWN : platform;
        if (this.platform != ServerPlatform.UNKNOWN) {
            this.tipo = this.platform.getLegacyTypeName();
        } else if (this.tipo != null && this.tipo.isBlank()) {
            this.tipo = null;
        }
        aplicarMetadatosCompatiblesConPlataforma(false);
    }

    public void setCapabilities(Set<ServerCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            this.capabilities = EnumSet.noneOf(ServerCapability.class);
            return;
        }
        this.capabilities = EnumSet.copyOf(capabilities);
    }

    public boolean migrarModeloLegacy() {
        boolean cambios = false;

        if ((tipo == null || tipo.isBlank()) && platform != null && platform != ServerPlatform.UNKNOWN) {
            tipo = platform.getLegacyTypeName();
            cambios = true;
        }

        if ((platform == null || platform == ServerPlatform.UNKNOWN) && tipo != null && !tipo.isBlank()) {
            ServerPlatform migratedPlatform = ServerPlatform.fromLegacyType(tipo);
            if (migratedPlatform != ServerPlatform.UNKNOWN || platform == null) {
                platform = migratedPlatform;
                cambios = true;
            }
        }

        if (platform == null) {
            platform = ServerPlatform.UNKNOWN;
            cambios = true;
        }
        if (loader == null) {
            loader = platform.getDefaultLoader();
            cambios = true;
        }
        if (ecosystemType == null) {
            ecosystemType = platform.getDefaultEcosystemType();
            cambios = true;
        }
        if (capabilities == null) {
            capabilities = EnumSet.copyOf(platform.defaultCapabilities());
            cambios = true;
        } else if (!(capabilities instanceof EnumSet<?>)) {
            capabilities = capabilities.isEmpty()
                    ? EnumSet.noneOf(ServerCapability.class)
                    : EnumSet.copyOf(capabilities);
            cambios = true;
        }
        if (extensions == null) {
            extensions = new ArrayList<>();
            cambios = true;
        }

        if (aplicarMetadatosCompatiblesConPlataforma(true)) {
            cambios = true;
        }
        if (normalizarExtensiones()) {
            cambios = true;
        }

        return cambios;
    }

    private boolean aplicarMetadatosCompatiblesConPlataforma(boolean soloSiFalta) {
        boolean cambios = false;
        ServerPlatform resolvedPlatform = platform == null ? ServerPlatform.UNKNOWN : platform;

        if (!soloSiFalta || loader == null || loader == ServerLoader.UNKNOWN) {
            ServerLoader targetLoader = resolvedPlatform.getDefaultLoader();
            if (targetLoader != null && loader != targetLoader) {
                loader = targetLoader;
                cambios = true;
            }
        }

        if (!soloSiFalta || ecosystemType == null || ecosystemType == ServerEcosystemType.UNKNOWN) {
            ServerEcosystemType targetEcosystem = resolvedPlatform.getDefaultEcosystemType();
            if (targetEcosystem != null && ecosystemType != targetEcosystem) {
                ecosystemType = targetEcosystem;
                cambios = true;
            }
        }

        Set<ServerCapability> targetCapabilities = resolvedPlatform.defaultCapabilities();
        if (!soloSiFalta || capabilities == null || capabilities.isEmpty()) {
            if (capabilities == null || !capabilities.equals(targetCapabilities)) {
                capabilities = targetCapabilities.isEmpty()
                        ? EnumSet.noneOf(ServerCapability.class)
                        : EnumSet.copyOf(targetCapabilities);
                cambios = true;
            }
        }

        return cambios;
    }

    private boolean normalizarExtensiones() {
        if (extensions == null) {
            extensions = new ArrayList<>();
            return true;
        }
        boolean cambios = false;
        for (ServerExtension extension : extensions) {
            if (extension == null) {
                continue;
            }
            if (extension.getId() == null || extension.getId().isBlank()) {
                extension.setId(UUID.randomUUID().toString());
                cambios = true;
            }
            if (extension.getSource() == null) {
                extension.setSource(new modelo.extensions.ExtensionSource());
                cambios = true;
            }
            if (extension.getExtensionType() == null) {
                extension.setExtensionType(ServerExtensionType.UNKNOWN);
                cambios = true;
            }
            if (extension.getPlatform() == null) {
                extension.setPlatform(ServerPlatform.UNKNOWN);
                cambios = true;
            }
            if (extension.getInstallState() == null) {
                extension.setInstallState(modelo.extensions.ExtensionInstallState.UNKNOWN);
                cambios = true;
            }
            if (extension.getLocalMetadata() == null) {
                extension.setLocalMetadata(new modelo.extensions.ExtensionLocalMetadata());
                cambios = true;
            } else {
                if (extension.getLocalMetadata().getEnabled() == null) {
                    extension.getLocalMetadata().setEnabled(Boolean.TRUE);
                    cambios = true;
                }
                if (extension.getLocalMetadata().getUpdateState() == null) {
                    extension.getLocalMetadata().setUpdateState(modelo.extensions.ExtensionUpdateState.UNKNOWN);
                    cambios = true;
                }
            }
        }
        return cambios;
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return null;
        String trimmed = texto.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    // ===== CONSOLA =====

    @JsonIgnore static final Pattern HORA = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}]\\s*");

    private String conHoraSiFalta(String linea){
        if(linea==null) return null;
        if(HORA.matcher(linea).find()) return linea; // ya tiene la hora
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "["+hora+"] "+linea;
    }

    public synchronized void appendConsoleLinea(String linea){
        linea = conHoraSiFalta(linea);
        if(!Objects.equals(linea, "")){
            consoleBuffer.append(linea).append("\n");
        }
        // limitamos el tamaño
        if(consoleBuffer.length()>300_000){
            consoleBuffer.delete(0, consoleBuffer.length()-200_000);
        }
        for(var listener: consoleListeners) listener.accept(linea);
        appendRawLogLine(linea);
    }

    @JsonIgnore public synchronized String getConsoleText(){
        return consoleBuffer.toString();
    }

    public synchronized void addConsoleListener(Consumer<String> listener){
        consoleListeners.add(listener);
    }

    public synchronized void removeConsoleListener(Consumer<String> listener){
        consoleListeners.remove(listener);
    }

   @JsonIgnore synchronized int getConsoleListenerCount(){
        return consoleListeners.size();
   }

    public synchronized void clearConsoleListener(){
        consoleListeners.clear();
    }

    public synchronized void appendRawLogLine(String linea){
        rawLogLines.add(linea);

        // límite para no sobrecargar memoria
        if(rawLogLines.size()>5000){
            rawLogLines.remove(0);
        }
    }

    public synchronized List<String> getRawLogLines(){
        return new ArrayList<>(rawLogLines);
    }

    // ===== GET =====

    // devuelve el icono, si no tiene usa el predeterminado
    @JsonIgnore
    public ImageIcon getServerIconOrUseDefault(){
        if(serverDir != null && !serverDir.isBlank()){ // si la carpeta existe intenta coger la imagen
            File iconFile = new File(serverDir, "server-icon.png");
            if (iconFile.exists()){
                try{
                    // lo leemos como imagen para comprobar que funciona correctamente
                    BufferedImage img = ImageIO.read(iconFile);
                    if(img != null) return new ImageIcon(img);
                } catch (IOException ignored){
                }
                return new ImageIcon(iconFile.getAbsolutePath());
            }
        }
        // si no se ha encontrado usamos la imagen por defecto
        ImageIcon porDefecto = new ImageIcon(getClass().getResource("/default_image.png"));
        return porDefecto;
    }
}

