import tools.jackson.databind.ObjectMapper;
import java.io.File;

public class ServerConfig {
    private int ramMin;
    private int ramMax;
    private String ruta;

    public ServerConfig(){
        this.ramMin = 1024;
        this.ramMax = 2048;
    }

    public ServerConfig(int ramMin, int ramMax, String ruta) {
        this.ramMin = ramMin;
        this.ramMax = ramMax;
        this.ruta = ruta;
    }

    public int getRamMin() {
        return ramMin;
    }
    public int getRamMax() {
        return ramMax;
    }
    public String getRuta() {
        return ruta;
    }
    public void setRamMin(int ramMin) {
        this.ramMin = ramMin;
    }
    public void setRamMax(int ramMax) {
        this.ramMax = ramMax;
    }
    public void setRuta(String ruta) {
        this.ruta = ruta;
    }


    // leer el fichero JSON y obtener la configuración de este
    ServerConfig leerServerConfig() {
        File serverConfigFile = new File("ServerConfigList.json");
        ObjectMapper mapper = new ObjectMapper();
        if (serverConfigFile.exists()) {
            try {
                return mapper.readValue(serverConfigFile, ServerConfig.class);
            } catch (Exception e) {
                System.out.println("El archivo ServerConfigList.json no contiene ningún servidor válido.");
                return null;
            }
        }

        return null;
    }

    // almacenar en el archivo JSON los valores del servidor
    void guardarServerConfig() {
        File serverConfigFile = new File("ServerConfigList.json");
        ObjectMapper mapper = new ObjectMapper();
        try{
            System.out.println();
            mapper.writeValue(serverConfigFile, this);
            System.out.println("Servidor guardado");
        }catch(Exception e){
            System.out.println("No se ha podido guardar el servidor en ServerConfigList.json");
        }
    }
}
