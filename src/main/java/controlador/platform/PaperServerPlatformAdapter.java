package controlador.platform;

import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class PaperServerPlatformAdapter extends AbstractServerPlatformAdapter {
    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.PAPER;
    }

    @Override
    public int getDetectionPriority() {
        return 100;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        if (!exists(serverDir, "paper.yml")) {
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
        return ServerPlatform.PAPER.defaultCapabilities();
    }
}
