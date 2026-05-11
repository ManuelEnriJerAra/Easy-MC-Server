package controlador.extensions;

import controlador.GestorConfiguracion;
import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class CurseForgeModpackService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String OVERRIDES_DIR = "overrides/";
    private static final String CURSEFORGE_API_BASE = "https://api.curseforge.com/v1";

    private final ExtensionHttpClient httpClient = new ExtensionHttpClient();

    public ExportResult exportServerPack(Server server, Path targetZip, ExportMode mode) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor a exportar.");
        }
        if (targetZip == null) {
            throw new IOException("No se ha indicado el archivo de destino.");
        }

        String minecraftVersion = normalize(server.getVersion());
        if (minecraftVersion == null) {
            throw new IOException("El servidor no tiene una version de Minecraft conocida.");
        }

        String loaderId = buildLoaderId(server);
        if (loaderId == null) {
            throw new IOException("No se ha podido determinar el loader exacto del modpack.");
        }

        List<ManifestFileRef> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ServerExtension extension : safeExtensions(server)) {
            if (extension == null) {
                continue;
            }
            ExtensionSource source = extension.getSource();
            if (source == null || source.getType() != ExtensionSourceType.CURSEFORGE) {
                skipped.add(displayName(extension) + ": no tiene un origen CurseForge compatible con manifest.");
                continue;
            }
            String projectId = normalize(source.getProjectId());
            String versionId = normalize(source.getVersionId());
            if (projectId == null || versionId == null || !isDigits(projectId) || !isDigits(versionId)) {
                skipped.add(displayName(extension) + ": faltan projectID/fileID de CurseForge.");
                continue;
            }
            if (!shouldIncludeInExport(extension, mode)) {
                continue;
            }
            String key = projectId + ":" + versionId;
            if (!seen.add(key)) {
                continue;
            }
            files.add(new ManifestFileRef(Integer.parseInt(projectId), Integer.parseInt(versionId), true));
        }

        Map<String, Object> root = new HashMap<>();
        root.put("minecraft", Map.of(
                "version", minecraftVersion,
                "modLoaders", List.of(Map.of("id", loaderId, "primary", true))
        ));
        root.put("manifestType", "minecraftModpack");
        root.put("manifestVersion", 1);
        root.put("name", safePackName(server));
        root.put("version", "1.0.0");
        root.put("author", "Easy-MC-Server");
        root.put("files", files);
        root.put("overrides", "overrides");

        if (targetZip.getParent() != null) {
            Files.createDirectories(targetZip.getParent());
        }
        try (OutputStream output = Files.newOutputStream(targetZip);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_FILE));
            zip.write(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry(OVERRIDES_DIR));
            zip.closeEntry();
        }

        return new ExportResult(targetZip, files.size(), List.copyOf(skipped));
    }

    public ImportManifest readManifest(Path sourceZip) throws IOException {
        if (sourceZip == null || !Files.isRegularFile(sourceZip)) {
            throw new IOException("No se ha encontrado el archivo del modpack.");
        }
        try (ZipFile zipFile = new ZipFile(sourceZip.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_FILE);
            if (manifestEntry == null) {
                throw new IOException("El archivo no contiene manifest.json.");
            }
            try (InputStream input = zipFile.getInputStream(manifestEntry)) {
                JsonNode root = OBJECT_MAPPER.readTree(input);
                String manifestType = text(root, "manifestType");
                if (!"minecraftModpack".equalsIgnoreCase(defaultString(manifestType, ""))) {
                    throw new IOException("El manifest no corresponde a un modpack de Minecraft compatible.");
                }
                JsonNode minecraft = root.path("minecraft");
                List<String> modLoaders = new ArrayList<>();
                JsonNode modLoadersNode = minecraft.path("modLoaders");
                if (modLoadersNode.isArray()) {
                    for (JsonNode loaderNode : modLoadersNode) {
                        String id = text(loaderNode, "id");
                        if (id != null && !id.isBlank()) {
                            modLoaders.add(id.trim());
                        }
                    }
                }
                List<ManifestFileRef> files = new ArrayList<>();
                JsonNode filesNode = root.path("files");
                if (filesNode.isArray()) {
                    for (JsonNode fileNode : filesNode) {
                        int projectId = fileNode.path("projectID").asInt(0);
                        int fileId = fileNode.path("fileID").asInt(0);
                        boolean required = !fileNode.has("required") || fileNode.path("required").asBoolean(true);
                        if (projectId > 0 && fileId > 0) {
                            files.add(new ManifestFileRef(projectId, fileId, required));
                        }
                    }
                }
                return new ImportManifest(
                        defaultString(text(root, "name"), sourceZip.getFileName().toString()),
                        text(root.path("minecraft"), "version"),
                        List.copyOf(modLoaders),
                        List.copyOf(files)
                );
            }
        }
    }

    public ExtensionDownloadPlan resolveDownloadPlan(int projectId, int fileId, Server server) throws IOException {
        String apiKey = GestorConfiguracion.getCurseForgeApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Se necesita una API key de CurseForge para importar modpacks manifest.");
        }
        Map<String, String> headers = Map.of("x-api-key", apiKey.trim());
        JsonNode fileRoot = OBJECT_MAPPER.readTree(httpClient.get(
                java.net.URI.create(CURSEFORGE_API_BASE + "/mods/" + projectId + "/files/" + fileId),
                headers
        ));
        JsonNode modRoot = OBJECT_MAPPER.readTree(httpClient.get(
                java.net.URI.create(CURSEFORGE_API_BASE + "/mods/" + projectId),
                headers
        ));
        JsonNode fileNode = fileRoot.path("data");
        JsonNode modNode = modRoot.path("data");

        String downloadUrl = text(fileNode, "downloadUrl");
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IOException("CurseForge no ha devuelto una URL de descarga para el archivo " + fileId + ".");
        }

        String minecraftConstraint = normalize(server == null ? null : server.getVersion());
        return new ExtensionDownloadPlan(
                "curseforge",
                Integer.toString(projectId),
                Integer.toString(fileId),
                firstNonBlank(text(modNode, "name"), text(fileNode, "displayName"), text(fileNode, "fileName")),
                firstAuthorName(modNode),
                firstNonBlank(text(modNode, "summary"), text(fileNode, "displayName")),
                defaultString(text(fileNode, "displayName"), text(fileNode, "fileName")),
                firstNonBlank(
                        text(modNode.path("logo"), "thumbnailUrl"),
                        text(modNode.path("logo"), "url")
                ),
                text(fileNode, "fileName"),
                downloadUrl,
                text(modNode.path("links"), "websiteUrl"),
                text(modNode.path("links"), "issuesUrl"),
                text(modNode.path("links"), "wikiUrl"),
                null,
                longValue(modNode, "downloadCount"),
                "unknown",
                "unknown",
                Set.of(),
                ExtensionSourceType.CURSEFORGE,
                modelo.extensions.ServerExtensionType.MOD,
                server == null ? modelo.extensions.ServerPlatform.UNKNOWN : server.getPlatform(),
                minecraftConstraint,
                true,
                "Descarga resuelta desde un manifest de CurseForge.",
                List.of()
        );
    }

    private String firstAuthorName(JsonNode modNode) {
        JsonNode authors = modNode == null ? null : modNode.path("authors");
        if (authors == null || !authors.isArray() || authors.isEmpty()) {
            return null;
        }
        JsonNode first = authors.get(0);
        return first == null ? null : text(first, "name");
    }

    private boolean shouldIncludeInExport(ServerExtension extension, ExportMode mode) {
        SideClassification side = classifySide(extension);
        return switch (mode) {
            case SERVER -> side != SideClassification.CLIENT_ONLY;
            case CLIENT -> side != SideClassification.SERVER_ONLY;
            case COMPLETE -> true;
        };
    }

    private SideClassification classifySide(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        String updateMessage = metadata == null ? null : metadata.getUpdateMessage();
        String description = extension == null ? null : extension.getDescription();
        String combined = (defaultString(updateMessage, "") + "\n" + defaultString(description, "")).toLowerCase(Locale.ROOT);
        if (combined.contains("client only") || combined.contains("solo cliente") || combined.contains("cliente solamente")) {
            return SideClassification.CLIENT_ONLY;
        }
        if (combined.contains("server only") || combined.contains("solo servidor") || combined.contains("servidor solamente")) {
            return SideClassification.SERVER_ONLY;
        }
        return SideClassification.BOTH_OR_UNKNOWN;
    }

    private List<ServerExtension> safeExtensions(Server server) {
        return server == null || server.getExtensions() == null ? List.of() : List.copyOf(server.getExtensions());
    }

    private String buildLoaderId(Server server) {
        if (server == null || server.getLoader() == null || server.getLoaderVersion() == null || server.getLoaderVersion().isBlank()) {
            return null;
        }
        ServerLoader loader = server.getLoader();
        String prefix = switch (loader) {
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case FABRIC -> "fabric";
            case QUILT -> "quilt";
            default -> null;
        };
        if (prefix == null) {
            return null;
        }
        return prefix + "-" + server.getLoaderVersion().trim();
    }

    private String safePackName(Server server) {
        String name = server == null ? null : server.getDisplayName();
        if (name == null || name.isBlank()) {
            return "Easy MC Server Pack";
        }
        return name.trim();
    }

    private String displayName(ServerExtension extension) {
        if (extension == null || extension.getDisplayName() == null || extension.getDisplayName().isBlank()) {
            return "Extension";
        }
        return extension.getDisplayName().trim();
    }

    private boolean isDigits(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private long longValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return 0L;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? 0L : value.asLong(0L);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public enum ExportMode {
        SERVER,
        CLIENT,
        COMPLETE
    }

    private enum SideClassification {
        CLIENT_ONLY,
        SERVER_ONLY,
        BOTH_OR_UNKNOWN
    }

    public record ExportResult(Path archivePath, int exportedFiles, List<String> skippedEntries) {
    }

    public record ImportManifest(String name, String minecraftVersion, List<String> modLoaders, List<ManifestFileRef> files) {
    }

    public record ManifestFileRef(int projectID, int fileID, boolean required) {
    }
}
