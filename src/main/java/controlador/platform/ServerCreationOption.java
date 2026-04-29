package controlador.platform;

import modelo.extensions.ServerPlatform;

public record ServerCreationOption(
        ServerPlatform platform,
        String minecraftVersion,
        String platformVersion,
        String displayName,
        String directoryName,
        String versionType
) {
    public ServerCreationOption(
            ServerPlatform platform,
            String minecraftVersion,
            String platformVersion,
            String displayName,
            String directoryName
    ) {
        this(platform, minecraftVersion, platformVersion, displayName, directoryName, null);
    }

    public boolean isSnapshot() {
        return "snapshot".equalsIgnoreCase(versionType);
    }

    public boolean isRelease() {
        return versionType == null || versionType.isBlank() || "release".equalsIgnoreCase(versionType);
    }
}
