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
        if (!existsDirectory(serverDir, "mods") || !existsDirectory(serverDir, "libraries")) {
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
}
