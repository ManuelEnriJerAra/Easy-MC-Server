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
    public static final String VERSION_TYPE_RELEASE = "release";
    public static final String VERSION_TYPE_SNAPSHOT = "snapshot";

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
        return VERSION_TYPE_SNAPSHOT.equalsIgnoreCase(versionType);
    }

    public boolean isRelease() {
        return versionType == null || versionType.isBlank() || VERSION_TYPE_RELEASE.equalsIgnoreCase(versionType);
    }

    public static String versionTypeFromStability(boolean stable) {
        return stable ? VERSION_TYPE_RELEASE : VERSION_TYPE_SNAPSHOT;
    }

    public static String versionTypeFromText(String value) {
        if (value == null || value.isBlank()) {
            return VERSION_TYPE_RELEASE;
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches(".*(^|[^a-z0-9])(snapshot|alpha|beta|experimental|dev)([^a-z0-9]|$).*")
                || normalized.matches(".*(^|[^a-z0-9])(pre|rc)\\d*([^a-z0-9]|$).*")
                || normalized.matches(".*\\d{2}w\\d{2}[a-z].*")) {
            return VERSION_TYPE_SNAPSHOT;
        }
        return VERSION_TYPE_RELEASE;
    }

    public static boolean isCompatibleMinecraftVersion(String optionMinecraftVersion, String requestedMinecraftVersion) {
        String option = canonicalMinecraftVersion(optionMinecraftVersion);
        String requested = canonicalMinecraftVersion(requestedMinecraftVersion);
        return option != null && option.equals(requested);
    }

    public static String canonicalMinecraftVersion(String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            return minecraftVersion;
        }
        String normalized = minecraftVersion.trim()
                .replace('_', '-')
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.replaceFirst("(?i)-(snapshot|pre|rc)-?(\\d+)$", "-$1-$2");
    }
}
