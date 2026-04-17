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

import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;

import java.nio.file.Files;
import java.nio.file.Path;

public class DetectorTipoServidor {

    public static String detectarTipo(Path serverDir){
        if(serverDir == null) return "VANILLA";
        if(!Files.isDirectory(serverDir)) return "VANILLA";
        ServerPlatformProfile profile = ServerPlatformAdapters.detect(serverDir);
        return profile == null ? "VANILLA" : profile.platform().getLegacyTypeName();
    }
}

