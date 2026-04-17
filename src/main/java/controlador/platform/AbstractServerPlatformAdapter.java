package controlador.platform;

import modelo.Server;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

abstract class AbstractServerPlatformAdapter implements ServerPlatformAdapter {
    @Override
    public ServerValidationResult validate(Path serverDir) {
        if (serverDir == null) {
            return ServerValidationResult.error("No se ha indicado el directorio del servidor.");
        }
        if (!Files.isDirectory(serverDir)) {
            return ServerValidationResult.error("No existe la carpeta del servidor.");
        }
        try {
            Path executableJar = resolveExecutableJar(serverDir);
            if (executableJar == null || !Files.isRegularFile(executableJar)) {
                return ServerValidationResult.error("No se ha encontrado un .jar ejecutable valido.");
            }
            if (!MinecraftServerJarInspector.looksLikeMinecraftServerJar(executableJar)) {
                return ServerValidationResult.error("El .jar localizado no parece un servidor de Minecraft.");
            }
            return ServerValidationResult.ok();
        } catch (IOException e) {
            return ServerValidationResult.error("No se ha podido validar el servidor: " + e.getMessage());
        }
    }

    @Override
    public void install(Server server, ServerInstallationRequest request) throws IOException {
        throw new UnsupportedOperationException("La instalacion automatica para " + getPlatform().getLegacyTypeName() + " aun no esta implementada.");
    }

    @Override
    public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
        int ramInicial = server.getServerConfig().getRamInit();
        int ramMaxima = server.getServerConfig().getRamMax();
        ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                "-Xms" + ramInicial + "M",
                "-Xmx" + ramMaxima + "M",
                "-jar",
                executableJar.toString(),
                "nogui"
        );
        processBuilder.directory(Path.of(server.getServerDir()).toFile());
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    @Override
    public Path resolveExecutableJar(Path serverDir) throws IOException {
        return ServerJarLocator.findExecutableJar(serverDir, getPlatform());
    }

    protected ServerPlatformProfile buildProfile(Path serverDir, String loaderVersion) {
        try {
            Path executableJar = resolveExecutableJar(serverDir);
            String minecraftVersion = MinecraftServerJarInspector.readMinecraftVersion(executableJar);
            List<Path> extensionDirectories = getExtensionDirectories(serverDir);
            return new ServerPlatformProfile(
                    getPlatform(),
                    getLoader(),
                    loaderVersion,
                    minecraftVersion,
                    getEcosystemType(),
                    getCapabilities(),
                    extensionDirectories,
                    executableJar
            );
        } catch (IOException e) {
            return null;
        }
    }

    protected boolean exists(Path serverDir, String fileName) {
        return serverDir != null && Files.exists(serverDir.resolve(fileName));
    }

    protected boolean existsDirectory(Path serverDir, String fileName) {
        return serverDir != null && Files.isDirectory(serverDir.resolve(fileName));
    }
}
