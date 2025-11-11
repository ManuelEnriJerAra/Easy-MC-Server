import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ServerConfig {
    private int ramMin;
    private int ramMax;
    private String ruta;

    public ServerConfig(){}

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

    public static void guardarConfig(ServerConfig serverConfig) {
        ObjectMapper mapper = new ObjectMapper();
        // System.out.println(serverConfig.getRamMin() + " " + serverConfig.getRamMax() + " " + serverConfig.getRuta());
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("ServerConfig.json"), serverConfig);
        // System.out.println("ServerConfig.json guardado correctamente");
    }
}
