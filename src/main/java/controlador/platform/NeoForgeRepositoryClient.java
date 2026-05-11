package controlador.platform;

import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class NeoForgeRepositoryClient {
    private static final URI METADATA_URI = URI.create("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
    private static final String BASE_MAVEN_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    List<ServerCreationOption> listCreationOptions() throws IOException {
        List<String> artifactVersions;
        try (InputStream in = openMetadataStream()) {
            artifactVersions = ForgeRepositoryClient.parseArtifactVersions(in);
        }

        Map<String, String> latestByMinecraftVersion = new LinkedHashMap<>();
        for (String artifactVersion : artifactVersions) {
            String minecraftVersion = inferMinecraftVersion(artifactVersion);
            if (minecraftVersion == null || minecraftVersion.isBlank()) {
                continue;
            }
            String current = latestByMinecraftVersion.get(minecraftVersion);
            if (current == null || VersionStringComparator.compareVersionStrings(artifactVersion, current) > 0) {
                latestByMinecraftVersion.put(minecraftVersion, artifactVersion);
            }
        }

        return latestByMinecraftVersion.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(VersionStringComparator.descending()))
                .map(entry -> new ServerCreationOption(
                        ServerPlatform.NEOFORGE,
                        entry.getKey(),
                        entry.getValue(),
                        "Minecraft " + entry.getKey() + " (NeoForge " + entry.getValue() + ")",
                        "neoforge-" + entry.getKey() + "-server"
                ))
                .toList();
    }

    String getInstallerUrl(String artifactVersion) {
        if (artifactVersion == null || artifactVersion.isBlank()) {
            return null;
        }
        return BASE_MAVEN_URL + artifactVersion + "/neoforge-" + artifactVersion + "-installer.jar";
    }

    static String inferMinecraftVersion(String artifactVersion) {
        if (artifactVersion == null || artifactVersion.isBlank()) {
            return null;
        }
        if (artifactVersion.contains("-")) {
            return artifactVersion.substring(0, artifactVersion.indexOf('-'));
        }
        String[] parts = artifactVersion.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            int minecraftMinor = Integer.parseInt(parts[0]);
            int minecraftPatch = Integer.parseInt(parts[1]);
            return minecraftPatch <= 0 ? "1." + minecraftMinor : "1." + minecraftMinor + "." + minecraftPatch;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected InputStream openMetadataStream() throws IOException {
        URLConnection connection = METADATA_URI.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Easy-MC-Server/1.0 (+https://github.com/)");
        return connection.getInputStream();
    }
}
