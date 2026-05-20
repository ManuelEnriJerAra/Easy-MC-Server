package controlador.extensions;

import controlador.JsonNodeText;
import static controlador.JsonNodeText.isScalarText;
import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class HangarExtensionCatalogProvider implements ExtensionCatalogProvider {
    private static final String API_BASE_URL = "https://hangar.papermc.io/api/v1";
    private static final String SITE_BASE_URL = "https://hangar.papermc.io";
    private static final String MODRINTH_API_BASE_URL = "https://api.modrinth.com/v2";
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
    public Set<ServerExtensionType> getSupportedExtensionTypes() {
        return Set.of(ServerExtensionType.PLUGIN);
    }

    @Override
    public Set<ServerPlatform> getSupportedPlatforms() {
        return Set.of(ServerPlatform.PAPER, ServerPlatform.PURPUR, ServerPlatform.PUFFERFISH);
    }

    @Override
    public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
        ExtensionCatalogQuery normalized = normalizeQuery(query);
        if (!supportsQuery(normalized)) {
            return List.of();
        }

        List<ExtensionCatalogEntry> entries = new ArrayList<>();
        int requestedLimit = Math.max(1, normalized.limit());
        int offset = 0;
        while (entries.size() < requestedLimit) {
            int pageLimit = Math.min(25, requestedLimit - entries.size());
            URI uri = URI.create(API_BASE_URL + "/projects?" + buildSearchQueryString(normalized, pageLimit, offset));
            JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
            JsonNode results = root == null ? null : root.path("result");
            if (results == null || !results.isArray() || results.isEmpty()) {
                break;
            }
            int pageMatches = 0;
            for (JsonNode projectNode : results) {
                ExtensionCatalogEntry entry = toSearchEntry(projectNode, normalized);
                if (entry == null) {
                    continue;
                }
                if (!matchesPlatform(normalized, entry) || !matchesMinecraftVersion(normalized, entry)) {
                    continue;
                }
                entries.add(entry);
                pageMatches++;
                if (entries.size() >= requestedLimit) {
                    break;
                }
            }
            offset += results.size();
            if (results.size() < pageLimit || pageMatches == 0 && offset >= requestedLimit * 3) {
                break;
            }
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
        String detailedDescription = firstNonBlank(
                readMainPageContent(text(projectNode, "id")),
                text(projectNode, "mainPageContent"),
                text(projectNode, "description")
        );
        return Optional.of(new ExtensionCatalogDetails(
                entry,
                detailedDescription,
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
        String resolvedProjectId = firstNonBlank(text(projectNode, "id"), projectId);

        List<ExtensionCatalogVersion> versions = fetchVersions(projectNode, query);
        boolean exactVersionRequested = versionId != null && !versionId.isBlank();
        ExtensionCatalogVersion target = versions.stream()
                .filter(version -> exactVersionRequested
                        ? versionId.equalsIgnoreCase(version.versionId())
                        : true)
                .sorted(defaultVersionSelectionComparator(exactVersionRequested))
                .findFirst()
                .orElse(exactVersionRequested || versions.isEmpty() ? null : versions.getFirst());
        if (target == null) {
            return Optional.empty();
        }
        if (target.downloadUrl() == null || target.downloadUrl().isBlank()) {
            return Optional.empty();
        }

        String detailedDescription = firstNonBlank(
                readMainPageContent(text(projectNode, "id")),
                text(projectNode, "mainPageContent"),
                text(projectNode, "description")
        );
        return Optional.of(new ExtensionDownloadPlan(
                getProviderId(),
                resolvedProjectId,
                target.versionId(),
                text(projectNode, "name"),
                nestedText(projectNode, "namespace", "owner"),
                detailedDescription,
                target.versionNumber(),
                text(projectNode, "avatarUrl"),
                target.fileName(),
                target.downloadUrl(),
                buildProjectUrl(projectNode),
                findLink(projectNode, "issues"),
                buildProjectUrl(projectNode),
                nestedText(projectNode, "settings", "license", "name"),
                hangarDownloads(projectNode),
                "unsupported",
                "required",
                extractCategories(projectNode),
                getSourceType(),
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                joinVersions(target.supportedMinecraftVersions()),
                true,
                "Descarga resuelta desde Hangar para un servidor compatible con Paper.",
                target.dependencies()
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
                            ? "Hay una versión compatible más reciente en Hangar."
                            : "La extensión coincide con la última versión compatible encontrada en Hangar."
            ));
        }
        return updates;
    }

    private JsonNode readProject(String projectId) throws IOException {
        if (shouldSearchProjectBeforeDirect(projectId)) {
            ProjectSearchResult searchResult = searchProjectByIdentifier(projectId);
            if (searchResult.projectNode() != null) {
                return searchResult.projectNode();
            }
            if (searchResult.completed()) {
                return null;
            }
        }

        IOException directFailure = null;
        try {
            return readProjectDirect(projectId);
        } catch (IOException ex) {
            directFailure = ex;
        }

        ProjectSearchResult searchResult = searchProjectByIdentifier(projectId);
        if (searchResult.projectNode() != null) {
            return searchResult.projectNode();
        }
        if (isNotFound(directFailure)) {
            return null;
        }
        throw directFailure;
    }

    private boolean shouldSearchProjectBeforeDirect(String projectId) {
        if (projectId == null || projectId.isBlank() || projectId.contains("/")) {
            return false;
        }
        String normalized = normalizeIdentifier(projectId);
        return normalized != null && !normalized.matches("\\d+");
    }

    private boolean isNotFound(IOException ex) {
        return ex != null && ex.getMessage() != null && ex.getMessage().contains("HTTP 404");
    }

    private JsonNode readProjectDirect(String projectId) throws IOException {
        URI uri = URI.create(API_BASE_URL + "/projects/" + encodeProjectPath(projectId));
        return OBJECT_MAPPER.readTree(httpClient.get(uri, null));
    }

    private ProjectSearchResult searchProjectByIdentifier(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return new ProjectSearchResult(null, false);
        }
        try {
            URI uri = URI.create(API_BASE_URL + "/projects?query=" + encode(projectId.trim())
                    + "&platform=" + PLATFORM_KEY
                    + "&limit=10&offset=0&sort=-downloads");
            JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
            JsonNode results = root == null ? null : root.path("result");
            if (results == null || !results.isArray()) {
                return new ProjectSearchResult(null, true);
            }
            for (JsonNode projectNode : results) {
                if (matchesProjectIdentifier(projectId, projectNode)) {
                    return new ProjectSearchResult(projectNode, true);
                }
            }
            return new ProjectSearchResult(null, true);
        } catch (IOException | RuntimeException ex) {
            return new ProjectSearchResult(null, false);
        }
    }

    private boolean matchesProjectIdentifier(String projectId, JsonNode projectNode) {
        String expected = normalizeIdentifier(projectId);
        if (expected == null || projectNode == null || projectNode.isMissingNode() || projectNode.isNull()) {
            return false;
        }
        String owner = nestedText(projectNode, "namespace", "owner");
        String slug = nestedText(projectNode, "namespace", "slug");
        String namespacePath = owner == null || slug == null ? null : owner + "/" + slug;
        return expected.equals(normalizeIdentifier(text(projectNode, "id")))
                || expected.equals(normalizeIdentifier(text(projectNode, "name")))
                || expected.equals(normalizeIdentifier(slug))
                || expected.equals(normalizeIdentifier(namespacePath));
    }

    private String readMainPageContent(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(API_BASE_URL + "/pages/main/" + encodePath(projectId));
            return httpClient.get(uri, Map.of("Accept", "text/plain"));
        } catch (IOException ex) {
            return null;
        }
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
                .sorted(versionDisplayComparator())
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
                null,
                hangarDownloads(projectNode),
                "unsupported",
                "required"
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
                latest == null ? null : latest.downloadUrl(),
                hangarDownloads(projectNode),
                "unsupported",
                "required"
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
        String externalUrl = text(downloadNode, "externalUrl");
        ExternalDownload externalDownload = resolveExternalDownload(externalUrl, versionName, query);
        ExternalDownload directExternalDownload = directExternalDownload(externalUrl);
        String downloadUrl = firstNonBlank(
                text(downloadNode, "downloadUrl"),
                externalDownload == null ? null : externalDownload.downloadUrl(),
                directExternalDownload == null ? null : directExternalDownload.downloadUrl(),
                externalUrl == null ? buildDownloadEndpoint(namespace, versionName) : null
        );
        String fileName = firstNonBlank(
                nestedText(downloadNode, "fileInfo", "name"),
                externalDownload == null ? null : externalDownload.fileName(),
                directExternalDownload == null ? null : directExternalDownload.fileName()
        );
        if (downloadUrl == null || downloadUrl.isBlank()) {
            return null;
        }
        return new ExtensionCatalogVersion(
                getProviderId(),
                projectId,
                text(versionNode, "id"),
                versionName,
                versionName,
                Set.of(ServerPlatform.PAPER),
                supportedMinecraftVersions,
                text(versionNode, "description"),
                fileName,
                downloadUrl,
                parseInstant(text(versionNode, "createdAt")),
                isStableRelease(versionName),
                extractDependencies(versionNode)
        );
    }

    private ExternalDownload directExternalDownload(String externalUrl) {
        if (externalUrl == null || externalUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(externalUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                return null;
            }
            String rawPath = uri.getRawPath();
            if (rawPath == null || !rawPath.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return null;
            }
            String fileName = fileNameFromRawPath(rawPath);
            return new ExternalDownload(fileName, externalUrl.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String fileNameFromRawPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        int slashIndex = rawPath.lastIndexOf('/');
        String rawName = slashIndex >= 0 ? rawPath.substring(slashIndex + 1) : rawPath;
        if (rawName.isBlank()) {
            return null;
        }
        try {
            return URLDecoder.decode(rawName.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return rawName;
        }
    }

    private Comparator<ExtensionCatalogVersion> versionDisplayComparator() {
        return Comparator.comparingLong(ExtensionCatalogVersion::publishedAtEpochMillis).reversed();
    }

    private Comparator<ExtensionCatalogVersion> defaultVersionSelectionComparator(boolean exactVersionRequested) {
        Comparator<ExtensionCatalogVersion> newestFirst = versionDisplayComparator();
        if (exactVersionRequested) {
            return newestFirst;
        }
        return Comparator.comparing(ExtensionCatalogVersion::stableRelease).reversed()
                .thenComparing(newestFirst);
    }

    private boolean isStableRelease(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return !(normalized.contains("snapshot")
                || normalized.contains("alpha")
                || normalized.contains("beta")
                || normalized.contains("rc")
                || normalized.contains("pre"));
    }

    private ExternalDownload resolveExternalDownload(String externalUrl,
                                                     String versionName,
                                                     ExtensionCatalogQuery query) {
        String modrinthProject = modrinthProjectId(externalUrl);
        if (modrinthProject == null) {
            return null;
        }
        try {
            URI uri = URI.create(MODRINTH_API_BASE_URL + "/project/" + encodePath(modrinthProject) + "/version?"
                    + buildModrinthVersionsQueryString(query));
            JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
            if (root == null || !root.isArray()) {
                return null;
            }
            ExternalDownload fallback = null;
            for (JsonNode versionNode : root) {
                ExternalDownload candidate = toModrinthExternalDownload(versionNode);
                if (candidate == null) {
                    continue;
                }
                if (fallback == null) {
                    fallback = candidate;
                }
                String modrinthVersionNumber = text(versionNode, "version_number");
                String modrinthVersionName = text(versionNode, "name");
                if (versionName != null
                        && (versionName.equalsIgnoreCase(firstNonBlank(modrinthVersionNumber, ""))
                        || versionName.equalsIgnoreCase(firstNonBlank(modrinthVersionName, "")))) {
                    return candidate;
                }
            }
            return fallback;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private String buildModrinthVersionsQueryString(ExtensionCatalogQuery query) {
        List<String> params = new ArrayList<>();
        params.add("loaders=" + encode("[\"paper\"]"));
        if (query != null && query.minecraftVersion() != null && !query.minecraftVersion().isBlank()) {
            params.add("game_versions=" + encode("[\"" + query.minecraftVersion().trim() + "\"]"));
        }
        params.add("include_changelog=false");
        return String.join("&", params);
    }

    private ExternalDownload toModrinthExternalDownload(JsonNode versionNode) {
        JsonNode files = versionNode == null ? null : versionNode.path("files");
        if (files == null || !files.isArray() || files.isEmpty()) {
            return null;
        }
        JsonNode selected = null;
        for (JsonNode fileNode : files) {
            if (fileNode.path("primary").asBoolean(false)) {
                selected = fileNode;
                break;
            }
        }
        if (selected == null) {
            selected = files.get(0);
        }
        String downloadUrl = text(selected, "url");
        if (downloadUrl == null || downloadUrl.isBlank()) {
            return null;
        }
        return new ExternalDownload(text(selected, "filename"), downloadUrl);
    }

    private String modrinthProjectId(String externalUrl) {
        if (externalUrl == null || externalUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(externalUrl.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.equals("modrinth.com") && !host.endsWith(".modrinth.com")) {
                return null;
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("plugin".equalsIgnoreCase(parts[i]) || "mod".equalsIgnoreCase(parts[i])) {
                    String slug = parts[i + 1];
                    return slug == null || slug.isBlank() ? null : slug;
                }
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String buildSearchQueryString(ExtensionCatalogQuery query, int limit, int offset) {
        List<String> params = new ArrayList<>();
        if (query.queryText() != null && !query.queryText().isBlank()) {
            params.add("query=" + encode(query.queryText()));
        }
        params.add("platform=" + PLATFORM_KEY);
        if (query.minecraftVersion() != null && !query.minecraftVersion().isBlank()) {
            params.add("version=" + encode(query.minecraftVersion().trim()));
        }
        params.add("limit=" + Math.max(1, Math.min(limit, 100)));
        params.add("offset=" + Math.max(0, offset));
        params.add("sort=" + encode(resolveProjectSort(query)));
        return String.join("&", params);
    }

    private String resolveProjectSort(ExtensionCatalogQuery query) {
        String sort = query == null ? null : query.sortOrder();
        if (sort == null || sort.isBlank()) {
            return "-downloads";
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "downloads" -> "-downloads";
            case "updated", "actualizados" -> "-updated";
            case "newest", "date_created" -> "-newest";
            case "relevance", "relevancia" -> query != null && query.queryText() != null && !query.queryText().isBlank()
                    ? "-stars"
                    : "-downloads";
            default -> "-downloads";
        };
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

    @Override
    public boolean supportsQuery(ExtensionCatalogQuery query) {
        if (!supportsSearch()) {
            return false;
        }
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

    private List<ExtensionDependency> extractDependencies(JsonNode versionNode) {
        if (versionNode == null || versionNode.isMissingNode() || versionNode.isNull()) {
            return List.of();
        }
        List<ExtensionDependency> dependencies = new ArrayList<>();
        addDependencyArray(dependencies, versionNode.path("dependencies"), "required", true);
        addDependencyArray(dependencies, versionNode.path("pluginDependencies"), "required", true);
        addDependencyObject(dependencies, versionNode.path("dependencies"));
        addDependencyObject(dependencies, versionNode.path("pluginDependencies"));
        return dependencies;
    }

    private void addDependencyArray(List<ExtensionDependency> target,
                                    JsonNode dependenciesNode,
                                    String fallbackType,
                                    boolean fallbackRequired) {
        if (target == null || dependenciesNode == null || !dependenciesNode.isArray()) {
            return;
        }
        for (JsonNode dependencyNode : dependenciesNode) {
            ExtensionDependency dependency = toDependency(dependencyNode, null, fallbackType, fallbackRequired);
            if (dependency != null) {
                target.add(dependency);
            }
        }
    }

    private void addDependencyObject(List<ExtensionDependency> target, JsonNode dependenciesNode) {
        if (target == null || dependenciesNode == null || !dependenciesNode.isObject()) {
            return;
        }
        for (var entry : dependenciesNode.properties()) {
            String dependencyType = entry.getKey();
            JsonNode value = entry.getValue();
            if (value != null && value.isArray()) {
                addDependencyArray(target, value, dependencyType, isRequiredDependencyType(dependencyType));
            } else {
                ExtensionDependency dependency = toDependency(value, entry.getKey(), dependencyType, isRequiredDependencyType(dependencyType));
                if (dependency != null) {
                    target.add(dependency);
                }
            }
        }
    }

    private ExtensionDependency toDependency(JsonNode dependencyNode,
                                             String fallbackName,
                                             String fallbackType,
                                             boolean fallbackRequired) {
        if (dependencyNode == null || dependencyNode.isMissingNode() || dependencyNode.isNull()) {
            return null;
        }
        String projectId;
        String versionId = null;
        String displayName;
        String dependencyType = fallbackType;
        boolean required = fallbackRequired;
        if (dependencyNode.getNodeType() == tools.jackson.databind.node.JsonNodeType.STRING) {
            projectId = JsonNodeText.text(dependencyNode, null);
            displayName = projectId;
        } else {
            projectId = firstNonBlank(
                    text(dependencyNode, "projectId"),
                    text(dependencyNode, "project_id"),
                    text(dependencyNode, "slug"),
                    text(dependencyNode, "name"),
                    fallbackName
            );
            versionId = firstNonBlank(text(dependencyNode, "versionId"), text(dependencyNode, "version_id"));
            displayName = firstNonBlank(text(dependencyNode, "displayName"), text(dependencyNode, "name"), projectId);
            dependencyType = firstNonBlank(text(dependencyNode, "dependencyType"), text(dependencyNode, "type"), fallbackType);
            JsonNode requiredNode = dependencyNode.path("required");
            if (!requiredNode.isMissingNode() && !requiredNode.isNull()) {
                required = requiredNode.asBoolean(required);
            } else {
                required = isRequiredDependencyType(dependencyType);
            }
        }
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return new ExtensionDependency(
                getProviderId(),
                projectId,
                versionId,
                displayName,
                dependencyType,
                required
        );
    }

    private boolean isRequiredDependencyType(String dependencyType) {
        if (dependencyType == null || dependencyType.isBlank()) {
            return true;
        }
        String normalized = dependencyType.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("required")
                || normalized.equals("depends")
                || normalized.equals("dependency")
                || normalized.equals("server");
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
            String value = versionNode == null ? null : JsonNodeText.text(versionNode, null);
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
            String value = item == null ? null : JsonNodeText.text(item, null);
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
        String text = JsonNodeText.text(value, null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private long hangarDownloads(JsonNode projectNode) {
        if (projectNode == null) {
            return 0L;
        }
        return Math.max(0L, firstNumericField(projectNode, Set.of("downloads", "totalDownloads")));
    }

    private long firstNumericField(JsonNode node, Set<String> fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull() || fieldNames == null || fieldNames.isEmpty()) {
            return 0L;
        }
        if (node.isObject()) {
            for (String fieldName : fieldNames) {
                JsonNode value = node.path(fieldName);
                if (value == null || value.isMissingNode() || value.isNull()) {
                    continue;
                }
                if (isScalarText(value)) {
                    long parsed = value.asLong(0L);
                    if (parsed > 0L) {
                        return parsed;
                    }
                }
            }
            for (JsonNode child : node) {
                long parsed = firstNumericField(child, fieldNames);
                if (parsed > 0L) {
                    return parsed;
                }
            }
        }
        return 0L;
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

    private String normalizeIdentifier(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replaceAll("[^a-z0-9]+", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodeProjectPath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.trim().split("/");
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return encodePath(parts[0]) + "/" + encodePath(parts[1]);
        }
        return encode(value);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private record Namespace(String owner, String slug) {
    }

    private record ExternalDownload(String fileName, String downloadUrl) {
    }

    private record ProjectSearchResult(JsonNode projectNode, boolean completed) {
    }
}
