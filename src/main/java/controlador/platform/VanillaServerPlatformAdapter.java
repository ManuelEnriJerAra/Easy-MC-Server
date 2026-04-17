package controlador.platform;

import controlador.MojangAPI;
import controlador.Utilidades;
import modelo.Server;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VanillaServerPlatformAdapter extends AbstractServerPlatformAdapter {
    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.VANILLA;
    }

    @Override
    public int getDetectionPriority() {
        return -100;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return null;
        }
        ServerValidationResult validation = validate(serverDir);
        return validation.valid() ? buildProfile(serverDir, null) : null;
    }

    @Override
    public void install(Server server, ServerInstallationRequest request) throws IOException {
        if (server == null) {
            throw new IOException("El servidor no es valido.");
        }
        if (request == null || request.targetDirectory() == null) {
            throw new IOException("No se ha indicado la carpeta de instalacion.");
        }
        if (request.minecraftVersion() == null || request.minecraftVersion().isBlank()) {
            throw new IOException("No se ha indicado la version de Minecraft.");
        }
        MojangAPI mojangApi = request.mojangApi();
        if (mojangApi == null) {
            throw new IOException("No se ha proporcionado acceso a Mojang API.");
        }

        Files.createDirectories(request.targetDirectory());
        String url = mojangApi.obtenerUrlServerJar(request.minecraftVersion());
        if (url == null || url.isBlank()) {
            throw new IOException("No se ha podido obtener la URL del servidor para la version " + request.minecraftVersion());
        }

        Path destinationJar = request.targetDirectory().resolve(request.minecraftVersion() + "_server.jar");
        if (!Files.isRegularFile(destinationJar)) {
            mojangApi.descargar(url, destinationJar.toFile(), null);
        }

        if (request.acceptEula()) {
            Utilidades.rellenaEULA(request.targetDirectory().toFile());
        }
        if (request.defaultIconSource() != null && Files.isRegularFile(request.defaultIconSource())) {
            Utilidades.copiarArchivo(request.defaultIconSource().toFile(), request.targetDirectory().resolve("server-icon.png").toFile());
        }

        server.setServerDir(request.targetDirectory().toString());
        server.setVersion(request.minecraftVersion());
        server.setPlatform(ServerPlatform.VANILLA);
    }
}
