package controlador.extensions;

import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import modelo.Server;
import modelo.extensions.ExtensionInstallState;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerExtensionsService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TOML_STRING_PATTERN = "(?m)^\\s*%s\\s*=\\s*['\"](.*?)['\"]\\s*$";
    private static final String YAML_STRING_PATTERN = "(?m)^\\s*%s\\s*:\\s*(.+?)\\s*$";

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
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        if (sourceJar == null || !Files.isRegularFile(sourceJar)) {
            throw new IOException("No se ha indicado un .jar de extension valido.");
        }
        String fileName = sourceJar.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Solo se admiten archivos .jar.");
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
                ExtensionSourceType.MANUAL,
                now
        );
        installed.getSource().setProvider("local-manual");
        installed.getSource().setUrl(sourceJar.toAbsolutePath().toString());

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

    private ServerExtension mergeInstalledMetadata(ServerExtension detected, ServerExtension installed) {
        if (detected == null) {
            return installed;
        }
        detected.setInstallState(ExtensionInstallState.INSTALLED);
        if (detected.getSource() == null) {
            detected.setSource(new ExtensionSource());
        }
        detected.getSource().setType(ExtensionSourceType.MANUAL);
        detected.getSource().setProvider(installed.getSource().getProvider());
        detected.getSource().setUrl(installed.getSource().getUrl());
        return detected;
    }

    private boolean sameRelativePath(ServerExtension left, ServerExtension right) {
        String leftPath = left == null || left.getLocalMetadata() == null ? null : normalizeRelativePath(left.getLocalMetadata().getRelativePath());
        String rightPath = right == null || right.getLocalMetadata() == null ? null : normalizeRelativePath(right.getLocalMetadata().getRelativePath());
        return leftPath != null && leftPath.equals(rightPath);
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
        if (metadata.getDiscoveredAtEpochMillis() == null) {
            metadata.setDiscoveredAtEpochMillis(now);
        }
        metadata.setLastUpdatedAtEpochMillis(resolveLastModified(jarPath, now));
        if (metadata.getEnabled() == null) {
            metadata.setEnabled(Boolean.TRUE);
        }
        return extension;
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
                        serverPlatform == ServerPlatform.UNKNOWN ? ServerPlatform.FORGE : serverPlatform
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
                        ServerPlatform.FABRIC
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
                        ServerPlatform.QUILT
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
                                : serverPlatform
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
                        serverPlatform == ServerPlatform.UNKNOWN ? ServerPlatform.FORGE : serverPlatform
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
                    serverPlatform
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
            ServerPlatform platform
    ) {
    }
}
