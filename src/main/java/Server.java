import java.io.*;
import java.nio.charset.StandardCharsets;

import lombok.Setter;
import lombok.Getter;

@Setter
@Getter

public class Server {
    private String serverName;

    private ServerConfig serverConfig;
    private ServerProperties serverProperties;
    private Process serverProcess;

    public Server(){
        serverName = "server.jar";
        serverConfig = new ServerConfig();
        serverProperties = new ServerProperties();
    };

    public Server(String serverName, ServerConfig serverConfig, ServerProperties serverProperties, Process serverProcess) {
        this.serverConfig = serverConfig;
        this.serverProperties = serverProperties;
        this.serverProcess = serverProcess;
    }

    public void setServer(Server server) {
        this.serverConfig = server.serverConfig;
        this.serverProperties = server.serverProperties;
    }

    public void ejecutarServidor(){
        ProcessBuilder pb = new ProcessBuilder("java",
                "-Xms"+serverConfig.getRamMin()+"M",
                "-Xmx"+serverConfig.getRamMax()+"M",
                "-jar",
                serverConfig.getRuta(),
                "nogui");
        pb.directory(new File(this.serverConfig.getRuta()).getParentFile());
        pb.redirectErrorStream(true);

        try{
            System.out.println("Se va a ejecutar"+ pb.command());
            this.serverProcess = pb.start();
            System.out.println("El servidor se está iniciando...");
            Thread monitor = new Thread(()->{
               BufferedReader input = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
               String linea;
               try{
                   while(((linea=input.readLine())!=null)){
                       System.out.println(linea);
                       if(linea.contains("Done")){
                           System.out.println("El servidor se ha iniciado.");
                       }
                   }
                } catch (IOException e) {
                   throw new RuntimeException(e);
                }

            });
            monitor.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void forzarPararServidor(){
        if(this.serverProcess.isAlive()){
            serverProcess.destroy();
        }
    }

    public void safePararServidor() throws IOException {
        OutputStream os = serverProcess.getOutputStream();
        PrintWriter pw = new PrintWriter(os, true);
        pw.println("stop");
        System.out.println("El servidor se ha cerrado de forma segura.");
    }

    public void inicializarServidor(File servidor){
        serverName = servidor.getName();
        serverConfig = new ServerConfig();
        serverProperties = new ServerProperties();
        serverConfig.setRuta(String.valueOf(servidor));
        System.out.println("Ruta inicializada a "+serverConfig.getRuta());
        System.out.println("El servidor se llama " + serverName);
        ejecutarServidor();
        Thread monitor = new Thread(() ->{
           try{
               BufferedReader br = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
               String line;
               while ((line = br.readLine()) != null){
                   System.out.println(line);
                   if(line.contains("Done")){
                       System.out.println("Servidor correctamente incializado, cerrando proceso...");

                       break;
                   }
               }
               while(serverProcess.isAlive()){
                   Thread.sleep(1000);
               }
               System.out.println("El servidor se ha cerrado.");
           } catch (Exception e) {
               e.printStackTrace();
           }
        });
        monitor.start();
    }

    public boolean comprobarEstadoServidor(){
        return this.serverProcess.isAlive();
    }


}
