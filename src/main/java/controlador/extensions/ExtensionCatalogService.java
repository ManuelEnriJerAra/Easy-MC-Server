package controlador.extensions;

import modelo.Server;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExtensionCatalogService {
    private static final Logger LOGGER = Logger.getLogger(ExtensionCatalogService.class.getName());
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private final ExtensionCatalogRegistry registry;
    private final Map<String, CacheEntry<List<ExtensionCatalogEntry>>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Optional<ExtensionCatalogDetails>>> detailsCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Optional<ExtensionDownloadPlan>>> downloadCache = new ConcurrentHashMap<>();
    private final Map<String, FutureTask<List<ExtensionCatalogEntry>>> inFlightSearches = new ConcurrentHashMap<>();
    private final Map<String, FutureTask<Optional<ExtensionCatalogDetails>>> inFlightDetails = new ConcurrentHashMap<>();
    private final Map<String, FutureTask<Optional<ExtensionDownloadPlan>>> inFlightDownloads = new ConcurrentHashMap<>();

    public ExtensionCatalogService() {
        this(ExtensionCatalogRegistry.defaultRegistry());
    }

    public ExtensionCatalogService(ExtensionCatalogRegistry registry) {
        this.registry = registry == null ? ExtensionCatalogRegistry.defaultRegistry() : registry;
    }

    public List<ExtensionCatalogProviderDescriptor> getAvailableProviders() {
        return registry.describeProviders();
    }

    public ExtensionCatalogQuery buildQueryForServer(Server server, String queryText, int limit) {
        return ServerExtensionQueryFactory.forServer(server, queryText, limit);
    }

    public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
        ExtensionCatalogQuery normalized = normalizeQuery(query);
        String cacheKey = searchCacheKey(normalized);
        return resolveCached(cacheKey, searchCache, inFlightSearches, () -> {
            List<ExtensionCatalogEntry> aggregated = new ArrayList<>();
            IOException firstFailure = null;
            for (ExtensionCatalogProvider provider : registry.providers()) {
                if (!provider.supportsQuery(normalized)) {
                    continue;
                }
                if (normalized.providerId() != null
                        && !normalized.providerId().isBlank()
                        && !provider.getProviderId().equalsIgnoreCase(normalized.providerId().trim())) {
                    continue;
                }
                try {
                    aggregated.addAll(provider.search(normalized));
                } catch (IOException ex) {
                    if (firstFailure == null) {
                        firstFailure = ex;
                    }
                    LOGGER.log(Level.WARNING, "Fallo al buscar en el proveedor " + provider.getProviderId(), ex);
                } catch (RuntimeException ex) {
                    if (firstFailure == null) {
                        firstFailure = new IOException("El proveedor " + provider.getProviderId() + " ha devuelto una respuesta inválida.", ex);
                    }
                    LOGGER.log(Level.WARNING, "Fallo inesperado al buscar en el proveedor " + provider.getProviderId(), ex);
                }
            }
            if (aggregated.isEmpty() && firstFailure != null) {
                throw new IOException("No se ha podido consultar ningún proveedor del marketplace.", firstFailure);
            }
            List<ExtensionCatalogEntry> filtered = aggregated.stream()
                    .filter(entry -> matchesQuerySafety(normalized, entry))
                    .toList();
            return deduplicateSearchResults(sortSearchResults(filtered, normalized));
        });
    }

    public Optional<ExtensionCatalogDetails> getDetails(String providerId,
                                                        String projectId,
                                                        ExtensionCatalogQuery query) throws IOException {
        ExtensionCatalogQuery normalized = normalizeQuery(query);
        String cacheKey = detailsCacheKey(providerId, projectId, normalized);
        return resolveCached(cacheKey, detailsCache, inFlightDetails, () -> resolveProvider(providerId)
                .flatMap(provider -> fetchDetails(provider, projectId, normalized)));
    }

    public Optional<ExtensionDownloadPlan> resolveDownload(String providerId,
                                                           String projectId,
                                                           String versionId,
                                                           Server server) throws IOException {
        String cacheKey = downloadCacheKey(providerId, projectId, versionId, server);
        return resolveCached(cacheKey, downloadCache, inFlightDownloads, () -> resolveProvider(providerId)
                .flatMap(provider -> fetchDownload(provider, projectId, versionId, server))
                .filter(plan -> matchesServerSafety(server, plan)));
    }

    public List<ExtensionUpdateCandidate> findUpdates(Server server) throws IOException {
        List<ServerExtension> installedExtensions = server == null || server.getExtensions() == null
                ? List.of()
                : List.copyOf(server.getExtensions());

        List<ExtensionUpdateCandidate> candidates = new ArrayList<>();
        IOException firstFailure = null;
        for (ExtensionCatalogProvider provider : registry.providers()) {
            try {
                candidates.addAll(provider.findUpdates(server, installedExtensions));
            } catch (IOException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
                LOGGER.log(Level.WARNING, "Fallo al buscar actualizaciones en el proveedor " + provider.getProviderId(), ex);
            } catch (RuntimeException ex) {
                if (firstFailure == null) {
                    firstFailure = new IOException("El proveedor " + provider.getProviderId() + " ha fallado al revisar actualizaciones.", ex);
                }
                LOGGER.log(Level.WARNING, "Fallo inesperado al buscar actualizaciones en el proveedor " + provider.getProviderId(), ex);
            }
        }
        if (candidates.isEmpty() && firstFailure != null && !installedExtensions.isEmpty()) {
            throw new IOException("No se ha podido comprobar el estado remoto de las extensiones instaladas.", firstFailure);
        }
        return candidates;
    }

    private Optional<ExtensionCatalogProvider> resolveProvider(String providerId) {
        return registry.findById(providerId);
    }

    private Optional<ExtensionCatalogDetails> fetchDetails(ExtensionCatalogProvider provider,
                                                           String projectId,
                                                           ExtensionCatalogQuery query) {
        try {
            return provider.getDetails(projectId, query);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Fallo al cargar detalles desde " + provider.getProviderId(), ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Fallo inesperado al cargar detalles desde " + provider.getProviderId(), ex);
            return Optional.empty();
        }
    }

    private Optional<ExtensionDownloadPlan> fetchDownload(ExtensionCatalogProvider provider,
                                                          String projectId,
                                                          String versionId,
                                                          Server server) {
        try {
            return provider.resolveDownload(projectId, versionId, server);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Fallo al resolver descarga desde " + provider.getProviderId(), ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Fallo inesperado al resolver descarga desde " + provider.getProviderId(), ex);
            return Optional.empty();
        }
    }

    private ExtensionCatalogQuery normalizeQuery(ExtensionCatalogQuery query) {
        return query == null
                ? new ExtensionCatalogQuery(null, null, null, null, 20)
                : query;
    }

    private String searchCacheKey(ExtensionCatalogQuery query) {
        return "search|" + String.valueOf(query);
    }

    private String detailsCacheKey(String providerId, String projectId, ExtensionCatalogQuery query) {
        return "details|" + providerId + "|" + projectId + "|" + String.valueOf(query);
    }

    private String downloadCacheKey(String providerId, String projectId, String versionId, Server server) {
        String platform = server == null || server.getPlatform() == null ? "" : server.getPlatform().name();
        String version = server == null || server.getVersion() == null ? "" : server.getVersion();
        String loader = server == null || server.getLoader() == null ? "" : server.getLoader().name();
        return "download|" + providerId + "|" + projectId + "|" + versionId + "|" + platform + "|" + loader + "|" + version;
    }

    private List<ExtensionCatalogEntry> sortSearchResults(List<ExtensionCatalogEntry> entries, ExtensionCatalogQuery query) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        String sort = query == null || query.sortOrder() == null
                ? "downloads"
                : query.sortOrder().trim().toLowerCase(Locale.ROOT);
        if ("downloads".equals(sort) || sort.isBlank()) {
            return entries.stream()
                    .sorted(downloadsComparator(query))
                    .toList();
        }
        if ("relevance".equals(sort) || "relevancia".equals(sort)) {
            return entries.stream()
                    .sorted(searchRelevanceComparator(query))
                    .toList();
        }
        return entries;
    }

    private List<ExtensionCatalogEntry> deduplicateSearchResults(List<ExtensionCatalogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ExtensionCatalogEntry> unique = new ArrayList<>();
        for (ExtensionCatalogEntry entry : entries) {
            String key = searchResultIdentity(entry);
            if (key == null || seen.add(key)) {
                unique.add(entry);
            }
        }
        return unique;
    }

    private String searchResultIdentity(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        String provider = normalizeIdentityPart(entry.providerId());
        if (provider == null) {
            return null;
        }
        String project = normalizeIdentityPart(entry.projectId());
        if (project != null) {
            return provider + "|project|" + project;
        }
        String projectUrl = normalizeIdentityPart(entry.projectUrl());
        if (projectUrl != null) {
            return provider + "|url|" + projectUrl;
        }
        String displayName = normalizeIdentityPart(entry.displayName());
        return displayName == null ? null : provider + "|name|" + displayName;
    }

    private Comparator<ExtensionCatalogEntry> downloadsComparator(ExtensionCatalogQuery query) {
        String searchText = normalizeSearchText(query == null ? null : query.queryText());
        return Comparator
                .comparingLong((ExtensionCatalogEntry entry) -> Math.max(0L, entry.downloads()))
                .reversed()
                .thenComparingInt(entry -> relevanceRank(entry, searchText))
                .thenComparing(entry -> safeLower(entry.displayName()))
                .thenComparing(entry -> safeLower(entry.providerId()))
                .thenComparing(entry -> safeLower(entry.projectId()));
    }

    private Comparator<ExtensionCatalogEntry> searchRelevanceComparator(ExtensionCatalogQuery query) {
        String searchText = normalizeSearchText(query == null ? null : query.queryText());
        return Comparator
                .comparingInt((ExtensionCatalogEntry entry) -> relevanceRank(entry, searchText))
                .thenComparing(entry -> safeLower(entry.displayName()))
                .thenComparing(entry -> safeLower(entry.providerId()))
                .thenComparing(entry -> safeLower(entry.projectId()));
    }

    private int relevanceRank(ExtensionCatalogEntry entry, String searchText) {
        if (entry == null || searchText == null) {
            return 0;
        }
        String displayName = normalizeSearchText(entry.displayName());
        String projectId = normalizeSearchText(entry.projectId());
        String author = normalizeSearchText(entry.author());
        String description = normalizeSearchText(entry.description());
        if (displayName != null && displayName.equals(searchText) || projectId != null && projectId.equals(searchText)) {
            return 0;
        }
        if (displayName != null && displayName.contains(searchText) || projectId != null && projectId.contains(searchText)) {
            return 1;
        }
        if (author != null && author.contains(searchText)) {
            return 2;
        }
        if (description != null && description.contains(searchText)) {
            return 3;
        }
        return 4;
    }

    private boolean matchesQuerySafety(ExtensionCatalogQuery query, ExtensionCatalogEntry entry) {
        if (entry == null) {
            return false;
        }
        ServerExtensionType requestedType = query == null || query.extensionType() == null
                ? ServerExtensionType.UNKNOWN
                : query.extensionType();
        if (requestedType != ServerExtensionType.UNKNOWN
                && entry.extensionType() != null
                && entry.extensionType() != ServerExtensionType.UNKNOWN
                && entry.extensionType() != requestedType) {
            return false;
        }
        ServerPlatform requestedPlatform = query == null || query.platform() == null
                ? ServerPlatform.UNKNOWN
                : query.platform();
        if (requestedPlatform != ServerPlatform.UNKNOWN
                && entry.compatiblePlatforms() != null
                && !entry.compatiblePlatforms().isEmpty()
                && entry.compatiblePlatforms().stream()
                .filter(platform -> platform != null && platform != ServerPlatform.UNKNOWN)
                .noneMatch(platform -> arePlatformsCompatible(requestedPlatform, platform))) {
            return false;
        }
        return true;
    }

    private boolean matchesServerSafety(Server server, ExtensionDownloadPlan plan) {
        if (plan == null) {
            return false;
        }
        ServerPlatform serverPlatform = server == null || server.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : server.getPlatform();
        ServerEcosystemType ecosystem = server == null || server.getEcosystemType() == null
                ? ServerEcosystemType.UNKNOWN
                : server.getEcosystemType();
        if (ecosystem == ServerEcosystemType.UNKNOWN && serverPlatform != ServerPlatform.UNKNOWN) {
            ecosystem = serverPlatform.getDefaultEcosystemType();
        }
        ServerExtensionType requestedType = switch (ecosystem) {
            case MODS -> ServerExtensionType.MOD;
            case PLUGINS -> ServerExtensionType.PLUGIN;
            default -> ServerExtensionType.UNKNOWN;
        };
        if (requestedType == ServerExtensionType.UNKNOWN) {
            return false;
        }
        ServerExtensionType planType = plan.extensionType() == null ? ServerExtensionType.UNKNOWN : plan.extensionType();
        if (planType == ServerExtensionType.UNKNOWN || planType != requestedType) {
            return false;
        }
        return serverPlatform == ServerPlatform.UNKNOWN
                || plan.platform() == null
                || plan.platform() == ServerPlatform.UNKNOWN
                || arePlatformsCompatible(serverPlatform, plan.platform());
    }

    private boolean arePlatformsCompatible(ServerPlatform serverPlatform, ServerPlatform extensionPlatform) {
        if (serverPlatform == null || extensionPlatform == null) {
            return false;
        }
        if (serverPlatform == extensionPlatform) {
            return true;
        }
        ServerPlatform canonicalServer = canonicalizePluginPlatform(serverPlatform);
        ServerPlatform canonicalExtension = canonicalizePluginPlatform(extensionPlatform);
        return canonicalServer == canonicalExtension;
    }

    private ServerPlatform canonicalizePluginPlatform(ServerPlatform platform) {
        return switch (platform == null ? ServerPlatform.UNKNOWN : platform) {
            case PURPUR, PUFFERFISH -> ServerPlatform.PAPER;
            default -> platform;
        };
    }

    private String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIdentityPart(String value) {
        return normalizeSearchText(value);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private <T> T resolveCached(String cacheKey,
                                Map<String, CacheEntry<T>> cache,
                                Map<String, FutureTask<T>> inFlight,
                                CacheLoader<T> loader) throws IOException {
        CacheEntry<T> cached = cache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            return cached.value();
        }

        FutureTask<T> newTask = new FutureTask<>(loader::load);
        FutureTask<T> task = inFlight.putIfAbsent(cacheKey, newTask);
        boolean owner = task == null;
        if (owner) {
            task = newTask;
            task.run();
        }

        try {
            T resolved = task.get();
            cache.put(cacheKey, new CacheEntry<>(resolved));
            return resolved;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("La carga del marketplace ha sido interrumpida.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioEx) {
                throw ioEx;
            }
            throw new IOException("No se ha podido completar la carga del marketplace.", cause);
        } finally {
            if (owner) {
                inFlight.remove(cacheKey, task);
            }
        }
    }

    @FunctionalInterface
    private interface CacheLoader<T> {
        T load() throws IOException;
    }

    private record CacheEntry<T>(T value, long createdAtEpochMillis) {
        private CacheEntry(T value) {
            this(value, System.currentTimeMillis());
        }

        private boolean expired() {
            return System.currentTimeMillis() - createdAtEpochMillis > CACHE_TTL_MILLIS;
        }
    }
}
