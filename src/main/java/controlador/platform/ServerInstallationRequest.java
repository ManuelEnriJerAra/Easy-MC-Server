package controlador.platform;

import controlador.MojangAPI;

import java.nio.file.Path;

public record ServerInstallationRequest(
        Path targetDirectory,
        String minecraftVersion,
        boolean acceptEula,
        Path defaultIconSource,
        MojangAPI mojangApi
) {
}
