package controlador;

import modelo.Server;
import lombok.AllArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@AllArgsConstructor

public class ServerLogReader implements Runnable{
    private final Server server;
    private final InputStream inputStream;

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                server.appendConsoleLinea(linea);
            }
        } catch (IOException e) {
            server.appendConsoleLinea("[ERROR] Error leyendo logs: "+e.getMessage());
        } finally {
            server.setLogReaderIniciado(false);
        }

    }

}
