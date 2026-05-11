package controlador.platform;

import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class SpigotServerPlatformAdapter extends AbstractServerPlatformAdapter {
    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.SPIGOT;
    }

    @Override
    public int getDetectionPriority() {
        return 110;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasSpigotFiles = exists(serverDir, "spigot.yml");
        boolean hasSpigotJarMarkers = MinecraftServerJarInspector.looksLikeSpigotServerJar(executableJar);
        if (!hasSpigotFiles && !hasSpigotJarMarkers) {
            return null;
        }
        ServerValidationResult validation = validate(serverDir);
        return validation.valid() ? buildProfile(serverDir, null) : null;
    }

    @Override
    public List<Path> getExtensionDirectories(Path serverDir) {
        return serverDir == null ? List.of() : List.of(serverDir.resolve("plugins"));
    }

    @Override
    public Set<ServerCapability> getCapabilities() {
        return ServerPlatform.SPIGOT.defaultCapabilities();
    }

    @Override
    public String getCreationUnavailableReason() {
        return "Spigot requiere BuildTools y no ofrece una descarga oficial directa de servidor; se puede importar y detectar una instalacion existente.";
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (Exception e) {
            return null;
        }
    }
}
