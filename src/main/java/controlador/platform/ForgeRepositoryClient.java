package controlador.platform;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForgeRepositoryClient {
    private static final URI METADATA_URI = URI.create("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
    private static final String BASE_MAVEN_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String FIRST_MINECRAFT_VERSION_WITH_INSTALLER = "1.5.2";
    private static final Pattern ARTIFACT_VERSION_PATTERN = Pattern.compile(
            "^(\\d{2}w\\d{2}[a-z]|\\d+(?:\\.\\d+){1,2}(?:[-_](?:snapshot|pre|rc)[-_]?\\d+)?)-(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    public List<ServerCreationOption> listCreationOptions() throws IOException {
        byte[] metadata = PlatformRemoteLookupPolicy.getBytes(
                METADATA_URI.toString(),
                () -> {
                    try (InputStream in = openMetadataStream()) {
                        return in.readAllBytes();
                    }
                }
        );
        List<String> artifactVersions = parseArtifactVersions(new ByteArrayInputStream(metadata));

        Map<String, String> displayVersionByMinecraftVersion = new LinkedHashMap<>();
        Map<String, String> latestReleaseByMinecraftVersion = new LinkedHashMap<>();
        Map<String, String> latestFallbackReleaseByMinecraftVersion = new LinkedHashMap<>();
        Map<String, List<String>> snapshotArtifactsByMinecraftVersion = new LinkedHashMap<>();
        for (String artifactVersion : artifactVersions) {
            if (artifactVersion == null || artifactVersion.isBlank() || !artifactVersion.contains("-")) {
                continue;
            }
            String rawMinecraftVersion = minecraftVersion(artifactVersion);
            String minecraftVersion = ServerCreationOption.canonicalMinecraftVersion(rawMinecraftVersion);
            if (minecraftVersion == null || minecraftVersion.isBlank()) {
                continue;
            }
            if (!hasKnownInstallerArtifact(minecraftVersion)) {
                continue;
            }
            displayVersionByMinecraftVersion.putIfAbsent(minecraftVersion, displayMinecraftVersion(rawMinecraftVersion));
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
            String displayMinecraftVersion = displayVersionByMinecraftVersion.getOrDefault(minecraftVersion, minecraftVersion);
            String release = latestReleaseByMinecraftVersion.getOrDefault(
                    minecraftVersion,
                    latestFallbackReleaseByMinecraftVersion.get(minecraftVersion)
            );
            addOption(options, displayMinecraftVersion, release);
            List<String> snapshotArtifacts = snapshotArtifactsByMinecraftVersion.getOrDefault(minecraftVersion, List.of());
            snapshotArtifacts.stream()
                    .sorted(VersionStringComparator.descending())
                    .forEach(artifactVersion -> addOption(options, displayMinecraftVersion, artifactVersion));
        }
        return options;
    }

    public String getInstallerUrl(String artifactVersion) {
        if (artifactVersion == null || artifactVersion.isBlank()) {
            return null;
        }
        return BASE_MAVEN_URL + artifactVersion + "/forge-" + artifactVersion + "-installer.jar";
    }

    protected InputStream openMetadataStream() throws IOException {
        URLConnection connection = METADATA_URI.toURL().openConnection();
        connection.setConnectTimeout(PlatformRemoteLookupPolicy.CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(PlatformRemoteLookupPolicy.READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", PlatformRemoteLookupPolicy.USER_AGENT);
        return connection.getInputStream();
    }

    static List<String> parseArtifactVersions(InputStream inputStream) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(inputStream);
            NodeList versions = document.getElementsByTagName("version");
            List<String> values = new ArrayList<>();
            for (int i = 0; i < versions.getLength(); i++) {
                String value = versions.item(i).getTextContent();
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
            return values;
        } catch (Exception e) {
            throw new IOException("No se ha podido leer el metadata de Forge.", e);
        }
    }

    private static void addIfMissing(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static void addOption(List<ServerCreationOption> options, String minecraftVersion, String artifactVersion) {
        if (artifactVersion == null || artifactVersion.isBlank()) {
            return;
        }
        String forgeVersion = forgeVersion(artifactVersion);
        options.add(new ServerCreationOption(
                modelo.extensions.ServerPlatform.FORGE,
                minecraftVersion,
                artifactVersion,
                "Minecraft " + minecraftVersion + " (Forge " + forgeVersion + ")",
                "forge-" + minecraftVersion + "-server",
                versionType(minecraftVersion)
        ));
    }

    private static String versionType(String artifactVersion) {
        return ServerCreationOption.versionTypeFromText(artifactVersion);
    }

    private static String minecraftVersion(String artifactVersion) {
        ForgeArtifactVersion parsed = parseArtifactVersion(artifactVersion);
        return parsed == null ? null : parsed.minecraftVersion();
    }

    private static String forgeVersion(String artifactVersion) {
        ForgeArtifactVersion parsed = parseArtifactVersion(artifactVersion);
        return parsed == null ? artifactVersion : parsed.forgeVersion();
    }

    private static ForgeArtifactVersion parseArtifactVersion(String artifactVersion) {
        if (artifactVersion == null || artifactVersion.isBlank()) {
            return null;
        }
        Matcher matcher = ARTIFACT_VERSION_PATTERN.matcher(artifactVersion);
        if (!matcher.matches()) {
            return null;
        }
        return new ForgeArtifactVersion(matcher.group(1), matcher.group(2));
    }

    private static String displayMinecraftVersion(String minecraftVersion) {
        return minecraftVersion == null ? null : minecraftVersion.trim().replace('_', '-');
    }

    private static boolean isStableLoaderArtifact(String artifactVersion) {
        return ServerCreationOption.VERSION_TYPE_RELEASE.equals(versionType(forgeVersion(artifactVersion)));
    }

    private static boolean hasKnownInstallerArtifact(String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            return false;
        }
        return !ServerCreationOption.VERSION_TYPE_RELEASE.equals(versionType(minecraftVersion))
                || VersionStringComparator.compareVersionStrings(
                        minecraftVersion,
                        FIRST_MINECRAFT_VERSION_WITH_INSTALLER
                ) >= 0;
    }

    private record ForgeArtifactVersion(String minecraftVersion, String forgeVersion) {
    }
}
