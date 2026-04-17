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

import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import modelo.Server;

import java.nio.file.Path;

public class DetectorVersionServidor {

    // Detectamos la versión de un servidor vanilla
    public static String detectarVersionVanilla(Server server){
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        try {
            ServerPlatformProfile profile = ServerPlatformAdapters.detect(Path.of(server.getServerDir()));
            if (profile != null && profile.minecraftVersion() != null && !profile.minecraftVersion().isBlank()) {
                return profile.minecraftVersion();
            }
        } catch (RuntimeException e) {
            System.out.println("No se ha podido detectar la version del servidor: " + e.getMessage());
            return null;
        }
        return null;
    }
}

