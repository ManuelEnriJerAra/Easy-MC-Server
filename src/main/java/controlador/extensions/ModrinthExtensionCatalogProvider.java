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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public Set<ServerExtensionType> getSupportedExtensionTypes() {
        return Set.of(ServerExtensionType.MOD, ServerExtensionType.PLUGIN);
    }

    @Override
    public Set<ServerPlatform> getSupportedPlatforms() {
        return Set.of(
                ServerPlatform.FORGE,
                ServerPlatform.NEOFORGE,
                ServerPlatform.FABRIC,
                ServerPlatform.QUILT,
                ServerPlatform.PAPER,
                ServerPlatform.SPIGOT,
                ServerPlatform.BUKKIT,
                ServerPlatform.PURPUR,
                ServerPlatform.PUFFERFISH
        );
    }

    @Override
    public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
        ExtensionCatalogQuery normalized = query == null
                ? new ExtensionCatalogQuery(null, null, null, null, 20)
                : query;
        List<ExtensionCatalogEntry> entries = new ArrayList<>();
        Set<String> seenEntries = new LinkedHashSet<>();
        int requestedLimit = Math.max(1, normalized.limit());
        int offset = 0;
        while (entries.size() < requestedLimit) {
            int pageLimit = Math.min(100, requestedLimit - entries.size());
            URI uri = URI.create(API_BASE_URL + "/search?" + buildSearchQueryString(normalized, pageLimit, offset));
            JsonNode root = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
            JsonNode hits = root == null ? null : root.path("hits");
            if (hits == null || !hits.isArray() || hits.isEmpty()) {
                break;
            }
            int pageMatches = 0;
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
                String identity = searchResultIdentity(entry);
                if (identity != null && !seenEntries.add(identity)) {
                    continue;
                }
                entries.add(entry);
                pageMatches++;
                if (entries.size() >= requestedLimit) {
                    break;
                }
            }
            offset += hits.size();
            if (hits.size() < pageLimit || pageMatches == 0 && offset >= requestedLimit * 3) {
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
        JsonNode projectNode = OBJECT_MAPPER.readTree(httpClient.get(projectUri(projectId), null));
        if (projectNode == null || projectNode.isMissingNode()) {
            return Optional.empty();
        }

        ExtensionCatalogQuery normalized = query == null
                ? new ExtensionCatalogQuery(null, null, null, null, 20)
                : query;
        List<ExtensionCatalogVersion> versions = fetchVersions(projectId, normalized);
        ExtensionCatalogEntry baseEntry = toProjectEntry(projectNode, versions);
        String projectUrl = text(projectNode, "slug") == null
                ? null
                : "https://modrinth.com/" + text(projectNode, "project_type") + "/" + text(projectNode, "slug");
        return Optional.of(new ExtensionCatalogDetails(
                baseEntry,
                text(projectNode, "body"),
                firstNonBlank(text(projectNode, "wiki_url"), text(projectNode, "source_url"), projectUrl),
                text(projectNode, "issues_url"),
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
        boolean exactVersionRequested = versionId != null && !versionId.isBlank();
        ExtensionCatalogVersion target = versions.stream()
                .filter(version -> exactVersionRequested
                        ? versionId.equalsIgnoreCase(version.versionId())
                        : true)
                .sorted(defaultVersionSelectionComparator(exactVersionRequested))
                .findFirst()
                .orElse(versions.isEmpty() ? null : versions.getFirst());
        if (target == null) {
            return Optional.empty();
        }
        ServerExtensionType resolvedType = inferExtensionType(query);
        List<ExtensionDependency> dependencies = enrichDependencies(target.dependencies(), resolvedType);
        return Optional.of(new ExtensionDownloadPlan(
                getProviderId(),
                projectId,
                target.versionId(),
                text(projectNode, "title"),
                text(projectNode, "author"),
                firstNonBlank(text(projectNode, "body"), text(projectNode, "description")),
                target.versionNumber(),
                text(projectNode, "icon_url"),
                target.fileName(),
                target.downloadUrl(),
                text(projectNode, "slug") == null ? null : "https://modrinth.com/" + text(projectNode, "project_type") + "/" + text(projectNode, "slug"),
                text(projectNode, "issues_url"),
                text(projectNode, "source_url"),
                nestedText(projectNode, "license", "name"),
                longValue(projectNode, "downloads"),
                text(projectNode, "client_side"),
                text(projectNode, "server_side"),
                setOfStrings(projectNode.path("categories")),
                getSourceType(),
                resolvedType,
                inferPlatform(target.supportedPlatforms(), query.platform()),
                joinVersions(target.supportedMinecraftVersions()),
                true,
                "Descarga resuelta desde Modrinth para el servidor seleccionado.",
                dependencies
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
                .sorted(versionDisplayComparator())
                .toList();
    }

    private ExtensionCatalogEntry toSearchEntry(JsonNode hit, ExtensionCatalogQuery query) {
        String projectType = text(hit, "project_type");
        Set<ServerPlatform> platforms = extractPlatforms(hit.path("categories"));
        ServerExtensionType extensionType = inferExtensionType(projectType, platforms, query.extensionType(), query.platform());
        if (query.extensionType() != ServerExtensionType.UNKNOWN && extensionType != query.extensionType()) {
            return null;
        }
        Set<String> versions = setOfStrings(hit.path("versions"));
        String projectId = firstNonBlank(text(hit, "project_id"), text(hit, "slug"));
        String versionId = text(hit, "latest_version");
        String projectUrl = text(hit, "slug") == null ? null : "https://modrinth.com/" + toProjectType(extensionType) + "/" + text(hit, "slug");
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
                null,
                longValue(hit, "downloads"),
                text(hit, "client_side"),
                text(hit, "server_side")
        );
    }

    private String searchResultIdentity(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        String project = normalize(entry.projectId());
        if (project != null) {
            return "project|" + project;
        }
        String projectUrl = normalize(entry.projectUrl());
        if (projectUrl != null) {
            return "url|" + projectUrl;
        }
        String displayName = normalize(entry.displayName());
        return displayName == null ? null : "name|" + displayName;
    }

    private ExtensionCatalogEntry toProjectEntry(JsonNode projectNode, List<ExtensionCatalogVersion> versions) {
        ExtensionCatalogVersion latest = versions.isEmpty() ? null : versions.getFirst();
        Set<ServerPlatform> platforms = latest == null ? extractPlatforms(projectNode.path("categories")) : latest.supportedPlatforms();
        ServerExtensionType extensionType = inferExtensionType(text(projectNode, "project_type"), platforms, ServerExtensionType.UNKNOWN, ServerPlatform.UNKNOWN);
        return new ExtensionCatalogEntry(
                getProviderId(),
                firstNonBlank(text(projectNode, "id"), text(projectNode, "slug")),
                latest == null ? null : latest.versionId(),
                text(projectNode, "title"),
                text(projectNode, "author"),
                latest == null ? null : latest.versionNumber(),
                text(projectNode, "description"),
                getSourceType(),
                extensionType,
                platforms,
                latest == null ? setOfStrings(projectNode.path("game_versions")) : latest.supportedMinecraftVersions(),
                text(projectNode, "icon_url"),
                text(projectNode, "slug") == null ? null : "https://modrinth.com/" + toProjectType(extensionType) + "/" + text(projectNode, "slug"),
                latest == null ? null : latest.downloadUrl(),
                longValue(projectNode, "downloads"),
                text(projectNode, "client_side"),
                text(projectNode, "server_side")
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
                parseInstant(text(versionNode, "date_published")),
                isStableRelease(versionNode),
                extractDependencies(versionNode.path("dependencies"))
        );
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

    private boolean isStableRelease(JsonNode versionNode) {
        String versionType = text(versionNode, "version_type");
        if (versionType != null && !versionType.isBlank()) {
            return "release".equalsIgnoreCase(versionType);
        }
        return isStableRelease(firstNonBlank(text(versionNode, "version_number"), text(versionNode, "name")));
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

    private List<ExtensionDependency> extractDependencies(JsonNode dependenciesNode) {
        if (dependenciesNode == null || !dependenciesNode.isArray()) {
            return List.of();
        }
        List<ExtensionDependency> dependencies = new ArrayList<>();
        for (JsonNode dependencyNode : dependenciesNode) {
            String type = text(dependencyNode, "dependency_type");
            String dependencyProjectId = text(dependencyNode, "project_id");
            String dependencyVersionId = text(dependencyNode, "version_id");
            if (dependencyProjectId == null && dependencyVersionId == null) {
                continue;
            }
            dependencies.add(new ExtensionDependency(
                    getProviderId(),
                    dependencyProjectId,
                    dependencyVersionId,
                    dependencyProjectId,
                    type,
                    "required".equalsIgnoreCase(type)
            ));
        }
        return dependencies;
    }

    private List<ExtensionDependency> enrichDependencies(List<ExtensionDependency> dependencies,
                                                         ServerExtensionType requestedType) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        List<ExtensionDependency> enriched = new ArrayList<>();
        Map<String, JsonNode> projectCache = new HashMap<>();
        Map<String, JsonNode> versionCache = new HashMap<>();
        for (ExtensionDependency dependency : dependencies) {
            if (dependency == null) {
                continue;
            }
            String projectId = dependency.projectId();
            String versionId = dependency.versionId();
            if ((projectId == null || projectId.isBlank()) && versionId != null && !versionId.isBlank()) {
                JsonNode versionNode = versionCache.computeIfAbsent(versionId, this::readVersionQuietly);
                projectId = text(versionNode, "project_id");
            }
            String displayName = dependency.displayName();
            ServerExtensionType dependencyType = ServerExtensionType.UNKNOWN;
            if (projectId != null && !projectId.isBlank()) {
                JsonNode projectNode = projectCache.computeIfAbsent(projectId, this::readProjectQuietly);
                dependencyType = toExtensionType(text(projectNode, "project_type"));
                if (!dependencyMatchesRequestedType(dependencyType, requestedType)) {
                    continue;
                }
                displayName = firstNonBlank(text(projectNode, "title"), text(projectNode, "slug"), displayName, projectId);
            }
            enriched.add(new ExtensionDependency(
                    dependency.providerId(),
                    projectId,
                    versionId,
                    displayName,
                    dependency.dependencyType(),
                    dependency.required()
            ));
        }
        return enriched;
    }

    private boolean dependencyMatchesRequestedType(ServerExtensionType dependencyType, ServerExtensionType requestedType) {
        if (requestedType == null || requestedType == ServerExtensionType.UNKNOWN
                || dependencyType == null || dependencyType == ServerExtensionType.UNKNOWN) {
            return true;
        }
        return dependencyType == requestedType;
    }

    private JsonNode readProjectQuietly(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(httpClient.get(projectUri(projectId), null));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private JsonNode readVersionQuietly(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(httpClient.get(versionUri(versionId), null));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private String buildSearchQueryString(ExtensionCatalogQuery query) {
        return buildSearchQueryString(query, query.limit(), 0);
    }

    private String buildSearchQueryString(ExtensionCatalogQuery query, int limit, int offset) {
        List<String> params = new ArrayList<>();
        if (query.queryText() != null && !query.queryText().isBlank()) {
            params.add("query=" + encode(query.queryText()));
        }
        params.add("limit=" + Math.max(1, Math.min(limit, 100)));
        params.add("offset=" + Math.max(0, offset));
        params.add("index=" + encode(resolveSearchIndex(query)));
        params.add("facets=" + encode(buildSearchFacets(query)));
        return String.join("&", params);
    }

    private String resolveSearchIndex(ExtensionCatalogQuery query) {
        String sort = query == null ? null : query.sortOrder();
        if (sort == null || sort.isBlank()) {
            return "downloads";
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "relevance", "relevancia" -> "relevance";
            case "updated", "actualizados" -> "updated";
            case "follows", "seguidores" -> "follows";
            default -> "downloads";
        };
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
        params.add("include_changelog=false");
        return String.join("&", params);
    }

    private String buildSearchFacets(ExtensionCatalogQuery query) {
        List<String> andGroups = new ArrayList<>();
        if (query.extensionType() != ServerExtensionType.UNKNOWN) {
            if (query.extensionType() == ServerExtensionType.PLUGIN) {
                andGroups.add("[\"project_type:plugin\",\"project_type:mod\"]");
            } else {
                andGroups.add("[\"project_type:" + toProjectType(query.extensionType()) + "\"]");
            }
        }
        String loaderFacet = ServerExtensionQueryFactory.resolveLoaderFacet(null, query);
        if (loaderFacet != null && !loaderFacet.isBlank()) {
            andGroups.add("[\"categories:" + loaderFacet + "\"]");
        }
        if (query.minecraftVersion() != null && !query.minecraftVersion().isBlank()) {
            andGroups.add("[\"versions:" + query.minecraftVersion().trim() + "\"]");
        }
        appendSideFacets(andGroups, query.sideFilter());
        return "[" + String.join(",", andGroups) + "]";
    }

    private void appendSideFacets(List<String> andGroups, ExtensionSideFilter sideFilter) {
        ExtensionSideFilter resolved = sideFilter == null ? ExtensionSideFilter.ANY : sideFilter;
        switch (resolved) {
            case CLIENT -> {
                andGroups.add("[\"client_side:required\",\"client_side:optional\"]");
                andGroups.add("[\"server_side:unsupported\"]");
            }
            case CLIENT_AND_SERVER -> {
                andGroups.add("[\"client_side:required\",\"client_side:optional\"]");
                andGroups.add("[\"server_side:required\",\"server_side:optional\"]");
            }
            case SERVER -> {
                andGroups.add("[\"client_side:unsupported\"]");
                andGroups.add("[\"server_side:required\",\"server_side:optional\"]");
            }
            case ANY -> {
            }
        }
    }

    private URI projectUri(String projectId) {
        return URI.create(API_BASE_URL + "/project/" + encode(projectId));
    }

    private URI versionUri(String versionId) {
        return URI.create(API_BASE_URL + "/version/" + encode(versionId));
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

    private ServerExtensionType inferExtensionType(String projectType,
                                                   Set<ServerPlatform> platforms,
                                                   ServerExtensionType requestedType,
                                                   ServerPlatform requestedPlatform) {
        ServerExtensionType declaredType = toExtensionType(projectType);
        ServerExtensionType platformType = inferExtensionTypeFromPlatforms(platforms);
        if (requestedType != null && requestedType != ServerExtensionType.UNKNOWN && platformType == requestedType) {
            return requestedType;
        }
        if (requestedType != null
                && requestedType != ServerExtensionType.UNKNOWN
                && hasRequestedPlatformType(platforms, requestedPlatform, requestedType)) {
            return requestedType;
        }
        if (declaredType != ServerExtensionType.UNKNOWN && platformType == ServerExtensionType.UNKNOWN) {
            return declaredType;
        }
        if (declaredType == ServerExtensionType.UNKNOWN) {
            return platformType;
        }
        if (declaredType == ServerExtensionType.MOD && platformType == ServerExtensionType.PLUGIN) {
            return ServerExtensionType.PLUGIN;
        }
        return declaredType;
    }

    private boolean hasRequestedPlatformType(Set<ServerPlatform> platforms,
                                             ServerPlatform requestedPlatform,
                                             ServerExtensionType requestedType) {
        if (platforms == null || platforms.isEmpty()
                || requestedPlatform == null || requestedPlatform == ServerPlatform.UNKNOWN
                || requestedType == null || requestedType == ServerExtensionType.UNKNOWN) {
            return false;
        }
        ServerPlatform normalizedRequested = canonicalizePlatform(requestedPlatform);
        for (ServerPlatform platform : platforms) {
            if (platform == null || platform == ServerPlatform.UNKNOWN) {
                continue;
            }
            if (canonicalizePlatform(platform) != normalizedRequested) {
                continue;
            }
            if (requestedType == ServerExtensionType.PLUGIN && platform.isPluginPlatform()) {
                return true;
            }
            if (requestedType == ServerExtensionType.MOD && platform.isModPlatform()) {
                return true;
            }
        }
        return false;
    }

    private ServerExtensionType inferExtensionTypeFromPlatforms(Set<ServerPlatform> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return ServerExtensionType.UNKNOWN;
        }
        boolean hasPluginPlatform = false;
        boolean hasModPlatform = false;
        for (ServerPlatform platform : platforms) {
            if (platform == null || platform == ServerPlatform.UNKNOWN) {
                continue;
            }
            hasPluginPlatform |= platform.isPluginPlatform();
            hasModPlatform |= platform.isModPlatform();
        }
        if (hasPluginPlatform && !hasModPlatform) {
            return ServerExtensionType.PLUGIN;
        }
        if (hasModPlatform && !hasPluginPlatform) {
            return ServerExtensionType.MOD;
        }
        return ServerExtensionType.UNKNOWN;
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
            case "folia" -> ServerPlatform.PAPER;
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

    private long longValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return 0L;
        }
        JsonNode value = node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? 0L : Math.max(0L, value.asLong(0L));
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
