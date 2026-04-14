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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;
import lombok.Getter;

import javax.swing.*;
import javax.imageio.ImageIO;

@Setter
@Getter

public class Server {
    // ===== DATOS PERSISTENTES =====
    private String id; // identificador único del servidor, es persistente, lo guardamos en el JSON
    private String displayName; // nombre que el usuario le da al servidor, es persistente, lo guardamos en el JSON
    private String version; // versión del servidor
    private String tipo; // tipo del servidor (Vanilla, Forge, Fabric, etc.)
    private String serverDir; // carpeta del servidor
    private ServerConfig serverConfig; // contiene XMS RAM, XMX RAM y el puerto
    private Integer ordenLista; // orden manual/base de la lista, preparado para drag and drop futuro
    private Boolean favorito; // indica si el servidor se muestra en el bloque superior de favoritos
    private Integer ordenFavorito; // orden estable entre favoritos, segun cuando se marcaron por primera vez
    private Integer estadisticasRangoSegundos; // rango visible preferido en el panel de estadísticas
    private Boolean estadisticasPersistenciaActiva; // si el histórico se guarda en disco para este servidor
    private Integer estadisticasVentanaRecienteSegundos; // muestras recientes conservadas a resolución de 1 segundo
    private Integer estadisticasResolucionHistoricaSegundos; // bucket para compactar histórico antiguo
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
        this.serverConfig = new ServerConfig();
        this.estadisticasRangoSegundos = 300;
        this.estadisticasPersistenciaActiva = true;
        this.estadisticasVentanaRecienteSegundos = 30 * 24 * 60 * 60;
        this.estadisticasResolucionHistoricaSegundos = 60;
        this.estadisticasRamActiva = true;
        this.estadisticasRamHistorial = true;
        this.estadisticasDiscoActiva = true;
        this.estadisticasDiscoHistorial = true;
        this.estadisticasJugadoresActiva = false;
        this.estadisticasJugadoresHistorial = true;
        this.previewRenderProfileId = "balanced";
        this.previewRenderRealtime = false;
        this.previewShowSpawn = false;
        this.previewShowPlayers = false;
        this.previewShowChunkGrid = false;
        this.previewUseWholeMap = false;
        this.previewRenderLimitPixels = 256;
        this.previewRenderCenterId = "spawn";
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

