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


package Modelo;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
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

import static tools.jackson.databind.type.LogicalType.DateTime;

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

    // ===== DATOS DE EJECUCIÓN =====
    // lo que sea transient no se va a guardar en el JSON
    @JsonIgnore private transient Process serverProcess; // proceso del servidor NUNCA DEBE GUARDARSE, ES DE TIPO RUNTIME
    @JsonIgnore transient StringBuilder consoleBuffer = new StringBuilder(); // buffer de consola NO LO GUARDO
    @JsonIgnore transient List<Consumer<String>> consoleListeners = new ArrayList<>(); // es transient, no se guarda
    @JsonIgnore transient Boolean logReaderIniciado = false; // NO LO GUARDO
    @JsonIgnore transient List<String> rawLogLines = new ArrayList<>(); // líneas de logs sin traducir

    /*

    ===== EN PRINCIPIO TOODO ESTO SOBRA =====


    private String serverJar; // nombre del fichero .jar -------ACTUALIZAR A JARFILE------------------------------------ fixme
    private String serverMotd; // nombre del servidor en propiedades NO DEBE GUARDARSE AQUÍ, YA EXISTE EN EL PROPERTIES
    private ServerProperties serverProperties; // server.properties NO DEBE GUARDARSE AQUÍ, YA EXISTE EL ARCHIVO PROPERTIES
    private ImageIcon serverImage; // icono del servidor NO LO GUARDAMOS
    */



    // ===== CONSTRUCTORES =====

    // constructor por defecto
    public Server(){
        id = UUID.randomUUID().toString();
        this.serverConfig = new ServerConfig();
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
            rawLogLines.removeFirst();
        }
    }

    public synchronized List<String> getRawLogLines(){
        return new ArrayList<>(rawLogLines);
    }

    // ===== GET =====

    @JsonIgnore
    public ImageIcon getServerIconOrUseDefault(){
        File iconFile = new File(serverDir, "server-icon.png");
        if (iconFile.exists()){
            return new ImageIcon(iconFile.getAbsolutePath());
        }
        return new ImageIcon("default-image.png");
    }

    /*
    public ImageIcon getImage(){
        File imagenFile = new File(serverConfig.getServerRuta(),"server-icon.png");
        return new ImageIcon(imagenFile.getAbsolutePath());
    }

    private void crearDesdeCarpeta(File carpeta){
        serverImage = new ImageIcon("default_image.png");
        if(carpeta != null && carpeta.isDirectory()){
            for(File fichero : carpeta.listFiles()){
                if(fichero.getName().endsWith(".jar")){
                    serverJar = fichero.getName();
                }
                else if(fichero.getName().endsWith(".properties")){
                    serverProperties = new ServerProperties(this);
                    serverProperties.leerPropiedades();
                    serverMotd = serverProperties.getMotd();
                }
                else if(fichero.getName().equals("server-icon.png")){
                    serverImage = new ImageIcon(fichero.getAbsolutePath());
                }
            }
        }
    }*/
}
