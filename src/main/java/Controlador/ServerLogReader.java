package Controlador;

import Modelo.Server;
import lombok.AllArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AllArgsConstructor

public class ServerLogReader implements Runnable{
    private final Server server;
    private final InputStream inputStream;

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                server.appendRawLogLine(linea);
                server.appendConsoleLinea(linea);
            }
        } catch (IOException e) {
            server.appendConsoleLinea("[ERROR] Error leyendo logs: "+e.getMessage());
        } finally {
            server.setLogReaderIniciado(false);
        }

    }

}
