/*
 * Fichero: GestorServidores.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase lleva a cabo toda la gestión de los servidores como conjunto algunas funciones son crear, borrar, listar,
 * importar, inicializar, etc.
 * */

package Controlador;

import Modelo.Server;
import Modelo.ServerConfig;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static Controlador.Utilidades.copiarArchivo;
import static Controlador.Utilidades.rellenaEULA;

@Getter
@Setter

public class GestorServidores {
    // ===== ATRIBUTOS =====
    private static final MojangAPI MOJANG_API = new MojangAPI();

    private static final String JSON_FILE = "ServerList.json";

    private final ObjectMapper mapper = new ObjectMapper();


    // Lista Principal (runtime + persistencia)
    private List<Server> listaServidores;
    private Server servidorSeleccionado;

    // Esto gestiona los listeners
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // ===== CONSTRUCTORES =====

    // Constructor por defecto
    public GestorServidores() {
        this.listaServidores = cargarServidores();
    }

    // cargamos todos los servidores del JSON
    private List<Server> cargarServidores(){
        File file = new File(JSON_FILE);
        if (!file.exists()) {
            // el JSON está vacío, empezamos de cero
            return new ArrayList<>();
        }
        try{
            return mapper.readValue(
                    file,
                    new TypeReference<List<Server>>(){}
            );
        } catch (JacksonException e) {
            System.err.println("Error al cargar servidores: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    // ===== LISTENERS Y CAMBIOS =====

    // Esto permite que otros escuchen
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    // ¿Esto permite que dejen de escuchar????????????????
    public void removePropertyChangeListener(PropertyChangeListener listener){
        pcs.removePropertyChangeListener(listener);
    }

    // este método notifica a los oyentes de que ha ocurrido un cambio
    private void notificarCambio(){
        pcs.firePropertyChange("listaServidores", null, listaServidores);
    }

    // ===== FUNCIONES Y MÉTODOS =====

    public Server crearServidor(){
        // Conectándonos a la API de Mojang nos descargamos el server.jar de la versión seleccionada por el usuario
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona donde se creará la carpeta del servidor");
        if(chooser.showDialog(chooser,"Seleccionar") == JFileChooser.APPROVE_OPTION){
            int eula = JOptionPane.showConfirmDialog(null, "¿Aceptas el EULA de Mojang (https://aka.ms/MinecraftEULA)?", "EULA",  JOptionPane.YES_NO_OPTION);
            if(eula == JOptionPane.YES_OPTION){
                File carpetaSeleccionada = chooser.getSelectedFile();
                if(!carpetaSeleccionada.isDirectory()){
                    carpetaSeleccionada = carpetaSeleccionada.getParentFile();
                }
                // listado y selección de versión
                JComboBox versionesBox = new JComboBox(MOJANG_API.obtenerListaVersiones().toArray());
                int versionSeleccionada = JOptionPane.showConfirmDialog(null, versionesBox, "Selecciona una versión", JOptionPane.OK_CANCEL_OPTION);
                if (versionSeleccionada == JOptionPane.OK_OPTION) {
                    File newCarpeta = new File (carpetaSeleccionada.getAbsoluteFile(), versionesBox.getSelectedItem().toString()+"_server");
                    newCarpeta.mkdir();
                    carpetaSeleccionada = newCarpeta;
                    String version = (String) versionesBox.getSelectedItem();
                    File serverFile = new File(carpetaSeleccionada,version+"_server.jar");
                    MOJANG_API.descargar(MOJANG_API.obtenerUrlServerJar(version), serverFile);
                    rellenaEULA(carpetaSeleccionada);
                    File icono = new File(carpetaSeleccionada, "server-icon.png");
                    copiarArchivo(new File("default_image.png"), icono);
                    Server server = new Server();
                    server.setDisplayName("Servidor "+version);
                    server.setVersion(version);
                    server.setTipo("VANILLA");
                    server.setServerDir(carpetaSeleccionada.getAbsolutePath());
                    guardarServidor(server);

                    return server;
                }
            }
        }
        return null;
    }

    public Server importarServidor(){
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona el directorio del servidor");

        if(chooser.showDialog(chooser,"Seleccionar") == JFileChooser.APPROVE_OPTION){
            File directorio = chooser.getSelectedFile();
            for(Server server : listaServidores){
                if(server.getServerDir().equals(directorio.getAbsolutePath())){
                    System.out.println("El servidor ya está importado.");
                    return server;
                }
            }
            Server server = new Server();
            server.setDisplayName("Servidor "+chooser.getSelectedFile().getName());
            server.setServerDir(directorio.getAbsolutePath());
            server.setVersion(DetectorVersionServidor.detectarVersionVanilla(server));
            server.setTipo("IMPORTADO");

            guardarServidor(server);
            return server;
        }
        return null;
    }

    public void guardarServidores(){
        try{
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(JSON_FILE), listaServidores);
        } catch (JacksonException e) {
            System.err.println("Error al guardar servidores: " + e.getMessage());
        }
    }

    public void guardarServidor(Server server){
        listaServidores.removeIf(servidor -> servidor.getId().equals(server.getId()));
        listaServidores.add(server);
        guardarServidores();
        notificarCambio();
    }

    /*
    public Server getOrCreateServidor(ServerConfig serverConfig){
        Path key = serverConfig.;
        Server server = serverCache.get(key);
        if(server == null){
            server = new Server();
            server.setServerDir(key);
            server.setServerConfig(serverConfig);
            serverCache.put(key, server);
        }
        return server;
    }

    // devuelve una lista de ServerConfig obteniendo los datos del JSON
    public List<ServerConfig> listarServerConfig(){
        List<ServerConfig> serverConfigs;
        File file = new File("ServerList.json");
        ObjectMapper mapper = new ObjectMapper();
        try{
            // leemos todos los servidores y los metemos en la lista servers
            serverConfigs = mapper.readValue(
                    file,
                    new TypeReference<>(){}
            );
            System.out.println("Se han detectado servidores guardados.");
            // se pueden introducir también direcciones que no contengan servidores, aquí no se comprueba
            return serverConfigs;
        }
        catch (Exception e){
            // si está vacío lo indicamos, pero no es problemático
            System.out.println("No se han detectado servidores guardados.");
            serverConfigs = new ArrayList<>();
            return serverConfigs;
        }
    }


    private void guardarServidor(Server server){
        File json = new File("ServerList.json");
        ObjectMapper mapper = new ObjectMapper();

        if(this.listaConfigs == null){
            this.listaConfigs = new ArrayList<>();
        }

        String rutaNueva = server.getServerDir().toString();
        listaConfigs.removeIf(serverConfig -> serverConfig.equals(rutaNueva));
        try{
            // leemos el JSON y hacemos una lista de los serverConfigs que hay
            if(this.listaServidores != null){ // si hay serverConfigs guardados
                for(ServerConfig serverConfig: listaServidores){ // comprobamos si ya existe el mismo serverConfig
                    if(serverConfig.getServerRuta().equals(server.getServerConfig().getServerRuta())){
                        listaServidores.remove(serverConfig); // quitamos el servidor antiguo para posteriormente poner el nuevo
                        break;
                    }
                }
            }
            else{ // si no hay ningún servidor todavía
                listaServidores = new ArrayList<>(); // creamos una lista vacía
            }
            listaServidores.add(server.getServerConfig());
            // escribimos los servidores en el archivo JSON
            mapper.writerWithDefaultPrettyPrinter().writeValue(json, listaServidores);
            System.out.println("Servidor guardado en el JSON.");
            notificarCambio();
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }*/

    // esta función es la encargada de eliminar un servidor del JSON, si lo elimina devuelve 1
    /*public int quitarServidorDeJSON(ServerConfig serverConfigIn){
        File json = new File("ServerList.json");
        ObjectMapper mapper = new ObjectMapper();
        List<ServerConfig> listaServidoresAux = new ArrayList<>(listaServidores);
        try{
            for(ServerConfig serverConfig: listaServidoresAux){
                if(serverConfig.getServerRuta().equals(serverConfigIn.getServerRuta())){
                    listaServidores.remove(serverConfig);
                    return 1;
                }
            }
            return 0;
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
    }*/

    // esta función elimina una lista de servidores del archivo JSON y devuelve cuántos ha borrado

    public int quitarListaServidoresJSON(List<Server> lista){

        int contador = 0;
        for(Server server: lista){
            ObjectMapper mapper = new ObjectMapper();
            List<Server> listaServidoresAux = new ArrayList<>(lista);
            /*
            try{
                for(ServerConfig serverConfigAux: listaServidoresAux){
                    if(serverConfigAux.getServerRuta().equals(listaServidores.getServerRuta())){}
                }
                IMPLEMENTAR--------------------

            }*/
        }
        System.out.println("Quitando " + contador + " servidores del JSON.");
        return contador;
    }

    public int eliminarServidorCompleto(Server server){
        return 0; // POR IMPLEMENTAR
    }

    // ===== MÉTODOS DE CONTROL DEL SERVIDOR =====

    public synchronized void iniciarServidor(Server server) throws IOException {
        // Compruebo si está en marcha, si no, prosigo
        if(server.getServerProcess()!=null&&server.getServerProcess().isAlive()) {
            server.appendConsoleLinea("[INFO] El servidor ya está iniciado. ");
            return;
        }
        Path dir = Path.of(server.getServerDir());
        Path jar = Utilidades.encontrarEjecutableJar(dir);

        // Creo un proceso con la dirección del servidor y la RAM elegida
        ServerConfig serverConfig = server.getServerConfig();

        ProcessBuilder pb = new ProcessBuilder("java",
                "-Xms"+serverConfig.getRamInit()+"M",
                "-Xmx"+serverConfig.getRamMax()+"M",
                "-jar",
                jar.toString(),
                "nogui"
        );

        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        server.appendConsoleLinea("[INFO] Iniciando servidor con "+serverConfig.getRamInit()+"M y "+serverConfig.getRamMax()+"M de RAM.");

        try{
            // creo el proceso y se lo asigno al servidor
            Process proceso = pb.start();
            server.setServerProcess(proceso);

            // comienzo a leer la consola
            startLogReader(server);

            // detecto cuando el proceso finaliza
            proceso.onExit().thenRun(()->{
               server.appendConsoleLinea("[INFO] El servidor se ha detenido.");
            });

        } catch (IOException e) {
            server.appendConsoleLinea("[ERROR] El servidor no se pudo iniciar"+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void forzarPararServidor(Server server){
        Process proceso = server.getServerProcess();

        if(proceso==null || !proceso.isAlive()) {
            server.appendConsoleLinea("[INFO] El servidor no está en ejecución");
            server.setServerProcess(null);
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
        }
    }

    public void safePararServidor(Server server){
        if(server.getServerProcess()==null || !server.getServerProcess().isAlive()) {
            server.appendConsoleLinea("[INFO] No has iniciado el servidor.");
            return;
        }
        try{
            OutputStream os = server.getServerProcess().getOutputStream();
            PrintWriter pw = new PrintWriter(os, true);
            pw.println("stop");
            server.appendConsoleLinea("[INFO] Enviado comando: 'stop'.");

            /* Esperamos el fin del proceso
            Thread monitor = new Thread(()->{
                try{
                    int code = server.getServerProcess().waitFor();
                    server.appendConsoleLinea("[INFO] Servidor detenido exitosamente.");
                } catch (InterruptedException e) {
                    server.appendConsoleLinea("[ERROR] Error en la espera: "+e.getMessage());
                }
            }, "wait-exit-" + server.getServerDir());
            monitor.setDaemon(true);
            monitor.start();
            */
        } catch (Exception e) {
            server.appendConsoleLinea("[ERROR] Error mandando 'stop': "+e.getMessage());
        }
    }

    public Server getServerById(String id){
        return listaServidores.stream().filter(servidor -> servidor.getId().equals(id)).findFirst().orElse(null);
    }

    // ===== LECTURA DE LOGS =====

    private void startLogReader(Server server){
        if (server.getLogReaderIniciado()) return;
        if (server.getServerProcess() == null) return;

        server.setLogReaderIniciado(true);

        Thread lector = new Thread(
            new ServerLogReader(
                    server,
                    server.getServerProcess().getInputStream()
            ),
            "log-reader-"+server.getId()
        );
        lector.setDaemon(true);
        lector.start();
    }
}
