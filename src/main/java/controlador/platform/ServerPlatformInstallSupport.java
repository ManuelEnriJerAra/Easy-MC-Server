package controlador.platform;

import controlador.Utilidades;
import modelo.Server;
import modelo.ServerProperties;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

final class ServerPlatformInstallSupport {
    private ServerPlatformInstallSupport() {
    }

    static void requireServerAndRequest(Server server, ServerInstallationRequest request, String platformVersionName) throws IOException {
        if (server == null) {
            throw new IOException("El servidor no es valido.");
        }
        if (request == null || request.targetDirectory() == null) {
            throw new IOException("No se ha indicado la carpeta de instalacion.");
        }
        if (request.minecraftVersion() == null || request.minecraftVersion().isBlank()) {
            throw new IOException("No se ha indicado la versión de Minecraft.");
        }
        if (platformVersionName != null && !platformVersionName.isBlank()
                && (request.platformVersion() == null || request.platformVersion().isBlank())) {
            throw new IOException("No se ha indicado la versión de " + platformVersionName + ".");
        }
        if (request.downloader() == null) {
            throw new IOException("No se ha proporcionado un descargador de archivos.");
        }
    }

    static void prepareCommonFiles(Server server,
                                   ServerInstallationRequest request,
                                   ServerPlatform platform,
                                   String loaderVersion,
                                   String motd,
                                   List<Path> extensionDirectories) throws IOException {
        Files.createDirectories(request.targetDirectory());
        if (extensionDirectories != null) {
            for (Path extensionDirectory : extensionDirectories) {
                if (extensionDirectory != null) {
                    Files.createDirectories(extensionDirectory);
                }
            }
        }

        if (request.acceptEula()) {
            Utilidades.rellenaEULA(request.targetDirectory().toFile());
        }
        if (request.defaultIconSource() != null && Files.isRegularFile(request.defaultIconSource())) {
            Utilidades.copiarArchivo(request.defaultIconSource().toFile(), request.targetDirectory().resolve("server-icon.png").toFile());
        }
        ensureDefaultServerProperties(request.targetDirectory());
        Utilidades.escribirPuertoEnProperties(request.targetDirectory(), 25565);
        if (motd != null && !motd.isBlank()) {
            Utilidades.escribirMotdEnProperties(request.targetDirectory(), motd);
        }

        server.setServerDir(request.targetDirectory().toString());
        server.setVersion(request.minecraftVersion());
        server.setLoaderVersion(loaderVersion);
        server.setPlatform(platform);
    }

    private static void ensureDefaultServerProperties(Path targetDirectory) throws IOException {
        if (targetDirectory == null) {
            return;
        }
        Path propertiesPath = targetDirectory.resolve("server.properties");
        Properties merged = new ServerProperties().toProperties();
        if (Files.isRegularFile(propertiesPath)) {
            Properties existing = Utilidades.cargarPropertiesUtf8(propertiesPath);
            merged.putAll(existing);
        }
        Utilidades.guardarPropertiesUtf8(propertiesPath, merged, null);
    }
}
