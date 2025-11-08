import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ServerConfig {
    int ramMin;
    int ramMax;
    String ruta;
    public ServerConfig(int ramMin, int ramMax, String ruta) {
        this.ramMin = ramMin;
        this.ramMax = ramMax;
        this.ruta = ruta;
    }

    public static void guardarConfig(int ramMin, int ramMax, String ruta) {
        ServerConfig serverConfig = new ServerConfig(ramMin, ramMax, ruta);
        ObjectMapper mapper = new ObjectMapper();
        //try{
            mapper.writeValue(new File("ServerConfig.json"), serverConfig);
        //} catch (IOException ex) {

        //}
    }
}
