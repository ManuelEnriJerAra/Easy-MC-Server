package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

abstract class AbstractStubExtensionCatalogProvider implements ExtensionCatalogProvider {
    private final String providerId;
    private final String displayName;
    private final ExtensionSourceType sourceType;
    private final List<ExtensionCatalogDetails> catalog;

    protected AbstractStubExtensionCatalogProvider(String providerId,
                                                   String displayName,
                                                   ExtensionSourceType sourceType,
                                                   List<ExtensionCatalogDetails> catalog) {
        this.providerId = providerId;
        this.displayName = displayName;
        this.sourceType = sourceType;
        this.catalog = catalog == null ? List.of() : List.copyOf(catalog);
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public ExtensionSourceType getSourceType() {
        return sourceType;
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
    public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) {
        String normalizedQuery = normalize(query == null ? null : query.queryText());
        List<ExtensionCatalogEntry> matches = new ArrayList<>();
        for (ExtensionCatalogDetails details : catalog) {
            ExtensionCatalogEntry entry = details.entry();
            if (!matchesQuery(entry, details, normalizedQuery)) {
                continue;
            }
            if (!matchesPlatform(query, entry)) {
                continue;
            }
            if (!matchesType(query, entry)) {
                continue;
            }
            if (!matchesMinecraftVersion(query, entry)) {
                continue;
            }
            matches.add(entry);
        }
        int limit = query == null ? 20 : query.limit();
        return matches.stream().limit(limit).toList();
    }

    @Override
    public Optional<ExtensionCatalogDetails> getDetails(String projectId,
                                                        ExtensionCatalogQuery query) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        return catalog.stream()
                .filter(details -> projectId.equalsIgnoreCase(details.entry().projectId()))
                .findFirst();
    }

    @Override
    public Optional<ExtensionDownloadPlan> resolveDownload(String projectId,
                                                           String versionId,
                                                           Server server) {
        Optional<ExtensionCatalogDetails> details = getDetails(projectId, null);
        if (details.isEmpty()) {
            return Optional.empty();
        }

        ExtensionCatalogVersion targetVersion = details.get().versions().stream()
                .filter(version -> versionId == null || versionId.isBlank() || versionId.equalsIgnoreCase(version.versionId()))
                .findFirst()
                .orElse(details.get().versions().isEmpty() ? null : details.get().versions().getFirst());

        if (targetVersion == null) {
            return Optional.empty();
        }

        String message = "Descarga preparada para integrar el proveedor " + displayName + " cuando se active la API real.";
        if (server != null && server.getEcosystemType() == ServerEcosystemType.NONE) {
            message = "El servidor actual no admite mods ni plugins, pero el contrato de descarga ya esta resuelto.";
        }

        return Optional.of(new ExtensionDownloadPlan(
                providerId,
                projectId,
                targetVersion.versionId(),
                targetVersion.versionNumber(),
                details.get().entry().iconUrl(),
                targetVersion.fileName(),
                targetVersion.downloadUrl(),
                sourceType,
                details.get().entry().extensionType(),
                inferPlatform(targetVersion.supportedPlatforms()),
                joinVersions(targetVersion.supportedMinecraftVersions()),
                true,
                message
        ));
    }

    private ServerPlatform inferPlatform(Set<ServerPlatform> platforms) {
        return platforms == null || platforms.isEmpty() ? ServerPlatform.UNKNOWN : platforms.iterator().next();
    }

    private String joinVersions(Set<String> versions) {
        return versions == null || versions.isEmpty() ? null : String.join(" || ", versions);
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
            if (!providerId.equalsIgnoreCase(installed.getSource().getProvider())) {
                continue;
            }
            String projectId = installed.getSource().getProjectId();
            if (projectId == null || projectId.isBlank()) {
                continue;
            }
            Optional<ExtensionCatalogDetails> details = getDetails(projectId, null);
            if (details.isEmpty() || details.get().versions().isEmpty()) {
                continue;
            }
            ExtensionCatalogVersion latest = details.get().versions().getFirst();
            String installedVersion = normalize(installed.getVersion());
            String remoteVersion = normalize(latest.versionNumber());
            boolean updateAvailable = remoteVersion != null && !remoteVersion.equals(installedVersion);
            updates.add(new ExtensionUpdateCandidate(
                    providerId,
                    projectId,
                    installed,
                    latest,
                    updateAvailable,
                    updateAvailable
                            ? "Hay una version remota mas reciente disponible."
                            : "La extension ya coincide con la ultima version conocida del catalogo."
            ));
        }
        return updates;
    }

    protected static ExtensionCatalogDetails createDetails(String providerId,
                                                           ExtensionSourceType sourceType,
                                                           String projectId,
                                                           String displayName,
                                                           String author,
                                                           String version,
                                                           String description,
                                                           ServerExtensionType extensionType,
                                                           Set<ServerPlatform> platforms,
                                                           Set<String> minecraftVersions,
                                                           String projectUrl,
                                                           String issuesUrl,
                                                           String licenseName,
                                                           Set<String> categories,
                                                           String fileName) {
        ExtensionCatalogEntry entry = new ExtensionCatalogEntry(
                providerId,
                projectId,
                projectId + "-latest",
                displayName,
                author,
                version,
                description,
                sourceType,
                extensionType,
                platforms,
                minecraftVersions,
                null,
                projectUrl,
                projectUrl + "/download/" + fileName
        );
        ExtensionCatalogVersion catalogVersion = new ExtensionCatalogVersion(
                providerId,
                projectId,
                entry.versionId(),
                displayName + " " + version,
                version,
                platforms,
                minecraftVersions,
                "Version de ejemplo preparada para el futuro cliente HTTP de " + displayName + ".",
                fileName,
                entry.downloadUrl(),
                System.currentTimeMillis()
        );
        return new ExtensionCatalogDetails(
                entry,
                description,
                projectUrl,
                issuesUrl,
                licenseName,
                categories,
                List.of(catalogVersion)
        );
    }

    private boolean matchesQuery(ExtensionCatalogEntry entry,
                                 ExtensionCatalogDetails details,
                                 String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        return contains(entry.displayName(), normalizedQuery)
                || contains(entry.author(), normalizedQuery)
                || contains(entry.description(), normalizedQuery)
                || contains(details.summary(), normalizedQuery)
                || details.categories().stream().anyMatch(category -> contains(category, normalizedQuery));
    }

    private boolean matchesPlatform(ExtensionCatalogQuery query, ExtensionCatalogEntry entry) {
        if (query == null || query.platform() == null || query.platform() == ServerPlatform.UNKNOWN) {
            return true;
        }
        return entry.compatiblePlatforms().isEmpty() || entry.compatiblePlatforms().contains(query.platform());
    }

    private boolean matchesType(ExtensionCatalogQuery query, ExtensionCatalogEntry entry) {
        if (query == null || query.extensionType() == null || query.extensionType() == ServerExtensionType.UNKNOWN) {
            return true;
        }
        return entry.extensionType() == query.extensionType();
    }

    private boolean matchesMinecraftVersion(ExtensionCatalogQuery query, ExtensionCatalogEntry entry) {
        if (query == null || query.minecraftVersion() == null || query.minecraftVersion().isBlank()) {
            return true;
        }
        return entry.compatibleMinecraftVersions().isEmpty()
                || entry.compatibleMinecraftVersions().contains(query.minecraftVersion().trim());
    }

    private boolean contains(String text, String normalizedQuery) {
        String normalizedText = normalize(text);
        return normalizedText != null && normalizedText.contains(normalizedQuery);
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
