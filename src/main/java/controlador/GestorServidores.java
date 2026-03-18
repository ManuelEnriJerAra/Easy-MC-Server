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

import modelo.Server;
import modelo.ServerConfig;
import com.formdev.flatlaf.extras.components.FlatProgressBar;
import lombok.Getter;
import lombok.Setter;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static controlador.Utilidades.copiarArchivo;
import static controlador.Utilidades.rellenaEULA;
import static java.lang.Math.max;
import static java.lang.Math.min;

@Getter
@Setter

public class GestorServidores {
    // ===== ATRIBUTOS =====
    private static final MojangAPI MOJANG_API = new MojangAPI();

    // AtomicInteger nos elimina el riesgo de condición de carrera si varios servidores intentan acceder a la vez
    private static final AtomicInteger NEXT_PORT_SESION = new AtomicInteger(25565);

    private static final String JSON_FILE = "ServerList.json";

    private final ObjectMapper mapper = new ObjectMapper();

    private String avisoServidoresNoCargados;

    // Lista Principal (runtime + persistencia)
    private List<Server> listaServidores;
    private Server servidorSeleccionado;

    // Esto gestiona los listeners
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // ===== CONSTRUCTORES =====

    // Constructor por defecto
    public GestorServidores() {
        this.listaServidores = cargarServidores();
        validarYLimpiarServidoresPersistidos();
    }

    // cargamos todos los servidores del JSON
    private List<Server> cargarServidores(){
        File file = new File(JSON_FILE);
        if (!file.exists()) {
            // el JSON está vacío, empezamos de cero
            return new ArrayList<>();
        }
        try{
            return mapper.readValue(file, new TypeReference<>(){});
        } catch (JacksonException e) {
            System.err.println("Error al cargar servidores: " + e.getMessage());
            return new ArrayList<>();
        }
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
                    String tipoDetectado = DetectorTipoServidor.detectarTipo(Path.of(server.getServerDir()));
                    // si no tiene ningún tipo lo descartamos, no es un servidor correcto
                    if (tipoDetectado != null && !tipoDetectado.equals(server.getTipo())) {
                        // si tiene tipo se lo establecemos
                        server.setTipo(tipoDetectado);
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
        if (noCargables.isEmpty() && !cambios) return;

        listaServidores = cargables;
        guardarServidores();
        notificarCambio();

        if (noCargables.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("No se han podido cargar ")
                .append(noCargables.size())
                .append(" servidores guardados (carpeta inexistente o servidor invalido).")
                .append("\nNo se mostraran y se han eliminado de ServerList.json.");

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

        try (Stream<Path> archivos = Files.list(dir)) {
            List<Path> jars = archivos
                    .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                    .toList();
            if (jars.size() != 1) return false;

            Path jar = jars.get(0);
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                boolean tieneVersionJson = jarFile.getJarEntry("version.json") != null;
                boolean tieneMinecraftServerClass = jarFile.getJarEntry("net/minecraft/server/MinecraftServer.class") != null;
                return tieneVersionJson || tieneMinecraftServerClass;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
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
        pcs.firePropertyChange("listaServidores", null, listaServidores);
    }

    // este método notifica a los oyentes de que ha ocurrido un cambio en el estado del servidor
    public void notificarEstadoServidor(Server server){
        pcs.firePropertyChange("estadoServidor", null, server);
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
                JComboBox<String> versionesBox = new JComboBox<>(MOJANG_API.obtenerListaVersiones().toArray(new String[0]));

                int versionSeleccionada = JOptionPane.showConfirmDialog(null, versionesBox, "Selecciona una versión", JOptionPane.OK_CANCEL_OPTION);
                if (versionSeleccionada == JOptionPane.OK_OPTION) {
                    File newCarpeta = new File (carpetaSeleccionada.getAbsoluteFile(), versionesBox.getSelectedItem().toString()+"_server");
                    newCarpeta.mkdir();
                    carpetaSeleccionada = newCarpeta;
                    String version = (String) versionesBox.getSelectedItem();
                    File serverFile = new File(carpetaSeleccionada,version+"_server.jar");
                    String urlServer = MOJANG_API.obtenerUrlServerJar(version);
                    if(urlServer == null || urlServer.isBlank()){
                        JOptionPane.showMessageDialog(
                                null,
                                "No se ha podido obtener la URL del servidor para la version " + version,
                                "Descarga",
                                JOptionPane.ERROR_MESSAGE
                        );
                        return null;
                    }

                    boolean descargado = descargarConBarra(urlServer, serverFile, "Descargando servidor " + version);
                    if(!descargado){
                        return null;
                    }
                    rellenaEULA(carpetaSeleccionada);
                    File icono = new File(carpetaSeleccionada, "server-icon.png");
                    copiarArchivo(new File("default_image.png"), icono);
                    Server server = new Server();
                    server.setDisplayName("Servidor "+version);
                    server.setVersion(version);
                    server.setServerDir(carpetaSeleccionada.getAbsolutePath());
                    server.setTipo(DetectorTipoServidor.detectarTipo(Path.of(server.getServerDir())));
                    guardarServidor(server);

                    return server;
                }
            }
        }
        return null;
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

    // Esta función es la encargada de importar un servidor a partir de una carpeta
    public Server importarServidor(){
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona el directorio del servidor");

        // pedimos al usuario que indique la carpeta
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
            server.setTipo(DetectorTipoServidor.detectarTipo(Path.of(server.getServerDir())));

            guardarServidor(server);
            return server;
        }
        return null;
    }

    // Guarda la lista de servidores en el JSON
    public void guardarServidores(){
        try{
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(JSON_FILE), listaServidores);
        } catch (JacksonException e) {
            System.err.println("Error al guardar servidores: " + e.getMessage());
        }
    }

    // Guarda un sólo servidor en la lista de servidores y luego lo guarda en el JSON
    public void guardarServidor(Server server){
        listaServidores.removeIf(servidor -> servidor.getId().equals(server.getId()));
        listaServidores.add(server);
        guardarServidores();
        notificarCambio();
    }

    // Elimina un servidor de la lista de servidores y del JSON, no toca los archivos del servidor
    public boolean eliminarServidor(Server server){
        if(server == null) return false;
        if(server.getServerProcess() != null && server.getServerProcess().isAlive()){
            return false; // no podemos eliminarlo mientras está en ejecución para no dejar procesos abiertos
        }

        boolean removed = listaServidores.removeIf(s -> s.getId().equals(server.getId()));
        if(!removed) return false;

        if(servidorSeleccionado != null && servidorSeleccionado.getId().equals(server.getId())){
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
        Path jar;
        try {
            jar = Utilidades.encontrarEjecutableJar(dir);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido encontrar el .jar del servidor: " + e.getMessage());
            return;
        }
        if (jar == null) {
            server.appendConsoleLinea("[ERROR] No se ha podido encontrar el .jar del servidor.");
            return;
        }

        // Creo un proceso con la dirección del servidor y la RAM elegida y en el primer puerto que esté libre desde 25565
        ServerConfig serverConfig = server.getServerConfig();
        int puerto = elegirPuertoParaServidor(server);
        if(puerto != serverConfig.getPuerto()){
            // si el puerto no está libre pasamos al siguiente
            serverConfig.setPuerto(puerto);
            try{
                guardarServidor(server);
            } catch (RuntimeException ignored) {
            }
        }
        try{
            // escribimos el puerto nuevo en las propiedades del servidor
            Utilidades.escribirPuertoEnProperties(dir, puerto);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido escribir el puerto en server.properties: " + e.getMessage());
        }

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
            notificarEstadoServidor(server);

            // comienzo a leer la consola
            startLogReader(server);
            notificarEstadoServidor(server);

            // detecto cuando el proceso finaliza
            proceso.onExit().thenRun(()->{
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
            notificarEstadoServidor(server);
        }
    }

    public void safePararServidor(Server server){
        mandarComando(server, "stop");
    }

    public Server getServerById(String id){
        return listaServidores.stream().filter(servidor -> servidor.getId().equals(id)).findFirst().orElse(null);
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
                    server.getServerProcess().getInputStream()
            ),
            "log-reader-"+server.getId()
        );
        lector.setDaemon(true);
        lector.start();
    }

    private int elegirPuertoParaServidor(Server server){
        if(server == null) return 25565; // por defecto usamos el 25565
        ServerConfig serverConfig = server.getServerConfig();
        if(serverConfig == null){
            serverConfig = new ServerConfig();
            server.setServerConfig(serverConfig);
        }

        // Política: siempre intentar 25565, luego 25566, 25567...
        for(int p = 25565; p <= 65535; p++){
            if(isPortAvailable(p)){
                probarSiguientePuerto(p);
                return p;
            }
        }

        throw new RuntimeException("No hay puertos disponibles entre 25565 y 65535");
    }

    // comprobamos si hay libre un puerto
    private static boolean isPortAvailable(int port){
        if(port <= 0 || port > 65535) return false;
        try(ServerSocket socket = new ServerSocket()){
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("0.0.0.0", port)); // escucho en 0.0.0.0, si conecto está libre
            return true;
        } catch (IOException e) {
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
}
