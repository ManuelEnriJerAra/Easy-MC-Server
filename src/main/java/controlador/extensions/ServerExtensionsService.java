package controlador.extensions;

import controlador.platform.FileDownloader;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import modelo.Server;
import modelo.extensions.ExtensionInstallState;
import modelo.extensions.ExtensionLocalMetadata;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerExtensionsService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TOML_STRING_PATTERN = "(?m)^\\s*%s\\s*=\\s*['\"](.*?)['\"]\\s*$";
    private static final String YAML_STRING_PATTERN = "(?m)^\\s*%s\\s*:\\s*(.+?)\\s*$";
    private static final Pattern MINECRAFT_VERSION_HINT_PATTERN = Pattern.compile("(?i)(?<!\\d)(1\\.(?:1[0-9]|20|21)(?:\\.\\d+)?)(?!\\d)");

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
        if (server.getExtensions() != null) {
            for (ServerExtension extension : server.getExtensions()) {
                if (extension == null || extension.getLocalMetadata() == null) {
                    continue;
                }
                String relativePath = normalizeRelativePath(extension.getLocalMetadata().getRelativePath());
                if (relativePath != null) {
                    existingByRelativePath.put(relativePath, extension);
                }
            }
        }

        Path serverDir = resolveServerDir(server);
        ServerPlatformProfile profile = resolveProfile(server);
        ServerPlatform platform = resolvePlatform(server, profile);
        ServerEcosystemType ecosystemType = resolveEcosystemType(server, profile);
        long now = System.currentTimeMillis();
        List<ServerExtension> detected = new ArrayList<>();

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
                    ServerExtension existing = existingByRelativePath.get(relativePath);
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
        return detected;
    }

    public ServerExtension installManualJar(Server server, Path sourceJar) throws IOException {
        return installJar(server, sourceJar, null, ExtensionSourceType.MANUAL, "local-manual");
    }

    public ServerExtension installCatalogDownload(Server server,
                                                  ExtensionDownloadPlan downloadPlan,
                                                  FileDownloader downloader) throws IOException {
        if (downloadPlan == null || !downloadPlan.ready()) {
            throw new IOException("No hay un plan de descarga valido para la extension.");
        }
        if (downloadPlan.downloadUrl() == null || downloadPlan.downloadUrl().isBlank()) {
            throw new IOException("La extension externa no incluye una URL de descarga valida.");
        }
        if (downloader == null) {
            throw new IOException("No se ha indicado un descargador para instalar la extension externa.");
        }
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
        return evaluateCatalogInstallation(
                server,
                entry.providerId(),
                entry.projectId(),
                entry.versionId(),
                entry.version(),
                null,
                firstNonBlank(entry.displayName(), entry.projectId(), "La extension")
        );
    }

    public ExtensionInstallResolution evaluateCatalogInstallation(Server server,
                                                                  ExtensionDownloadPlan downloadPlan) {
        if (downloadPlan == null) {
            return new ExtensionInstallResolution(ExtensionInstallResolutionState.AVAILABLE, null, null, null, null, null);
        }
        return evaluateCatalogInstallation(
                server,
                downloadPlan.providerId(),
                downloadPlan.projectId(),
                downloadPlan.versionId(),
                downloadPlan.versionNumber(),
                downloadPlan.fileName(),
                firstNonBlank(downloadPlan.projectId(), "La extension")
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
                        "No se ha podido determinar una version remota compatible.");
                continue;
            }

            changed |= applyTrackingState(metadata,
                    candidate.targetVersion().versionNumber(),
                    candidate.targetVersion().versionId(),
                    now,
                    candidate.updateAvailable() ? ExtensionUpdateState.UPDATE_AVAILABLE : ExtensionUpdateState.UP_TO_DATE,
                    candidate.message());
        }
        return changed;
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
            throw new IOException("No se ha indicado un .jar de extension valido.");
        }
        String fileName = resolveInstallFileName(sourceJar, downloadPlan);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Solo se admiten archivos .jar.");
        }
        validateLocalJar(sourceJar);

        ExtensionCompatibilityReport compatibility = validateCompatibility(server, sourceJar);
        if (compatibility.incompatible()) {
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
        return installed;
    }

    public ExtensionCompatibilityReport validateCompatibility(Server server, Path extensionJar) throws IOException {
        if (server == null) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    "No se ha indicado el servidor de destino.",
                    List.of("No existe contexto del servidor para validar la extension.")
            );
        }
        if (extensionJar == null || !Files.isRegularFile(extensionJar)) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    "No se ha indicado un .jar de extension valido.",
                    List.of("El archivo de la extension no existe o no es accesible.")
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
                    List.of("No existe contexto del servidor para validar la extension.")
            );
        }
        if (extension == null) {
            return new ExtensionCompatibilityReport(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    "No se ha podido leer la extension.",
                    List.of("No se han detectado metadatos suficientes para validar la extension.")
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

        if (serverEcosystem == ServerEcosystemType.MODS && extensionType == ServerExtensionType.PLUGIN) {
            incompatibilities.add("El servidor usa Mods y la extension se ha detectado como Plugin.");
        } else if (serverEcosystem == ServerEcosystemType.PLUGINS && extensionType == ServerExtensionType.MOD) {
            incompatibilities.add("El servidor usa Plugins y la extension se ha detectado como Mod.");
        } else if (extensionType == ServerExtensionType.UNKNOWN) {
            warnings.add("No se ha podido determinar si la extension es un mod o un plugin.");
        }

        if (extensionPlatform != ServerPlatform.UNKNOWN
                && serverPlatform != ServerPlatform.UNKNOWN
                && !arePlatformsCompatible(serverPlatform, extensionPlatform)) {
            incompatibilities.add("La extension se ha detectado para " + extensionPlatform.getLegacyTypeName()
                    + " y el servidor actual es " + serverPlatform.getLegacyTypeName() + ".");
        } else if (extensionPlatform == ServerPlatform.UNKNOWN) {
            warnings.add("La plataforma objetivo de la extension no se ha podido determinar.");
        }

        String serverVersion = server.getVersion();
        String minecraftConstraint = extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getMinecraftVersionConstraint();
        if (serverVersion == null || serverVersion.isBlank()) {
            warnings.add("No se conoce la version de Minecraft del servidor.");
        } else if (minecraftConstraint == null || minecraftConstraint.isBlank()) {
            warnings.add("La extension no declara una regla de compatibilidad para la version de Minecraft.");
        } else if (!matchesMinecraftConstraint(serverVersion, minecraftConstraint)) {
            incompatibilities.add("La extension declara compatibilidad con '" + minecraftConstraint
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
            return false;
        }

        Files.deleteIfExists(extensionPath);
        server.setExtensions(detectInstalledExtensions(server));
        return true;
    }

    private ServerExtension mergeInstalledMetadata(ServerExtension detected, ServerExtension installed) {
        if (detected == null) {
            return installed;
        }
        detected.setInstallState(ExtensionInstallState.INSTALLED);
        if (detected.getSource() == null) {
            detected.setSource(new ExtensionSource());
        }
        detected.getSource().setType(installed.getSource().getType());
        detected.getSource().setProvider(installed.getSource().getProvider());
        detected.getSource().setProjectId(installed.getSource().getProjectId());
        detected.getSource().setVersionId(installed.getSource().getVersionId());
        detected.getSource().setUrl(installed.getSource().getUrl());
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
            detected.getLocalMetadata().setUpdateState(installed.getLocalMetadata().getUpdateState());
            detected.getLocalMetadata().setUpdateMessage(installed.getLocalMetadata().getUpdateMessage());
        }
        return detected;
    }

    private void ensureCatalogExtensionNotAlreadyInstalled(Server server,
                                                           ExtensionDownloadPlan downloadPlan) throws IOException {
        ExtensionInstallResolution resolution = evaluateCatalogInstallation(server, downloadPlan);
        if (resolution.blocksInstall()) {
            throw new IOException(firstNonBlank(resolution.message(), "La extension ya esta instalada o entra en conflicto con otra existente."));
        }
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
        String displayName = firstNonBlank(fallbackName, normalizedProjectId, "La extension");
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
                        installedName + " ya esta instalada en este servidor con la misma version."
                );
            }
            return new ExtensionInstallResolution(
                    ExtensionInstallResolutionState.UPDATE_AVAILABLE,
                    projectMatch,
                    requestedFileName,
                    requestedVersion,
                    installedVersion,
                    installedName + " ya esta instalada con la version "
                            + firstNonBlank(installedVersion, "desconocida")
                            + ". El marketplace ofrece "
                            + firstNonBlank(requestedVersion, "otra version")
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
        if (downloadPlan != null && downloadPlan.iconUrl() != null && !downloadPlan.iconUrl().isBlank()) {
            source.setIconUrl(downloadPlan.iconUrl());
        }
        if (downloadPlan != null) {
            source.setProjectId(downloadPlan.projectId());
            source.setVersionId(downloadPlan.versionId());
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
            if (metadata.getInstalledVersion() != null
                    && metadata.getInstalledVersion().equalsIgnoreCase(firstNonBlank(downloadPlan.versionNumber(), metadata.getInstalledVersion()))) {
                metadata.setUpdateState(ExtensionUpdateState.UP_TO_DATE);
                metadata.setUpdateMessage("La extension se ha instalado desde su version remota conocida.");
            } else {
                metadata.setUpdateState(ExtensionUpdateState.UNKNOWN);
                metadata.setUpdateMessage("La extension conserva origen remoto, pero su estado de actualizacion todavia no se ha verificado.");
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
        ExtensionDescriptor descriptor = inspectJarDescriptor(jarPath, serverPlatform, ecosystemType);
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
        if (source.getAuthor() == null || source.getAuthor().isBlank()) {
            source.setAuthor(descriptor.author());
        }

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
        metadata.setFileSizeBytes(Files.size(jarPath));
        metadata.setSha256(calculateSha256(jarPath));
        metadata.setInstalledVersion(firstNonBlank(extension.getVersion(), metadata.getInstalledVersion()));
        metadata.setMinecraftVersionConstraint(firstNonBlank(
                descriptor.minecraftVersionConstraint(),
                inferMinecraftVersionHint(jarPath.getFileName().toString(), descriptor.version(), descriptor.displayName(), descriptor.description())
        ));
        if (metadata.getDiscoveredAtEpochMillis() == null) {
            metadata.setDiscoveredAtEpochMillis(now);
        }
        metadata.setLastUpdatedAtEpochMillis(resolveLastModified(jarPath, now));
        if (metadata.getEnabled() == null) {
            metadata.setEnabled(Boolean.TRUE);
        }
        if (metadata.getUpdateState() == null || metadata.getUpdateState() == ExtensionUpdateState.UNKNOWN) {
            metadata.setUpdateState(resolveDefaultUpdateState(source));
        }
        if (metadata.getUpdateMessage() == null || metadata.getUpdateMessage().isBlank()) {
            metadata.setUpdateMessage(defaultUpdateMessage(source));
        }
        return extension;
    }

    private void validateDownloadedJar(Path jarPath, ExtensionDownloadPlan downloadPlan) throws IOException {
        if (downloadPlan != null && downloadPlan.fileName() != null && !downloadPlan.fileName().isBlank()) {
            String expectedName = downloadPlan.fileName().trim().toLowerCase(Locale.ROOT);
            if (!expectedName.endsWith(".jar")) {
                throw new IOException("La descarga remota no apunta a un archivo .jar valido.");
            }
        }
        validateLocalJar(jarPath);
    }

    private void validateLocalJar(Path jarPath) throws IOException {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            throw new IOException("El archivo descargado no existe o no es accesible.");
        }
        long size = Files.size(jarPath);
        if (size <= 0L) {
            throw new IOException("El archivo descargado esta vacio.");
        }
        byte[] header;
        try (InputStream input = Files.newInputStream(jarPath)) {
            header = input.readNBytes(4);
        }
        if (header.length < 2 || header[0] != 'P' || header[1] != 'K') {
            throw new IOException("El archivo descargado no tiene formato ZIP/JAR valido.");
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            boolean hasEntries = jarFile.entries().hasMoreElements();
            if (!hasEntries) {
                throw new IOException("El archivo descargado no contiene entradas JAR.");
            }
        } catch (ZipException ex) {
            throw new IOException("El archivo descargado esta corrupto o no es un JAR valido.", ex);
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
                ? "La extension tiene origen remoto, pero su estado de actualizacion aun no se ha comprobado."
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
            if (jarFile.getJarEntry("META-INF/mods.toml") != null) {
                String modsToml = readJarText(jarFile, "META-INF/mods.toml");
                return new ExtensionDescriptor(
                        firstNonBlank(readTomlValue(modsToml, "displayName"), readManifestValue(jarFile.getManifest(), "Implementation-Title")),
                        firstNonBlank(readTomlValue(modsToml, "version"), readManifestValue(jarFile.getManifest(), "Implementation-Version")),
                        firstNonBlank(readTomlValue(modsToml, "description"), readManifestValue(jarFile.getManifest(), "Implementation-Description")),
                        firstNonBlank(readTomlValue(modsToml, "authors"), readManifestValue(jarFile.getManifest(), "Implementation-Vendor")),
                        ServerExtensionType.MOD,
                        serverPlatform == ServerPlatform.UNKNOWN ? ServerPlatform.FORGE : serverPlatform,
                        readModsTomlMinecraftConstraint(modsToml)
                );
            }
            if (jarFile.getJarEntry("fabric.mod.json") != null) {
                JsonNode node = readJson(jarFile, "fabric.mod.json");
                return new ExtensionDescriptor(
                        text(node, "name"),
                        text(node, "version"),
                        text(node, "description"),
                        text(node, "authors", 0),
                        ServerExtensionType.MOD,
                        ServerPlatform.FABRIC,
                        readNestedMinecraftConstraint(node, "depends")
                );
            }
            if (jarFile.getJarEntry("quilt.mod.json") != null) {
                JsonNode node = readJson(jarFile, "quilt.mod.json");
                JsonNode quiltLoader = node == null ? null : node.path("quilt_loader");
                JsonNode metadata = quiltLoader == null ? null : quiltLoader.path("metadata");
                return new ExtensionDescriptor(
                        firstNonBlank(text(quiltLoader, "name"), text(metadata, "name")),
                        text(quiltLoader, "version"),
                        text(metadata, "description"),
                        text(metadata, "contributors", 0),
                        ServerExtensionType.MOD,
                        ServerPlatform.QUILT,
                        readNestedMinecraftConstraint(quiltLoader, "depends")
                );
            }
            if (jarFile.getJarEntry("plugin.yml") != null || jarFile.getJarEntry("paper-plugin.yml") != null) {
                String descriptorPath = jarFile.getJarEntry("paper-plugin.yml") != null ? "paper-plugin.yml" : "plugin.yml";
                String pluginYaml = readJarText(jarFile, descriptorPath);
                return new ExtensionDescriptor(
                        firstNonBlank(readYamlValue(pluginYaml, "name"), readManifestValue(jarFile.getManifest(), "Implementation-Title")),
                        firstNonBlank(readYamlValue(pluginYaml, "version"), readManifestValue(jarFile.getManifest(), "Implementation-Version")),
                        firstNonBlank(readYamlValue(pluginYaml, "description"), readManifestValue(jarFile.getManifest(), "Implementation-Description")),
                        firstNonBlank(readYamlValue(pluginYaml, "author"), readManifestValue(jarFile.getManifest(), "Implementation-Vendor")),
                        ServerExtensionType.PLUGIN,
                        serverPlatform == ServerPlatform.UNKNOWN
                                ? (descriptorPath.equals("paper-plugin.yml") ? ServerPlatform.PAPER : ServerPlatform.UNKNOWN)
                                : serverPlatform,
                        readYamlValue(pluginYaml, "api-version")
                );
            }
            if (jarFile.getJarEntry("mcmod.info") != null) {
                JsonNode infoNode = readJson(jarFile, "mcmod.info");
                JsonNode first = infoNode != null && infoNode.isArray() && !infoNode.isEmpty() ? infoNode.get(0) : infoNode;
                return new ExtensionDescriptor(
                        text(first, "name"),
                        text(first, "version"),
                        text(first, "description"),
                        text(first, "authorList", 0),
                        ServerExtensionType.MOD,
                        serverPlatform == ServerPlatform.UNKNOWN ? ServerPlatform.FORGE : serverPlatform,
                        text(first, "mcversion")
                );
            }

            Manifest manifest = jarFile.getManifest();
            return new ExtensionDescriptor(
                    readManifestValue(manifest, "Implementation-Title"),
                    readManifestValue(manifest, "Implementation-Version"),
                    readManifestValue(manifest, "Implementation-Description"),
                    readManifestValue(manifest, "Implementation-Vendor"),
                    ecosystemType == ServerEcosystemType.PLUGINS ? ServerExtensionType.PLUGIN
                            : ecosystemType == ServerEcosystemType.MODS ? ServerExtensionType.MOD
                            : ServerExtensionType.UNKNOWN,
                    serverPlatform,
                    null
            );
        }
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
        return matchPattern(toml, TOML_STRING_PATTERN.formatted(Pattern.quote(key)));
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

    private String readYamlValue(String yaml, String key) {
        String value = matchPattern(yaml, YAML_STRING_PATTERN.formatted(Pattern.quote(key)));
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
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
        String value = matcher.group(1);
        return value == null || value.isBlank() ? null : value.trim();
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
        if (profile != null && profile.platform() != null) {
            return ServerPlatformAdapters.forPlatform(profile.platform());
        }
        return ServerPlatformAdapters.forPlatform(server == null ? null : server.getPlatform());
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
        if (profile != null && profile.platform() != null && profile.platform() != ServerPlatform.UNKNOWN) {
            return profile.platform();
        }
        return server == null || server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
    }

    private ServerEcosystemType resolveEcosystemType(Server server, ServerPlatformProfile profile) {
        if (profile != null && profile.ecosystemType() != null && profile.ecosystemType() != ServerEcosystemType.UNKNOWN) {
            return profile.ecosystemType();
        }
        return server == null || server.getEcosystemType() == null ? ServerEcosystemType.UNKNOWN : server.getEcosystemType();
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
        return normalizeFileName(sourceJar == null || sourceJar.getFileName() == null ? null : sourceJar.getFileName().toString());
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
            return "Extension";
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

    private record ExtensionDescriptor(
            String displayName,
            String version,
            String description,
            String author,
            ServerExtensionType extensionType,
            ServerPlatform platform,
            String minecraftVersionConstraint
    ) {
    }
}
