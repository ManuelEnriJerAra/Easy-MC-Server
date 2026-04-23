package controlador.console.content;

import controlador.DetectorTipoServidor;
import controlador.DetectorVersionServidor;
import controlador.Utilidades;
import controlador.console.SuggestionCategory;
import modelo.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detección local basada en filesystem, jars y metadatos habituales de cada plataforma.
 */
public final class FilesystemServerInstallationDetector implements ServerInstallationDetector {
    private static final Pattern TOML_ASSIGNMENT = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=\\s*\"([^\"]*)\"\\s*$");

    @Override
    public ServerInstallationReport detect(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return new ServerInstallationReport(
                    server == null ? "" : server.getId(),
                    server == null ? "" : server.getDisplayName(),
                    null,
                    "",
                    "",
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    fingerprint(List.of()),
                    List.of("No hay directorio de servidor disponible para analizar."),
                    Instant.now(),
                    Map.of()
            );
        }

        Path serverDir = Path.of(server.getServerDir());
        List<String> warnings = new ArrayList<>();
        List<InstalledComponent> serverComponents = new ArrayList<>();
        List<InstalledComponent> mods = new ArrayList<>();
        List<InstalledComponent> plugins = new ArrayList<>();
        List<InstalledComponent> datapacks = new ArrayList<>();
        List<PotentialContentSource> potentialSources = new ArrayList<>();

        String detectedType = DetectorTipoServidor.detectarTipo(serverDir);
        String detectedVersion = detectVersion(server, warnings);
        Path executableJar = detectExecutableJar(serverDir, warnings);

        if (executableJar != null) {
            serverComponents.add(detectServerJarComponent(executableJar, detectedType, detectedVersion));
            potentialSources.add(new PotentialContentSource(
                    ContentSourceKind.UNKNOWN,
                    "server-jar",
                    "Jar principal del servidor; posible fuente indirecta de comandos o registries.",
                    executableJar,
                    Set.of(SuggestionCategory.COMMAND, SuggestionCategory.ITEM, SuggestionCategory.BLOCK, SuggestionCategory.ENTITY),
                    20,
                    Map.of("serverType", detectedType)
            ));
        }

        mods.addAll(detectMods(serverDir, detectedType, warnings, potentialSources));
        plugins.addAll(detectPlugins(serverDir, detectedType, warnings, potentialSources));
        datapacks.addAll(detectDatapacks(serverDir, warnings, potentialSources));
        detectSpecialDirectories(serverDir, potentialSources);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("serverType", detectedType);
        metadata.put("minecraftVersion", detectedVersion);
        metadata.put("modCount", String.valueOf(mods.size()));
        metadata.put("pluginCount", String.valueOf(plugins.size()));
        metadata.put("datapackCount", String.valueOf(datapacks.size()));

        String contentFingerprint = fingerprint(buildFingerprintEntries(
                detectedType,
                detectedVersion,
                serverComponents,
                mods,
                plugins,
                datapacks,
                potentialSources
        ));

        return new ServerInstallationReport(
                server.getId(),
                server.getDisplayName(),
                serverDir,
                detectedType,
                detectedVersion,
                executableJar,
                sortComponents(serverComponents),
                sortComponents(mods),
                sortComponents(plugins),
                sortComponents(datapacks),
                sortSources(potentialSources),
                contentFingerprint,
                List.copyOf(warnings),
                Instant.now(),
                Map.copyOf(metadata)
        );
    }

    private String detectVersion(Server server, List<String> warnings) {
        String version = safeTrim(server.getVersion());
        if (!version.isBlank()) {
            return version;
        }
        try {
            String detected = DetectorVersionServidor.detectarVersionVanilla(server);
            if (detected != null && !detected.isBlank()) {
                return detected.trim();
            }
        } catch (RuntimeException ex) {
            warnings.add("No se pudo detectar la version automaticamente: " + ex.getMessage());
        }
        warnings.add("No se pudo determinar la version exacta del servidor.");
        return "";
    }

    private Path detectExecutableJar(Path serverDir, List<String> warnings) {
        try {
            return Utilidades.encontrarEjecutableJar(serverDir);
        } catch (RuntimeException ex) {
            warnings.add("No se pudo localizar un jar ejecutable unico: " + ex.getMessage());
            return null;
        }
    }

    private InstalledComponent detectServerJarComponent(Path jarPath, String serverType, String version) {
        Map<String, String> metadata = readJarManifestAttributes(jarPath);
        return new InstalledComponent(
                InstalledComponentKind.SERVER_JAR,
                deriveComponentId(jarPath.getFileName().toString()),
                jarPath.getFileName().toString(),
                version,
                jarPath,
                serverType,
                metadata
        );
    }

    private List<InstalledComponent> detectMods(
            Path serverDir,
            String detectedType,
            List<String> warnings,
            List<PotentialContentSource> potentialSources
    ) {
        Path modsDir = serverDir.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }

        List<InstalledComponent> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(modsDir)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList()) {
                InstalledComponent component = detectModJar(path, detectedType);
                result.add(component);
                potentialSources.add(new PotentialContentSource(
                        ContentSourceKind.MOD_JAR,
                        component.componentId().isBlank() ? path.getFileName().toString() : component.componentId(),
                        "Mod detectado localmente; fuente potencial de items, bloques, entidades y comandos.",
                        path,
                        Set.of(
                                SuggestionCategory.ITEM,
                                SuggestionCategory.BLOCK,
                                SuggestionCategory.ENTITY,
                                SuggestionCategory.COMMAND,
                                SuggestionCategory.TAG
                        ),
                        70,
                        Map.of("loader", component.loader(), "version", component.version())
                ));
            }
        } catch (IOException ex) {
            warnings.add("No se pudo leer la carpeta mods: " + ex.getMessage());
        }
        return result;
    }

    private InstalledComponent detectModJar(Path jarPath, String detectedType) {
        Map<String, String> metadata = new LinkedHashMap<>(readJarManifestAttributes(jarPath));
        String fileName = jarPath.getFileName().toString();
        String componentId = deriveComponentId(fileName);
        String displayName = fileName;
        String version = "";
        String loader = detectedType;

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry fabricEntry = jarFile.getJarEntry("fabric.mod.json");
            if (fabricEntry != null) {
                loader = "FABRIC";
                String text = readJarText(jarFile, fabricEntry);
                componentId = firstJsonString(text, "\"id\"\\s*:\\s*\"([^\"]+)\"", componentId);
                displayName = firstJsonString(text, "\"name\"\\s*:\\s*\"([^\"]+)\"", displayName);
                version = firstJsonString(text, "\"version\"\\s*:\\s*\"([^\"]+)\"", version);
                metadata.put("metadataSource", "fabric.mod.json");
            } else {
                JarEntry forgeEntry = jarFile.getJarEntry("META-INF/mods.toml");
                if (forgeEntry != null) {
                    loader = "FORGE";
                    Map<String, String> toml = readSimpleToml(jarFile, forgeEntry);
                    componentId = firstNonBlank(toml.get("modId"), componentId);
                    displayName = firstNonBlank(toml.get("displayName"), displayName);
                    version = firstNonBlank(toml.get("version"), version);
                    metadata.put("metadataSource", "META-INF/mods.toml");
                }
            }
        } catch (IOException ignored) {
        }

        return new InstalledComponent(
                InstalledComponentKind.MOD,
                componentId,
                displayName,
                version,
                jarPath,
                loader,
                metadata
        );
    }

    private List<InstalledComponent> detectPlugins(
            Path serverDir,
            String detectedType,
            List<String> warnings,
            List<PotentialContentSource> potentialSources
    ) {
        Path pluginsDir = serverDir.resolve("plugins");
        if (!Files.isDirectory(pluginsDir)) {
            return List.of();
        }

        List<InstalledComponent> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList()) {
                InstalledComponent component = detectPluginJar(path, detectedType);
                result.add(component);
                potentialSources.add(new PotentialContentSource(
                        ContentSourceKind.PLUGIN_JAR,
                        component.componentId().isBlank() ? path.getFileName().toString() : component.componentId(),
                        "Plugin detectado localmente; fuente potencial de comandos custom y valores administrativos.",
                        path,
                        Set.of(SuggestionCategory.COMMAND, SuggestionCategory.PLAYER, SuggestionCategory.TAG, SuggestionCategory.FREE_VALUE),
                        65,
                        Map.of("loader", component.loader(), "version", component.version())
                ));
            }
        } catch (IOException ex) {
            warnings.add("No se pudo leer la carpeta plugins: " + ex.getMessage());
        }
        return result;
    }

    private InstalledComponent detectPluginJar(Path jarPath, String detectedType) {
        Map<String, String> metadata = new LinkedHashMap<>(readJarManifestAttributes(jarPath));
        String fileName = jarPath.getFileName().toString();
        String componentId = deriveComponentId(fileName);
        String displayName = fileName;
        String version = "";
        String loader = switch (safeTrim(detectedType).toUpperCase(Locale.ROOT)) {
            case "PAPER" -> "PAPER";
            case "SPIGOT" -> "SPIGOT";
            case "BUKKIT" -> "BUKKIT";
            default -> "PLUGIN";
        };

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry paperEntry = jarFile.getJarEntry("paper-plugin.yml");
            if (paperEntry != null) {
                loader = "PAPER";
                Map<String, String> yml = readSimpleYaml(jarFile, paperEntry);
                componentId = firstNonBlank(yml.get("name"), componentId);
                displayName = firstNonBlank(yml.get("name"), displayName);
                version = firstNonBlank(yml.get("version"), version);
                metadata.put("metadataSource", "paper-plugin.yml");
            } else {
                JarEntry pluginEntry = jarFile.getJarEntry("plugin.yml");
                if (pluginEntry != null) {
                    Map<String, String> yml = readSimpleYaml(jarFile, pluginEntry);
                    componentId = firstNonBlank(yml.get("name"), componentId);
                    displayName = firstNonBlank(yml.get("name"), displayName);
                    version = firstNonBlank(yml.get("version"), version);
                    metadata.put("metadataSource", "plugin.yml");
                }
            }
        } catch (IOException ignored) {
        }

        return new InstalledComponent(
                InstalledComponentKind.PLUGIN,
                componentId,
                displayName,
                version,
                jarPath,
                loader,
                metadata
        );
    }

    private List<InstalledComponent> detectDatapacks(
            Path serverDir,
            List<String> warnings,
            List<PotentialContentSource> potentialSources
    ) {
        Path worldDir = locatePrimaryWorldDirectory(serverDir);
        if (worldDir == null) {
            return List.of();
        }
        Path datapacksDir = worldDir.resolve("datapacks");
        if (!Files.isDirectory(datapacksDir)) {
            return List.of();
        }

        List<InstalledComponent> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(datapacksDir)) {
            for (Path datapack : stream.sorted().toList()) {
                if (!Files.isDirectory(datapack)) {
                    continue;
                }
                String name = datapack.getFileName().toString();
                InstalledComponent component = new InstalledComponent(
                        InstalledComponentKind.DATAPACK,
                        deriveComponentId(name),
                        name,
                        "",
                        datapack,
                        "DATAPACK",
                        Map.of("world", worldDir.getFileName() == null ? "" : worldDir.getFileName().toString())
                );
                result.add(component);
                potentialSources.add(new PotentialContentSource(
                        ContentSourceKind.DATAPACK_DIRECTORY,
                        component.componentId(),
                        "Datapack detectado; fuente potencial de tags, funciones y namespaces custom.",
                        datapack,
                        Set.of(SuggestionCategory.TAG, SuggestionCategory.COMMAND, SuggestionCategory.ITEM, SuggestionCategory.BLOCK, SuggestionCategory.ENTITY),
                        80,
                        Map.of("world", worldDir.toString())
                ));
                inspectDatapackNamespaces(datapack, potentialSources);
            }
        } catch (IOException ex) {
            warnings.add("No se pudo leer la carpeta datapacks: " + ex.getMessage());
        }
        return result;
    }

    private void inspectDatapackNamespaces(Path datapack, List<PotentialContentSource> potentialSources) {
        Path dataDir = datapack.resolve("data");
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).sorted().toList()) {
                String namespace = namespaceDir.getFileName().toString();
                Set<SuggestionCategory> categories = inferNamespaceCategories(namespaceDir);
                if (categories.isEmpty()) {
                    categories = Set.of(SuggestionCategory.TAG, SuggestionCategory.COMMAND);
                }
                potentialSources.add(new PotentialContentSource(
                        ContentSourceKind.DATAPACK_NAMESPACE,
                        namespace,
                        "Namespace de datapack con contenido potencialmente relevante para sugerencias.",
                        namespaceDir,
                        categories,
                        75,
                        Map.of("datapack", datapack.getFileName().toString())
                ));
            }
        } catch (IOException ignored) {
        }
    }

    private void detectSpecialDirectories(Path serverDir, List<PotentialContentSource> potentialSources) {
        Path kubeJsDir = serverDir.resolve("kubejs");
        if (Files.isDirectory(kubeJsDir)) {
            potentialSources.add(new PotentialContentSource(
                    ContentSourceKind.KUBEJS_DIRECTORY,
                    "kubejs",
                    "KubeJS detectado; alta probabilidad de items, recetas, tags o comandos custom.",
                    kubeJsDir,
                    Set.of(SuggestionCategory.ITEM, SuggestionCategory.BLOCK, SuggestionCategory.ENTITY, SuggestionCategory.TAG, SuggestionCategory.COMMAND),
                    90,
                    Map.of()
            ));
        }

        Path configDir = serverDir.resolve("config");
        if (Files.isDirectory(configDir)) {
            potentialSources.add(new PotentialContentSource(
                    ContentSourceKind.CONFIG_DIRECTORY,
                    "config",
                    "Directorio de configuracion detectado; posible fuente auxiliar para identificar contenido modded.",
                    configDir,
                    Set.of(SuggestionCategory.FREE_VALUE),
                    35,
                    Map.of()
            ));
        }

        Path resourcePack = serverDir.resolve("server.properties");
        if (Files.isRegularFile(resourcePack)) {
            String resourcePackUri = readServerResourcePack(serverDir);
            if (!resourcePackUri.isBlank()) {
                potentialSources.add(new PotentialContentSource(
                        ContentSourceKind.RESOURCEPACK_HINT,
                        "server-resource-pack",
                        "server.properties referencia un resource pack; puede aportar ids visibles aunque no registries oficiales.",
                        resourcePack,
                        Set.of(SuggestionCategory.FREE_VALUE, SuggestionCategory.TAG),
                        25,
                        Map.of("resourcePack", resourcePackUri)
                ));
            }
        }
    }

    private Set<SuggestionCategory> inferNamespaceCategories(Path namespaceDir) {
        LinkedHashSet<SuggestionCategory> categories = new LinkedHashSet<>();
        if (Files.isDirectory(namespaceDir.resolve("tags"))) {
            categories.add(SuggestionCategory.TAG);
        }
        if (Files.isDirectory(namespaceDir.resolve("functions"))) {
            categories.add(SuggestionCategory.COMMAND);
        }
        if (Files.isDirectory(namespaceDir.resolve("loot_tables"))) {
            categories.add(SuggestionCategory.ITEM);
        }
        if (Files.isDirectory(namespaceDir.resolve("predicates"))) {
            categories.add(SuggestionCategory.FREE_VALUE);
        }
        if (Files.isDirectory(namespaceDir.resolve("worldgen"))) {
            categories.add(SuggestionCategory.DIMENSION);
        }
        return Set.copyOf(categories);
    }

    private Path locatePrimaryWorldDirectory(Path serverDir) {
        Path propertiesPath = serverDir.resolve("server.properties");
        if (Files.isRegularFile(propertiesPath)) {
            try {
                Properties properties = Utilidades.cargarPropertiesUtf8(propertiesPath);
                String levelName = safeTrim(properties.getProperty("level-name"));
                if (!levelName.isBlank()) {
                    Path candidate = serverDir.resolve(levelName);
                    if (Files.isDirectory(candidate)) {
                        return candidate;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        Path worldDir = serverDir.resolve("world");
        return Files.isDirectory(worldDir) ? worldDir : null;
    }

    private String readServerResourcePack(Path serverDir) {
        Path propertiesPath = serverDir.resolve("server.properties");
        if (!Files.isRegularFile(propertiesPath)) {
            return "";
        }
        try {
            Properties properties = Utilidades.cargarPropertiesUtf8(propertiesPath);
            return safeTrim(properties.getProperty("resource-pack"));
        } catch (IOException ex) {
            return "";
        }
    }

    private Map<String, String> readJarManifestAttributes(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            if (jarFile.getManifest() == null) {
                return Map.of();
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            jarFile.getManifest().getMainAttributes().forEach((key, value) -> {
                if (key != null && value != null) {
                    metadata.put(String.valueOf(key), String.valueOf(value));
                }
            });
            return metadata;
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private String readJarText(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream in = jarFile.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> readSimpleToml(JarFile jarFile, JarEntry entry) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = TOML_ASSIGNMENT.matcher(line.trim());
                if (!matcher.matches()) {
                    continue;
                }
                values.putIfAbsent(matcher.group(1), matcher.group(2));
            }
        }
        return values;
    }

    private Map<String, String> readSimpleYaml(JarFile jarFile, JarEntry entry) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains(":")) {
                    continue;
                }
                int separator = trimmed.indexOf(':');
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                values.putIfAbsent(key, value);
            }
        }
        return values;
    }

    private String firstJsonString(String text, String regex, String fallback) {
        if (text == null || text.isBlank()) {
            return safeTrim(fallback);
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            return safeTrim(matcher.group(1));
        }
        return safeTrim(fallback);
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalizedPreferred = safeTrim(preferred);
        return normalizedPreferred.isBlank() ? safeTrim(fallback) : normalizedPreferred;
    }

    private String deriveComponentId(String rawName) {
        String normalized = safeTrim(rawName).toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jar")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.replace(' ', '_');
    }

    private List<InstalledComponent> sortComponents(List<InstalledComponent> components) {
        return components.stream()
                .sorted(Comparator
                        .comparing(InstalledComponent::kind)
                        .thenComparing(InstalledComponent::componentId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(component -> component.path() == null ? "" : component.path().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<PotentialContentSource> sortSources(List<PotentialContentSource> sources) {
        return sources.stream()
                .sorted(Comparator
                        .comparingInt(PotentialContentSource::confidence).reversed()
                        .thenComparing(PotentialContentSource::kind)
                        .thenComparing(PotentialContentSource::sourceId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<String> buildFingerprintEntries(
            String detectedType,
            String detectedVersion,
            List<InstalledComponent> serverComponents,
            List<InstalledComponent> mods,
            List<InstalledComponent> plugins,
            List<InstalledComponent> datapacks,
            List<PotentialContentSource> sources
    ) {
        TreeSet<String> entries = new TreeSet<>();
        entries.add("type|" + safeTrim(detectedType));
        entries.add("version|" + safeTrim(detectedVersion));
        serverComponents.stream().map(InstalledComponent::stableDescriptor).forEach(entries::add);
        mods.stream().map(InstalledComponent::stableDescriptor).forEach(entries::add);
        plugins.stream().map(InstalledComponent::stableDescriptor).forEach(entries::add);
        datapacks.stream().map(InstalledComponent::stableDescriptor).forEach(entries::add);
        sources.stream().map(PotentialContentSource::stableDescriptor).forEach(entries::add);
        return List.copyOf(entries);
    }

    private String fingerprint(List<String> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String entry : entries) {
                digest.update(entry.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
