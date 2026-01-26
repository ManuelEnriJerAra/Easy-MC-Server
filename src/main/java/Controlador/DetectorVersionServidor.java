/*
 * Fichero: DetectorVersionServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 16/01/2026
 *
 * Descripción:
 * Esta clase engloba todos los métodos necesarios para determinar cuál es la versión en un servidor de Minecraft.
 * Contiene varios métodos para comprobarlo, siendo válido para servidores de antes y después de la 1.14
 * */

package Controlador;

import Modelo.Server;
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

    public static String detectarVersionVanilla(Server server){
        Path jarString = Utilidades.encontrarEjecutableJar(Path.of(server.getServerDir()));

        if(jarString != null){
            try{
                System.out.println("Jar String: "+jarString);
                JarFile jar = new JarFile(jarString.toFile());
                JarEntry jarEntry = jar.getJarEntry("version.json");
                if(jarEntry != null){
                    InputStream in = jar.getInputStream(jarEntry);
                    String version = leerVersionJSON(in);
                    in.close();
                    System.out.println("EL SERVIDOR "+server.getId()+" tiene version "+version);
                    if(version != null){
                        return version;
                    }
                }
                JarEntry jarEntry2 = jar.getJarEntry("net/minecraft/server/MinecraftServer.class");
                if(jarEntry2 != null){
                    InputStream in2 = jar.getInputStream(jarEntry2);
                    return leerVersionCLASS(in2);
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
