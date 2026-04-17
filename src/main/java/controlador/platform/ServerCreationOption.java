package controlador.platform;

import modelo.extensions.ServerPlatform;

public record ServerCreationOption(
        ServerPlatform platform,
        String minecraftVersion,
        String platformVersion,
        String displayName,
        String directoryName
) {
}
