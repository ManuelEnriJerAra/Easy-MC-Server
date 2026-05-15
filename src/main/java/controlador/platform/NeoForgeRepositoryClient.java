package controlador.platform;

import modelo.extensions.ServerPlatform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
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

        Map<String, String> latestReleaseByMinecraftVersion = new LinkedHashMap<>();
        Map<String, String> latestFallbackReleaseByMinecraftVersion = new LinkedHashMap<>();
        Map<String, List<String>> snapshotArtifactsByMinecraftVersion = new LinkedHashMap<>();
        for (String artifactVersion : artifactVersions) {
            String minecraftVersion = inferMinecraftVersion(artifactVersion);
            if (minecraftVersion == null || minecraftVersion.isBlank()) {
                continue;
            }
            String minecraftVersionType = versionType(minecraftVersion);
            if (ServerCreationOption.VERSION_TYPE_RELEASE.equals(minecraftVersionType)) {
                if (isStableLoaderArtifact(artifactVersion)) {
                    String current = latestReleaseByMinecraftVersion.get(minecraftVersion);
                    if (current == null || VersionStringComparator.compareVersionStrings(artifactVersion, current) > 0) {
                        latestReleaseByMinecraftVersion.put(minecraftVersion, artifactVersion);
                    }
                } else {
                    String current = latestFallbackReleaseByMinecraftVersion.get(minecraftVersion);
                    if (current == null || VersionStringComparator.compareVersionStrings(artifactVersion, current) > 0) {
                        latestFallbackReleaseByMinecraftVersion.put(minecraftVersion, artifactVersion);
                    }
                }
            } else {
                snapshotArtifactsByMinecraftVersion.computeIfAbsent(minecraftVersion, key -> new ArrayList<>()).add(artifactVersion);
            }
        }

        List<String> minecraftVersions = new ArrayList<>();
        latestReleaseByMinecraftVersion.keySet().forEach(version -> addIfMissing(minecraftVersions, version));
        latestFallbackReleaseByMinecraftVersion.keySet().forEach(version -> addIfMissing(minecraftVersions, version));
        snapshotArtifactsByMinecraftVersion.keySet().forEach(version -> addIfMissing(minecraftVersions, version));
        minecraftVersions.sort(VersionStringComparator.minecraftVersionsDescending());

        List<ServerCreationOption> options = new ArrayList<>();
        for (String minecraftVersion : minecraftVersions) {
            String release = latestReleaseByMinecraftVersion.getOrDefault(
                    minecraftVersion,
                    latestFallbackReleaseByMinecraftVersion.get(minecraftVersion)
            );
            addOption(options, minecraftVersion, release);
            List<String> snapshotArtifacts = snapshotArtifactsByMinecraftVersion.getOrDefault(minecraftVersion, List.of());
            snapshotArtifacts.stream()
                    .sorted(VersionStringComparator.descending())
                    .forEach(artifactVersion -> addOption(options, minecraftVersion, artifactVersion));
        }
        return options;
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
        String metadata = null;
        int metadataIndex = releasePart.indexOf('+');
        if (metadataIndex >= 0) {
            metadata = releasePart.substring(metadataIndex + 1);
            releasePart = releasePart.substring(0, metadataIndex);
        }
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
            String snapshotTarget = snapshotMinecraftVersionFromMetadata(first, minecraftPatch, parts, metadata);
            if (snapshotTarget != null) {
                return snapshotTarget;
            }
            if (parts.length >= 4) {
                Integer third = parseInt(parts[2]);
                return third == null ? null : first + "." + minecraftPatch + "." + third;
            }

            Integer third = parts.length >= 3 ? parseInt(parts[2]) : null;
            if (first >= 22 && third != null && third < 100) {
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

    private static void addIfMissing(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static String snapshotMinecraftVersionFromMetadata(int first,
                                                               int minecraftPatch,
                                                               String[] parts,
                                                               String metadata) {
        if (metadata == null || metadata.isBlank() || parts.length < 3) {
            return null;
        }
        Integer third = parseInt(parts[2]);
        if (third == null || third != 0) {
            return null;
        }
        String normalized = metadata.toLowerCase(java.util.Locale.ROOT);
        String[] channels = {"snapshot", "pre", "rc"};
        for (String channel : channels) {
            int index = normalized.indexOf(channel);
            if (index < 0) {
                continue;
            }
            int numberStart = index + channel.length();
            while (numberStart < normalized.length()
                    && !Character.isDigit(normalized.charAt(numberStart))) {
                numberStart++;
            }
            int numberEnd = numberStart;
            while (numberEnd < normalized.length()
                    && Character.isDigit(normalized.charAt(numberEnd))) {
                numberEnd++;
            }
            if (numberEnd > numberStart) {
                return first + "." + minecraftPatch + "-" + channel + "-" + normalized.substring(numberStart, numberEnd);
            }
        }
        return null;
    }

    private static void addOption(List<ServerCreationOption> options, String minecraftVersion, String artifactVersion) {
        if (artifactVersion == null || artifactVersion.isBlank()) {
            return;
        }
        options.add(new ServerCreationOption(
                ServerPlatform.NEOFORGE,
                minecraftVersion,
                artifactVersion,
                "Minecraft " + minecraftVersion + " (NeoForge " + artifactVersion + ")",
                "neoforge-" + minecraftVersion + "-server",
                versionType(minecraftVersion)
        ));
    }

    private static String versionType(String artifactVersion) {
        return ServerCreationOption.versionTypeFromText(artifactVersion);
    }

    private static boolean isStableLoaderArtifact(String artifactVersion) {
        return ServerCreationOption.VERSION_TYPE_RELEASE.equals(versionType(artifactVersion));
    }
}
