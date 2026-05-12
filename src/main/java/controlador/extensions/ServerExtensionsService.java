package controlador.extensions;

import controlador.platform.FileDownloader;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import modelo.Server;
import modelo.extensions.ExtensionInstallState;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionRemoteDependency;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerExtensionsService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String YAML_STRING_PATTERN = "(?m)^\\s*%s\\s*:\\s*(.+?)\\s*$";
    private static final Pattern MINECRAFT_VERSION_HINT_PATTERN = Pattern.compile("(?i)(?<![\\d.])(1\\.(?:1[0-9]|2[0-9])(?:\\.\\d+)?)(?![\\d.])");
    private static final Logger LOGGER = Logger.getLogger(ServerExtensionsService.class.getName());

    private final InstalledExtensionsCacheService installedExtensionsCacheService = new InstalledExtensionsCacheService();
    private final Map<String, CachedJarMetadata> jarMetadataCache = new ConcurrentHashMap<>();

    public List<Path> getManagedExtensionDirectories(Server server) {
        ServerPlatformProfile profile = resolveProfile(server);
        if (profile != null && profile.extensionDirectories() != null && !profile.extensionDirectories().isEmpty()) {
            return profile.extensionDirectories().stream()
                    .filter(Objects::nonNull)
                    .toList();
        }

        ServerPlatformAdapter adapter = resolveAdapter(server, profile);
        List<Path> directories = adapter.getExtensionDirectories(resolveServerDir(server));
        if (directories == null || directories.isEmpty()) {
            return List.of();
        }
        return directories.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean supportsManagedExtensions(Server server) {
        if (server == null) {
            return false;
        }
        Set<ServerCapability> capabilities = server.getCapabilities();
        if (capabilities != null && capabilities.contains(ServerCapability.EXTENSIONS)) {
            return !getManagedExtensionDirectories(server).isEmpty();
        }
        return !getManagedExtensionDirectories(server).isEmpty();
    }

    public List<ServerExtension> detectInstalledExtensions(Server server) throws IOException {
        List<Path> extensionDirectories = getManagedExtensionDirectories(server);
        if (extensionDirectories.isEmpty()) {
            return List.of();
        }

        Map<String, ServerExtension> existingByRelativePath = new HashMap<>();
        Map<String, ServerExtension> existingByFileName = new HashMap<>();
        indexStoredExtensions(existingByRelativePath, existingByFileName, installedExtensionsCacheService.load(server));
        if (server.getExtensions() != null) {
            indexStoredExtensions(existingByRelativePath, existingByFileName, server.getExtensions());
        }

        Path serverDir = resolveServerDir(server);
        ServerPlatformProfile profile = resolveProfile(server);
        ServerPlatform platform = resolvePlatform(server, profile);
        ServerEcosystemType ecosystemType = resolveEcosystemType(server, profile);
        long now = System.currentTimeMillis();
        List<ServerExtension> detected = new ArrayList<>();
        Set<String> detectedRelativePaths = new LinkedHashSet<>();

        for (Path extensionDirectory : extensionDirectories) {
            if (!Files.isDirectory(extensionDirectory)) {
                continue;
            }
            try (var children = Files.list(extensionDirectory)) {
                for (Path candidate : children
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList()) {
                    String relativePath = normalizeRelativePath(serverDir.relativize(candidate).toString());
                    ServerExtension existing = relativePath == null ? null : existingByRelativePath.get(relativePath);
                    if (existing == null) {
                        existing = existingByFileName.get(normalizeFileName(candidate.getFileName().toString()));
                    }
                    if (relativePath != null) {
                        detectedRelativePaths.add(relativePath);
                    }
                    detected.add(readExtensionMetadata(
                            candidate,
                            relativePath,
                            existing,
                            platform,
                            ecosystemType,
                            ExtensionInstallState.DISCOVERED,
                            ExtensionSourceType.LOCAL_FILE,
                            now
                    ));
                }
            }
        }
        for (ServerExtension existing : existingByRelativePath.values()) {
            if (existing == null || existing.getLocalMetadata() == null) {
                continue;
            }
            String relativePath = normalizeRelativePath(existing.getLocalMetadata().getRelativePath());
            if (relativePath == null || detectedRelativePaths.contains(relativePath)) {
                continue;
            }
            Path candidate = serverDir.resolve(relativePath.replace('/', java.io.File.separatorChar));
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            ServerPlatform preservedPlatform = existing.getPlatform() == null || existing.getPlatform() == ServerPlatform.UNKNOWN
                    ? platform
                    : existing.getPlatform();
            ServerEcosystemType preservedEcosystem = ecosystemForExtension(existing, ecosystemType);
            detected.add(readExtensionMetadata(
                    candidate,
                    relativePath,
                    existing,
                    preservedPlatform,
                    preservedEcosystem,
                    ExtensionInstallState.DISCOVERED,
                    ExtensionSourceType.LOCAL_FILE,
                    now
            ));
        }
        return detected;
    }

    private ServerEcosystemType ecosystemForExtension(ServerExtension extension, ServerEcosystemType fallback) {
        ServerExtensionType type = extension == null || extension.getExtensionType() == null
                ? ServerExtensionType.UNKNOWN
                : extension.getExtensionType();
        return switch (type) {
            case MOD -> ServerEcosystemType.MODS;
            case PLUGIN -> ServerEcosystemType.PLUGINS;
            case UNKNOWN -> fallback == null ? ServerEcosystemType.UNKNOWN : fallback;
        };
    }

    public ServerExtension installManualJar(Server server, Path sourceJar) throws IOException {
        return installJar(server, sourceJar, null, ExtensionSourceType.MANUAL, "local-manual");
    }

    public ServerExtension installCatalogDownload(Server server,
                                                  ExtensionDownloadPlan downloadPlan,
                                                  FileDownloader downloader) throws IOException {
        if (downloadPlan == null || !downloadPlan.ready()) {
            throw new IOException("No hay un plan de descarga válido para la extensión.");
        }
        if (downloadPlan.downloadUrl() == null || downloadPlan.downloadUrl().isBlank()) {
            throw new IOException("La extensión externa no incluye una URL de descarga válida.");
        }
        if (downloader == null) {
            throw new IOException("No se ha indicado un descargador para instalar la extensión externa.");
        }
        ensureCatalogPlanCompatibleWithServer(server, downloadPlan);
        ensureCatalogExtensionNotAlreadyInstalled(server, downloadPlan);

        Path tempJar = Files.createTempFile("easymc-extension-", ".jar");
        try {
            downloader.download(downloadPlan.downloadUrl(), tempJar.toFile());
            validateDownloadedJar(tempJar, downloadPlan);
            return installJar(server, tempJar, downloadPlan, downloadPlan.sourceType(), downloadPlan.providerId());
        } finally {
            Files.deleteIfExists(tempJar);
        }
    }

    public ExtensionInstallResolution evaluateCatalogInstallation(Server server,
                                                                  ExtensionCatalogEntry entry) {
        if (entry == null) {
            return new ExtensionInstallResolution(ExtensionInstallResolutionState.AVAILABLE, null, null, null, null, null);
        }
        ExtensionInstallResolution compatibility = evaluateCatalogCompatibility(
                server,
                entry.extensionType(),
                entry.compatiblePlatforms(),
                entry.compatibleMinecraftVersions()
        );
        if (compatibility != null) {
            return compatibility;
        }
        return evaluateCatalogInstallation(
                server,
                entry.providerId(),
                entry.projectId(),
                entry.versionId(),
                entry.version(),
                null,
                firstNonBlank(entry.displayName(), entry.projectId(), "La extensión")
        );
    }

    public ExtensionInstallResolution evaluateCatalogInstallation(Server server,
                                                                  ExtensionDownloadPlan downloadPlan) {
        if (downloadPlan == null) {
            return new ExtensionInstallResolution(ExtensionInstallResolutionState.AVAILABLE, null, null, null, null, null);
        }
        ExtensionInstallResolution compatibility = evaluateCatalogCompatibility(
                server,
                downloadPlan.extensionType(),
                downloadPlan.platform() == null || downloadPlan.platform() == ServerPlatform.UNKNOWN
                        ? Set.of()
                        : Set.of(downloadPlan.platform()),
                minecraftVersionsFromConstraint(downloadPlan.minecraftVersionConstraint())
        );
        if (compatibility != null) {
            return compatibility;
        }
        return evaluateCatalogInstallation(
                server,
                downloadPlan.providerId(),
                downloadPlan.projectId(),
                downloadPlan.versionId(),
                downloadPlan.versionNumber(),
                downloadPlan.fileName(),
                firstNonBlank(downloadPlan.projectId(), "La extensión")
        );
    }

    public boolean applyUpdateMetadata(Server server, List<ExtensionUpdateCandidate> updates) {
        if (server == null || server.getExtensions() == null || server.getExtensions().isEmpty()) {
            return false;
        }

        Map<String, ExtensionUpdateCandidate> updatesByExtensionId = new HashMap<>();
        if (updates != null) {
            for (ExtensionUpdateCandidate update : updates) {
                if (update == null || update.installedExtension() == null) {
                    continue;
                }
                String extensionId = update.installedExtension().getId();
                if (extensionId != null && !extensionId.isBlank()) {
                    updatesByExtensionId.put(extensionId, update);
                }
            }
        }

        long now = System.currentTimeMillis();
        boolean changed = false;
        for (ServerExtension extension : server.getExtensions()) {
            if (extension == null) {
                continue;
            }
            ExtensionLocalMetadata metadata = ensureLocalMetadata(extension);
            changed |= updateTrackingSnapshot(extension, metadata, now);

            ExtensionSource source = ensureSource(extension);
            if (!hasTrackedRemoteOrigin(source)) {
                changed |= applyTrackingState(metadata, null, null, now,
                        ExtensionUpdateState.UNTRACKED,
                        "Instalada sin origen remoto conocido.");
                continue;
            }

            ExtensionUpdateCandidate candidate = updatesByExtensionId.get(extension.getId());
            if (candidate == null || candidate.targetVersion() == null) {
                changed |= applyTrackingState(metadata,
                        metadata.getKnownRemoteVersion(),
                        metadata.getKnownRemoteVersionId(),
                        now,
                        ExtensionUpdateState.UNKNOWN,
                        "No se ha podido determinar una versión remota compatible.");
                continue;
            }

            changed |= applyTrackingState(metadata,
                    candidate.targetVersion().versionNumber(),
                    candidate.targetVersion().versionId(),
                    now,
                    candidate.updateAvailable() ? ExtensionUpdateState.UPDATE_AVAILABLE : ExtensionUpdateState.UP_TO_DATE,
                    candidate.updateAvailable() ? candidate.message() : null);
        }
        if (changed) {
            persistInstalledExtensionCacheQuietly(server);
        }
        return changed;
    }

    public void persistInstalledExtensionCache(Server server) throws IOException {
        if (server == null) {
            return;
        }
        installedExtensionsCacheService.persist(server, server.getExtensions());
    }

    private ServerExtension installJar(Server server,
                                       Path sourceJar,
                                       ExtensionDownloadPlan downloadPlan,
                                       ExtensionSourceType sourceType,
                                       String defaultProvider) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        if (sourceJar == null || !Files.isRegularFile(sourceJar)) {
            throw new IOException("No se ha indicado un .jar de extensión válido.");
        }
        String fileName = resolveInstallFileName(sourceJar, downloadPlan);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Solo se admiten archivos .jar.");
        }
        validateLocalJar(sourceJar);

        ExtensionCompatibilityReport compatibility = validateCompatibility(server, sourceJar);
        if (compatibility.incompatible() && !canTrustCatalogPlanOverLocalType(server, sourceJar, downloadPlan)) {
            throw new IOException(compatibility.summary());
        }

        List<Path> extensionDirectories = getManagedExtensionDirectories(server);
        if (extensionDirectories.isEmpty()) {
            throw new IOException("La plataforma del servidor no admite extensiones gestionadas por Easy-MC-Server.");
        }

        Path targetDirectory = extensionDirectories.getFirst();
        Files.createDirectories(targetDirectory);
        Path targetJar = targetDirectory.resolve(fileName);
        Files.copy(sourceJar, targetJar, StandardCopyOption.REPLACE_EXISTING);

        ServerPlatformProfile profile = resolveProfile(server);
        ServerPlatform platform = resolvePlatform(server, profile);
        ServerEcosystemType ecosystemType = resolveEcosystemType(server, profile);
        long now = System.currentTimeMillis();
        ServerExtension installed = readExtensionMetadata(
                targetJar,
                normalizeRelativePath(resolveServerDir(server).relativize(targetJar).toString()),
                null,
                platform,
                ecosystemType,
                ExtensionInstallState.INSTALLED,
                sourceType == null ? ExtensionSourceType.MANUAL : sourceType,
                now
        );
        applySourceMetadata(installed, sourceJar, downloadPlan, defaultProvider);

        List<ServerExtension> refreshed = detectInstalledExtensions(server);
        boolean replaced = false;
        for (int i = 0; i < refreshed.size(); i++) {
            ServerExtension extension = refreshed.get(i);
            if (sameRelativePath(extension, installed)) {
                refreshed.set(i, mergeInstalledMetadata(extension, installed));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            refreshed.add(installed);
        }
        server.setExtensions(refreshed);
        persistInstalledExtensionCacheQuietly(server);
        return installed;
    }

    public ExtensionCompatibilityReport validateCompatibility(Server server, Path extensionJar) throws IOException {
        if (server == null) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    "No se ha indicado el servidor de destino.",
                    List.of("No existe contexto del servidor para validar la extensión.")
            );
        }
        if (extensionJar == null || !Files.isRegularFile(extensionJar)) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    "No se ha indicado un .jar de extensión válido.",
                    List.of("El archivo de la extensión no existe o no es accesible.")
            );
        }

        ServerPlatformProfile profile = resolveProfile(server);
        ServerPlatform platform = resolvePlatform(server, profile);
        ServerEcosystemType ecosystemType = resolveEcosystemType(server, profile);
        ServerExtension extension = readExtensionMetadata(
                extensionJar,
                extensionJar.getFileName().toString(),
                null,
                platform,
                ecosystemType,
                ExtensionInstallState.UNKNOWN,
                ExtensionSourceType.LOCAL_FILE,
                System.currentTimeMillis()
        );
        return validateCompatibility(server, extension);
    }

    public ExtensionCompatibilityReport validateCompatibility(Server server, ServerExtension extension) {
        if (server == null) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    "No se ha indicado el servidor de destino.",
                    List.of("No existe contexto del servidor para validar la extensión.")
            );
        }
        if (extension == null) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    "No se ha podido leer la extensión.",
                    List.of("No se han detectado metadatos suficientes para validar la extensión.")
            );
        }

        List<String> incompatibilities = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ServerPlatform serverPlatform = resolvePlatform(server, resolveProfile(server));
        ServerEcosystemType serverEcosystem = resolveEcosystemType(server, resolveProfile(server));
        ServerExtensionType extensionType = extension.getExtensionType() == null
                ? ServerExtensionType.UNKNOWN
                : extension.getExtensionType();
        ServerPlatform extensionPlatform = extension.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : extension.getPlatform();

        if (serverEcosystem == ServerEcosystemType.NONE) {
            incompatibilities.add("Los servidores Vanilla no admiten mods ni plugins. Convierte el servidor a una plataforma compatible antes de instalar extensiones.");
        } else if (serverEcosystem == ServerEcosystemType.UNKNOWN) {
            incompatibilities.add("No se ha podido determinar el ecosistema del servidor. Revisa o convierte la plataforma antes de instalar extensiones.");
        } else if (serverEcosystem == ServerEcosystemType.MODS && extensionType == ServerExtensionType.PLUGIN) {
            incompatibilities.add("El servidor usa Mods y la extensión se ha detectado como Plugin.");
        } else if (serverEcosystem == ServerEcosystemType.PLUGINS && extensionType == ServerExtensionType.MOD) {
            incompatibilities.add("El servidor usa Plugins y la extensión se ha detectado como Mod.");
        } else if (extensionType == ServerExtensionType.UNKNOWN) {
            incompatibilities.add("No se ha podido determinar si el archivo es un mod o un plugin.");
        }

        if (extensionPlatform != ServerPlatform.UNKNOWN
                && serverPlatform != ServerPlatform.UNKNOWN
                && !arePlatformsCompatible(serverPlatform, extensionPlatform)) {
            incompatibilities.add("La extensión se ha detectado para " + extensionPlatform.getLegacyTypeName()
                    + " y el servidor actual es " + serverPlatform.getLegacyTypeName() + ".");
        } else if (extensionPlatform == ServerPlatform.UNKNOWN) {
            warnings.add("La plataforma objetivo de la extensión no se ha podido determinar.");
        }

        String serverVersion = normalizeMinecraftVersion(server.getVersion());
        String minecraftConstraint = extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getMinecraftVersionConstraint();
        if (serverVersion == null || serverVersion.isBlank()) {
            warnings.add("No se conoce la versión de Minecraft del servidor.");
        } else if (minecraftConstraint == null || minecraftConstraint.isBlank()) {
            warnings.add("La extensión no declara una regla de compatibilidad para la versión de Minecraft.");
        } else if (!matchesMinecraftConstraint(serverVersion, minecraftConstraint)) {
            incompatibilities.add("La extensión declara compatibilidad con '" + minecraftConstraint
                    + "' y el servidor usa Minecraft " + serverVersion + ".");
        }

        if (!incompatibilities.isEmpty()) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    incompatibilities.getFirst(),
                    incompatibilities
            );
        }
        if (!warnings.isEmpty()) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.WARNING,
                    warnings.getFirst(),
                    warnings
            );
        }
        return new ExtensionCompatibilityReport(
                ExtensionCompatibilityStatus.COMPATIBLE,
                "Compatible con el servidor actual.",
                List.of()
        );
    }

    public InstalledExtensionStatus assessInstalledExtension(Server server, ServerExtension extension) {
        ExtensionCompatibilityReport compatibility = validateCompatibility(server, extension);
        ExtensionInstallState installState = extension == null || extension.getInstallState() == null
                ? ExtensionInstallState.UNKNOWN
                : extension.getInstallState();
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        ExtensionUpdateState updateState = metadata == null || metadata.getUpdateState() == null
                ? ExtensionUpdateState.UNKNOWN
                : metadata.getUpdateState();

        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Path serverDir = resolveServerDir(server);
        Path extensionPath = resolveExtensionPath(serverDir, server, extension);
        boolean filePresent = extensionPath != null && Files.isRegularFile(extensionPath);
        if (!filePresent) {
            problems.add("El archivo instalado no existe o no se puede leer.");
        }
        if (installState == ExtensionInstallState.FAILED) {
            problems.add("La instalacion quedo marcada como fallida.");
        } else if (installState == ExtensionInstallState.UNKNOWN) {
            warnings.add("No se conoce el estado de instalación local.");
        }

        List<String> missingDependencies = findMissingDependencies(server, extension);
        return new InstalledExtensionStatus(
                compatibility,
                installState,
                updateState,
                filePresent,
                updateState == ExtensionUpdateState.UPDATE_AVAILABLE,
                missingDependencies,
                warnings,
                problems
        );
    }

    public boolean removeExtension(Server server, ServerExtension extension) throws IOException {
        if (server == null || extension == null) {
            return false;
        }

        Path serverDir = resolveServerDir(server);
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return false;
        }

        Path extensionPath = resolveExtensionPath(serverDir, server, extension);
        if (extensionPath == null || !Files.isRegularFile(extensionPath)) {
            server.setExtensions(detectInstalledExtensions(server));
            persistInstalledExtensionCacheQuietly(server);
            return false;
        }

        Files.deleteIfExists(extensionPath);
        server.setExtensions(detectInstalledExtensions(server));
        persistInstalledExtensionCacheQuietly(server);
        return true;
    }

    private List<String> findMissingDependencies(Server server, ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        if (metadata == null || metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (ExtensionRemoteDependency dependency : metadata.getDependencies()) {
            if (dependency == null || Boolean.FALSE.equals(dependency.getRequired())) {
                continue;
            }
            if (!isDependencyInstalled(server, dependency)) {
                missing.add(firstNonBlank(dependency.getDisplayName(), dependency.getProjectId(), "Dependencia sin nombre"));
            }
        }
        return missing;
    }

    private boolean isDependencyInstalled(Server server, ExtensionRemoteDependency dependency) {
        if (server == null || server.getExtensions() == null || dependency == null) {
            return false;
        }
        for (ServerExtension installed : server.getExtensions()) {
            if (installed == null) {
                continue;
            }
            ExtensionSource source = installed.getSource();
            if (source != null
                    && isSameText(source.getProvider(), dependency.getProviderId())
                    && isSameText(source.getProjectId(), dependency.getProjectId())) {
                return true;
            }
            if (isSameText(installed.getDisplayName(), dependency.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameText(String left, String right) {
        return left != null && right != null && !left.isBlank() && !right.isBlank()
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private ServerExtension mergeInstalledMetadata(ServerExtension detected, ServerExtension installed) {
        if (detected == null) {
            return installed;
        }
        detected.setInstallState(ExtensionInstallState.INSTALLED);
        detected.setDisplayName(firstNonBlank(installed.getDisplayName(), detected.getDisplayName()));
        detected.setVersion(firstNonBlank(installed.getVersion(), detected.getVersion()));
        detected.setDescription(preferMeaningfulDescription(installed.getDescription(), detected.getDescription()));
        if (installed.getExtensionType() != null && installed.getExtensionType() != ServerExtensionType.UNKNOWN) {
            detected.setExtensionType(installed.getExtensionType());
        }
        if (installed.getPlatform() != null && installed.getPlatform() != ServerPlatform.UNKNOWN) {
            detected.setPlatform(installed.getPlatform());
        }
        if (detected.getSource() == null) {
            detected.setSource(new ExtensionSource());
        }
        detected.getSource().setType(installed.getSource().getType());
        detected.getSource().setProvider(installed.getSource().getProvider());
        detected.getSource().setProjectId(installed.getSource().getProjectId());
        detected.getSource().setVersionId(installed.getSource().getVersionId());
        detected.getSource().setUrl(installed.getSource().getUrl());
        detected.getSource().setProjectUrl(installed.getSource().getProjectUrl());
        detected.getSource().setIssuesUrl(installed.getSource().getIssuesUrl());
        detected.getSource().setWebsiteUrl(installed.getSource().getWebsiteUrl());
        detected.getSource().setLicenseName(installed.getSource().getLicenseName());
        detected.getSource().setAuthor(installed.getSource().getAuthor());
        detected.getSource().setIconUrl(installed.getSource().getIconUrl());
        if (installed.getLocalMetadata() != null && detected.getLocalMetadata() != null) {
            if (installed.getLocalMetadata().getMinecraftVersionConstraint() != null
                    && !installed.getLocalMetadata().getMinecraftVersionConstraint().isBlank()) {
                detected.getLocalMetadata().setMinecraftVersionConstraint(installed.getLocalMetadata().getMinecraftVersionConstraint());
            }
            detected.getLocalMetadata().setInstalledVersion(installed.getLocalMetadata().getInstalledVersion());
            detected.getLocalMetadata().setKnownRemoteVersion(installed.getLocalMetadata().getKnownRemoteVersion());
            detected.getLocalMetadata().setKnownRemoteVersionId(installed.getLocalMetadata().getKnownRemoteVersionId());
            detected.getLocalMetadata().setLastCheckedForUpdatesAtEpochMillis(installed.getLocalMetadata().getLastCheckedForUpdatesAtEpochMillis());
            detected.getLocalMetadata().setLastMetadataSyncAtEpochMillis(installed.getLocalMetadata().getLastMetadataSyncAtEpochMillis());
            detected.getLocalMetadata().setUpdateState(installed.getLocalMetadata().getUpdateState());
            detected.getLocalMetadata().setUpdateMessage(installed.getLocalMetadata().getUpdateMessage());
            detected.getLocalMetadata().setDownloadCount(installed.getLocalMetadata().getDownloadCount());
            detected.getLocalMetadata().setClientSide(installed.getLocalMetadata().getClientSide());
            detected.getLocalMetadata().setServerSide(installed.getLocalMetadata().getServerSide());
            detected.getLocalMetadata().setLocalIconUrl(installed.getLocalMetadata().getLocalIconUrl());
            detected.getLocalMetadata().setLocalIconPath(installed.getLocalMetadata().getLocalIconPath());
            detected.getLocalMetadata().setWebsiteUrl(installed.getLocalMetadata().getWebsiteUrl());
            detected.getLocalMetadata().setIssuesUrl(installed.getLocalMetadata().getIssuesUrl());
            detected.getLocalMetadata().setLicenseName(installed.getLocalMetadata().getLicenseName());
            if (installed.getLocalMetadata().getCategories() != null) {
                detected.getLocalMetadata().setCategories(new ArrayList<>(installed.getLocalMetadata().getCategories()));
            }
            if (installed.getLocalMetadata().getSupportedLoaders() != null) {
                detected.getLocalMetadata().setSupportedLoaders(new ArrayList<>(installed.getLocalMetadata().getSupportedLoaders()));
            }
            if (installed.getLocalMetadata().getSupportedMinecraftVersions() != null) {
                detected.getLocalMetadata().setSupportedMinecraftVersions(new ArrayList<>(installed.getLocalMetadata().getSupportedMinecraftVersions()));
            }
            if (installed.getLocalMetadata().getEmbeddedMetadataFiles() != null) {
                detected.getLocalMetadata().setEmbeddedMetadataFiles(new ArrayList<>(installed.getLocalMetadata().getEmbeddedMetadataFiles()));
            }
            if (detected.getLocalMetadata().getDependencies() == null
                    || detected.getLocalMetadata().getDependencies().isEmpty()) {
                detected.getLocalMetadata().setDependencies(copyDependencies(installed.getLocalMetadata().getDependencies()));
            }
        }
        return detected;
    }

    private String preferMeaningfulDescription(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private void ensureCatalogExtensionNotAlreadyInstalled(Server server,
                                                           ExtensionDownloadPlan downloadPlan) throws IOException {
        ExtensionInstallResolution resolution = evaluateCatalogInstallation(server, downloadPlan);
        if (resolution.blocksInstall()) {
            throw new IOException(firstNonBlank(resolution.message(), "La extensión ya está instalada o entra en conflicto con otra existente."));
        }
    }

    private void ensureCatalogPlanCompatibleWithServer(Server server, ExtensionDownloadPlan downloadPlan) throws IOException {
        ServerPlatformProfile profile = resolveProfile(server);
        ServerEcosystemType serverEcosystem = resolveEcosystemType(server, profile);
        if (serverEcosystem != ServerEcosystemType.MODS && serverEcosystem != ServerEcosystemType.PLUGINS) {
            throw new IOException("Las extensiones del marketplace requieren un servidor de mods o plugins.");
        }
        ExtensionInstallResolution compatibility = evaluateCatalogCompatibility(
                server,
                downloadPlan == null ? ServerExtensionType.UNKNOWN : downloadPlan.extensionType(),
                downloadPlan == null || downloadPlan.platform() == null || downloadPlan.platform() == ServerPlatform.UNKNOWN
                        ? Set.of()
                        : Set.of(downloadPlan.platform()),
                downloadPlan == null ? Set.of() : minecraftVersionsFromConstraint(downloadPlan.minecraftVersionConstraint())
        );
        if (compatibility != null && compatibility.blocksInstall()) {
            throw new IOException(firstNonBlank(compatibility.message(), "La extensión no es compatible con este servidor."));
        }
    }

    private boolean canTrustCatalogPlanOverLocalType(Server server,
                                                     Path sourceJar,
                                                     ExtensionDownloadPlan downloadPlan) throws IOException {
        if (downloadPlan == null
                || downloadPlan.extensionType() == null
                || downloadPlan.extensionType() == ServerExtensionType.UNKNOWN) {
            return false;
        }

        ServerPlatformProfile profile = resolveProfile(server);
        ServerEcosystemType serverEcosystem = resolveEcosystemType(server, profile);
        if (serverEcosystem != ServerEcosystemType.MODS && serverEcosystem != ServerEcosystemType.PLUGINS) {
            return false;
        }

        Set<ServerPlatform> declaredPlatforms = downloadPlan.platform() == null || downloadPlan.platform() == ServerPlatform.UNKNOWN
                ? Set.of()
                : Set.of(downloadPlan.platform());
        ExtensionInstallResolution catalogCompatibility = evaluateCatalogCompatibility(
                server,
                downloadPlan.extensionType(),
                declaredPlatforms,
                minecraftVersionsFromConstraint(downloadPlan.minecraftVersionConstraint())
        );
        if (catalogCompatibility != null && catalogCompatibility.blocksInstall()) {
            return false;
        }

        if (serverEcosystem == ServerEcosystemType.PLUGINS
                && downloadPlan.extensionType() == ServerExtensionType.PLUGIN) {
            return jarContainsAnyEntry(sourceJar, "plugin.yml", "paper-plugin.yml");
        }
        if (serverEcosystem == ServerEcosystemType.MODS
                && downloadPlan.extensionType() == ServerExtensionType.MOD) {
            return jarContainsAnyEntry(
                    sourceJar,
                    "META-INF/mods.toml",
                    "META-INF/neoforge.mods.toml",
                    "fabric.mod.json",
                    "quilt.mod.json",
                    "mcmod.info"
            );
        }
        return false;
    }

    private boolean jarContainsAnyEntry(Path jarPath, String... entryNames) throws IOException {
        if (jarPath == null || entryNames == null || entryNames.length == 0) {
            return false;
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            for (String entryName : entryNames) {
                if (entryName != null && jarFile.getJarEntry(entryName) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private ExtensionInstallResolution evaluateCatalogCompatibility(Server server,
                                                                    ServerExtensionType extensionType,
                                                                    Set<ServerPlatform> platforms,
                                                                    Set<String> minecraftVersions) {
        ServerPlatformProfile profile = resolveProfile(server);
        ServerEcosystemType serverEcosystem = resolveEcosystemType(server, profile);
        ServerPlatform serverPlatform = resolvePlatform(server, profile);
        ServerExtensionType requestedType = extensionType == null ? ServerExtensionType.UNKNOWN : extensionType;

        if (serverEcosystem == ServerEcosystemType.NONE) {
            return incompatibleCatalogResolution("Los servidores Vanilla no admiten mods ni plugins. Convierte el servidor antes de instalar extensiones.");
        }
        if (serverEcosystem == ServerEcosystemType.UNKNOWN) {
            return incompatibleCatalogResolution("No se ha podido determinar el ecosistema del servidor. Revisa o convierte la plataforma antes de instalar extensiones.");
        }
        if (requestedType == ServerExtensionType.UNKNOWN) {
            return incompatibleCatalogResolution("La extensión no declara si es mod o plugin.");
        }
        if (serverEcosystem == ServerEcosystemType.MODS && requestedType == ServerExtensionType.PLUGIN) {
            return incompatibleCatalogResolution("Este servidor acepta mods. No se pueden instalar plugins en su carpeta de mods.");
        }
        if (serverEcosystem == ServerEcosystemType.PLUGINS && requestedType == ServerExtensionType.MOD) {
            return incompatibleCatalogResolution("Este servidor acepta plugins. No se pueden instalar mods en su carpeta de plugins.");
        }
        if (platforms != null && !platforms.isEmpty() && serverPlatform != ServerPlatform.UNKNOWN) {
            boolean platformMatch = platforms.stream()
                    .filter(Objects::nonNull)
                    .filter(platform -> platform != ServerPlatform.UNKNOWN)
                    .anyMatch(platform -> arePlatformsCompatible(serverPlatform, platform));
            if (!platformMatch) {
                return incompatibleCatalogResolution("La extensión no declara compatibilidad con la plataforma del servidor.");
            }
        }
        String serverVersion = normalizeMinecraftVersion(server == null ? null : server.getVersion());
        if (serverVersion != null && !serverVersion.isBlank()
                && minecraftVersions != null && !minecraftVersions.isEmpty()
                && minecraftVersions.stream()
                .filter(Objects::nonNull)
                .filter(version -> !version.isBlank())
                .noneMatch(version -> matchesMinecraftConstraint(serverVersion, version))) {
            return incompatibleCatalogResolution("La extensión no declara compatibilidad con Minecraft " + serverVersion + ".");
        }
        return null;
    }

    private ExtensionInstallResolution incompatibleCatalogResolution(String message) {
        return new ExtensionInstallResolution(
                ExtensionInstallResolutionState.INCOMPATIBLE,
                null,
                null,
                null,
                null,
                message
        );
    }

    private Set<String> minecraftVersionsFromConstraint(String constraint) {
        if (constraint == null || constraint.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        for (String part : constraint.split(",")) {
            String cleaned = part == null ? null : part.trim();
            if (cleaned != null && !cleaned.isBlank()) {
                versions.add(cleaned);
            }
        }
        return versions;
    }

    private ExtensionInstallResolution evaluateCatalogInstallation(Server server,
                                                                   String providerId,
                                                                   String projectId,
                                                                   String versionId,
                                                                   String versionNumber,
                                                                   String fileName,
                                                                   String fallbackName) {
        if (server == null || server.getExtensions() == null || server.getExtensions().isEmpty()) {
            return new ExtensionInstallResolution(
                    ExtensionInstallResolutionState.AVAILABLE,
                    null,
                    normalizeFileName(fileName),
                    firstNonBlank(versionNumber, versionId),
                    null,
                    null
            );
        }

        String normalizedProviderId = firstNonBlank(providerId);
        String normalizedProjectId = firstNonBlank(projectId);
        String normalizedVersionId = firstNonBlank(versionId);
        String normalizedVersionNumber = firstNonBlank(versionNumber);
        String requestedFileName = normalizeFileName(fileName);
        String displayName = firstNonBlank(fallbackName, normalizedProjectId, "La extensión");
        String requestedVersion = firstNonBlank(normalizedVersionNumber, normalizedVersionId);

        ServerExtension projectMatch = null;
        boolean exactVersionInstalled = false;
        ServerExtension incompleteMetadataMatch = null;
        ServerExtension fileMatch = null;

        for (ServerExtension installed : server.getExtensions()) {
            if (installed == null) {
                continue;
            }
            ExtensionSource source = installed.getSource();
            if (source != null
                    && normalizedProviderId != null
                    && normalizedProjectId != null
                    && normalizedProviderId.equalsIgnoreCase(firstNonBlank(source.getProvider()))
                    && normalizedProjectId.equalsIgnoreCase(firstNonBlank(source.getProjectId()))) {
                projectMatch = installed;
                String installedVersionId = firstNonBlank(source.getVersionId(), installed.getLocalMetadata() == null ? null : installed.getLocalMetadata().getKnownRemoteVersionId());
                String installedVersionNumber = firstNonBlank(
                        installed.getVersion(),
                        installed.getLocalMetadata() == null ? null : installed.getLocalMetadata().getInstalledVersion(),
                        installed.getLocalMetadata() == null ? null : installed.getLocalMetadata().getKnownRemoteVersion()
                );
                exactVersionInstalled = (normalizedVersionId != null && normalizedVersionId.equalsIgnoreCase(installedVersionId))
                        || (normalizedVersionNumber != null && normalizedVersionNumber.equalsIgnoreCase(installedVersionNumber));
                break;
            }
            if (incompleteMetadataMatch == null
                    && mayRepresentSameExtensionWithIncompleteMetadata(installed, normalizedProjectId, requestedFileName, displayName)) {
                incompleteMetadataMatch = installed;
            }
            if (requestedFileName != null && requestedFileName.equalsIgnoreCase(normalizeFileName(installed.getFileName()))) {
                fileMatch = installed;
            }
        }

        if (projectMatch != null) {
            String installedName = firstNonBlank(projectMatch.getDisplayName(), stripJarExtension(projectMatch.getFileName()), displayName);
            String installedVersion = resolveInstalledVersion(projectMatch);
            if (exactVersionInstalled) {
                return new ExtensionInstallResolution(
                        ExtensionInstallResolutionState.INSTALLED_EXACT,
                        projectMatch,
                        requestedFileName,
                        requestedVersion,
                        installedVersion,
                        installedName + " está correctamente instalada en este servidor."
                );
            }
            return new ExtensionInstallResolution(
                    ExtensionInstallResolutionState.UPDATE_AVAILABLE,
                    projectMatch,
                    requestedFileName,
                    requestedVersion,
                    installedVersion,
                    installedName + " ya está instalada con la versión "
                            + firstNonBlank(installedVersion, "desconocida")
                            + ". El marketplace ofrece "
                            + firstNonBlank(requestedVersion, "otra versión")
                            + "."
            );
        }

        if (incompleteMetadataMatch != null) {
            String installedName = firstNonBlank(incompleteMetadataMatch.getDisplayName(), stripJarExtension(incompleteMetadataMatch.getFileName()), displayName);
            return new ExtensionInstallResolution(
                    ExtensionInstallResolutionState.INSTALLED_WITH_INCOMPLETE_METADATA,
                    incompleteMetadataMatch,
                    requestedFileName,
                    requestedVersion,
                    resolveInstalledVersion(incompleteMetadataMatch),
                    installedName + " ya existe en el servidor, pero su metadata local no conserva un origen remoto completo. Revisa si fue instalada manualmente antes de duplicarla."
            );
        }

        if (fileMatch != null) {
            String installedName = firstNonBlank(fileMatch.getDisplayName(), stripJarExtension(fileMatch.getFileName()), displayName);
            return new ExtensionInstallResolution(
                    ExtensionInstallResolutionState.FILE_NAME_CONFLICT,
                    fileMatch,
                    requestedFileName,
                    requestedVersion,
                    resolveInstalledVersion(fileMatch),
                    installedName + " ya ocupa ese archivo en el servidor."
            );
        }

        return new ExtensionInstallResolution(
                ExtensionInstallResolutionState.AVAILABLE,
                null,
                requestedFileName,
                requestedVersion,
                null,
                null
        );
    }

    private boolean mayRepresentSameExtensionWithIncompleteMetadata(ServerExtension installed,
                                                                    String projectId,
                                                                    String requestedFileName,
                                                                    String fallbackName) {
        if (installed == null || !hasIncompleteRemoteMetadata(installed)) {
            return false;
        }
        String installedFileName = normalizeFileName(firstNonBlank(
                installed.getFileName(),
                installed.getLocalMetadata() == null ? null : installed.getLocalMetadata().getFileName()
        ));
        if (requestedFileName != null && requestedFileName.equalsIgnoreCase(installedFileName)) {
            return true;
        }

        String requestedNameKey = normalizeNameKey(firstNonBlank(fallbackName, stripJarExtension(requestedFileName), projectId));
        if (requestedNameKey == null) {
            return false;
        }
        String installedDisplayKey = normalizeNameKey(installed.getDisplayName());
        String installedFileKey = normalizeNameKey(stripJarExtension(installedFileName));
        return requestedNameKey.equals(installedDisplayKey) || requestedNameKey.equals(installedFileKey);
    }

    private boolean hasIncompleteRemoteMetadata(ServerExtension installed) {
        if (installed == null) {
            return false;
        }
        ExtensionSource source = installed.getSource();
        if (source == null) {
            return true;
        }
        if (hasTrackedRemoteOrigin(source)) {
            return false;
        }
        return source.getType() == null
                || source.getType() == ExtensionSourceType.UNKNOWN
                || source.getType() == ExtensionSourceType.MANUAL
                || source.getType() == ExtensionSourceType.LOCAL_FILE
                || source.getProvider() == null
                || source.getProvider().isBlank()
                || source.getProjectId() == null
                || source.getProjectId().isBlank();
    }

    private String resolveInstalledVersion(ServerExtension installed) {
        if (installed == null) {
            return null;
        }
        ExtensionLocalMetadata metadata = installed.getLocalMetadata();
        return firstNonBlank(
                installed.getVersion(),
                metadata == null ? null : metadata.getInstalledVersion(),
                metadata == null ? null : metadata.getKnownRemoteVersion(),
                installed.getSource() == null ? null : installed.getSource().getVersionId()
        );
    }

    private String normalizeNameKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = stripJarExtension(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return normalized.isBlank() ? null : normalized;
    }

    private void applySourceMetadata(ServerExtension installed,
                                     Path sourceJar,
                                     ExtensionDownloadPlan downloadPlan,
                                     String defaultProvider) {
        if (installed == null) {
            return;
        }
        if (installed.getSource() == null) {
            installed.setSource(new ExtensionSource());
        }
        ExtensionSource source = installed.getSource();
        if (downloadPlan != null && downloadPlan.sourceType() != null) {
            source.setType(downloadPlan.sourceType());
        }
        source.setProvider(defaultProvider);
        source.setUrl(downloadPlan != null && downloadPlan.downloadUrl() != null && !downloadPlan.downloadUrl().isBlank()
                ? downloadPlan.downloadUrl()
                : sourceJar.toAbsolutePath().toString());
        if (downloadPlan != null) {
            source.setProjectUrl(firstNonBlank(downloadPlan.projectUrl(), source.getProjectUrl()));
            source.setIssuesUrl(firstNonBlank(downloadPlan.issuesUrl(), source.getIssuesUrl()));
            source.setWebsiteUrl(firstNonBlank(downloadPlan.websiteUrl(), source.getWebsiteUrl()));
            source.setLicenseName(firstNonBlank(downloadPlan.licenseName(), source.getLicenseName()));
            source.setAuthor(firstNonBlank(downloadPlan.author(), source.getAuthor()));
        }
        if (downloadPlan != null && downloadPlan.iconUrl() != null && !downloadPlan.iconUrl().isBlank()) {
            source.setIconUrl(downloadPlan.iconUrl());
        }
        if (downloadPlan != null) {
            source.setProjectId(downloadPlan.projectId());
            source.setVersionId(downloadPlan.versionId());
            installed.setDisplayName(firstNonBlank(downloadPlan.displayName(), installed.getDisplayName()));
            installed.setDescription(firstNonBlank(downloadPlan.description(), installed.getDescription()));
            if (downloadPlan.platform() != null && downloadPlan.platform() != ServerPlatform.UNKNOWN) {
                installed.setPlatform(downloadPlan.platform());
            }
            if (downloadPlan.extensionType() != null && downloadPlan.extensionType() != ServerExtensionType.UNKNOWN) {
                installed.setExtensionType(downloadPlan.extensionType());
            }
            if (installed.getLocalMetadata() != null
                    && downloadPlan.minecraftVersionConstraint() != null
                    && !downloadPlan.minecraftVersionConstraint().isBlank()) {
                installed.getLocalMetadata().setMinecraftVersionConstraint(downloadPlan.minecraftVersionConstraint());
            }
            ExtensionLocalMetadata metadata = ensureLocalMetadata(installed);
            metadata.setKnownRemoteVersion(firstNonBlank(downloadPlan.versionNumber(), installed.getVersion()));
            metadata.setKnownRemoteVersionId(downloadPlan.versionId());
            metadata.setLastCheckedForUpdatesAtEpochMillis(System.currentTimeMillis());
            metadata.setLastMetadataSyncAtEpochMillis(System.currentTimeMillis());
            metadata.setDownloadCount(downloadPlan.downloads() > 0L
                    ? downloadPlan.downloads()
                    : metadata.getDownloadCount() == null ? 0L : metadata.getDownloadCount());
            metadata.setClientSide(firstNonBlank(downloadPlan.clientSide(), metadata.getClientSide()));
            metadata.setServerSide(firstNonBlank(downloadPlan.serverSide(), metadata.getServerSide()));
            if (downloadPlan.categories() != null && !downloadPlan.categories().isEmpty()) {
                metadata.setCategories(new ArrayList<>(downloadPlan.categories()));
            }
            if (downloadPlan.dependencies() != null && !downloadPlan.dependencies().isEmpty()) {
                metadata.setDependencies(copyDependenciesFromPlan(downloadPlan.dependencies()));
            }
            if (metadata.getInstalledVersion() != null
                    && metadata.getInstalledVersion().equalsIgnoreCase(firstNonBlank(downloadPlan.versionNumber(), metadata.getInstalledVersion()))) {
                metadata.setUpdateState(ExtensionUpdateState.UP_TO_DATE);
                metadata.setUpdateMessage("La extensión se ha instalado desde su versión remota conocida.");
            } else {
                metadata.setUpdateState(ExtensionUpdateState.UNKNOWN);
                metadata.setUpdateMessage("La extensión conserva origen remoto, pero su estado de actualización todavía no se ha verificado.");
            }
        }
    }

    private boolean sameRelativePath(ServerExtension left, ServerExtension right) {
        String leftPath = left == null || left.getLocalMetadata() == null ? null : normalizeRelativePath(left.getLocalMetadata().getRelativePath());
        String rightPath = right == null || right.getLocalMetadata() == null ? null : normalizeRelativePath(right.getLocalMetadata().getRelativePath());
        return leftPath != null && leftPath.equals(rightPath);
    }

    private boolean arePlatformsCompatible(ServerPlatform serverPlatform, ServerPlatform extensionPlatform) {
        if (serverPlatform == extensionPlatform) {
            return true;
        }
        return canonicalizePlatform(serverPlatform) == canonicalizePlatform(extensionPlatform);
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

    private Path resolveExtensionPath(Path serverDir, Server server, ServerExtension extension) {
        if (serverDir == null || extension == null) {
            return null;
        }

        ExtensionLocalMetadata metadata = extension.getLocalMetadata();
        if (metadata != null && metadata.getRelativePath() != null && !metadata.getRelativePath().isBlank()) {
            Path candidate = serverDir.resolve(metadata.getRelativePath().replace('/', java.io.File.separatorChar));
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        String fileName = extension.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        for (Path directory : getManagedExtensionDirectories(server)) {
            if (directory == null) {
                continue;
            }
            Path candidate = directory.resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private ServerExtension readExtensionMetadata(Path jarPath,
                                                  String relativePath,
                                                  ServerExtension existing,
                                                  ServerPlatform serverPlatform,
                                                  ServerEcosystemType ecosystemType,
                                                  ExtensionInstallState defaultState,
                                                  ExtensionSourceType defaultSourceType,
                                                  long now) throws IOException {
        CachedJarMetadata jarMetadata = cachedJarMetadata(jarPath, serverPlatform, ecosystemType, now);
        ExtensionDescriptor descriptor = jarMetadata.descriptor();
        ServerExtension extension = existing == null ? new ServerExtension() : existing;
        if (extension.getId() == null || extension.getId().isBlank()) {
            extension.setId(UUID.randomUUID().toString());
        }

        extension.setDisplayName(firstNonBlank(
                descriptor.displayName(),
                stripJarExtension(jarPath.getFileName().toString())
        ));
        extension.setVersion(firstNonBlank(descriptor.version(), extension.getVersion()));
        extension.setDescription(firstNonBlank(descriptor.description(), extension.getDescription()));
        extension.setFileName(jarPath.getFileName().toString());
        extension.setExtensionType(descriptor.extensionType());
        extension.setPlatform(descriptor.platform());

        ExtensionSource source = extension.getSource();
        if (source == null) {
            source = new ExtensionSource();
            extension.setSource(source);
        }
        if (source.getType() == null || source.getType() == ExtensionSourceType.UNKNOWN) {
            source.setType(defaultSourceType);
        }
        source.setAuthor(firstNonBlank(source.getAuthor(), descriptor.author()));
        source.setIconUrl(firstNonBlank(source.getIconUrl(), descriptor.localIconUrl()));
        source.setProjectUrl(firstNonBlank(source.getProjectUrl(), descriptor.websiteUrl()));
        source.setWebsiteUrl(firstNonBlank(source.getWebsiteUrl(), descriptor.websiteUrl()));
        source.setIssuesUrl(firstNonBlank(source.getIssuesUrl(), descriptor.issuesUrl()));
        source.setLicenseName(firstNonBlank(source.getLicenseName(), descriptor.licenseName()));

        if (extension.getInstallState() == null || extension.getInstallState() == ExtensionInstallState.UNKNOWN) {
            extension.setInstallState(defaultState);
        } else if (defaultState == ExtensionInstallState.INSTALLED) {
            extension.setInstallState(ExtensionInstallState.INSTALLED);
        }

        ExtensionLocalMetadata metadata = extension.getLocalMetadata();
        if (metadata == null) {
            metadata = new ExtensionLocalMetadata();
            extension.setLocalMetadata(metadata);
        }
        metadata.setRelativePath(relativePath);
        metadata.setFileName(jarPath.getFileName().toString());
        metadata.setFileSizeBytes(jarMetadata.fileSizeBytes());
        metadata.setSha256(jarMetadata.sha256());
        metadata.setInstalledVersion(firstNonBlank(extension.getVersion(), metadata.getInstalledVersion()));
        metadata.setMinecraftVersionConstraint(firstNonBlank(
                descriptor.minecraftVersionConstraint(),
                inferMinecraftVersionHint(jarPath.getFileName().toString(), descriptor.version(), descriptor.displayName(), descriptor.description())
        ));
        metadata.setAuthors(copyStrings(descriptor.authors()));
        metadata.setSupportedLoaders(copyStrings(descriptor.supportedLoaders()));
        metadata.setSupportedMinecraftVersions(copyStrings(descriptor.supportedMinecraftVersions()));
        metadata.setLocalIconUrl(descriptor.localIconUrl());
        metadata.setLocalIconPath(descriptor.localIconPath());
        metadata.setWebsiteUrl(descriptor.websiteUrl());
        metadata.setIssuesUrl(descriptor.issuesUrl());
        metadata.setLicenseName(descriptor.licenseName());
        metadata.setEmbeddedMetadataFiles(copyStrings(descriptor.embeddedMetadataFiles()));
        metadata.setManifestAttributes(new LinkedHashMap<>(descriptor.manifestAttributes()));
        List<ExtensionRemoteDependency> descriptorDependencies = copyDependencies(descriptor.dependencies());
        if (!descriptorDependencies.isEmpty() || metadata.getDependencies() == null) {
            metadata.setDependencies(descriptorDependencies);
        }
        metadata.setLocalDependencyDescriptions(copyStrings(descriptor.localDependencyDescriptions()));
        metadata.setClientSide(firstNonBlank(descriptor.clientSide(), metadata.getClientSide()));
        metadata.setServerSide(firstNonBlank(descriptor.serverSide(), metadata.getServerSide()));
        if (metadata.getDiscoveredAtEpochMillis() == null) {
            metadata.setDiscoveredAtEpochMillis(now);
        }
        metadata.setLastUpdatedAtEpochMillis(jarMetadata.lastModifiedEpochMillis());
        if (metadata.getEnabled() == null) {
            metadata.setEnabled(Boolean.TRUE);
        }
        if (metadata.getUpdateState() == null || metadata.getUpdateState() == ExtensionUpdateState.UNKNOWN) {
            metadata.setUpdateState(resolveDefaultUpdateState(source));
        }
        if (metadata.getUpdateMessage() == null || metadata.getUpdateMessage().isBlank()) {
            metadata.setUpdateMessage(defaultUpdateMessage(source));
        }
        if (metadata.getCategories() == null) {
            metadata.setCategories(new ArrayList<>());
        }
        return extension;
    }

    private CachedJarMetadata cachedJarMetadata(Path jarPath,
                                                ServerPlatform serverPlatform,
                                                ServerEcosystemType ecosystemType,
                                                long now) throws IOException {
        long fileSize = Files.size(jarPath);
        long lastModified = resolveLastModified(jarPath, now);
        String key = jarMetadataCacheKey(jarPath, serverPlatform, ecosystemType);
        CachedJarMetadata cached = jarMetadataCache.get(key);
        if (cached != null
                && cached.fileSizeBytes() == fileSize
                && cached.lastModifiedEpochMillis() == lastModified) {
            return cached;
        }
        ExtensionDescriptor descriptor = inspectJarDescriptor(jarPath, serverPlatform, ecosystemType);
        String sha256 = calculateSha256(jarPath);
        CachedJarMetadata fresh = new CachedJarMetadata(fileSize, lastModified, descriptor, sha256);
        jarMetadataCache.put(key, fresh);
        return fresh;
    }

    private String jarMetadataCacheKey(Path jarPath, ServerPlatform serverPlatform, ServerEcosystemType ecosystemType) {
        Path normalizedPath = jarPath == null ? Path.of("") : jarPath.toAbsolutePath().normalize();
        return normalizedPath + "|"
                + (serverPlatform == null ? ServerPlatform.UNKNOWN : serverPlatform).name()
                + "|"
                + (ecosystemType == null ? ServerEcosystemType.UNKNOWN : ecosystemType).name();
    }

    private void validateDownloadedJar(Path jarPath, ExtensionDownloadPlan downloadPlan) throws IOException {
        rejectObviousTextDownload(jarPath);
        validateLocalJar(jarPath);
    }

    private void rejectObviousTextDownload(Path jarPath) throws IOException {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            throw new IOException("El archivo descargado no existe o no es accesible.");
        }
        byte[] prefix;
        try (InputStream input = Files.newInputStream(jarPath)) {
            prefix = input.readNBytes(64);
        }
        int offset = 0;
        while (offset < prefix.length && Character.isWhitespace((char) (prefix[offset] & 0xff))) {
            offset++;
        }
        if (offset >= prefix.length) {
            throw new IOException("El archivo descargado está vacío.");
        }
        int first = prefix[offset] & 0xff;
        if (first == '<' || first == '{' || first == '[') {
            throw new IOException("La descarga remota no parece ser un archivo JAR.");
        }
    }

    private void validateLocalJar(Path jarPath) throws IOException {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            throw new IOException("El archivo descargado no existe o no es accesible.");
        }
        long size = Files.size(jarPath);
        if (size <= 0L) {
            throw new IOException("El archivo descargado está vacío.");
        }
        byte[] header;
        try (InputStream input = Files.newInputStream(jarPath)) {
            header = input.readNBytes(4);
        }
        if (header.length < 2 || header[0] != 'P' || header[1] != 'K') {
            throw new IOException("El archivo descargado no tiene formato ZIP/JAR válido.");
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            boolean hasEntries = jarFile.entries().hasMoreElements();
            if (!hasEntries) {
                throw new IOException("El archivo descargado no contiene entradas JAR.");
            }
        } catch (ZipException ex) {
            throw new IOException("El archivo descargado está corrupto o no es un JAR válido.", ex);
        }
    }

    private ExtensionSource ensureSource(ServerExtension extension) {
        if (extension.getSource() == null) {
            extension.setSource(new ExtensionSource());
        }
        return extension.getSource();
    }

    private ExtensionLocalMetadata ensureLocalMetadata(ServerExtension extension) {
        if (extension.getLocalMetadata() == null) {
            extension.setLocalMetadata(new ExtensionLocalMetadata());
        }
        return extension.getLocalMetadata();
    }

    private void indexStoredExtensions(Map<String, ServerExtension> byRelativePath,
                                       Map<String, ServerExtension> byFileName,
                                       List<ServerExtension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return;
        }
        for (ServerExtension extension : extensions) {
            if (extension == null) {
                continue;
            }
            String relativePath = extension.getLocalMetadata() == null ? null : normalizeRelativePath(extension.getLocalMetadata().getRelativePath());
            if (relativePath != null) {
                byRelativePath.merge(relativePath, extension, this::mergeStoredExtensionSafely);
            }
            String fileName = normalizeFileName(firstNonBlank(
                    extension.getFileName(),
                    extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getFileName()
            ));
            if (fileName != null) {
                byFileName.merge(fileName, extension, this::mergeStoredExtensionSafely);
            }
        }
    }

    private ServerExtension mergeStoredExtensionSafely(ServerExtension current, ServerExtension candidate) {
        if (canMergeStoredExtensions(current, candidate)) {
            return mergeInstalledMetadata(current, candidate);
        }
        return current;
    }

    private boolean canMergeStoredExtensions(ServerExtension current, ServerExtension candidate) {
        if (current == null || candidate == null) {
            return true;
        }
        ServerExtensionType currentType = current.getExtensionType() == null ? ServerExtensionType.UNKNOWN : current.getExtensionType();
        ServerExtensionType candidateType = candidate.getExtensionType() == null ? ServerExtensionType.UNKNOWN : candidate.getExtensionType();
        if (currentType != ServerExtensionType.UNKNOWN
                && candidateType != ServerExtensionType.UNKNOWN
                && currentType != candidateType) {
            ExtensionSource currentSource = current.getSource();
            ExtensionSource candidateSource = candidate.getSource();
            return currentSource != null
                    && candidateSource != null
                    && isSameText(currentSource.getProvider(), candidateSource.getProvider())
                    && isSameText(currentSource.getProjectId(), candidateSource.getProjectId())
                    && currentType == candidateType;
        }
        return true;
    }

    private void persistInstalledExtensionCacheQuietly(Server server) {
        try {
            persistInstalledExtensionCache(server);
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "No se ha podido actualizar la cache local de extensiones.", ex);
        }
    }

    private List<ExtensionRemoteDependency> copyDependencies(List<ExtensionRemoteDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        List<ExtensionRemoteDependency> copied = new ArrayList<>();
        for (ExtensionRemoteDependency dependency : dependencies) {
            if (dependency == null) {
                continue;
            }
            ExtensionRemoteDependency copy = new ExtensionRemoteDependency();
            copy.setProviderId(dependency.getProviderId());
            copy.setProjectId(dependency.getProjectId());
            copy.setVersionId(dependency.getVersionId());
            copy.setDisplayName(dependency.getDisplayName());
            copy.setDependencyType(dependency.getDependencyType());
            copy.setRequired(dependency.getRequired());
            copied.add(copy);
        }
        return copied;
    }

    private List<ExtensionRemoteDependency> copyDependenciesFromPlan(List<ExtensionDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        List<ExtensionRemoteDependency> copied = new ArrayList<>();
        for (ExtensionDependency dependency : dependencies) {
            if (dependency == null) {
                continue;
            }
            ExtensionRemoteDependency copy = new ExtensionRemoteDependency();
            copy.setProviderId(dependency.providerId());
            copy.setProjectId(dependency.projectId());
            copy.setVersionId(dependency.versionId());
            copy.setDisplayName(dependency.displayName());
            copy.setDependencyType(dependency.dependencyType());
            copy.setRequired(dependency.required());
            copied.add(copy);
        }
        return copied;
    }

    private boolean updateTrackingSnapshot(ServerExtension extension,
                                           ExtensionLocalMetadata metadata,
                                           long now) {
        boolean changed = false;
        changed |= setIfChanged(metadata.getInstalledVersion(), extension.getVersion(), metadata::setInstalledVersion);
        if (metadata.getEnabled() == null) {
            metadata.setEnabled(Boolean.TRUE);
            changed = true;
        }
        if (metadata.getUpdateState() == null || metadata.getUpdateState() == ExtensionUpdateState.UNKNOWN) {
            metadata.setUpdateState(resolveDefaultUpdateState(extension.getSource()));
            changed = true;
        }
        if (metadata.getUpdateMessage() == null || metadata.getUpdateMessage().isBlank()) {
            metadata.setUpdateMessage(defaultUpdateMessage(extension.getSource()));
            changed = true;
        }
        if (metadata.getLastCheckedForUpdatesAtEpochMillis() == null && metadata.getUpdateState() == ExtensionUpdateState.UNTRACKED) {
            metadata.setLastCheckedForUpdatesAtEpochMillis(now);
            changed = true;
        }
        return changed;
    }

    private boolean applyTrackingState(ExtensionLocalMetadata metadata,
                                       String knownRemoteVersion,
                                       String knownRemoteVersionId,
                                       long checkedAt,
                                       ExtensionUpdateState updateState,
                                       String updateMessage) {
        boolean changed = false;
        changed |= setIfChanged(metadata.getKnownRemoteVersion(), knownRemoteVersion, metadata::setKnownRemoteVersion);
        changed |= setIfChanged(metadata.getKnownRemoteVersionId(), knownRemoteVersionId, metadata::setKnownRemoteVersionId);
        changed |= setIfChanged(metadata.getLastCheckedForUpdatesAtEpochMillis(), checkedAt, metadata::setLastCheckedForUpdatesAtEpochMillis);
        changed |= setIfChanged(metadata.getUpdateState(), updateState == null ? ExtensionUpdateState.UNKNOWN : updateState, metadata::setUpdateState);
        changed |= setIfChanged(metadata.getUpdateMessage(), updateMessage, metadata::setUpdateMessage);
        return changed;
    }

    private boolean hasTrackedRemoteOrigin(ExtensionSource source) {
        if (source == null || source.getType() == null) {
            return false;
        }
        if (source.getProvider() == null || source.getProvider().isBlank()) {
            return false;
        }
        if (source.getProjectId() == null || source.getProjectId().isBlank()) {
            return false;
        }
        return switch (source.getType()) {
            case MODRINTH, CURSEFORGE, HANGAR -> true;
            default -> false;
        };
    }

    private ExtensionUpdateState resolveDefaultUpdateState(ExtensionSource source) {
        return hasTrackedRemoteOrigin(source) ? ExtensionUpdateState.UNKNOWN : ExtensionUpdateState.UNTRACKED;
    }

    private String defaultUpdateMessage(ExtensionSource source) {
        return hasTrackedRemoteOrigin(source)
                ? "La extensión tiene origen remoto, pero su estado de actualización aún no se ha comprobado."
                : "Instalada sin origen remoto conocido.";
    }

    private <T> boolean setIfChanged(T currentValue, T newValue, java.util.function.Consumer<T> setter) {
        if (Objects.equals(currentValue, newValue)) {
            return false;
        }
        setter.accept(newValue);
        return true;
    }

    private ExtensionDescriptor inspectJarDescriptor(Path jarPath,
                                                     ServerPlatform serverPlatform,
                                                     ServerEcosystemType ecosystemType) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarMetadataAccumulator metadata = new JarMetadataAccumulator(jarPath, jarFile);
            boolean hasPluginDescriptor = jarFile.getJarEntry("plugin.yml") != null || jarFile.getJarEntry("paper-plugin.yml") != null;
            if (hasPluginDescriptor && prefersPluginDescriptor(serverPlatform, ecosystemType)) {
                return readPluginDescriptor(jarFile, serverPlatform, metadata);
            }
            if (jarFile.getJarEntry("META-INF/mods.toml") != null || jarFile.getJarEntry("META-INF/neoforge.mods.toml") != null) {
                String descriptorPath = jarFile.getJarEntry("META-INF/neoforge.mods.toml") != null
                        ? "META-INF/neoforge.mods.toml"
                        : "META-INF/mods.toml";
                String modsToml = readJarText(jarFile, descriptorPath);
                metadata.addMetadataFile(descriptorPath);
                metadata.addManifest(jarFile.getManifest());
                metadata.primaryId = readTomlValue(modsToml, "modId");
                metadata.addString(metadata.authors, readTomlValue(modsToml, "authors"));
                metadata.addString(metadata.supportedLoaders, descriptorPath.contains("neoforge") ? "NeoForge" : "Forge");
                metadata.addString(metadata.supportedLoaders, readTomlValue(modsToml, "modLoader"));
                metadata.addString(metadata.localDependencyDescriptions, readTomlDependencies(modsToml));
                metadata.addDependencies(readTomlDependencyObjects(modsToml, descriptorPath.contains("neoforge") ? "neoforge" : "forge"));
                metadata.addString(metadata.supportedMinecraftVersions, readModsTomlMinecraftConstraint(modsToml));
                metadata.addIcon(readTomlValue(modsToml, "logoFile"));
                metadata.websiteUrl = firstNonBlank(readTomlValue(modsToml, "displayURL"), readTomlValue(modsToml, "updateJSONURL"));
                metadata.issuesUrl = readTomlValue(modsToml, "issueTrackerURL");
                metadata.licenseName = readTomlValue(modsToml, "license");
                return new ExtensionDescriptor(
                        firstNonBlank(readTomlValue(modsToml, "displayName"), readManifestValue(jarFile.getManifest(), "Implementation-Title")),
                        firstNonBlank(readTomlValue(modsToml, "version"), readManifestValue(jarFile.getManifest(), "Implementation-Version")),
                        firstNonBlank(readTomlValue(modsToml, "description"), readManifestValue(jarFile.getManifest(), "Implementation-Description")),
                        firstNonBlank(readTomlValue(modsToml, "authors"), readManifestValue(jarFile.getManifest(), "Implementation-Vendor")),
                        ServerExtensionType.MOD,
                        serverPlatform == ServerPlatform.UNKNOWN
                                ? (descriptorPath.contains("neoforge") ? ServerPlatform.NEOFORGE : ServerPlatform.FORGE)
                                : serverPlatform,
                        readModsTomlMinecraftConstraint(modsToml),
                        metadata.snapshot()
                );
            }
            if (jarFile.getJarEntry("fabric.mod.json") != null) {
                JsonNode node = readJson(jarFile, "fabric.mod.json");
                metadata.addMetadataFile("fabric.mod.json");
                metadata.addManifest(jarFile.getManifest());
                metadata.addJsonStringOrArray(metadata.authors, node, "authors");
                metadata.addString(metadata.supportedLoaders, "Fabric");
                metadata.addString(metadata.supportedMinecraftVersions, readNestedMinecraftConstraint(node, "depends"));
                metadata.addJsonDependencies(node, "depends");
                metadata.addJsonDependencies(node, "recommends");
                metadata.addJsonDependencies(node, "suggests");
                metadata.addJsonDependencies(node, "breaks");
                metadata.addJsonDependencies(node, "conflicts");
                metadata.addIcon(text(node, "icon"));
                JsonNode contact = node == null ? null : node.path("contact");
                metadata.websiteUrl = firstNonBlank(text(contact, "homepage"), text(contact, "sources"), text(contact, "discord"));
                metadata.issuesUrl = text(contact, "issues");
                metadata.licenseName = text(node, "license");
                metadata.clientSide = text(node, "environment");
                metadata.serverSide = text(node, "environment");
                return new ExtensionDescriptor(
                        text(node, "name"),
                        text(node, "version"),
                        text(node, "description"),
                        text(node, "authors", 0),
                        ServerExtensionType.MOD,
                        ServerPlatform.FABRIC,
                        readNestedMinecraftConstraint(node, "depends"),
                        metadata.snapshot()
                );
            }
            if (jarFile.getJarEntry("quilt.mod.json") != null) {
                JsonNode node = readJson(jarFile, "quilt.mod.json");
                JsonNode quiltLoader = node == null ? null : node.path("quilt_loader");
                JsonNode quiltMetadata = quiltLoader == null ? null : quiltLoader.path("metadata");
                JarMetadataAccumulator jarMetadata = new JarMetadataAccumulator(jarPath, jarFile);
                jarMetadata.addMetadataFile("quilt.mod.json");
                jarMetadata.addManifest(jarFile.getManifest());
                jarMetadata.addString(jarMetadata.supportedLoaders, "Quilt");
                jarMetadata.addString(jarMetadata.supportedMinecraftVersions, readNestedMinecraftConstraint(quiltLoader, "depends"));
                jarMetadata.addJsonDependencies(quiltLoader, "depends");
                jarMetadata.addJsonDependencies(quiltLoader, "breaks");
                jarMetadata.addJsonStringOrArray(jarMetadata.authors, quiltMetadata, "contributors");
                jarMetadata.addIcon(firstNonBlank(text(quiltMetadata, "icon"), text(node, "icon")));
                JsonNode contact = quiltMetadata == null ? null : quiltMetadata.path("contact");
                jarMetadata.websiteUrl = firstNonBlank(text(contact, "homepage"), text(contact, "sources"));
                jarMetadata.issuesUrl = text(contact, "issues");
                jarMetadata.licenseName = text(quiltMetadata, "license");
                return new ExtensionDescriptor(
                        firstNonBlank(text(quiltLoader, "name"), text(quiltMetadata, "name")),
                        text(quiltLoader, "version"),
                        text(quiltMetadata, "description"),
                        text(quiltMetadata, "contributors", 0),
                        ServerExtensionType.MOD,
                        ServerPlatform.QUILT,
                        readNestedMinecraftConstraint(quiltLoader, "depends"),
                        jarMetadata.snapshot()
                );
            }
            if (hasPluginDescriptor) {
                return readPluginDescriptor(jarFile, serverPlatform, metadata);
            }
            if (jarFile.getJarEntry("mcmod.info") != null) {
                JsonNode infoNode = readJson(jarFile, "mcmod.info");
                JsonNode first = infoNode != null && infoNode.isArray() && !infoNode.isEmpty() ? infoNode.get(0) : infoNode;
                metadata.addMetadataFile("mcmod.info");
                metadata.addManifest(jarFile.getManifest());
                metadata.addJsonStringOrArray(metadata.authors, first, "authorList");
                metadata.addString(metadata.supportedLoaders, "Forge");
                metadata.addString(metadata.supportedMinecraftVersions, text(first, "mcversion"));
                metadata.addIcon(text(first, "logoFile"));
                metadata.websiteUrl = text(first, "url");
                return new ExtensionDescriptor(
                        text(first, "name"),
                        text(first, "version"),
                        text(first, "description"),
                        text(first, "authorList", 0),
                        ServerExtensionType.MOD,
                        serverPlatform == ServerPlatform.UNKNOWN ? ServerPlatform.FORGE : serverPlatform,
                        text(first, "mcversion"),
                        metadata.snapshot()
                );
            }

            Manifest manifest = jarFile.getManifest();
            metadata.addManifest(manifest);
            return new ExtensionDescriptor(
                    readManifestValue(manifest, "Implementation-Title"),
                    readManifestValue(manifest, "Implementation-Version"),
                    readManifestValue(manifest, "Implementation-Description"),
                    readManifestValue(manifest, "Implementation-Vendor"),
                    ecosystemType == ServerEcosystemType.PLUGINS ? ServerExtensionType.PLUGIN
                            : ecosystemType == ServerEcosystemType.MODS ? ServerExtensionType.MOD
                            : ServerExtensionType.UNKNOWN,
                    serverPlatform,
                    null,
                    metadata.snapshot()
            );
        }
    }

    private boolean prefersPluginDescriptor(ServerPlatform serverPlatform, ServerEcosystemType ecosystemType) {
        if (ecosystemType == ServerEcosystemType.PLUGINS) {
            return true;
        }
        return serverPlatform != null && serverPlatform.isPluginPlatform();
    }

    private ExtensionDescriptor readPluginDescriptor(JarFile jarFile,
                                                     ServerPlatform serverPlatform,
                                                     JarMetadataAccumulator metadata) throws IOException {
        String descriptorPath = jarFile.getJarEntry("paper-plugin.yml") != null ? "paper-plugin.yml" : "plugin.yml";
        String pluginYaml = readJarText(jarFile, descriptorPath);
        metadata.addMetadataFile(descriptorPath);
        metadata.addManifest(jarFile.getManifest());
        metadata.addYamlValues(metadata.authors, pluginYaml, "author");
        metadata.addYamlValues(metadata.authors, pluginYaml, "authors");
        metadata.addString(metadata.supportedLoaders, descriptorPath.equals("paper-plugin.yml") ? "Paper" : "Bukkit");
        String apiVersion = readYamlValue(pluginYaml, "api-version");
        metadata.addString(metadata.supportedMinecraftVersions, apiVersion);
        metadata.addYamlDependencies(pluginYaml, "depend", descriptorPath.equals("paper-plugin.yml") ? "paper" : "bukkit", true);
        metadata.addYamlDependencies(pluginYaml, "softdepend", descriptorPath.equals("paper-plugin.yml") ? "paper" : "bukkit", false);
        metadata.addYamlDependencies(pluginYaml, "loadbefore", descriptorPath.equals("paper-plugin.yml") ? "paper" : "bukkit", false);
        if (descriptorPath.equals("paper-plugin.yml")) {
            metadata.addDependencies(readPaperPluginDependencies(pluginYaml));
        }
        metadata.websiteUrl = readYamlValue(pluginYaml, "website");
        return new ExtensionDescriptor(
                firstNonBlank(readYamlValue(pluginYaml, "name"), readManifestValue(jarFile.getManifest(), "Implementation-Title")),
                firstNonBlank(readYamlValue(pluginYaml, "version"), readManifestValue(jarFile.getManifest(), "Implementation-Version")),
                firstNonBlank(readYamlValue(pluginYaml, "description"), readManifestValue(jarFile.getManifest(), "Implementation-Description")),
                firstNonBlank(readYamlValue(pluginYaml, "author"), readManifestValue(jarFile.getManifest(), "Implementation-Vendor")),
                ServerExtensionType.PLUGIN,
                serverPlatform == ServerPlatform.UNKNOWN
                        ? (descriptorPath.equals("paper-plugin.yml") ? ServerPlatform.PAPER : ServerPlatform.UNKNOWN)
                        : serverPlatform,
                pluginApiVersionConstraint(apiVersion),
                metadata.snapshot()
        );
    }

    private JsonNode readJson(JarFile jarFile, String entryName) throws IOException {
        String raw = readJarText(jarFile, entryName);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OBJECT_MAPPER.readTree(raw);
    }

    private String readJarText(JarFile jarFile, String entryName) throws IOException {
        JarEntry entry = jarFile.getJarEntry(entryName);
        if (entry == null) {
            return null;
        }
        try (InputStream input = jarFile.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readTomlValue(String toml, String key) {
        if (toml == null || toml.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        String[] lines = toml.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null) {
                continue;
            }
            String trimmed = line.stripLeading();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String foundKey = trimmed.substring(0, equalsIndex).trim();
            if (!key.equals(foundKey)) {
                continue;
            }
            String value = trimmed.substring(equalsIndex + 1).stripLeading();
            if (value.startsWith("'''") || value.startsWith("\"\"\"")) {
                String delimiter = value.startsWith("'''") ? "'''" : "\"\"\"";
                String remainder = value.substring(3);
                int end = remainder.indexOf(delimiter);
                if (end >= 0) {
                    return cleanMetadataValue(remainder.substring(0, end));
                }
                StringBuilder block = new StringBuilder(remainder);
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j] == null ? "" : lines[j];
                    int blockEnd = next.indexOf(delimiter);
                    if (blockEnd >= 0) {
                        if (!block.isEmpty()) {
                            block.append('\n');
                        }
                        block.append(next, 0, blockEnd);
                        return cleanMetadataValue(block.toString());
                    }
                    if (!block.isEmpty()) {
                        block.append('\n');
                    }
                    block.append(next);
                }
                return cleanMetadataValue(block.toString());
            }
            return cleanMetadataValue(stripTomlInlineComment(value));
        }
        return null;
    }

    private String stripTomlInlineComment(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && doubleQuoted) {
                escaped = true;
                continue;
            }
            if (ch == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                continue;
            }
            if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (ch == '#' && !singleQuoted && !doubleQuoted) {
                return value.substring(0, i).trim();
            }
        }
        return value.trim();
    }

    private List<String> readTomlDependencies(String toml) {
        if (toml == null || toml.isBlank()) {
            return List.of();
        }
        List<String> dependencies = new ArrayList<>();
        Pattern dependencyBlock = Pattern.compile("(?s)\\[\\[dependencies\\.[^]]+]](.*?)(?=\\n\\s*\\[\\[|\\z)");
        Matcher matcher = dependencyBlock.matcher(toml);
        while (matcher.find()) {
            String block = matcher.group(1);
            String modId = readTomlValue(block, "modId");
            if (modId == null || modId.isBlank()) {
                continue;
            }
            String type = firstNonBlank(readTomlValue(block, "type"), readTomlValue(block, "ordering"), readTomlValue(block, "mandatory"));
            String version = firstNonBlank(readTomlValue(block, "versionRange"), readTomlValue(block, "version"));
            dependencies.add(modId + (version == null ? "" : " " + version) + (type == null ? "" : " (" + type + ")"));
        }
        return dependencies;
    }

    private List<ExtensionRemoteDependency> readTomlDependencyObjects(String toml, String providerId) {
        if (toml == null || toml.isBlank()) {
            return List.of();
        }
        List<ExtensionRemoteDependency> dependencies = new ArrayList<>();
        Pattern dependencyBlock = Pattern.compile("(?s)\\[\\[dependencies\\.[^]]+]](.*?)(?=\\n\\s*\\[\\[|\\z)");
        Matcher matcher = dependencyBlock.matcher(toml);
        while (matcher.find()) {
            String block = matcher.group(1);
            String modId = readTomlValue(block, "modId");
            if (isRuntimeDependencyId(modId)) {
                continue;
            }
            String version = firstNonBlank(readTomlValue(block, "versionRange"), readTomlValue(block, "version"));
            String mandatory = readTomlValue(block, "mandatory");
            boolean required = mandatory == null || !"false".equalsIgnoreCase(mandatory);
            String dependencyType = firstNonBlank(readTomlValue(block, "type"), required ? "required" : "optional");
            dependencies.add(localDependency(providerId, modId, modId, version, dependencyType, required));
        }
        return dependencies;
    }

    private String readModsTomlMinecraftConstraint(String toml) {
        if (toml == null || toml.isBlank()) {
            return null;
        }
        Pattern dependencyBlock = Pattern.compile("(?s)\\[\\[dependencies\\.[^]]+]](.*?)(?=\\n\\s*\\[\\[|\\z)");
        Matcher matcher = dependencyBlock.matcher(toml);
        while (matcher.find()) {
            String block = matcher.group(1);
            String modId = readTomlValue(block, "modId");
            if (!"minecraft".equalsIgnoreCase(modId)) {
                continue;
            }
            return firstNonBlank(readTomlValue(block, "versionRange"), readTomlValue(block, "version"));
        }
        return null;
    }

    private List<String> readYamlListValues(String yaml, String key) {
        if (yaml == null || yaml.isBlank() || key == null || key.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();

        String[] lines = yaml.split("\\R", -1);
        Pattern keyPattern = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*:\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);
        boolean inBlock = false;
        int keyIndent = -1;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (!inBlock) {
                Matcher keyMatcher = keyPattern.matcher(line);
                if (keyMatcher.matches()) {
                    String inline = cleanMetadataValue(keyMatcher.group(1));
                    if (inline != null && !inline.isBlank()) {
                        addDelimitedYamlValues(values, inline);
                        return values;
                    }
                    inBlock = true;
                    keyIndent = indent;
                }
                continue;
            }
            if (indent <= keyIndent && trimmed.contains(":")) {
                break;
            }
            if (trimmed.startsWith("-")) {
                addDelimitedYamlValues(values, trimmed.substring(1).trim());
            }
        }
        return values;
    }

    private void addDelimitedYamlValues(List<String> target, String rawValue) {
        String cleaned = cleanMetadataValue(rawValue);
        if (cleaned == null || cleaned.isBlank()) {
            return;
        }
        for (String value : cleaned.split(",")) {
            String item = cleanMetadataValue(value);
            if (item != null && !item.isBlank()) {
                target.add(item);
            }
        }
    }

    private List<ExtensionRemoteDependency> readPaperPluginDependencies(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return List.of();
        }
        List<ExtensionRemoteDependency> dependencies = new ArrayList<>();
        String[] lines = yaml.split("\\R", -1);
        boolean inDependencies = false;
        boolean inServer = false;
        int dependenciesIndent = -1;
        int serverIndent = -1;
        String currentPlugin = null;
        int currentIndent = -1;
        boolean currentRequired = true;
        String currentLoad = null;

        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (!inDependencies) {
                if (trimmed.equalsIgnoreCase("dependencies:")) {
                    inDependencies = true;
                    dependenciesIndent = indent;
                }
                continue;
            }
            if (indent <= dependenciesIndent && trimmed.endsWith(":")) {
                break;
            }
            if (!inServer) {
                if (indent > dependenciesIndent && trimmed.equalsIgnoreCase("server:")) {
                    inServer = true;
                    serverIndent = indent;
                }
                continue;
            }
            if (indent <= serverIndent) {
                break;
            }

            Matcher pluginMatcher = Pattern.compile("^([^:#][^:]*):\\s*$").matcher(trimmed);
            if (indent > serverIndent && pluginMatcher.matches()) {
                addPendingPaperDependency(dependencies, currentPlugin, currentRequired, currentLoad);
                currentPlugin = cleanMetadataValue(pluginMatcher.group(1));
                currentIndent = indent;
                currentRequired = true;
                currentLoad = null;
                continue;
            }
            if (currentPlugin == null || indent <= currentIndent || !trimmed.contains(":")) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            String key = parts[0].trim();
            String value = cleanMetadataValue(parts.length > 1 ? parts[1] : null);
            if ("required".equalsIgnoreCase(key) && value != null) {
                currentRequired = !"false".equalsIgnoreCase(value);
            } else if ("load".equalsIgnoreCase(key)) {
                currentLoad = value;
            }
        }
        addPendingPaperDependency(dependencies, currentPlugin, currentRequired, currentLoad);
        return dependencies;
    }

    private void addPendingPaperDependency(List<ExtensionRemoteDependency> target,
                                           String pluginName,
                                           boolean required,
                                           String load) {
        if (target == null || pluginName == null || pluginName.isBlank()) {
            return;
        }
        String dependencyType = firstNonBlank(load == null ? null : "server:" + load.toLowerCase(Locale.ROOT), "server");
        target.add(localDependency("paper", pluginName, pluginName, null, dependencyType, required));
    }

    private ExtensionRemoteDependency localDependency(String providerId,
                                                      String projectId,
                                                      String displayName,
                                                      String versionId,
                                                      String dependencyType,
                                                      boolean required) {
        ExtensionRemoteDependency dependency = new ExtensionRemoteDependency();
        dependency.setProviderId(providerId);
        dependency.setProjectId(projectId);
        dependency.setVersionId(versionId);
        dependency.setDisplayName(displayName);
        dependency.setDependencyType(dependencyType);
        dependency.setRequired(required);
        return dependency;
    }

    private boolean isRuntimeDependencyId(String id) {
        if (id == null || id.isBlank()) {
            return true;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("minecraft")
                || normalized.equals("java")
                || normalized.equals("fabricloader")
                || normalized.equals("quilt_loader")
                || normalized.equals("forge")
                || normalized.equals("neoforge");
    }

    private String readYamlValue(String yaml, String key) {
        String value = matchPattern(yaml, YAML_STRING_PATTERN.formatted(Pattern.quote(key)));
        if (value == null) {
            return null;
        }
        String trimmed = cleanMetadataValue(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String matchPattern(String content, String expression) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile(expression).matcher(content);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        String value = firstNonBlank(matcher.group(1), matcher.groupCount() >= 2 ? matcher.group(2) : null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cleanMetadataValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        int comment = cleaned.indexOf('#');
        if (comment >= 0) {
            cleaned = cleaned.substring(0, comment).trim();
        }
        if (cleaned.length() >= 2
                && ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'")))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.length() >= 2 && cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).replace("\"", "").replace("'", "").trim();
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private String readManifestValue(Manifest manifest, String attributeName) {
        if (manifest == null || attributeName == null || attributeName.isBlank()) {
            return null;
        }
        Attributes attributes = manifest.getMainAttributes();
        String value = attributes == null ? null : attributes.getValue(attributeName);
        return value == null || value.isBlank() ? null : value.trim();
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

    private String text(JsonNode node, String fieldName, int index) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isArray() && value.size() > index) {
            JsonNode candidate = value.get(index);
            if (candidate == null) {
                return null;
            }
            if (candidate.isObject()) {
                return firstNonBlank(text(candidate, "name"), text(candidate, "user"));
            }
            String text = candidate.asText(null);
            return text == null || text.isBlank() ? null : text.trim();
        }
        return text(node, fieldName);
    }

    private String readNestedMinecraftConstraint(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode dependencies = node.path(fieldName);
        if (dependencies.isMissingNode() || dependencies.isNull()) {
            return null;
        }
        JsonNode minecraft = dependencies.path("minecraft");
        if (minecraft.isMissingNode() || minecraft.isNull()) {
            return null;
        }
        if (minecraft.isArray() && !minecraft.isEmpty()) {
            JsonNode first = minecraft.get(0);
            return first == null ? null : first.asText(null);
        }
        if (minecraft.isObject()) {
            return firstNonBlank(text(minecraft, "version"), text(minecraft, "versions", 0));
        }
        return minecraft.asText(null);
    }

    private List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    private boolean matchesMinecraftConstraint(String serverVersion, String constraint) {
        if (serverVersion == null || serverVersion.isBlank() || constraint == null || constraint.isBlank()) {
            return true;
        }
        String normalizedConstraint = constraint.trim();
        if (normalizedConstraint.contains("||")) {
            for (String option : normalizedConstraint.split("\\|\\|")) {
                if (matchesMinecraftConstraint(serverVersion, option)) {
                    return true;
                }
            }
            return false;
        }
        if (normalizedConstraint.contains(",")) {
            boolean allSatisfied = true;
            for (String part : normalizedConstraint.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (looksLikeRangePart(trimmed)) {
                    allSatisfied &= matchesSingleConstraint(serverVersion, trimmed);
                }
            }
            if (allSatisfied && normalizedConstraint.chars().noneMatch(ch -> ch == '[' || ch == '(')) {
                return true;
            }
        }
        if ((normalizedConstraint.startsWith("[") || normalizedConstraint.startsWith("("))
                && (normalizedConstraint.endsWith("]") || normalizedConstraint.endsWith(")"))) {
            return matchesMavenRange(serverVersion, normalizedConstraint);
        }
        return matchesSingleConstraint(serverVersion, normalizedConstraint);
    }

    private String pluginApiVersionConstraint(String apiVersion) {
        String normalized = normalizeMinecraftVersion(apiVersion);
        return normalized == null ? null : ">=" + normalized;
    }

    private String normalizeMinecraftVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        Matcher matcher = MINECRAFT_VERSION_HINT_PATTERN.matcher(version.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean looksLikeRangePart(String value) {
        return value.startsWith(">") || value.startsWith("<") || value.startsWith("=") || value.startsWith("~") || value.startsWith("^");
    }

    private boolean matchesSingleConstraint(String serverVersion, String constraint) {
        String value = constraint == null ? "" : constraint.trim();
        if (value.isEmpty() || "*".equals(value)) {
            return true;
        }
        if (value.startsWith(">=")) {
            return compareVersions(serverVersion, value.substring(2).trim()) >= 0;
        }
        if (value.startsWith("<=")) {
            return compareVersions(serverVersion, value.substring(2).trim()) <= 0;
        }
        if (value.startsWith(">")) {
            return compareVersions(serverVersion, value.substring(1).trim()) > 0;
        }
        if (value.startsWith("<")) {
            return compareVersions(serverVersion, value.substring(1).trim()) < 0;
        }
        if (value.startsWith("=")) {
            return compareVersions(serverVersion, value.substring(1).trim()) == 0;
        }
        if (value.startsWith("~")) {
            String base = value.substring(1).trim();
            return serverVersion.startsWith(base);
        }
        if (value.startsWith("^")) {
            String base = value.substring(1).trim();
            String[] serverParts = splitVersion(serverVersion);
            String[] baseParts = splitVersion(base);
            return serverParts.length > 0 && baseParts.length > 0
                    && serverParts[0].equals(baseParts[0])
                    && compareVersions(serverVersion, base) >= 0;
        }
        return compareVersions(serverVersion, value) == 0
                || serverVersion.startsWith(value + ".")
                || value.startsWith(serverVersion + ".");
    }

    private boolean matchesMavenRange(String serverVersion, String range) {
        String trimmed = range.trim();
        boolean includeLower = trimmed.startsWith("[");
        boolean includeUpper = trimmed.endsWith("]");
        String body = trimmed.substring(1, trimmed.length() - 1);
        if (!body.contains(",")) {
            String exact = body.trim();
            if (exact.isEmpty()) {
                return true;
            }
            if (!includeLower || !includeUpper) {
                return false;
            }
            return compareVersions(serverVersion, exact) == 0;
        }
        String[] parts = body.split(",", -1);
        String lower = parts.length > 0 ? parts[0].trim() : "";
        String upper = parts.length > 1 ? parts[1].trim() : "";
        if (!lower.isEmpty()) {
            int compare = compareVersions(serverVersion, lower);
            if (compare < 0 || (!includeLower && compare == 0)) {
                return false;
            }
        }
        if (!upper.isEmpty()) {
            int compare = compareVersions(serverVersion, upper);
            if (compare > 0 || (!includeUpper && compare == 0)) {
                return false;
            }
        }
        return true;
    }

    private int compareVersions(String left, String right) {
        String[] a = splitVersion(left);
        String[] b = splitVersion(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int partA = i < a.length ? parseVersionPart(a[i]) : 0;
            int partB = i < b.length ? parseVersionPart(b[i]) : 0;
            int compare = Integer.compare(partA, partB);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private String[] splitVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("[^A-Za-z0-9]+");
    }

    private int parseVersionPart(String part) {
        if (part == null || part.isBlank()) {
            return 0;
        }
        String digits = part.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String inferMinecraftVersionHint(String... texts) {
        String found = null;
        if (texts == null) {
            return null;
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher matcher = MINECRAFT_VERSION_HINT_PATTERN.matcher(text);
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (found == null) {
                    found = candidate;
                } else if (!found.equals(candidate)) {
                    return null;
                }
            }
        }
        return found;
    }

    private String calculateSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 no disponible.", e);
        }
    }

    private long resolveLastModified(Path file, long fallback) throws IOException {
        FileTime lastModified = Files.getLastModifiedTime(file);
        return lastModified == null ? fallback : lastModified.toMillis();
    }

    private ServerPlatformProfile resolveProfile(Server server) {
        Path serverDir = resolveServerDir(server);
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return null;
        }
        return ServerPlatformAdapters.detect(serverDir);
    }

    private ServerPlatformAdapter resolveAdapter(Server server, ServerPlatformProfile profile) {
        ServerPlatform configuredPlatform = server == null || server.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : server.getPlatform();
        if (configuredPlatform != ServerPlatform.UNKNOWN) {
            return ServerPlatformAdapters.forPlatform(configuredPlatform);
        }
        if (profile != null && profile.platform() != null) {
            return ServerPlatformAdapters.forPlatform(profile.platform());
        }
        return ServerPlatformAdapters.forPlatform(ServerPlatform.UNKNOWN);
    }

    private Path resolveServerDir(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        try {
            return Path.of(server.getServerDir());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ServerPlatform resolvePlatform(Server server, ServerPlatformProfile profile) {
        ServerPlatform configuredPlatform = server == null || server.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : server.getPlatform();
        if (configuredPlatform != ServerPlatform.UNKNOWN) {
            return configuredPlatform;
        }
        if (profile != null && profile.platform() != null && profile.platform() != ServerPlatform.UNKNOWN) {
            return profile.platform();
        }
        return ServerPlatform.UNKNOWN;
    }

    private ServerEcosystemType resolveEcosystemType(Server server, ServerPlatformProfile profile) {
        ServerEcosystemType configuredEcosystem = server == null || server.getEcosystemType() == null
                ? ServerEcosystemType.UNKNOWN
                : server.getEcosystemType();
        if (configuredEcosystem != ServerEcosystemType.UNKNOWN) {
            return configuredEcosystem;
        }
        if (profile != null && profile.ecosystemType() != null && profile.ecosystemType() != ServerEcosystemType.UNKNOWN) {
            return profile.ecosystemType();
        }
        return ServerEcosystemType.UNKNOWN;
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

    private String resolveInstallFileName(Path sourceJar, ExtensionDownloadPlan downloadPlan) {
        String requested = normalizeFileName(downloadPlan == null ? null : downloadPlan.fileName());
        if (requested != null) {
            return requested;
        }
        String rawUrlFileName = fileNameFromUrl(downloadPlan == null ? null : downloadPlan.downloadUrl());
        String urlFileName = normalizeFileName(rawUrlFileName);
        if (urlFileName != null && rawUrlFileName != null && rawUrlFileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return urlFileName;
        }
        String metadataFileName = normalizeFileName(downloadPlan == null ? null : firstNonBlank(downloadPlan.displayName(), downloadPlan.projectId()));
        if (metadataFileName != null) {
            return metadataFileName;
        }
        return normalizeFileName(sourceJar == null || sourceJar.getFileName() == null ? null : sourceJar.getFileName().toString());
    }

    private String fileNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(url.trim()).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            return Path.of(path).getFileName().toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String normalized = Path.of(fileName.trim()).getFileName().toString().trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT).endsWith(".jar") ? normalized : normalized + ".jar";
    }

    private String stripJarExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Extensión";
        }
        String normalized = fileName.trim();
        return normalized.toLowerCase(Locale.ROOT).endsWith(".jar")
                ? normalized.substring(0, normalized.length() - 4)
                : normalized;
    }

    private String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.replace('\\', '/');
    }

    private final class JarMetadataAccumulator {
        private final Path jarPath;
        private final JarFile jarFile;
        private final List<String> authors = new ArrayList<>();
        private final List<String> supportedLoaders = new ArrayList<>();
        private final List<String> supportedMinecraftVersions = new ArrayList<>();
        private final List<String> embeddedMetadataFiles = new ArrayList<>();
        private final List<String> localDependencyDescriptions = new ArrayList<>();
        private final List<ExtensionRemoteDependency> dependencies = new ArrayList<>();
        private final Map<String, String> manifestAttributes = new LinkedHashMap<>();
        private String localIconUrl;
        private String localIconPath;
        private boolean declaredIconPathSeen;
        private String primaryId;
        private String websiteUrl;
        private String issuesUrl;
        private String licenseName;
        private String clientSide;
        private String serverSide;

        private JarMetadataAccumulator(Path jarPath, JarFile jarFile) {
            this.jarPath = jarPath;
            this.jarFile = jarFile;
            addCommonMetadataFiles();
        }

        private void addCommonMetadataFiles() {
            List.of(
                    "META-INF/mods.toml",
                    "META-INF/neoforge.mods.toml",
                    "fabric.mod.json",
                    "quilt.mod.json",
                    "mcmod.info",
                    "plugin.yml",
                    "paper-plugin.yml",
                    "META-INF/MANIFEST.MF"
            ).forEach(this::addMetadataFileIfPresent);
        }

        private void addMetadataFileIfPresent(String entryName) {
            if (entryName != null && jarFile.getJarEntry(entryName) != null) {
                addMetadataFile(entryName);
            }
        }

        private void addMetadataFile(String entryName) {
            addString(embeddedMetadataFiles, entryName);
        }

        private void addManifest(Manifest manifest) {
            if (manifest == null || manifest.getMainAttributes() == null) {
                return;
            }
            for (Map.Entry<Object, Object> entry : manifest.getMainAttributes().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    manifestAttributes.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
        }

        private void addJsonStringOrArray(List<String> target, JsonNode node, String fieldName) {
            if (node == null || fieldName == null || fieldName.isBlank()) {
                return;
            }
            JsonNode value = node.path(fieldName);
            if (value.isMissingNode() || value.isNull()) {
                return;
            }
            if (value.isArray()) {
                for (int i = 0; i < value.size(); i++) {
                    addJsonValue(target, value.get(i));
                }
                return;
            }
            if (value.isObject()) {
                for (Map.Entry<String, JsonNode> entry : value.properties()) {
                    addString(target, firstNonBlank(text(entry.getValue(), "name"), entry.getKey()));
                }
                return;
            }
            addString(target, value.asText(null));
        }

        private void addJsonValue(List<String> target, JsonNode value) {
            if (value == null || value.isNull() || value.isMissingNode()) {
                return;
            }
            if (value.isObject()) {
                addString(target, firstNonBlank(text(value, "name"), text(value, "user"), text(value, "email")));
            } else {
                addString(target, value.asText(null));
            }
        }

        private void addJsonDependencies(JsonNode node, String fieldName) {
            if (node == null || fieldName == null || fieldName.isBlank()) {
                return;
            }
            JsonNode dependencies = node.path(fieldName);
            if (dependencies.isMissingNode() || dependencies.isNull()) {
                return;
            }
            if (dependencies.isObject()) {
                for (Map.Entry<String, JsonNode> entry : dependencies.properties()) {
                    String name = entry.getKey();
                    String version = jsonDependencyVersion(entry.getValue());
                    addString(localDependencyDescriptions, name + (version == null ? "" : " " + version));
                    if ("minecraft".equalsIgnoreCase(name)) {
                        addString(supportedMinecraftVersions, version);
                    } else if (!isRuntimeDependencyId(name)) {
                        addDependency("local", name, name, version, fieldName, isRequiredJsonDependency(fieldName));
                    }
                }
                return;
            }
            if (dependencies.isArray()) {
                for (int i = 0; i < dependencies.size(); i++) {
                    JsonNode dependency = dependencies.get(i);
                    String id = firstNonBlank(text(dependency, "id"), text(dependency, "modid"));
                    String version = jsonDependencyVersion(dependency);
                    addString(localDependencyDescriptions, firstNonBlank(id, dependency.asText(null)) + (version == null ? "" : " " + version));
                    if ("minecraft".equalsIgnoreCase(id)) {
                        addString(supportedMinecraftVersions, version);
                    } else if (!isRuntimeDependencyId(id)) {
                        addDependency("local", id, id, version, fieldName, isRequiredJsonDependency(fieldName));
                    }
                }
            }
        }

        private boolean isRequiredJsonDependency(String fieldName) {
            return fieldName != null && fieldName.equalsIgnoreCase("depends");
        }

        private String jsonDependencyVersion(JsonNode value) {
            if (value == null || value.isNull() || value.isMissingNode()) {
                return null;
            }
            if (value.isObject()) {
                return firstNonBlank(text(value, "version"), text(value, "versions"));
            }
            if (value.isArray() && !value.isEmpty()) {
                return value.get(0).asText(null);
            }
            return value.asText(null);
        }

        private void addIcon(String iconPath) {
            boolean declaredIcon = iconPath != null && !iconPath.isBlank();
            declaredIconPathSeen |= declaredIcon;
            String normalized = normalizeJarEntryPath(iconPath);
            if (normalized == null) {
                normalized = findDeclaredIconByFileName(iconPath);
            }
            if (normalized == null && !declaredIconPathSeen) {
                normalized = findLikelyIcon();
            }
            if (normalized == null) {
                return;
            }
            localIconPath = normalized;
            localIconUrl = "jar:" + jarPath.toUri() + "!/" + normalized;
        }

        private String findDeclaredIconByFileName(String iconPath) {
            String declaredName = normalizeJarEntryPathText(iconPath);
            if (declaredName == null) {
                return null;
            }
            int slash = declaredName.lastIndexOf('/');
            String fileName = slash >= 0 ? declaredName.substring(slash + 1) : declaredName;
            if (fileName.isBlank() || !isImageEntry(fileName)) {
                return null;
            }
            String lowerFileName = fileName.toLowerCase(Locale.ROOT);
            return jarFile.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name != null && !name.endsWith("/"))
                    .filter(this::isImageEntry)
                    .filter(name -> {
                        int entrySlash = name.lastIndexOf('/');
                        String entryFileName = entrySlash >= 0 ? name.substring(entrySlash + 1) : name;
                        return entryFileName.equalsIgnoreCase(fileName)
                                || name.toLowerCase(Locale.ROOT).endsWith("/" + lowerFileName);
                    })
                    .sorted(Comparator
                            .comparingInt((String name) -> declaredIconScore(name, declaredName, lowerFileName))
                            .thenComparing(String::length)
                            .thenComparing(String.CASE_INSENSITIVE_ORDER))
                    .findFirst()
                    .orElse(null);
        }

        private int declaredIconScore(String entryName, String declaredName, String lowerFileName) {
            String lowerEntry = entryName.toLowerCase(Locale.ROOT);
            String lowerDeclared = declaredName.toLowerCase(Locale.ROOT);
            if (lowerEntry.equals(lowerDeclared)) {
                return 0;
            }
            if (!declaredName.contains("/") && lowerEntry.equals(lowerFileName)) {
                return 1;
            }
            if (lowerEntry.endsWith("/" + lowerDeclared)) {
                return 2;
            }
            if (lowerEntry.endsWith("/" + lowerFileName)) {
                return 3;
            }
            return 4;
        }

        private String findLikelyIcon() {
            List<String> candidates = safeIconCandidates();
            for (String candidate : candidates) {
                if (jarFile.getJarEntry(candidate) != null) {
                    return candidate;
                }
            }
            return candidates.stream()
                    .flatMap(candidate -> jarFile.stream()
                            .map(JarEntry::getName)
                            .filter(name -> name != null && !name.endsWith("/"))
                            .filter(name -> name.equalsIgnoreCase(candidate)))
                    .findFirst()
                    .orElse(null);
        }

        private List<String> safeIconCandidates() {
            List<String> candidates = new ArrayList<>(List.of(
                    "icon.png",
                    "logo.png",
                    "pack.png",
                    "assets/icon.png",
                    "assets/logo.png"
            ));
            String normalizedId = primaryId == null ? null : primaryId.trim().toLowerCase(Locale.ROOT);
            if (normalizedId != null && !normalizedId.isBlank() && normalizedId.matches("[a-z0-9_.-]+")) {
                candidates.add("assets/" + normalizedId + "/icon.png");
                candidates.add("assets/" + normalizedId + "/logo.png");
                candidates.add("assets/" + normalizedId + "/" + normalizedId + ".png");
            }
            return candidates;
        }

        private String normalizeJarEntryPath(String iconPath) {
            String normalized = normalizeJarEntryPathText(iconPath);
            if (normalized == null) {
                return null;
            }
            if (jarFile.getJarEntry(normalized) != null) {
                return normalized;
            }
            return jarFile.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name != null && !name.endsWith("/"))
                    .filter(name -> name.equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElse(null);
        }

        private String normalizeJarEntryPathText(String iconPath) {
            if (iconPath == null || iconPath.isBlank()) {
                return null;
            }
            String normalized = iconPath.trim().replace('\\', '/');
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                return null;
            }
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            return normalized.isBlank() ? null : normalized;
        }

        private boolean isImageEntry(String entryName) {
            if (entryName == null || entryName.isBlank()) {
                return false;
            }
            String lower = entryName.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".webp");
        }

        private void addString(List<String> target, String value) {
            if (target == null || value == null || value.isBlank()) {
                return;
            }
            String cleaned = cleanMetadataValue(value);
            if (cleaned == null || cleaned.isBlank()) {
                return;
            }
            target.add(cleaned);
        }

        private void addString(List<String> target, List<String> values) {
            if (values == null) {
                return;
            }
            for (String value : values) {
                addString(target, value);
            }
        }

        private void addDependency(String providerId,
                                   String projectId,
                                   String displayName,
                                   String versionId,
                                   String dependencyType,
                                   boolean required) {
            if (projectId == null || projectId.isBlank()) {
                return;
            }
            dependencies.add(localDependency(providerId, projectId, displayName, versionId, dependencyType, required));
        }

        private void addDependencies(List<ExtensionRemoteDependency> values) {
            if (values == null || values.isEmpty()) {
                return;
            }
            for (ExtensionRemoteDependency dependency : values) {
                if (dependency != null && dependency.getProjectId() != null && !dependency.getProjectId().isBlank()) {
                    dependencies.add(dependency);
                    addString(localDependencyDescriptions, firstNonBlank(dependency.getDisplayName(), dependency.getProjectId())
                            + (dependency.getVersionId() == null || dependency.getVersionId().isBlank() ? "" : " " + dependency.getVersionId())
                            + (dependency.getDependencyType() == null || dependency.getDependencyType().isBlank() ? "" : " (" + dependency.getDependencyType() + ")"));
                }
            }
        }

        private void addYamlDependencies(String yaml, String key, String providerId, boolean required) {
            for (String dependencyName : readYamlListValues(yaml, key)) {
                if (dependencyName == null || dependencyName.isBlank()) {
                    continue;
                }
                addString(localDependencyDescriptions, dependencyName + " (" + key + ")");
                addDependency(providerId, dependencyName, dependencyName, null, key, required);
            }
        }

        private void addYamlValues(List<String> target, String yaml, String key) {
            for (String value : readYamlListValues(yaml, key)) {
                addString(target, value);
            }
        }

        private JarMetadataSnapshot snapshot() {
            if (localIconUrl == null && !declaredIconPathSeen) {
                addIcon(null);
            }
            return new JarMetadataSnapshot(
                    copyStrings(authors),
                    copyStrings(supportedLoaders),
                    copyStrings(supportedMinecraftVersions),
                    localIconUrl,
                    localIconPath,
                    websiteUrl,
                    issuesUrl,
                    licenseName,
                    copyStrings(embeddedMetadataFiles),
                    new LinkedHashMap<>(manifestAttributes),
                    copyDependencies(dependencies),
                    copyStrings(localDependencyDescriptions),
                    clientSide,
                    serverSide
            );
        }
    }

    private record JarMetadataSnapshot(
            List<String> authors,
            List<String> supportedLoaders,
            List<String> supportedMinecraftVersions,
            String localIconUrl,
            String localIconPath,
            String websiteUrl,
            String issuesUrl,
            String licenseName,
            List<String> embeddedMetadataFiles,
            Map<String, String> manifestAttributes,
            List<ExtensionRemoteDependency> dependencies,
            List<String> localDependencyDescriptions,
            String clientSide,
            String serverSide
    ) {
    }

    private record CachedJarMetadata(
            long fileSizeBytes,
            long lastModifiedEpochMillis,
            ExtensionDescriptor descriptor,
            String sha256
    ) {
    }

    private record ExtensionDescriptor(
            String displayName,
            String version,
            String description,
            String author,
            ServerExtensionType extensionType,
            ServerPlatform platform,
            String minecraftVersionConstraint,
            JarMetadataSnapshot metadata
    ) {
        List<String> authors() {
            return metadata == null ? List.of() : metadata.authors();
        }

        List<String> supportedLoaders() {
            return metadata == null ? List.of() : metadata.supportedLoaders();
        }

        List<String> supportedMinecraftVersions() {
            return metadata == null ? List.of() : metadata.supportedMinecraftVersions();
        }

        String localIconUrl() {
            return metadata == null ? null : metadata.localIconUrl();
        }

        String localIconPath() {
            return metadata == null ? null : metadata.localIconPath();
        }

        String websiteUrl() {
            return metadata == null ? null : metadata.websiteUrl();
        }

        String issuesUrl() {
            return metadata == null ? null : metadata.issuesUrl();
        }

        String licenseName() {
            return metadata == null ? null : metadata.licenseName();
        }

        List<String> embeddedMetadataFiles() {
            return metadata == null ? List.of() : metadata.embeddedMetadataFiles();
        }

        Map<String, String> manifestAttributes() {
            return metadata == null ? Map.of() : metadata.manifestAttributes();
        }

        List<ExtensionRemoteDependency> dependencies() {
            return metadata == null ? List.of() : metadata.dependencies();
        }

        List<String> localDependencyDescriptions() {
            return metadata == null ? List.of() : metadata.localDependencyDescriptions();
        }

        String clientSide() {
            return metadata == null ? null : metadata.clientSide();
        }

        String serverSide() {
            return metadata == null ? null : metadata.serverSide();
        }
    }
}
