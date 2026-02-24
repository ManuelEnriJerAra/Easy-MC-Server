/*
 * Fichero: DetectorTipoServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Esta clase engloba todos los métodos necesarios para determinar cuál es el tipo de un servidor de Minecraft. Puede
 * ser VANILLA, Forge, Fabric, Paper, Spigot o Bukkit.
 * */

package controlador;

import java.nio.file.Files;
import java.nio.file.Path;

public class DetectorTipoServidor {

    public static String detectarTipo(Path serverDir){
        if(serverDir == null) return "VANILLA";
        if(!Files.isDirectory(serverDir)) return "VANILLA";

        // Paper / Spigot / Bukkit
        // si existen los archivos con el nombre de cada tipo ya sabemos lo que es
        if(existe(serverDir, "paper.yml")) return "PAPER";
        if(existe(serverDir, "spigot.yml")) return "SPIGOT";
        if(existe(serverDir, "bukkit.yml")) return "BUKKIT";

        // Fabric
        // comprobamos si ha modificado el nombre del jar o si existe el fichero .fabric
        if(existe(serverDir, "fabric-server-launch.jar") || existeDirectorio(serverDir, ".fabric")) return "FABRIC";

        // Forge
        // comprobamos si existe la carpeta mods Y si existe el directorio libraries
        if(existeDirectorio(serverDir, "mods") && existeDirectorio(serverDir, "libraries")) return "FORGE";

        // Vanilla (sin indicios de otros tipos)
        return "VANILLA";
    }

    // esta función comprueba si existe un archivo incdicado dentro de una carpeta indicada
    private static boolean existe(Path dir, String name){
        return Files.exists(dir.resolve(name));
    }

    // esta función comprueba si existe un directorio indicado dentro de un directorio indicado
    private static boolean existeDirectorio(Path dir, String name){
        return Files.isDirectory(dir.resolve(name));
    }
}

