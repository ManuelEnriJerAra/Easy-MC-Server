package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class ModrinthExtensionCatalogProvider implements ExtensionCatalogProvider {
    private static final String API_BASE_URL = "https://api.modrinth.com/v2";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExtensionHttpClient httpClient;

    ModrinthExtensionCatalogProvider() {
        this(new ExtensionHttpClient());
    }

    ModrinthExtensionCatalogProvider(ExtensionHttpClient httpClient) {
        this.httpClient = httpClient == null ? new ExtensionHttpClient() : httpClient;
    }

    @Override
    public String getProviderId() {
        return "modrinth";
    }

    @Override
    public String getDisplayName() {
        return "Modrinth";
    }

    @Override
    public ExtensionSourceType getSourceType() {
        return ExtensionSourceType.MODRINTH;
    }

    @Override
    public Set<ExtensionCatalogCapability> getCapabilities() {
        return Set.of(
                ExtensionCatalogCapability.SEARCH,
                ExtensionCatalogCapability.DETAILS,
                ExtensionCatalogCapability.DOWNLOAD,
                ExtensionCatalogCapability.UPDATES
        );
    }

    @Override
    public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
        ExtensionCatalogQuery normalized = query == null
                ? new ExtensionCatalogQuery(null, null, null, null, 20)
                : query;
        URI uri = URI.create(API_BASE_URL + "/search?" + buildSearchQueryString(normalized));
        JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
        JsonNode hits = root == null ? null : root.path("hits");
        if (hits == null || !hits.isArray()) {
            return List.of();
        }

        List<ExtensionCatalogEntry> entries = new ArrayList<>();
        for (JsonNode hit : hits) {
            ExtensionCatalogEntry entry = toSearchEntry(hit, normalized);
            if (entry == null) {
                continue;
            }
            if (!matchesPlatform(normalized, entry)) {
                continue;
            }
            if (!matchesMinecraftVersion(normalized, entry)) {
                continue;
            }
            entries.add(entry);
        }
        return entries.stream()
                .limit(normalized.limit())
                .toList();
    }

    @Override
    public Optional<ExtensionCatalogDetails> getDetails(String projectId,
                                                        ExtensionCatalogQuery query) throws IOException {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        JsonNode projectNode = OBJECT_MAPPER.readTree(httpClient.get(projectUri(projectId), null));
        if (projectNode == null || projectNode.isMissingNode()) {
            return Optional.empty();
        }

        ExtensionCatalogQuery normalized = query == null
                ? new ExtensionCatalogQuery(null, null, null, null, 20)
                : query;
        List<ExtensionCatalogVersion> versions = fetchVersions(projectId, normalized);
        ExtensionCatalogEntry baseEntry = toProjectEntry(projectNode, versions);
        return Optional.of(new ExtensionCatalogDetails(
                baseEntry,
                text(projectNode, "body"),
                text(projectNode, "issues_url"),
                text(projectNode, "source_url"),
                nestedText(projectNode, "license", "name"),
                setOfStrings(projectNode.path("categories")),
                versions
        ));
    }

    @Override
    public Optional<ExtensionDownloadPlan> resolveDownload(String projectId,
                                                           String versionId,
                                                           Server server) throws IOException {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        ExtensionCatalogQuery query = ServerExtensionQueryFactory.forServer(server, null, 20);
        JsonNode projectNode = OBJECT_MAPPER.readTree(httpClient.get(projectUri(projectId), null));
        List<ExtensionCatalogVersion> versions = fetchVersions(projectId, query);
        ExtensionCatalogVersion target = versions.stream()
                .filter(version -> versionId != null && !versionId.isBlank()
                        ? versionId.equalsIgnoreCase(version.versionId())
                        : true)
                .findFirst()
                .orElse(versions.isEmpty() ? null : versions.getFirst());
        if (target == null) {
            return Optional.empty();
        }
        return Optional.of(new ExtensionDownloadPlan(
                getProviderId(),
                projectId,
                target.versionId(),
                target.versionNumber(),
                text(projectNode, "icon_url"),
                target.fileName(),
                target.downloadUrl(),
                getSourceType(),
                inferExtensionType(query),
                inferPlatform(target.supportedPlatforms(), query.platform()),
                joinVersions(target.supportedMinecraftVersions()),
                true,
                "Descarga resuelta desde Modrinth para el servidor seleccionado."
        ));
    }

    @Override
    public List<ExtensionUpdateCandidate> findUpdates(Server server,
                                                      List<ServerExtension> installedExtensions) throws IOException {
        if (installedExtensions == null || installedExtensions.isEmpty()) {
            return List.of();
        }

        List<ExtensionUpdateCandidate> updates = new ArrayList<>();
        for (ServerExtension installed : installedExtensions) {
            if (installed == null || installed.getSource() == null) {
                continue;
            }
            if (!getProviderId().equalsIgnoreCase(installed.getSource().getProvider())) {
                continue;
            }
            String projectId = installed.getSource().getProjectId();
            if (projectId == null || projectId.isBlank()) {
                continue;
            }
            ExtensionCatalogQuery query = ServerExtensionQueryFactory.forServer(server, installed.getDisplayName(), 20);
            List<ExtensionCatalogVersion> versions = fetchVersions(projectId, query);
            if (versions.isEmpty()) {
                continue;
            }
            ExtensionCatalogVersion latest = versions.getFirst();
            String localVersion = normalize(installed.getVersion());
            String remoteVersion = normalize(latest.versionNumber());
            updates.add(new ExtensionUpdateCandidate(
                    getProviderId(),
                    projectId,
                    installed,
                    latest,
                    remoteVersion != null && !remoteVersion.equals(localVersion),
                    remoteVersion != null && !remoteVersion.equals(localVersion)
                            ? "Hay una version compatible mas reciente en Modrinth."
                            : "La extension coincide con la ultima version compatible encontrada en Modrinth."
            ));
        }
        return updates;
    }

    private List<ExtensionCatalogVersion> fetchVersions(String projectId,
                                                        ExtensionCatalogQuery query) throws IOException {
        URI uri = URI.create(API_BASE_URL + "/project/" + encode(projectId) + "/version?" + buildVersionQueryString(query));
        JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<ExtensionCatalogVersion> versions = new ArrayList<>();
        for (JsonNode versionNode : root) {
            ExtensionCatalogVersion version = toCatalogVersion(projectId, versionNode);
            if (version != null) {
                versions.add(version);
            }
        }
        return versions.stream()
                .sorted(Comparator.comparingLong(ExtensionCatalogVersion::publishedAtEpochMillis).reversed())
                .toList();
    }

    private ExtensionCatalogEntry toSearchEntry(JsonNode hit, ExtensionCatalogQuery query) {
        String projectType = text(hit, "project_type");
        ServerExtensionType extensionType = toExtensionType(projectType);
        if (query.extensionType() != ServerExtensionType.UNKNOWN && extensionType != query.extensionType()) {
            return null;
        }
        Set<ServerPlatform> platforms = extractPlatforms(hit.path("categories"));
        Set<String> versions = setOfStrings(hit.path("versions"));
        String projectId = firstNonBlank(text(hit, "project_id"), text(hit, "slug"));
        String versionId = text(hit, "latest_version");
        String projectUrl = text(hit, "slug") == null ? null : "https://modrinth.com/" + projectType + "/" + text(hit, "slug");
        return new ExtensionCatalogEntry(
                getProviderId(),
                projectId,
                versionId,
                text(hit, "title"),
                text(hit, "author"),
                text(hit, "latest_version"),
                text(hit, "description"),
                getSourceType(),
                extensionType,
                platforms,
                versions,
                text(hit, "icon_url"),
                projectUrl,
                null
        );
    }

    private ExtensionCatalogEntry toProjectEntry(JsonNode projectNode, List<ExtensionCatalogVersion> versions) {
        ExtensionCatalogVersion latest = versions.isEmpty() ? null : versions.getFirst();
        return new ExtensionCatalogEntry(
                getProviderId(),
                firstNonBlank(text(projectNode, "id"), text(projectNode, "slug")),
                latest == null ? null : latest.versionId(),
                text(projectNode, "title"),
                text(projectNode, "author"),
                latest == null ? null : latest.versionNumber(),
                text(projectNode, "description"),
                getSourceType(),
                toExtensionType(text(projectNode, "project_type")),
                latest == null ? extractPlatforms(projectNode.path("categories")) : latest.supportedPlatforms(),
                latest == null ? setOfStrings(projectNode.path("game_versions")) : latest.supportedMinecraftVersions(),
                text(projectNode, "icon_url"),
                text(projectNode, "slug") == null ? null : "https://modrinth.com/" + text(projectNode, "project_type") + "/" + text(projectNode, "slug"),
                latest == null ? null : latest.downloadUrl()
        );
    }

    private ExtensionCatalogVersion toCatalogVersion(String projectId, JsonNode versionNode) {
        JsonNode files = versionNode.path("files");
        JsonNode primaryFile = null;
        if (files.isArray()) {
            for (JsonNode fileNode : files) {
                if (fileNode.path("primary").asBoolean(false)) {
                    primaryFile = fileNode;
                    break;
                }
            }
            if (primaryFile == null && !files.isEmpty()) {
                primaryFile = files.get(0);
            }
        }
        if (primaryFile == null || primaryFile.isMissingNode()) {
            return null;
        }
        return new ExtensionCatalogVersion(
                getProviderId(),
                projectId,
                text(versionNode, "id"),
                text(versionNode, "name"),
                text(versionNode, "version_number"),
                extractPlatforms(versionNode.path("loaders")),
                setOfStrings(versionNode.path("game_versions")),
                text(versionNode, "changelog"),
                text(primaryFile, "filename"),
                text(primaryFile, "url"),
                parseInstant(text(versionNode, "date_published"))
        );
    }

    private String buildSearchQueryString(ExtensionCatalogQuery query) {
        List<String> params = new ArrayList<>();
        if (query.queryText() != null && !query.queryText().isBlank()) {
            params.add("query=" + encode(query.queryText()));
        }
        params.add("limit=" + query.limit());
        params.add("index=relevance");
        params.add("facets=" + encode(buildSearchFacets(query)));
        return String.join("&", params);
    }

    private String buildVersionQueryString(ExtensionCatalogQuery query) {
        List<String> params = new ArrayList<>();
        String loaderFacet = ServerExtensionQueryFactory.resolveLoaderFacet(null, query);
        if (loaderFacet != null && !loaderFacet.isBlank()) {
            params.add("loaders=" + encode("[\"" + loaderFacet + "\"]"));
        }
        if (query.minecraftVersion() != null && !query.minecraftVersion().isBlank()) {
            params.add("game_versions=" + encode("[\"" + query.minecraftVersion().trim() + "\"]"));
        }
        params.add("featured=true");
        params.add("include_changelog=false");
        return String.join("&", params);
    }

    private String buildSearchFacets(ExtensionCatalogQuery query) {
        List<String> andGroups = new ArrayList<>();
        if (query.extensionType() != ServerExtensionType.UNKNOWN) {
            andGroups.add("[\"project_type:" + toProjectType(query.extensionType()) + "\"]");
        }
        String loaderFacet = ServerExtensionQueryFactory.resolveLoaderFacet(null, query);
        if (loaderFacet != null && !loaderFacet.isBlank()) {
            andGroups.add("[\"categories:" + loaderFacet + "\"]");
        }
        if (query.minecraftVersion() != null && !query.minecraftVersion().isBlank()) {
            andGroups.add("[\"versions:" + query.minecraftVersion().trim() + "\"]");
        }
        return "[" + String.join(",", andGroups) + "]";
    }

    private URI projectUri(String projectId) {
        return URI.create(API_BASE_URL + "/project/" + encode(projectId));
    }

    private boolean matchesPlatform(ExtensionCatalogQuery query, ExtensionCatalogEntry entry) {
        if (query.platform() == null || query.platform() == ServerPlatform.UNKNOWN) {
            return true;
        }
        if (entry.compatiblePlatforms().isEmpty()) {
            return true;
        }
        ServerPlatform requested = canonicalizePlatform(query.platform());
        for (ServerPlatform platform : entry.compatiblePlatforms()) {
            if (canonicalizePlatform(platform) == requested) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMinecraftVersion(ExtensionCatalogQuery query, ExtensionCatalogEntry entry) {
        if (query.minecraftVersion() == null || query.minecraftVersion().isBlank()) {
            return true;
        }
        return entry.compatibleMinecraftVersions().isEmpty()
                || entry.compatibleMinecraftVersions().contains(query.minecraftVersion().trim());
    }

    private ServerExtensionType inferExtensionType(ExtensionCatalogQuery query) {
        return query.extensionType() == null ? ServerExtensionType.UNKNOWN : query.extensionType();
    }

    private ServerPlatform inferPlatform(Set<ServerPlatform> supportedPlatforms, ServerPlatform fallback) {
        if (supportedPlatforms == null || supportedPlatforms.isEmpty()) {
            return fallback == null ? ServerPlatform.UNKNOWN : fallback;
        }
        ServerPlatform normalizedFallback = canonicalizePlatform(fallback);
        if (normalizedFallback != ServerPlatform.UNKNOWN) {
            for (ServerPlatform platform : supportedPlatforms) {
                if (canonicalizePlatform(platform) == normalizedFallback) {
                    return platform;
                }
            }
        }
        return supportedPlatforms.iterator().next();
    }

    private Set<ServerPlatform> extractPlatforms(JsonNode valuesNode) {
        Set<ServerPlatform> platforms = new LinkedHashSet<>();
        if (valuesNode == null || !valuesNode.isArray()) {
            return platforms;
        }
        for (JsonNode node : valuesNode) {
            String value = node == null ? null : node.asText(null);
            if (value == null || value.isBlank()) {
                continue;
            }
            ServerPlatform platform = toPlatform(value);
            if (platform != ServerPlatform.UNKNOWN) {
                platforms.add(platform);
            }
        }
        return platforms;
    }

    private Set<String> setOfStrings(JsonNode arrayNode) {
        Set<String> values = new LinkedHashSet<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return values;
        }
        for (JsonNode node : arrayNode) {
            String value = node == null ? null : node.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private ServerPlatform toPlatform(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return ServerPlatform.UNKNOWN;
        }
        return switch (normalized) {
            case "forge" -> ServerPlatform.FORGE;
            case "neoforge" -> ServerPlatform.NEOFORGE;
            case "fabric" -> ServerPlatform.FABRIC;
            case "quilt" -> ServerPlatform.QUILT;
            case "paper" -> ServerPlatform.PAPER;
            case "spigot" -> ServerPlatform.SPIGOT;
            case "bukkit" -> ServerPlatform.BUKKIT;
            case "purpur" -> ServerPlatform.PURPUR;
            case "pufferfish" -> ServerPlatform.PUFFERFISH;
            default -> ServerPlatform.UNKNOWN;
        };
    }

    private ServerExtensionType toExtensionType(String projectType) {
        String normalized = normalize(projectType);
        if ("mod".equals(normalized)) {
            return ServerExtensionType.MOD;
        }
        if ("plugin".equals(normalized)) {
            return ServerExtensionType.PLUGIN;
        }
        return ServerExtensionType.UNKNOWN;
    }

    private String toProjectType(ServerExtensionType extensionType) {
        return extensionType == ServerExtensionType.PLUGIN ? "plugin" : "mod";
    }

    private ServerPlatform canonicalizePlatform(ServerPlatform platform) {
        if (platform == null) {
            return ServerPlatform.UNKNOWN;
        }
        return switch (platform) {
            case PURPUR, PUFFERFISH -> ServerPlatform.PAPER;
            default -> platform;
        };
    }

    private String nestedText(JsonNode node, String parent, String child) {
        if (node == null) {
            return null;
        }
        return text(node.path(parent), child);
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
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String joinVersions(Set<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        return String.join(" || ", versions);
    }

    private long parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(value.trim()).toEpochMilli();
        } catch (RuntimeException ex) {
            return 0L;
        }
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
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
