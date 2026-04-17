package controlador.platform;

import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record ServerPlatformProfile(
        ServerPlatform platform,
        ServerLoader loader,
        String loaderVersion,
        String minecraftVersion,
        ServerEcosystemType ecosystemType,
        Set<ServerCapability> capabilities,
        List<Path> extensionDirectories,
        Path executableJar
) {
}
