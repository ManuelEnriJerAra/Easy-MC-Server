package controlador.platform;

import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class BukkitServerPlatformAdapter extends AbstractServerPlatformAdapter {
    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.BUKKIT;
    }

    @Override
    public int getDetectionPriority() {
        return 100;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasBukkitFiles = exists(serverDir, "bukkit.yml")
                || exists(serverDir, "commands.yml")
                || existsDirectory(serverDir, "plugins");
        boolean hasBukkitJarMarkers = MinecraftServerJarInspector.looksLikeBukkitServerJar(executableJar);
        if (!hasBukkitFiles && !hasBukkitJarMarkers) {
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
        return ServerPlatform.BUKKIT.defaultCapabilities();
    }

    @Override
    public String getCreationUnavailableReason() {
        return "Bukkit/CraftBukkit no ofrece una descarga oficial directa y segura; se puede importar y detectar una instalación existente.";
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (Exception e) {
            return null;
        }
    }
}
