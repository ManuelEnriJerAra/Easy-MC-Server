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

final class HangarExtensionCatalogProvider implements ExtensionCatalogProvider {
    private static final String API_BASE_URL = "https://hangar.papermc.io/api/v1";
    private static final String SITE_BASE_URL = "https://hangar.papermc.io";
    private static final String PLATFORM_KEY = "PAPER";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExtensionHttpClient httpClient;

    HangarExtensionCatalogProvider() {
        this(new ExtensionHttpClient());
    }

    HangarExtensionCatalogProvider(ExtensionHttpClient httpClient) {
        this.httpClient = httpClient == null ? new ExtensionHttpClient() : httpClient;
    }

    @Override
    public String getProviderId() {
        return "hangar";
    }

    @Override
    public String getDisplayName() {
        return "Hangar";
    }

    @Override
    public ExtensionSourceType getSourceType() {
        return ExtensionSourceType.HANGAR;
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
        ExtensionCatalogQuery normalized = normalizeQuery(query);
        if (!supportsQuery(normalized)) {
            return List.of();
        }

        URI uri = URI.create(API_BASE_URL + "/projects?" + buildSearchQueryString(normalized));
        JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
        JsonNode results = root == null ? null : root.path("result");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        List<ExtensionCatalogEntry> entries = new ArrayList<>();
        for (JsonNode projectNode : results) {
            ExtensionCatalogEntry entry = toSearchEntry(projectNode, normalized);
            if (entry == null) {
                continue;
            }
            if (!matchesPlatform(normalized, entry) || !matchesMinecraftVersion(normalized, entry)) {
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

        ExtensionCatalogQuery normalized = normalizeQuery(query);
        if (!supportsQuery(normalized)) {
            return Optional.empty();
        }

        JsonNode projectNode = readProject(projectId);
        if (projectNode == null || projectNode.isMissingNode()) {
            return Optional.empty();
        }

        List<ExtensionCatalogVersion> versions = fetchVersions(projectNode, normalized);
        ExtensionCatalogEntry entry = toProjectEntry(projectNode, versions);
        return Optional.of(new ExtensionCatalogDetails(
                entry,
                firstNonBlank(text(projectNode, "mainPageContent"), text(projectNode, "description")),
                buildProjectUrl(projectNode),
                findLink(projectNode, "issues"),
                nestedText(projectNode, "settings", "license", "name"),
                extractCategories(projectNode),
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
        if (!supportsQuery(query)) {
            return Optional.empty();
        }

        JsonNode projectNode = readProject(projectId);
        if (projectNode == null || projectNode.isMissingNode()) {
            return Optional.empty();
        }

        List<ExtensionCatalogVersion> versions = fetchVersions(projectNode, query);
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
                text(projectNode, "avatarUrl"),
                target.fileName(),
                target.downloadUrl(),
                getSourceType(),
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                joinVersions(target.supportedMinecraftVersions()),
                true,
                "Descarga resuelta desde Hangar para un servidor compatible con Paper."
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

            JsonNode projectNode = readProject(projectId);
            if (projectNode == null || projectNode.isMissingNode()) {
                continue;
            }

            ExtensionCatalogQuery query = ServerExtensionQueryFactory.forServer(server, installed.getDisplayName(), 20);
            if (!supportsQuery(query)) {
                continue;
            }

            List<ExtensionCatalogVersion> versions = fetchVersions(projectNode, query);
            if (versions.isEmpty()) {
                continue;
            }

            ExtensionCatalogVersion latest = versions.getFirst();
            String localVersion = normalize(installed.getVersion());
            String remoteVersion = normalize(latest.versionNumber());
            boolean hasUpdate = remoteVersion != null && !remoteVersion.equals(localVersion);
            updates.add(new ExtensionUpdateCandidate(
                    getProviderId(),
                    projectId,
                    installed,
                    latest,
                    hasUpdate,
                    hasUpdate
                            ? "Hay una version compatible mas reciente en Hangar."
                            : "La extension coincide con la ultima version compatible encontrada en Hangar."
            ));
        }
        return updates;
    }

    private JsonNode readProject(String projectId) throws IOException {
        URI uri = URI.create(API_BASE_URL + "/projects/" + encode(projectId));
        return OBJECT_MAPPER.readTree(httpClient.get(uri, null));
    }

    private List<ExtensionCatalogVersion> fetchVersions(JsonNode projectNode,
                                                        ExtensionCatalogQuery query) throws IOException {
        Namespace namespace = namespace(projectNode);
        if (namespace == null) {
            return List.of();
        }

        URI uri = URI.create(API_BASE_URL + "/projects/"
                + encodePath(namespace.owner()) + "/"
                + encodePath(namespace.slug()) + "/versions?"
                + buildVersionsQueryString(query));
        JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
        JsonNode results = root == null ? null : root.path("result");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        String projectId = text(projectNode, "id");
        List<ExtensionCatalogVersion> versions = new ArrayList<>();
        for (JsonNode versionNode : results) {
            ExtensionCatalogVersion version = toCatalogVersion(projectId, namespace, versionNode, query);
            if (version != null) {
                versions.add(version);
            }
        }
        return versions.stream()
                .sorted(Comparator.comparingLong(ExtensionCatalogVersion::publishedAtEpochMillis).reversed())
                .toList();
    }

    private ExtensionCatalogEntry toSearchEntry(JsonNode projectNode, ExtensionCatalogQuery query) {
        Set<ServerPlatform> platforms = extractSupportedPlatforms(projectNode.path("supportedPlatforms"));
        if (platforms.isEmpty()) {
            return null;
        }

        Set<String> versions = extractSupportedMinecraftVersions(projectNode.path("supportedPlatforms"));
        if (!matchesRequestedMinecraftVersion(query, versions)) {
            return null;
        }

        String projectId = text(projectNode, "id");
        if (projectId == null) {
            return null;
        }

        return new ExtensionCatalogEntry(
                getProviderId(),
                projectId,
                null,
                text(projectNode, "name"),
                nestedText(projectNode, "namespace", "owner"),
                null,
                text(projectNode, "description"),
                getSourceType(),
                ServerExtensionType.PLUGIN,
                platforms,
                versions,
                text(projectNode, "avatarUrl"),
                buildProjectUrl(projectNode),
                null
        );
    }

    private ExtensionCatalogEntry toProjectEntry(JsonNode projectNode, List<ExtensionCatalogVersion> versions) {
        ExtensionCatalogVersion latest = versions.isEmpty() ? null : versions.getFirst();
        return new ExtensionCatalogEntry(
                getProviderId(),
                text(projectNode, "id"),
                latest == null ? null : latest.versionId(),
                text(projectNode, "name"),
                nestedText(projectNode, "namespace", "owner"),
                latest == null ? null : latest.versionNumber(),
                text(projectNode, "description"),
                getSourceType(),
                ServerExtensionType.PLUGIN,
                latest == null ? extractSupportedPlatforms(projectNode.path("supportedPlatforms")) : latest.supportedPlatforms(),
                latest == null ? extractSupportedMinecraftVersions(projectNode.path("supportedPlatforms")) : latest.supportedMinecraftVersions(),
                text(projectNode, "avatarUrl"),
                buildProjectUrl(projectNode),
                latest == null ? null : latest.downloadUrl()
        );
    }

    private ExtensionCatalogVersion toCatalogVersion(String projectId,
                                                     Namespace namespace,
                                                     JsonNode versionNode,
                                                     ExtensionCatalogQuery query) {
        JsonNode downloadNode = versionNode.path("downloads").path(PLATFORM_KEY);
        if (downloadNode.isMissingNode() || downloadNode.isNull()) {
            return null;
        }

        Set<String> supportedMinecraftVersions = extractPlatformVersions(versionNode.path("platformDependencies"), PLATFORM_KEY);
        if (!matchesRequestedMinecraftVersion(query, supportedMinecraftVersions)) {
            return null;
        }

        String versionName = text(versionNode, "name");
        String downloadUrl = firstNonBlank(
                text(downloadNode, "downloadUrl"),
                buildDownloadEndpoint(namespace, versionName)
        );
        return new ExtensionCatalogVersion(
                getProviderId(),
                projectId,
                text(versionNode, "id"),
                versionName,
                versionName,
                Set.of(ServerPlatform.PAPER),
                supportedMinecraftVersions,
                text(versionNode, "description"),
                nestedText(downloadNode, "fileInfo", "name"),
                downloadUrl,
                parseInstant(text(versionNode, "createdAt"))
        );
    }

    private String buildSearchQueryString(ExtensionCatalogQuery query) {
        List<String> params = new ArrayList<>();
        if (query.queryText() != null && !query.queryText().isBlank()) {
            params.add("query=" + encode(query.queryText()));
        }
        params.add("platform=" + PLATFORM_KEY);
        if (query.minecraftVersion() != null && !query.minecraftVersion().isBlank()) {
            params.add("version=" + encode(query.minecraftVersion().trim()));
        }
        params.add("limit=" + Math.max(1, Math.min(query.limit(), 25)));
        params.add("offset=0");
        params.add("sort=stars");
        return String.join("&", params);
    }

    private String buildVersionsQueryString(ExtensionCatalogQuery query) {
        List<String> params = new ArrayList<>();
        params.add("platform=" + PLATFORM_KEY);
        if (query.minecraftVersion() != null && !query.minecraftVersion().isBlank()) {
            params.add("platformVersion=" + encode(query.minecraftVersion().trim()));
        }
        params.add("limit=" + Math.max(1, Math.min(query.limit(), 25)));
        params.add("offset=0");
        return String.join("&", params);
    }

    private String buildProjectUrl(JsonNode projectNode) {
        Namespace namespace = namespace(projectNode);
        if (namespace == null) {
            return null;
        }
        return SITE_BASE_URL + "/" + encodePath(namespace.owner()) + "/" + encodePath(namespace.slug());
    }

    private String buildDownloadEndpoint(Namespace namespace, String versionName) {
        if (namespace == null || versionName == null || versionName.isBlank()) {
            return null;
        }
        return SITE_BASE_URL + "/api/v1/projects/"
                + encodePath(namespace.owner()) + "/"
                + encodePath(namespace.slug()) + "/versions/"
                + encodePath(versionName) + "/"
                + PLATFORM_KEY + "/download";
    }

    private boolean supportsQuery(ExtensionCatalogQuery query) {
        if (query == null) {
            return true;
        }
        if (query.extensionType() != null
                && query.extensionType() != ServerExtensionType.UNKNOWN
                && query.extensionType() != ServerExtensionType.PLUGIN) {
            return false;
        }
        return query.platform() == null
                || query.platform() == ServerPlatform.UNKNOWN
                || canonicalizePlatform(query.platform()) == ServerPlatform.PAPER;
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
        return matchesRequestedMinecraftVersion(query, entry.compatibleMinecraftVersions());
    }

    private boolean matchesRequestedMinecraftVersion(ExtensionCatalogQuery query, Set<String> versions) {
        if (query == null || query.minecraftVersion() == null || query.minecraftVersion().isBlank()) {
            return true;
        }
        return versions == null || versions.isEmpty() || versions.contains(query.minecraftVersion().trim());
    }

    private Set<ServerPlatform> extractSupportedPlatforms(JsonNode supportedPlatformsNode) {
        Set<ServerPlatform> platforms = new LinkedHashSet<>();
        if (supportedPlatformsNode == null || !supportedPlatformsNode.isObject()) {
            return platforms;
        }
        if (!supportedPlatformsNode.path(PLATFORM_KEY).isMissingNode()) {
            platforms.add(ServerPlatform.PAPER);
        }
        return platforms;
    }

    private Set<String> extractSupportedMinecraftVersions(JsonNode supportedPlatformsNode) {
        return extractPlatformVersions(supportedPlatformsNode, PLATFORM_KEY);
    }

    private Set<String> extractPlatformVersions(JsonNode node, String platformKey) {
        Set<String> versions = new LinkedHashSet<>();
        if (node == null || platformKey == null || platformKey.isBlank()) {
            return versions;
        }
        JsonNode versionsNode = node.path(platformKey);
        if (!versionsNode.isArray()) {
            return versions;
        }
        for (JsonNode versionNode : versionsNode) {
            String value = versionNode == null ? null : versionNode.asText(null);
            if (value != null && !value.isBlank()) {
                versions.add(value.trim());
            }
        }
        return versions;
    }

    private Set<String> extractCategories(JsonNode projectNode) {
        Set<String> categories = new LinkedHashSet<>();
        String category = text(projectNode, "category");
        if (category != null) {
            categories.add(category);
        }
        addAll(categories, nestedArrayTexts(projectNode, "settings", "keywords"));
        addAll(categories, nestedArrayTexts(projectNode, "settings", "tags"));
        return categories;
    }

    private String findLink(JsonNode projectNode, String linkName) {
        if (projectNode == null || linkName == null || linkName.isBlank()) {
            return null;
        }
        JsonNode linkGroups = projectNode.path("settings").path("links");
        if (!linkGroups.isArray()) {
            return null;
        }
        String expected = normalize(linkName);
        for (JsonNode group : linkGroups) {
            JsonNode links = group.path("links");
            if (!links.isArray()) {
                continue;
            }
            for (JsonNode linkNode : links) {
                String name = normalize(text(linkNode, "name"));
                if (expected.equals(name)) {
                    return text(linkNode, "url");
                }
            }
        }
        return null;
    }

    private Set<String> nestedArrayTexts(JsonNode node, String parent, String child) {
        if (node == null) {
            return Set.of();
        }
        return arrayTexts(node.path(parent).path(child));
    }

    private Set<String> arrayTexts(JsonNode arrayNode) {
        Set<String> values = new LinkedHashSet<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return values;
        }
        for (JsonNode item : arrayNode) {
            String value = item == null ? null : item.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private void addAll(Set<String> target, Set<String> values) {
        if (target == null || values == null) {
            return;
        }
        target.addAll(values);
    }

    private Namespace namespace(JsonNode projectNode) {
        if (projectNode == null) {
            return null;
        }
        String owner = nestedText(projectNode, "namespace", "owner");
        String slug = nestedText(projectNode, "namespace", "slug");
        if (owner == null || slug == null) {
            return null;
        }
        return new Namespace(owner, slug);
    }

    private String nestedText(JsonNode node, String parent, String child) {
        if (node == null) {
            return null;
        }
        return text(node.path(parent), child);
    }

    private String nestedText(JsonNode node, String grandParent, String parent, String child) {
        if (node == null) {
            return null;
        }
        return text(node.path(grandParent).path(parent), child);
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

    private ExtensionCatalogQuery normalizeQuery(ExtensionCatalogQuery query) {
        return query == null
                ? new ExtensionCatalogQuery(null, ServerPlatform.UNKNOWN, ServerExtensionType.PLUGIN, null, 20)
                : query;
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

    private String joinVersions(Set<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        return String.join(" || ", versions);
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private record Namespace(String owner, String slug) {
    }
}
