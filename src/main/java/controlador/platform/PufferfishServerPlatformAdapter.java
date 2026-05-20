package controlador.platform;

import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class PufferfishServerPlatformAdapter extends AbstractServerPlatformAdapter {
    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.PUFFERFISH;
    }

    @Override
    public int getDetectionPriority() {
        return 140;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasPufferfishFiles = exists(serverDir, "pufferfish.yml")
                || exists(serverDir, "pufferfish.conf");
        boolean hasPufferfishJarMarkers = MinecraftServerJarInspector.looksLikePufferfishServerJar(executableJar);
        if (!hasPufferfishFiles && !hasPufferfishJarMarkers) {
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
        return ServerPlatform.PUFFERFISH.defaultCapabilities();
    }

    @Override
    public String getCreationUnavailableReason() {
        return "Pufferfish no mantiene un endpoint público estable equivalente a Paper/Purpur para instalaciones automatizadas; se puede importar y detectar una instalación existente.";
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (Exception e) {
            return null;
        }
    }
}
