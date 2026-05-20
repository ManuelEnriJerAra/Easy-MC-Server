package controlador.extensions;

import controlador.JsonNodeText;
import controlador.FileSystemSafety;
import controlador.platform.FileDownloader;
import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ModrinthModpackService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INDEX_FILE = "modrinth.index.json";
    private static final String OVERRIDES_DIR = "overrides/";
    private static final String SERVER_OVERRIDES_DIR = "server-overrides/";
    private static final String CLIENT_OVERRIDES_DIR = "client-overrides/";
    private static final String API_BASE = "https://api.modrinth.com/v2";
    private static final Set<String> ALLOWED_DOWNLOAD_HOSTS = Set.of(
            "cdn.modrinth.com",
            "github.com",
            "raw.githubusercontent.com",
            "gitlab.com"
    );

    private final ExtensionHttpClient httpClient;
    private final Map<String, ProjectMetadata> projectMetadataCache = new ConcurrentHashMap<>();

    public ModrinthModpackService() {
        this(new ExtensionHttpClient());
    }

    ModrinthModpackService(ExtensionHttpClient httpClient) {
        this.httpClient = httpClient == null ? new ExtensionHttpClient() : httpClient;
    }

    public ExportResult exportServerPack(Server server, Path targetPack, ExportMode mode) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor a exportar.");
        }
        if (targetPack == null) {
            throw new IOException("No se ha indicado el archivo de destino.");
        }
        Path serverDir = resolveServerDir(server);
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            throw new IOException("No se ha encontrado la carpeta del servidor.");
        }

        ExportMode resolvedMode = mode == null ? ExportMode.COMPLETE : mode;
        List<IndexedFile> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<Path, FileHashes> localHashCache = new HashMap<>();
        int resolvedManualFiles = 0;

        for (ServerExtension extension : safeExtensions(server)) {
            if (extension == null) {
                continue;
            }
            ExtensionSource source = extension.getSource();
            boolean hasCompleteModrinthSource = hasCompleteModrinthSource(source);
            if (!hasCompleteModrinthSource && !canResolveModrinthSourceByHash(source)) {
                skipped.add(displayName(extension) + ": omitido porque no tiene origen Modrinth.");
                continue;
            }
            if (!shouldIncludeInExport(extension, resolvedMode)) {
                skipped.add(displayName(extension) + ": omitido por la selección de lado del modpack.");
                continue;
            }

            Path localJar = resolveExtensionPath(serverDir, extension);
            if (localJar == null || !Files.isRegularFile(localJar)) {
                skipped.add(displayName(extension) + ": no se ha encontrado el archivo local.");
                continue;
            }
            String indexedPath = indexedPathFor(serverDir, localJar, extension);
            if (!isSafeRelativePath(indexedPath) || !indexedPath.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                skipped.add(displayName(extension) + ": la ruta local no es segura para el índice Modrinth.");
                continue;
            }

            VersionFileMetadata resolvedManualFileMetadata = null;
            FileHashes localHashes = null;
            if (!hasCompleteModrinthSource) {
                localHashes = hashesFor(localJar, localHashCache);
                ExportFileResolution resolution = resolveManualModrinthExportFile(extension, localJar, localHashes, skipped);
                if (resolution == null) {
                    continue;
                }
                resolvedManualFileMetadata = resolution.fileMetadata();
                applyResolvedModrinthSource(extension, resolution);
                resolvedManualFiles++;
                source = extension.getSource();
            }
            String projectId = clean(source == null ? null : source.getProjectId());
            String versionId = clean(source == null ? null : source.getVersionId());
            if (projectId == null || versionId == null) {
                skipped.add(displayName(extension) + ": faltan projectId/versionId de Modrinth.");
                continue;
            }
            String identity = projectId + ":" + versionId;
            if (!seen.add(identity)) {
                continue;
            }

            VersionFileMetadata fileMetadata = resolvedManualFileMetadata;
            if (fileMetadata == null) {
                try {
                    fileMetadata = resolveVersionFileMetadata(versionId, source.getUrl(), localJar.getFileName().toString());
                } catch (IOException ex) {
                    skipped.add(displayName(extension) + ": no se ha podido resolver la versión Modrinth (" + ex.getMessage() + ").");
                    continue;
                }
            }
            if (fileMetadata == null || fileMetadata.downloads().isEmpty()) {
                skipped.add(displayName(extension) + ": Modrinth no ha devuelto una descarga indexable.");
                continue;
            }
            if (localHashes == null) {
                localHashes = hashesFor(localJar, localHashCache);
            }
            Map<String, String> hashes = new LinkedHashMap<>(fileMetadata.hashes());
            ensureLocalHashes(hashes, localHashes);
            if (!hasRequiredModrinthHashes(hashes)) {
                skipped.add(displayName(extension) + ": faltan hashes SHA-1/SHA-512 para el índice Modrinth.");
                continue;
            }
            if (!hashMatches(localHashes.sha1(), hashes.get("sha1"))
                    || !hashMatches(localHashes.sha512(), hashes.get("sha512"))) {
                skipped.add(displayName(extension) + ": el archivo local no coincide con los hashes publicados por Modrinth.");
                continue;
            }

            Env env = envFor(extension);
            files.add(new IndexedFile(
                    indexedPath,
                    hashes,
                    env,
                    List.copyOf(fileMetadata.downloads()),
                    fileMetadata.fileSize()
            ));
        }

        Map<String, String> dependencies = buildDependencies(server);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", 1);
        root.put("game", "minecraft");
        root.put("versionId", "dora-" + UUID.randomUUID());
        root.put("name", safePackName(server));
        root.put("summary", "Modpack exportado desde Dora.");
        root.put("files", files);
        root.put("dependencies", dependencies);

        if (targetPack.getParent() != null) {
            Files.createDirectories(targetPack.getParent());
        }
        try (OutputStream output = Files.newOutputStream(targetPack);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(INDEX_FILE));
            zip.write(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
            zip.closeEntry();
            int overrides = writeOverrides(zip, serverDir, resolvedMode, skipped);
            return new ExportResult(targetPack, files.size(), overrides, resolvedManualFiles, List.copyOf(skipped));
        }
    }

    public ImportIndex readIndex(Path sourcePack) throws IOException {
        if (sourcePack == null || !Files.isRegularFile(sourcePack)) {
            throw new IOException("No se ha encontrado el archivo del modpack.");
        }
        try (ZipFile zipFile = new ZipFile(sourcePack.toFile(), StandardCharsets.UTF_8)) {
            validateZipPaths(zipFile);
            ZipEntry indexEntry = zipFile.getEntry(INDEX_FILE);
            if (indexEntry == null || indexEntry.isDirectory()) {
                throw new IOException("El archivo no contiene modrinth.index.json.");
            }
            try (InputStream input = zipFile.getInputStream(indexEntry)) {
                JsonNode root = OBJECT_MAPPER.readTree(input);
                int formatVersion = root.path("formatVersion").asInt(0);
                if (formatVersion != 1) {
                    throw new IOException("El formato del modpack Modrinth no es compatible.");
                }
                String game = text(root, "game");
                if (!"minecraft".equalsIgnoreCase(defaultString(game, ""))) {
                    throw new IOException("El modpack no declara Minecraft como juego.");
                }
                List<IndexedFile> files = readIndexedFiles(root.path("files"));
                if (files.isEmpty()) {
                    throw new IOException("El modpack no contiene archivos indexados instalables.");
                }
                return new ImportIndex(
                        defaultString(text(root, "name"), sourcePack.getFileName().toString()),
                        text(root, "versionId"),
                        text(root, "summary"),
                        readDependencies(root.path("dependencies")),
                        files
                );
            }
        }
    }

    public List<String> validateDependencies(ImportIndex index, Server server) {
        if (index == null || server == null) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        String serverVersion = normalize(server.getVersion());
        String packVersion = index.dependencies().get("minecraft");
        if (serverVersion != null && packVersion != null && !serverVersion.equalsIgnoreCase(packVersion)) {
            warnings.add("El modpack usa Minecraft " + packVersion + " y el servidor actual usa " + serverVersion + ".");
        }
        String expectedLoader = loaderDependencyKey(server);
        Set<String> packLoaders = new LinkedHashSet<>(index.dependencies().keySet());
        packLoaders.remove("minecraft");
        if (expectedLoader != null) {
            if (!packLoaders.isEmpty() && !packLoaders.contains(expectedLoader)) {
                warnings.add("El modpack declara loader " + String.join(", ", packLoaders)
                        + " y el servidor actual usa " + expectedLoader + ".");
            }
        } else if (!packLoaders.isEmpty()) {
            warnings.add("El modpack declara loader " + String.join(", ", packLoaders)
                    + " y no se ha podido comparar con el servidor actual.");
        }
        return warnings;
    }

    public ImportFileDecision evaluateImportFile(IndexedFile file, ImportOptions options) {
        if (file == null) {
            return new ImportFileDecision(false, "Entrada del modpack omitida porque no es válida.");
        }
        ImportOptions resolvedOptions = options == null ? ImportOptions.server() : options;
        ImportMode mode = resolvedOptions.mode() == null ? ImportMode.SERVER : resolvedOptions.mode();
        Env env = file.env();
        if (mode == ImportMode.COMPLETE) {
            boolean unsupportedEverywhere = env != null
                    && "unsupported".equals(normalize(env.client()))
                    && "unsupported".equals(normalize(env.server()));
            return unsupportedEverywhere
                    ? new ImportFileDecision(false, file.path() + ": omitido porque está marcado como no compatible con cliente ni servidor.")
                    : new ImportFileDecision(true, null);
        }

        String side = mode == ImportMode.CLIENT
                ? env == null ? null : env.client()
                : env == null ? null : env.server();
        String sideName = mode == ImportMode.CLIENT ? "cliente" : "servidor";
        String normalizedSide = normalize(side);
        if ("required".equals(normalizedSide) || "optional".equals(normalizedSide)) {
            return new ImportFileDecision(true, null);
        }
        if ("unsupported".equals(normalizedSide)) {
            return new ImportFileDecision(false, file.path() + ": omitido porque está marcado como no compatible con " + sideName + ".");
        }
        return new ImportFileDecision(true, file.path() + ": incluido aunque no declara compatibilidad explicita para " + sideName + ".");
    }

    public ExtensionDownloadPlan resolveImportDownloadPlan(IndexedFile file, Server server) throws IOException {
        if (file == null) {
            throw new IOException("El archivo del modpack no es válido.");
        }
        if (!file.path().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("El archivo indexado no es un .jar instalable por el gestor de extensiones.");
        }
        String downloadUrl = firstAllowedDownload(file.downloads());
        if (downloadUrl == null) {
            throw new IOException("El archivo indexado no tiene una URL de descarga HTTPS permitida.");
        }

        VersionMetadata metadata = resolveVersionMetadata(file);
        String projectId = metadata == null ? null : metadata.projectId();
        String versionId = metadata == null ? null : metadata.versionId();
        String versionNumber = metadata == null ? null : metadata.versionNumber();
        ProjectMetadata project = projectId == null ? null : resolveProjectMetadata(projectId);
        String fileName = fileNameFromPath(file.path());
        String minecraftConstraint = server == null ? null : server.getVersion();
        ServerPlatform platform = platformForServer(server);

        return new ExtensionDownloadPlan(
                "modrinth",
                projectId,
                versionId,
                firstNonBlank(project == null ? null : project.title(), fileName),
                project == null ? null : project.author(),
                project == null ? null : project.description(),
                versionNumber,
                project == null ? null : project.iconUrl(),
                fileName,
                downloadUrl,
                project == null ? null : project.projectUrl(),
                project == null ? null : project.issuesUrl(),
                project == null ? null : project.websiteUrl(),
                project == null ? null : project.licenseName(),
                project == null ? 0L : project.downloads(),
                file.env() == null ? "unknown" : file.env().client(),
                file.env() == null ? "unknown" : file.env().server(),
                Set.of(),
                ExtensionSourceType.MODRINTH,
                ServerExtensionType.MOD,
                platform,
                minecraftConstraint,
                true,
                metadata == null
                        ? "Descarga resuelta desde .mrpack; no se ha podido completar la identidad Modrinth."
                        : "Descarga resuelta desde .mrpack de Modrinth.",
                List.of()
        );
    }

    public void downloadAndVerify(IndexedFile file,
                                  String requestedUrl,
                                  File destination,
                                  FileDownloader downloader) throws IOException {
        if (file == null) {
            throw new IOException("El archivo del modpack no es válido.");
        }
        if (destination == null) {
            throw new IOException("No se ha indicado el destino temporal de descarga.");
        }
        if (downloader == null) {
            throw new IOException("No se ha indicado un descargador para el modpack.");
        }
        String downloadUrl = isAllowedDownloadUrl(requestedUrl) ? requestedUrl : firstAllowedDownload(file.downloads());
        if (downloadUrl == null) {
            throw new IOException("El archivo indexado no tiene una URL de descarga HTTPS permitida.");
        }
        downloader.download(downloadUrl, destination);
        Path downloaded = destination.toPath();
        verifyDownloadedFile(file, downloaded);
    }

    public int extractServerOverrides(Path sourcePack, Server server, List<String> warnings) throws IOException {
        return extractOverrides(sourcePack, server, ImportOptions.server(), warnings);
    }

    public int extractOverrides(Path sourcePack, Server server, ImportOptions options, List<String> warnings) throws IOException {
        Path serverDir = resolveServerDir(server);
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            throw new IOException("No se ha encontrado la carpeta del servidor para extraer overrides.");
        }
        ImportOptions resolvedOptions = options == null ? ImportOptions.server() : options;
        int extracted = 0;
        try (ZipFile zipFile = new ZipFile(sourcePack.toFile(), StandardCharsets.UTF_8)) {
            validateZipPaths(zipFile);
            extracted += extractOverrideDirectory(zipFile, serverDir, OVERRIDES_DIR, warnings);
            if (resolvedOptions.mode() == ImportMode.SERVER || resolvedOptions.mode() == ImportMode.COMPLETE) {
                extracted += extractOverrideDirectory(zipFile, serverDir, SERVER_OVERRIDES_DIR, warnings);
            } else if (hasEntries(zipFile, SERVER_OVERRIDES_DIR) && warnings != null) {
                warnings.add("Se han omitido overrides de servidor porque elegiste importar contenido de cliente.");
            }
            if (resolvedOptions.mode() == ImportMode.CLIENT || resolvedOptions.mode() == ImportMode.COMPLETE) {
                extracted += extractOverrideDirectory(zipFile, serverDir, CLIENT_OVERRIDES_DIR, warnings);
            } else if (hasEntries(zipFile, CLIENT_OVERRIDES_DIR) && warnings != null) {
                warnings.add("Se han omitido overrides de cliente porque elegiste importar contenido de servidor.");
            }
        }
        return extracted;
    }

    public boolean isServerSideInstallable(IndexedFile file) {
        return evaluateImportFile(file, ImportOptions.server()).install();
    }

    private ExportFileResolution resolveManualModrinthExportFile(ServerExtension extension,
                                                                 Path localJar,
                                                                 FileHashes localHashes,
                                                                 List<String> skipped) throws IOException {
        ExportFileResolution resolution = resolveVersionFileMetadataByLocalHash(localJar, localHashes);
        if (resolution == null) {
            skipped.add(displayName(extension) + ": omitido porque no se pudo resolver una identidad Modrinth verificable por hash.");
            return null;
        }
        return resolution;
    }

    private VersionFileMetadata resolveVersionFileMetadata(String versionId, String preferredUrl, String preferredFileName) throws IOException {
        JsonNode versionNode = OBJECT_MAPPER.readTree(httpClient.get(URI.create(API_BASE + "/version/" + encodePath(versionId)), null));
        JsonNode filesNode = versionNode.path("files");
        if (!filesNode.isArray()) {
            return null;
        }
        List<VersionFileMetadata> files = new ArrayList<>();
        for (JsonNode fileNode : filesNode) {
            VersionFileMetadata metadata = toVersionFileMetadata(fileNode);
            if (metadata != null) {
                files.add(metadata);
            }
        }
        String normalizedUrl = clean(preferredUrl);
        if (normalizedUrl != null) {
            for (VersionFileMetadata metadata : files) {
                if (metadata.downloads().stream().anyMatch(url -> normalizedUrl.equalsIgnoreCase(url))) {
                    return metadata;
                }
            }
        }
        String normalizedFileName = clean(preferredFileName);
        if (normalizedFileName != null) {
            for (VersionFileMetadata metadata : files) {
                if (normalizedFileName.equalsIgnoreCase(metadata.fileName())) {
                    return metadata;
                }
            }
        }
        return files.stream()
                .filter(VersionFileMetadata::primary)
                .findFirst()
                .orElse(files.isEmpty() ? null : files.getFirst());
    }

    private ExportFileResolution resolveVersionFileMetadataByLocalHash(Path localJar, FileHashes localHashes) throws IOException {
        if (localJar == null || !Files.isRegularFile(localJar)) {
            return null;
        }
        FileHashes hashes = localHashes == null ? calculateSha1Sha512(localJar) : localHashes;
        ExportFileResolution resolution = readVersionFileByHashQuietly(hashes.sha512(), "sha512");
        if (resolution != null && versionFileMatchesLocalHashes(resolution.fileMetadata(), hashes)) {
            return resolution;
        }
        resolution = readVersionFileByHashQuietly(hashes.sha1(), "sha1");
        if (resolution != null && versionFileMatchesLocalHashes(resolution.fileMetadata(), hashes)) {
            return resolution;
        }
        return null;
    }

    private ExportFileResolution readVersionFileByHashQuietly(String hash, String algorithm) {
        if (hash == null || hash.isBlank() || algorithm == null || algorithm.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(API_BASE + "/version_file/" + encodePath(hash.trim()) + "?algorithm=" + algorithm);
            JsonNode versionNode = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
            String projectId = text(versionNode, "project_id");
            String versionId = text(versionNode, "id");
            if (projectId == null || versionId == null) {
                return null;
            }
            VersionFileMetadata fileMetadata = findMatchingVersionFile(versionNode.path("files"), hash);
            if (fileMetadata == null) {
                return null;
            }
            return new ExportFileResolution(projectId, versionId, text(versionNode, "version_number"), fileMetadata);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private VersionFileMetadata findMatchingVersionFile(JsonNode filesNode, String hash) {
        if (filesNode == null || !filesNode.isArray() || hash == null || hash.isBlank()) {
            return null;
        }
        for (JsonNode fileNode : filesNode) {
            VersionFileMetadata metadata = toVersionFileMetadata(fileNode);
            if (metadata != null && metadata.hashes().values().stream().anyMatch(hash::equalsIgnoreCase)) {
                return metadata;
            }
        }
        return null;
    }

    private boolean versionFileMatchesLocalHashes(VersionFileMetadata metadata, FileHashes localHashes) {
        if (metadata == null || metadata.hashes() == null || metadata.hashes().isEmpty()) {
            return false;
        }
        String publishedSha512 = metadata.hashes().get("sha512");
        if (publishedSha512 != null && localHashes != null && publishedSha512.equalsIgnoreCase(localHashes.sha512())) {
            return true;
        }
        String publishedSha1 = metadata.hashes().get("sha1");
        return publishedSha1 != null && localHashes != null && publishedSha1.equalsIgnoreCase(localHashes.sha1());
    }

    private void applyResolvedModrinthSource(ServerExtension extension, ExportFileResolution resolution) {
        if (extension == null || resolution == null) {
            return;
        }
        ExtensionSource source = extension.getSource();
        if (source == null) {
            source = new ExtensionSource();
            extension.setSource(source);
        }
        source.setType(ExtensionSourceType.MODRINTH);
        source.setProvider("modrinth");
        source.setProjectId(resolution.projectId());
        source.setVersionId(resolution.versionId());
        source.setUrl(firstNonBlank(firstAllowedDownload(resolution.fileMetadata().downloads()), source.getUrl()));

        ExtensionLocalMetadata metadata = extension.getLocalMetadata();
        if (metadata == null) {
            metadata = new ExtensionLocalMetadata();
            extension.setLocalMetadata(metadata);
        }
        metadata.setKnownRemoteVersion(firstNonBlank(resolution.versionNumber(), metadata.getKnownRemoteVersion()));
        metadata.setKnownRemoteVersionId(resolution.versionId());
        metadata.setLastCheckedForUpdatesAtEpochMillis(System.currentTimeMillis());
        metadata.setLastMetadataSyncAtEpochMillis(System.currentTimeMillis());
        metadata.setUpdateState(ExtensionUpdateState.UNKNOWN);
        metadata.setUpdateMessage("Identidad Modrinth resuelta por hash durante la exportación.");
    }

    private VersionMetadata resolveVersionMetadata(IndexedFile file) {
        String sha512 = file.hashes().get("sha512");
        String sha1 = file.hashes().get("sha1");
        VersionMetadata metadata = readVersionByHashQuietly(sha512, "sha512");
        if (metadata == null) {
            metadata = readVersionByHashQuietly(sha1, "sha1");
        }
        if (metadata != null) {
            return metadata;
        }
        return parseVersionMetadataFromDownloadUrl(file.downloads());
    }

    private VersionMetadata readVersionByHashQuietly(String hash, String algorithm) {
        if (hash == null || hash.isBlank() || algorithm == null || algorithm.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(API_BASE + "/version_file/" + encodePath(hash.trim()) + "?algorithm=" + algorithm);
            JsonNode node = OBJECT_MAPPER.readTree(httpClient.get(uri, null));
            return new VersionMetadata(text(node, "project_id"), text(node, "id"), text(node, "version_number"));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private ProjectMetadata resolveProjectMetadata(String projectId) {
        String normalizedProjectId = clean(projectId);
        if (normalizedProjectId == null) {
            return null;
        }
        ProjectMetadata cached = projectMetadataCache.get(normalizedProjectId);
        if (cached != null) {
            return cached;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(httpClient.get(URI.create(API_BASE + "/project/" + encodePath(normalizedProjectId)), null));
            String projectType = defaultString(text(node, "project_type"), "mod");
            String slug = text(node, "slug");
            ProjectMetadata metadata = new ProjectMetadata(
                    text(node, "title"),
                    text(node, "author"),
                    firstNonBlank(text(node, "description"), text(node, "body")),
                    text(node, "icon_url"),
                    slug == null ? null : "https://modrinth.com/" + projectType + "/" + slug,
                    text(node, "issues_url"),
                    firstNonBlank(text(node, "wiki_url"), text(node, "source_url")),
                    nestedText(node, "license", "name"),
                    longValue(node, "downloads")
            );
            projectMetadataCache.put(normalizedProjectId, metadata);
            return metadata;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private VersionMetadata parseVersionMetadataFromDownloadUrl(List<String> downloads) {
        if (downloads == null) {
            return null;
        }
        for (String download : downloads) {
            try {
                URI uri = URI.create(download);
                String path = uri.getPath();
                if (path == null) {
                    continue;
                }
                String[] parts = path.split("/");
                for (int i = 0; i + 3 < parts.length; i++) {
                    if ("data".equals(parts[i]) && "versions".equals(parts[i + 2])) {
                        return new VersionMetadata(parts[i + 1], parts[i + 3], null);
                    }
                }
            } catch (RuntimeException ignored) {
                // Try the next URL.
            }
        }
        return null;
    }

    private VersionFileMetadata toVersionFileMetadata(JsonNode fileNode) {
        String fileName = text(fileNode, "filename");
        String url = text(fileNode, "url");
        Map<String, String> hashes = readHashes(fileNode.path("hashes"));
        if (fileName == null || url == null || hashes.isEmpty()) {
            return null;
        }
        return new VersionFileMetadata(
                fileName,
                List.of(url),
                hashes,
                Math.max(0L, fileNode.path("size").asLong(0L)),
                fileNode.path("primary").asBoolean(false)
        );
    }

    private List<IndexedFile> readIndexedFiles(JsonNode filesNode) throws IOException {
        if (filesNode == null || !filesNode.isArray()) {
            return List.of();
        }
        List<IndexedFile> files = new ArrayList<>();
        for (JsonNode fileNode : filesNode) {
            String path = text(fileNode, "path");
            if (!isSafeRelativePath(path)) {
                throw new IOException("El índice contiene una ruta insegura: " + defaultString(path, "(vacía)"));
            }
            Map<String, String> hashes = readHashes(fileNode.path("hashes"));
            if (hashes.isEmpty()) {
                throw new IOException("El archivo indexado no declara hashes: " + path);
            }
            List<String> downloads = readStringList(fileNode.path("downloads"));
            if (downloads.isEmpty()) {
                throw new IOException("El archivo indexado no declara descargas: " + path);
            }
            files.add(new IndexedFile(
                    path.replace('\\', '/'),
                    hashes,
                    readEnv(fileNode.path("env")),
                    downloads,
                    Math.max(0L, fileNode.path("fileSize").asLong(0L))
            ));
        }
        return List.copyOf(files);
    }

    private Map<String, String> readHashes(JsonNode hashesNode) {
        Map<String, String> hashes = new LinkedHashMap<>();
        if (hashesNode == null || !hashesNode.isObject()) {
            return hashes;
        }
        for (Map.Entry<String, JsonNode> entry : hashesNode.properties()) {
            String key = normalize(entry.getKey());
            String value = entry.getValue() == null ? null : normalize(JsonNodeText.text(entry.getValue(), null));
            if (key != null && value != null) {
                hashes.put(key, value);
            }
        }
        return hashes;
    }

    private Map<String, String> readDependencies(JsonNode dependenciesNode) {
        Map<String, String> dependencies = new LinkedHashMap<>();
        if (dependenciesNode == null || !dependenciesNode.isObject()) {
            return dependencies;
        }
        for (Map.Entry<String, JsonNode> entry : dependenciesNode.properties()) {
            String key = normalize(entry.getKey());
            String value = entry.getValue() == null ? null : JsonNodeText.text(entry.getValue(), null);
            if (key != null && value != null && !value.isBlank()) {
                dependencies.put(key, value.trim());
            }
        }
        return dependencies;
    }

    private Env readEnv(JsonNode envNode) {
        if (envNode == null || !envNode.isObject()) {
            return null;
        }
        String client = normalizeSide(text(envNode, "client"));
        String server = normalizeSide(text(envNode, "server"));
        if (client == null && server == null) {
            return null;
        }
        return new Env(defaultString(client, "unknown"), defaultString(server, "unknown"));
    }

    private List<String> readStringList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            String value = node == null ? null : JsonNodeText.text(node, null);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private void validateZipPaths(ZipFile zipFile) throws IOException {
        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name == null || name.isBlank()) {
                throw new IOException("El modpack contiene una entrada ZIP sin nombre.");
            }
            String normalized = name.replace('\\', '/');
            String zipPathToValidate = entry.isDirectory() ? trimTrailingSlashes(normalized) : normalized;
            if (!zipPathToValidate.isEmpty() && !isSafeRelativePath(zipPathToValidate)) {
                throw new IOException("El modpack contiene una ruta ZIP insegura: " + name);
            }
            if (INDEX_FILE.equals(normalized)) {
                continue;
            }
            if (normalized.startsWith(OVERRIDES_DIR)
                    || normalized.startsWith(SERVER_OVERRIDES_DIR)
                    || normalized.startsWith(CLIENT_OVERRIDES_DIR)) {
                String relative = normalized.substring(normalized.indexOf('/') + 1);
                if (entry.isDirectory()) {
                    relative = trimTrailingSlashes(relative);
                    if (!relative.isEmpty() && !isSafeRelativePath(relative)) {
                        throw new IOException("El modpack contiene una ruta de override insegura: " + name);
                    }
                    continue;
                }
                if (!relative.isEmpty() && !isSafeRelativePath(relative)) {
                    throw new IOException("El modpack contiene una ruta de override insegura: " + name);
                }
                continue;
            }
            if (entry.isDirectory()) {
                continue;
            }
        }
    }

    private int extractOverrideDirectory(ZipFile zipFile, Path serverDir, String prefix, List<String> warnings) throws IOException {
        int extracted = 0;
        Path normalizedServerDir = serverDir.toAbsolutePath().normalize();
        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name == null || !name.replace('\\', '/').startsWith(prefix) || entry.isDirectory()) {
                continue;
            }
            String relative = name.replace('\\', '/').substring(prefix.length());
            if (relative.isBlank()) {
                continue;
            }
            if (!isAllowedOverridePath(relative)) {
                if (warnings != null) {
                    warnings.add("Override omitido por ruta no permitida: " + relative);
                }
                continue;
            }
            Path target = FileSystemSafety.resolveContainedRelativePath(normalizedServerDir, relative);
            if (!target.startsWith(normalizedServerDir)) {
                throw new IOException("El modpack intenta escribir fuera de la carpeta del servidor: " + relative);
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = zipFile.getInputStream(entry)) {
                Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            extracted++;
        }
        return extracted;
    }

    private boolean hasEntries(ZipFile zipFile, String prefix) {
        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName() != null && entry.getName().replace('\\', '/').startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String trimTrailingSlashes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value;
        while (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private int writeOverrides(ZipOutputStream zip,
                               Path serverDir,
                               ExportMode mode,
                               List<String> skipped) throws IOException {
        if (mode == ExportMode.CLIENT) {
            return 0;
        }
        List<Path> roots = new ArrayList<>(List.of(serverDir.resolve("config"), serverDir.resolve("defaultconfigs")));
        int written = 0;
        for (Path root : roots) {
            if (FileSystemSafety.isRegularFileNoFollow(root)) {
                written += writeOverrideFile(zip, serverDir, root, skipped);
                continue;
            }
            if (!FileSystemSafety.isDirectoryNoFollow(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                for (Path file : stream
                        .filter(FileSystemSafety::isRegularFileNoFollow)
                        .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                        .toList()) {
                    written += writeOverrideFile(zip, serverDir, file, skipped);
                }
            }
        }
        return written;
    }

    private int writeOverrideFile(ZipOutputStream zip, Path serverDir, Path file, List<String> skipped) throws IOException {
        if (!FileSystemSafety.isRegularFileNoFollow(file)) {
            skipped.add(file + ": override omitido porque no es un archivo regular seguro.");
            return 0;
        }
        String relative = serverDir.relativize(file).toString().replace('\\', '/');
        if (!isAllowedOverridePath(relative)) {
            skipped.add(relative + ": override omitido por ruta no permitida.");
            return 0;
        }
        zip.putNextEntry(new ZipEntry(SERVER_OVERRIDES_DIR + relative));
        Files.copy(file, zip);
        zip.closeEntry();
        return 1;
    }

    private void verifyDownloadedFile(IndexedFile file, Path downloaded) throws IOException {
        if (downloaded == null || !Files.isRegularFile(downloaded)) {
            throw new IOException("La descarga del modpack no ha generado un archivo válido.");
        }
        String sha512 = file.hashes().get("sha512");
        if (sha512 != null && !hashMatches(downloaded, sha512, "SHA-512")) {
            throw new IOException("El hash SHA-512 no coincide para " + file.path() + ".");
        }
        String sha1 = file.hashes().get("sha1");
        if (sha512 == null && sha1 != null && !hashMatches(downloaded, sha1, "SHA-1")) {
            throw new IOException("El hash SHA-1 no coincide para " + file.path() + ".");
        }
        if (sha512 == null && sha1 == null) {
            throw new IOException("El archivo " + file.path() + " no contiene hashes SHA-512/SHA-1 verificables.");
        }
    }

    private void ensureLocalHashes(Map<String, String> hashes, FileHashes localHashes) {
        if (hashes == null || localHashes == null) {
            return;
        }
        hashes.putIfAbsent("sha1", localHashes.sha1());
        hashes.putIfAbsent("sha512", localHashes.sha512());
    }

    private boolean hashMatches(String actualHash, String expectedHash) {
        if (actualHash == null || actualHash.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return expectedHash.trim().equalsIgnoreCase(actualHash.trim());
    }

    private FileHashes hashesFor(Path path, Map<Path, FileHashes> cache) throws IOException {
        if (path == null) {
            throw new IOException("No se ha indicado el archivo local.");
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (cache != null) {
            FileHashes cached = cache.get(normalizedPath);
            if (cached != null) {
                return cached;
            }
        }
        FileHashes fresh = calculateSha1Sha512(path);
        if (cache != null) {
            cache.put(normalizedPath, fresh);
        }
        return fresh;
    }

    private boolean hashMatches(Path path, String expectedHash, String algorithm) throws IOException {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return expectedHash.trim().equalsIgnoreCase(calculateHash(path, algorithm));
    }

    private String calculateHash(Path path, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("No se ha podido calcular " + algorithm + ".", ex);
        }
    }

    private FileHashes calculateSha1Sha512(Path path) throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    sha1.update(buffer, 0, read);
                    sha512.update(buffer, 0, read);
                }
            }
            return new FileHashes(
                    HexFormat.of().formatHex(sha1.digest()),
                    HexFormat.of().formatHex(sha512.digest())
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("No se han podido calcular los hashes del archivo.", ex);
        }
    }

    private Map<String, String> buildDependencies(Server server) {
        Map<String, String> dependencies = new LinkedHashMap<>();
        String minecraft = normalize(server == null ? null : server.getVersion());
        if (minecraft != null) {
            dependencies.put("minecraft", minecraft);
        }
        String loaderKey = loaderDependencyKey(server);
        String loaderVersion = normalize(server == null ? null : server.getLoaderVersion());
        if (loaderKey != null && loaderVersion != null) {
            dependencies.put(loaderKey, loaderVersion);
        }
        return dependencies;
    }

    private String loaderDependencyKey(Server server) {
        ServerLoader loader = server == null || server.getLoader() == null ? ServerLoader.UNKNOWN : server.getLoader();
        return switch (loader) {
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case FABRIC -> "fabric-loader";
            case QUILT -> "quilt-loader";
            default -> null;
        };
    }

    private ServerPlatform platformForServer(Server server) {
        if (server == null || server.getPlatform() == null) {
            return ServerPlatform.UNKNOWN;
        }
        return server.getPlatform();
    }

    private boolean shouldIncludeInExport(ServerExtension extension, ExportMode mode) {
        Env env = envFor(extension);
        String client = normalize(env.client());
        String server = normalize(env.server());
        return switch (mode) {
            case SERVER -> !"unsupported".equals(server);
            case CLIENT -> !"unsupported".equals(client);
            case COMPLETE -> true;
        };
    }

    private Env envFor(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        String client = normalizeSide(metadata == null ? null : metadata.getClientSide());
        String server = normalizeSide(metadata == null ? null : metadata.getServerSide());
        if (client == null && server == null) {
            return new Env("unknown", "unknown");
        }
        return new Env(defaultString(client, "unknown"), defaultString(server, "unknown"));
    }

    private String normalizeSide(String side) {
        String normalized = normalize(side);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "required", "optional", "unsupported" -> normalized;
            default -> "unknown";
        };
    }

    private Path resolveExtensionPath(Path serverDir, ServerExtension extension) {
        if (serverDir == null || extension == null) {
            return null;
        }
        ExtensionLocalMetadata metadata = extension.getLocalMetadata();
        if (metadata != null && metadata.getRelativePath() != null && !metadata.getRelativePath().isBlank()) {
            Path candidate = serverDir.resolve(metadata.getRelativePath().replace('/', java.io.File.separatorChar)).normalize();
            if (candidate.startsWith(serverDir.toAbsolutePath().normalize()) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        String fileName = firstNonBlank(
                extension.getFileName(),
                metadata == null ? null : metadata.getFileName()
        );
        if (fileName == null) {
            return null;
        }
        Path mods = serverDir.resolve("mods").resolve(fileName).normalize();
        return Files.isRegularFile(mods) ? mods : null;
    }

    private String indexedPathFor(Path serverDir, Path localJar, ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        String relative = metadata == null ? null : metadata.getRelativePath();
        if (relative != null && !relative.isBlank()) {
            return relative.replace('\\', '/');
        }
        if (serverDir != null && localJar != null) {
            try {
                return serverDir.relativize(localJar).toString().replace('\\', '/');
            } catch (RuntimeException ignored) {
                // Fallback below.
            }
        }
        return "mods/" + firstNonBlank(extension == null ? null : extension.getFileName(),
                localJar == null || localJar.getFileName() == null ? "extension.jar" : localJar.getFileName().toString());
    }

    private boolean isAllowedOverridePath(String path) {
        if (!isSafeRelativePath(path)) {
            return false;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.startsWith("config/") || normalized.startsWith("defaultconfigs/");
    }

    private boolean isSafeRelativePath(String path) {
        return FileSystemSafety.isSafeRelativePath(path);
    }

    private String firstAllowedDownload(List<String> downloads) {
        if (downloads == null) {
            return null;
        }
        for (String download : downloads) {
            if (isAllowedDownloadUrl(download)) {
                return download;
            }
        }
        return null;
    }

    private boolean isAllowedDownloadUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && ALLOWED_DOWNLOAD_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean hasRequiredModrinthHashes(Map<String, String> hashes) {
        return hashes != null
                && hashes.get("sha1") != null
                && !hashes.get("sha1").isBlank()
                && hashes.get("sha512") != null
                && !hashes.get("sha512").isBlank();
    }

    private boolean hasCompleteModrinthSource(ExtensionSource source) {
        return source != null
                && source.getType() == ExtensionSourceType.MODRINTH
                && clean(source.getProjectId()) != null
                && clean(source.getVersionId()) != null;
    }

    private boolean canResolveModrinthSourceByHash(ExtensionSource source) {
        if (source == null || source.getType() == null) {
            return true;
        }
        return source.getType() == ExtensionSourceType.UNKNOWN
                || source.getType() == ExtensionSourceType.MANUAL
                || source.getType() == ExtensionSourceType.LOCAL_FILE
                || (source.getType() == ExtensionSourceType.MODRINTH
                && (clean(source.getProjectId()) == null || clean(source.getVersionId()) == null));
    }

    private List<ServerExtension> safeExtensions(Server server) {
        return server == null || server.getExtensions() == null ? List.of() : List.copyOf(server.getExtensions());
    }

    private Path resolveServerDir(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        try {
            return Path.of(server.getServerDir()).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String fileNameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return "extension.jar";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return fileName.isBlank() ? "extension.jar" : fileName;
    }

    private String displayName(ServerExtension extension) {
        return firstNonBlank(extension == null ? null : extension.getDisplayName(), "Extensión");
    }

    private String safePackName(Server server) {
        return firstNonBlank(server == null ? null : server.getDisplayName(), "Dora Pack");
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = JsonNodeText.text(value, null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String nestedText(JsonNode node, String parent, String child) {
        return node == null ? null : text(node.path(parent), child);
    }

    private long longValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return 0L;
        }
        JsonNode value = node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? 0L : Math.max(0L, value.asLong(0L));
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

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String encodePath(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public enum ExportMode {
        SERVER,
        CLIENT,
        COMPLETE
    }

    public enum ImportMode {
        SERVER,
        CLIENT,
        COMPLETE
    }

    public record ImportOptions(ImportMode mode) {
        public ImportOptions {
            mode = mode == null ? ImportMode.SERVER : mode;
        }

        public static ImportOptions server() {
            return new ImportOptions(ImportMode.SERVER);
        }
    }

    public record ImportFileDecision(boolean install, String warning) {
    }

    public record ExportResult(Path archivePath,
                               int exportedFiles,
                               int overrideFiles,
                               int resolvedManualFiles,
                               List<String> skippedEntries) {
    }

    public record ImportIndex(String name,
                              String versionId,
                              String summary,
                              Map<String, String> dependencies,
                              List<IndexedFile> files) {
        public ImportIndex {
            dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    public record IndexedFile(String path,
                              Map<String, String> hashes,
                              Env env,
                              List<String> downloads,
                              long fileSize) {
        public IndexedFile {
            hashes = hashes == null ? Map.of() : Map.copyOf(hashes);
            downloads = downloads == null ? List.of() : List.copyOf(downloads);
        }
    }

    public record Env(String client, String server) {
    }

    private record VersionFileMetadata(String fileName,
                                       List<String> downloads,
                                       Map<String, String> hashes,
                                       long fileSize,
                                       boolean primary) {
    }

    private record VersionMetadata(String projectId, String versionId, String versionNumber) {
    }

    private record ExportFileResolution(String projectId,
                                        String versionId,
                                        String versionNumber,
                                        VersionFileMetadata fileMetadata) {
    }

    private record ProjectMetadata(String title,
                                   String author,
                                   String description,
                                   String iconUrl,
                                   String projectUrl,
                                   String issuesUrl,
                                   String websiteUrl,
                                   String licenseName,
                                   long downloads) {
    }

    private record FileHashes(String sha1, String sha512) {
    }
}
