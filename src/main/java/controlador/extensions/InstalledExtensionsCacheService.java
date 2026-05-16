package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionRemoteDependency;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InstalledExtensionsCacheService {
    private static final Logger LOGGER = Logger.getLogger(InstalledExtensionsCacheService.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CACHE_FILE_NAME = "dora-extensions.json";
    private static final int CACHE_SCHEMA_VERSION = 1;

    private final Map<Path, MemoryCacheEntry> memoryCache = new ConcurrentHashMap<>();

    public List<ServerExtension> load(Server server) {
        Path cacheFile = resolveReadableCacheFile(server);
        if (cacheFile == null || !Files.isRegularFile(cacheFile)) {
            if (cacheFile != null) {
                memoryCache.remove(cacheFile);
            }
            return List.of();
        }
        try {
            long lastModified = Files.getLastModifiedTime(cacheFile).toMillis();
            MemoryCacheEntry memoryEntry = memoryCache.get(cacheFile);
            if (memoryEntry != null && memoryEntry.lastModifiedEpochMillis() == lastModified) {
                return copyExtensions(memoryEntry.extensions());
            }
            CacheSnapshot snapshot = OBJECT_MAPPER.readValue(cacheFile.toFile(), CacheSnapshot.class);
            List<ServerExtension> normalized = normalizeExtensions(snapshot == null ? null : snapshot.extensions());
            memoryCache.put(cacheFile, new MemoryCacheEntry(lastModified, toContentSignature(normalized), copyExtensions(normalized)));
            return copyExtensions(normalized);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se ha podido leer la cache local de extensiones: " + cacheFile, ex);
            return List.of();
        }
    }

    public void persist(Server server, List<ServerExtension> extensions) throws IOException {
        Path cacheFile = resolveCacheFile(server);
        if (cacheFile == null) {
            return;
        }
        Files.createDirectories(cacheFile.getParent());
        List<ServerExtension> normalized = normalizeExtensions(extensions);
        String contentSignature = toContentSignature(normalized);
        MemoryCacheEntry memoryEntry = memoryCache.get(cacheFile);
        if (memoryEntry != null && Objects.equals(memoryEntry.contentSignature(), contentSignature)) {
            return;
        }
        if (memoryEntry == null && Files.isRegularFile(cacheFile)) {
            try {
                CacheSnapshot current = OBJECT_MAPPER.readValue(cacheFile.toFile(), CacheSnapshot.class);
                String currentSignature = toContentSignature(normalizeExtensions(current == null ? null : current.extensions()));
                if (Objects.equals(currentSignature, contentSignature)) {
                    long lastModified = Files.getLastModifiedTime(cacheFile).toMillis();
                    memoryCache.put(cacheFile, new MemoryCacheEntry(lastModified, contentSignature, copyExtensions(normalized)));
                    return;
                }
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "No se ha podido comparar la cache de extensiones existente.", ex);
            }
        }

        Path tempFile = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        Files.writeString(tempFile, toJson(normalized));
        Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        long lastModified = safeLastModified(cacheFile);
        memoryCache.put(cacheFile, new MemoryCacheEntry(lastModified, contentSignature, copyExtensions(normalized)));
    }

    private Path resolveCacheFile(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        try {
            return Path.of(server.getServerDir()).resolve(CACHE_FILE_NAME);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se ha podido resolver la ruta de la cache local de extensiones.", ex);
            return null;
        }
    }

    private Path resolveReadableCacheFile(Server server) {
        return resolveCacheFile(server);
    }

    private List<ServerExtension> normalizeExtensions(List<ServerExtension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return List.of();
        }
        Map<String, ServerExtension> deduplicated = new HashMap<>();
        for (ServerExtension extension : extensions) {
            if (extension == null) {
                continue;
            }
            ServerExtension copy = copyExtension(extension);
            normalizeExtension(copy);
            String key = cacheKey(copy);
            if (key == null) {
                key = UUID.randomUUID().toString();
            }
            ServerExtension existing = deduplicated.get(key);
            deduplicated.put(key, existing == null ? copy : mergeExtensions(existing, copy));
        }
        return deduplicated.values().stream()
                .sorted((left, right) -> compareNullable(resolveRelativePath(left), resolveRelativePath(right)))
                .toList();
    }

    private void normalizeExtension(ServerExtension extension) {
        if (extension.getSource() == null) {
            extension.setSource(new ExtensionSource());
        }
        if (extension.getLocalMetadata() == null) {
            extension.setLocalMetadata(new ExtensionLocalMetadata());
        }
        if (extension.getId() == null || extension.getId().isBlank()) {
            extension.setId(UUID.randomUUID().toString());
        }
        ExtensionLocalMetadata metadata = extension.getLocalMetadata();
        if (metadata.getEnabled() == null) {
            metadata.setEnabled(Boolean.TRUE);
        }
        if (metadata.getUpdateState() == null) {
            metadata.setUpdateState(ExtensionUpdateState.UNKNOWN);
        }
        if (metadata.getCategories() == null) {
            metadata.setCategories(new ArrayList<>());
        }
        if (metadata.getDependencies() == null) {
            metadata.setDependencies(new ArrayList<>());
        }
    }

    private ServerExtension mergeExtensions(ServerExtension base, ServerExtension candidate) {
        base.setDisplayName(firstNonBlank(base.getDisplayName(), candidate.getDisplayName()));
        base.setVersion(firstNonBlank(base.getVersion(), candidate.getVersion()));
        base.setDescription(preferLonger(base.getDescription(), candidate.getDescription()));
        base.setFileName(firstNonBlank(base.getFileName(), candidate.getFileName()));
        if (base.getExtensionType() == null || base.getExtensionType() == ServerExtensionType.UNKNOWN) {
            base.setExtensionType(candidate.getExtensionType());
        }
        if (base.getPlatform() == null || base.getPlatform() == ServerPlatform.UNKNOWN) {
            base.setPlatform(candidate.getPlatform());
        }
        if (base.getInstallState() == null || base.getInstallState().name().equals("UNKNOWN")) {
            base.setInstallState(candidate.getInstallState());
        }
        if (base.getSource() == null) {
            base.setSource(new ExtensionSource());
        }
        if (candidate.getSource() != null) {
            mergeSource(base.getSource(), candidate.getSource());
        }
        if (base.getLocalMetadata() == null) {
            base.setLocalMetadata(new ExtensionLocalMetadata());
        }
        if (candidate.getLocalMetadata() != null) {
            mergeLocalMetadata(base.getLocalMetadata(), candidate.getLocalMetadata());
        }
        return base;
    }

    private void mergeSource(ExtensionSource target, ExtensionSource candidate) {
        if (candidate == null || target == null) {
            return;
        }
        if (target.getType() == null || target.getType().name().equals("UNKNOWN")) {
            target.setType(candidate.getType());
        }
        target.setProvider(firstNonBlank(target.getProvider(), candidate.getProvider()));
        target.setProjectId(firstNonBlank(target.getProjectId(), candidate.getProjectId()));
        target.setVersionId(firstNonBlank(target.getVersionId(), candidate.getVersionId()));
        target.setUrl(firstNonBlank(target.getUrl(), candidate.getUrl()));
        target.setProjectUrl(firstNonBlank(target.getProjectUrl(), candidate.getProjectUrl()));
        target.setIssuesUrl(firstNonBlank(target.getIssuesUrl(), candidate.getIssuesUrl()));
        target.setWebsiteUrl(firstNonBlank(target.getWebsiteUrl(), candidate.getWebsiteUrl()));
        target.setLicenseName(firstNonBlank(target.getLicenseName(), candidate.getLicenseName()));
        target.setAuthor(firstNonBlank(target.getAuthor(), candidate.getAuthor()));
        target.setIconUrl(firstNonBlank(target.getIconUrl(), candidate.getIconUrl()));
    }

    private void mergeLocalMetadata(ExtensionLocalMetadata target, ExtensionLocalMetadata candidate) {
        target.setRelativePath(firstNonBlank(target.getRelativePath(), candidate.getRelativePath()));
        target.setFileName(firstNonBlank(target.getFileName(), candidate.getFileName()));
        target.setFileSizeBytes(firstNonNull(target.getFileSizeBytes(), candidate.getFileSizeBytes()));
        target.setSha256(firstNonBlank(target.getSha256(), candidate.getSha256()));
        target.setMinecraftVersionConstraint(firstNonBlank(target.getMinecraftVersionConstraint(), candidate.getMinecraftVersionConstraint()));
        target.setInstalledVersion(firstNonBlank(target.getInstalledVersion(), candidate.getInstalledVersion()));
        target.setKnownRemoteVersion(firstNonBlank(target.getKnownRemoteVersion(), candidate.getKnownRemoteVersion()));
        target.setKnownRemoteVersionId(firstNonBlank(target.getKnownRemoteVersionId(), candidate.getKnownRemoteVersionId()));
        target.setDiscoveredAtEpochMillis(firstNonNull(target.getDiscoveredAtEpochMillis(), candidate.getDiscoveredAtEpochMillis()));
        target.setLastUpdatedAtEpochMillis(maxLong(target.getLastUpdatedAtEpochMillis(), candidate.getLastUpdatedAtEpochMillis()));
        target.setLastCheckedForUpdatesAtEpochMillis(maxLong(target.getLastCheckedForUpdatesAtEpochMillis(), candidate.getLastCheckedForUpdatesAtEpochMillis()));
        target.setLastMetadataSyncAtEpochMillis(maxLong(target.getLastMetadataSyncAtEpochMillis(), candidate.getLastMetadataSyncAtEpochMillis()));
        target.setEnabled(firstNonNull(target.getEnabled(), candidate.getEnabled()));
        target.setUpdateState(target.getUpdateState() == null ? candidate.getUpdateState() : target.getUpdateState());
        target.setUpdateMessage(firstNonBlank(target.getUpdateMessage(), candidate.getUpdateMessage()));
        target.setDownloadCount(maxLong(target.getDownloadCount(), candidate.getDownloadCount()));
        target.setClientSide(firstNonBlank(target.getClientSide(), candidate.getClientSide()));
        target.setServerSide(firstNonBlank(target.getServerSide(), candidate.getServerSide()));
        target.setLocalIconUrl(firstNonBlank(target.getLocalIconUrl(), candidate.getLocalIconUrl()));
        target.setLocalIconPath(firstNonBlank(target.getLocalIconPath(), candidate.getLocalIconPath()));
        target.setWebsiteUrl(firstNonBlank(target.getWebsiteUrl(), candidate.getWebsiteUrl()));
        target.setIssuesUrl(firstNonBlank(target.getIssuesUrl(), candidate.getIssuesUrl()));
        target.setLicenseName(firstNonBlank(target.getLicenseName(), candidate.getLicenseName()));
        if ((target.getCategories() == null || target.getCategories().isEmpty()) && candidate.getCategories() != null) {
            target.setCategories(new ArrayList<>(candidate.getCategories()));
        }
        if ((target.getSupportedLoaders() == null || target.getSupportedLoaders().isEmpty()) && candidate.getSupportedLoaders() != null) {
            target.setSupportedLoaders(new ArrayList<>(candidate.getSupportedLoaders()));
        }
        if ((target.getSupportedMinecraftVersions() == null || target.getSupportedMinecraftVersions().isEmpty()) && candidate.getSupportedMinecraftVersions() != null) {
            target.setSupportedMinecraftVersions(new ArrayList<>(candidate.getSupportedMinecraftVersions()));
        }
        if ((target.getEmbeddedMetadataFiles() == null || target.getEmbeddedMetadataFiles().isEmpty()) && candidate.getEmbeddedMetadataFiles() != null) {
            target.setEmbeddedMetadataFiles(new ArrayList<>(candidate.getEmbeddedMetadataFiles()));
        }
        if ((target.getDependencies() == null || target.getDependencies().isEmpty()) && candidate.getDependencies() != null) {
            List<ExtensionRemoteDependency> copied = new ArrayList<>();
            for (ExtensionRemoteDependency dependency : candidate.getDependencies()) {
                copied.add(copyDependency(dependency));
            }
            target.setDependencies(copied);
        }
    }

    private String cacheKey(ServerExtension extension) {
        String relativePath = normalize(resolveRelativePath(extension));
        if (relativePath != null) {
            return "path:" + relativePath;
        }
        ExtensionSource source = extension.getSource();
        String provider = normalize(source == null ? null : source.getProvider());
        String projectId = normalize(source == null ? null : source.getProjectId());
        if (provider != null && projectId != null) {
            return "remote:" + provider + "::" + projectId;
        }
        String fileName = normalize(extension.getFileName());
        if (fileName != null) {
            return "file:" + fileName;
        }
        String sha256 = normalize(extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getSha256());
        return sha256 == null ? null : "sha:" + sha256;
    }

    private String resolveRelativePath(ServerExtension extension) {
        return extension == null || extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getRelativePath();
    }

    private String toJson(List<ServerExtension> normalized) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
                new CacheSnapshot(CACHE_SCHEMA_VERSION, System.currentTimeMillis(), normalized == null ? List.of() : normalized)
        );
    }

    private String toContentSignature(List<ServerExtension> normalized) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(normalized == null ? List.of() : normalized);
    }

    private List<ServerExtension> copyExtensions(List<ServerExtension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return List.of();
        }
        List<ServerExtension> copied = new ArrayList<>();
        for (ServerExtension extension : extensions) {
            copied.add(copyExtension(extension));
        }
        return copied;
    }

    private ServerExtension copyExtension(ServerExtension extension) {
        return extension == null ? null : OBJECT_MAPPER.convertValue(extension, ServerExtension.class);
    }

    private ExtensionRemoteDependency copyDependency(ExtensionRemoteDependency dependency) {
        return dependency == null ? null : OBJECT_MAPPER.convertValue(dependency, ExtensionRemoteDependency.class);
    }

    private long safeLastModified(Path cacheFile) {
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(cacheFile);
            return lastModifiedTime.toMillis();
        } catch (IOException ex) {
            return System.currentTimeMillis();
        }
    }

    private String preferLonger(String current, String candidate) {
        if (current == null || current.isBlank()) {
            return candidate;
        }
        if (candidate == null || candidate.isBlank()) {
            return current;
        }
        return candidate.length() > current.length() ? candidate : current;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private Long maxLong(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private int compareNullable(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return String.CASE_INSENSITIVE_ORDER.compare(left, right);
    }

    private record CacheSnapshot(int schemaVersion, long updatedAtEpochMillis, List<ServerExtension> extensions) {
    }

    private record MemoryCacheEntry(long lastModifiedEpochMillis, String contentSignature, List<ServerExtension> extensions) {
    }
}
