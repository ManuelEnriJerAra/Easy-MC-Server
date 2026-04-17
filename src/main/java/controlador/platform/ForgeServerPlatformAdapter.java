package controlador.platform;

import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class ForgeServerPlatformAdapter extends AbstractServerPlatformAdapter {
    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.FORGE;
    }

    @Override
    public int getDetectionPriority() {
        return 80;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasForgeDirectories = existsDirectory(serverDir, "mods")
                && (existsDirectory(serverDir, "libraries")
                || exists(serverDir, "unix_args.txt")
                || exists(serverDir, "win_args.txt")
                || exists(serverDir, "run.sh")
                || exists(serverDir, "run.bat"));
        boolean hasForgeJarMarkers = MinecraftServerJarInspector.looksLikeForgeServerJar(executableJar);
        if (!hasForgeDirectories && !hasForgeJarMarkers) {
            return null;
        }
        ServerValidationResult validation = validate(serverDir);
        return validation.valid() ? buildProfile(serverDir, null) : null;
    }

    @Override
    public List<Path> getExtensionDirectories(Path serverDir) {
        return serverDir == null ? List.of() : List.of(serverDir.resolve("mods"));
    }

    @Override
    public Set<ServerCapability> getCapabilities() {
        return ServerPlatform.FORGE.defaultCapabilities();
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (RuntimeException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
