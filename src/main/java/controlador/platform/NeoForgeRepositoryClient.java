package controlador.platform;

import modelo.extensions.ServerPlatform;

import java.io.ByteArrayInputStream;
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

    List<ServerCreationOption> listCreationOptions() throws IOException {
        byte[] metadata = PlatformRemoteLookupPolicy.getBytes(
                METADATA_URI.toString(),
                () -> {
                    try (InputStream in = openMetadataStream()) {
                        return in.readAllBytes();
                    }
                }
        );
        List<String> artifactVersions = ForgeRepositoryClient.parseArtifactVersions(new ByteArrayInputStream(metadata));

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
        String releasePart = artifactVersion.trim();
        int qualifierIndex = releasePart.indexOf('-');
        if (qualifierIndex > 0) {
            releasePart = releasePart.substring(0, qualifierIndex);
        }
        String[] parts = releasePart.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int minecraftPatch = Integer.parseInt(parts[1]);
            if (parts.length >= 4) {
                Integer third = parseInt(parts[2]);
                return third == null ? null : first + "." + minecraftPatch + "." + third;
            }

            Integer third = parts.length >= 3 ? parseInt(parts[2]) : null;
            if (first >= 2 && third != null && third < 100) {
                return first + "." + minecraftPatch + "." + third;
            }

            int minecraftMinor = first;
            return minecraftPatch <= 0 ? "1." + minecraftMinor : "1." + minecraftMinor + "." + minecraftPatch;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected InputStream openMetadataStream() throws IOException {
        URLConnection connection = METADATA_URI.toURL().openConnection();
        connection.setConnectTimeout(PlatformRemoteLookupPolicy.CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(PlatformRemoteLookupPolicy.READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", PlatformRemoteLookupPolicy.USER_AGENT);
        return connection.getInputStream();
    }
}
