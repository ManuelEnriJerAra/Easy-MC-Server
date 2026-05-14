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

public class ForgeRepositoryClient {
    private static final URI METADATA_URI = URI.create("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
    private static final String BASE_MAVEN_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/";

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

        Map<String, String> latestByMinecraftVersion = new LinkedHashMap<>();
        for (String artifactVersion : artifactVersions) {
            if (artifactVersion == null || artifactVersion.isBlank() || !artifactVersion.contains("-")) {
                continue;
            }
            String minecraftVersion = artifactVersion.substring(0, artifactVersion.indexOf('-'));
            String current = latestByMinecraftVersion.get(minecraftVersion);
            if (current == null || VersionStringComparator.compareVersionStrings(artifactVersion, current) > 0) {
                latestByMinecraftVersion.put(minecraftVersion, artifactVersion);
            }
        }

        return latestByMinecraftVersion.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(VersionStringComparator.descending()))
                .map(entry -> new ServerCreationOption(
                        modelo.extensions.ServerPlatform.FORGE,
                        entry.getKey(),
                        entry.getValue(),
                        "Minecraft " + entry.getKey() + " (Forge " + entry.getValue().substring(entry.getValue().indexOf('-') + 1) + ")",
                        "forge-" + entry.getKey() + "-server"
                ))
                .toList();
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
}
