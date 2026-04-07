/*
 * Fichero: DetectorVersionServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Esta clase engloba todos los métodos necesarios para determinar cuál es la versión en un servidor de Minecraft.
 * Contiene varios métodos para comprobarlo, siendo válido para servidores de antes y después de la 1.14
 * */

package controlador;

import modelo.Server;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetectorVersionServidor {

    // Obtenemos la versión guardada en el JSON del JAR del servidor
    private static String leerVersionJSON(InputStream in){
        try{
            InputStreamReader reader = new InputStreamReader(in);
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if(json.has("name")) {
                return json.get("name").getAsString();
            }
        } catch(Exception e){
            e.printStackTrace();
            System.out.println("No se ha podido obtener la versión del servidor");
        }

        return null;
    }

    // Detectamos la versión de un servidor vanilla
    public static String detectarVersionVanilla(Server server){
        Path jarString;
        try{
            // buscamos el jar, si no está no hay servidor
            jarString = Utilidades.encontrarEjecutableJar(Path.of(server.getServerDir()));
        } catch (RuntimeException e){
            System.out.println("No se ha podido acceder al archivo .jar: " + e.getMessage());
            return null;
        }

        if(jarString != null){ // si lo hemos encontrado
            try (JarFile jar = new JarFile(jarString.toFile())) {
                // buscamos si tiene un archivo version.json
                JarEntry jarEntry = jar.getJarEntry("version.json");
                if(jarEntry != null){
                    try (InputStream in = jar.getInputStream(jarEntry)) {
                        String version = leerVersionJSON(in);
                        System.out.println("EL SERVIDOR "+server.getId()+" tiene version "+version);
                        if(version != null){
                            return version;
                        }
                    }
                }
                /*
                 * Mojang empezó a añadir el archivo version.json a partir de la versión 1.14, por lo tanto si el servidor
                 * es de una versión anterior necesitamos leerlo por otro método, que será extrayéndolo de un archivo CLASS
                 */
                JarEntry jarEntry2 = jar.getJarEntry("net/minecraft/server/MinecraftServer.class");
                if(jarEntry2 != null){
                    try (InputStream in2 = jar.getInputStream(jarEntry2)) {
                        return leerVersionCLASS(in2);
                    }
                }
            }
            catch(Exception e){
                e.printStackTrace();
                System.out.println("Ha ocurrido un error accediendo al fichero .jar");
                return null;
            }
        }
        else{
            System.out.println("No se ha podido acceder al archivo .jar");
            return null;
        }
        return null;
    }

    private static String leerVersionCLASS(InputStream in){
        /* en este fichero siempre está la versión escondida entre bits, por lo que vamos a leer todos los bytes
         * y vamos a buscar un patrón que sea XX.XX o XX.XX.XX
         */

        try{
            byte[] bytes = in.readAllBytes();
            String texto = new String(bytes, StandardCharsets.ISO_8859_1);
            Pattern patron = Pattern.compile("([0-9]{1,2}\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?)");
            Matcher m = patron.matcher(texto);
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Ha ocurrido un error leyendo CLASS");
            return null;
        }
    }
}

