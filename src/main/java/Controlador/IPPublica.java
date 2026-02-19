/*
 * Fichero: IPPublica.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Esta clase contiene los métodos necesarios para conocer la IP pública del ordenador del usuario de forma asíncrona,
 * para que no bloquee la interfaz de Swing
 * */

package Controlador;

import javax.swing.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class IPPublica {
    private static volatile String cache; // guarda en memoria la última IP obtenida
    private static final AtomicBoolean fetching = new AtomicBoolean(false); // contiene si se está buscando ya

    public static void getAsync(Consumer<String> callback){
        Objects.requireNonNull(callback, "callback"); // pide un callback

        String actual = cache;
        if(actual != null && !actual.isBlank()){ // si el cache NO contiene ninguna iP
            SwingUtilities.invokeLater(() -> callback.accept(actual)); // llamamos al callback en un hilo de UI
            return;
        }

        // si no hay nadie fetching, decimos que estamos fetching
        if(fetching.compareAndSet(false, true)){
            // este hilo se encarga de conectar con una API que nos dice cuál es nuestra IP pública
            new Thread(() -> {
                String ip = null;
                try{
                    // hacemos la conexión con un timeout de 5 segundos
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
                    // hacemos un request de el contenido de la API, que es nuestra IP
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.ipify.org"))
                            .timeout(Duration.ofSeconds(8))
                            .GET()
                            .build();

                    // lee el contenido del request como string, si el HTTP respondió entre 200 y 299 está bien
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if(resp.statusCode() >= 200 && resp.statusCode() < 300){
                        ip = resp.body() == null ? null : resp.body().trim();
                    }
                } catch (Exception ignored) {
                } finally{
                    if(ip != null && !ip.isBlank()){
                        cache = ip; // si la IP obtenida es incorrecta devolvemos la que había en caché
                    }
                    fetching.set(false); // ya no estamos fetching
                    String finalIp = cache;
                    SwingUtilities.invokeLater(() -> callback.accept(finalIp));
                }
                // le ponemos nombre al hilo y lo empezamos
            }, "fetch-ip-publica").start();
        } else {
            // ya hay un fetch en curso: esperamos un poco y devolvemos lo que haya
            Timer t = new Timer(150, e -> callback.accept(cache));
            t.setRepeats(false);
            t.start();
        }
    }
}
