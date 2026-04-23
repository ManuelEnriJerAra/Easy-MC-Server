package controlador.console.vanilla;

import controlador.MojangAPI;
import controlador.console.SuggestionCategory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Genera un dataset vanilla por version usando los reports oficiales del server.jar.
 */
public final class AutomaticVanillaReportCatalogSource implements VanillaReportCatalogSource {
    private static final ExecutorService WARMUP_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vanilla-report-warmup");
        thread.setDaemon(true);
        return thread;
    });
    private static final long REPORT_TIMEOUT_MILLIS = 180_000L;

    private final ObjectMapper objectMapper;
    private final Path cacheDirectory;
    private final MojangAPI mojangAPI;
    private final LocalJavaRuntimeLocator javaRuntimeLocator;
    private final Map<String, VanillaCatalogVersion> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Optional<VanillaCatalogVersion>>> warmupsByVersion = new ConcurrentHashMap<>();

    public AutomaticVanillaReportCatalogSource() {
        this(
                new ObjectMapper(),
                Path.of(System.getProperty("user.home"), ".easy-mc-server", "cache", "console", "vanilla", "reports"),
                new MojangAPI(),
                new LocalJavaRuntimeLocator()
        );
    }

    AutomaticVanillaReportCatalogSource(
            ObjectMapper objectMapper,
            Path cacheDirectory,
            MojangAPI mojangAPI,
            LocalJavaRuntimeLocator javaRuntimeLocator
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory");
        this.mojangAPI = Objects.requireNonNull(mojangAPI, "mojangAPI");
        this.javaRuntimeLocator = Objects.requireNonNull(javaRuntimeLocator, "javaRuntimeLocator");
    }

    @Override
    public Optional<VanillaCatalogVersion> resolve(String version) {
        String normalizedVersion = normalizeVersion(version);
        if (normalizedVersion.isBlank()) {
            return Optional.empty();
        }

        VanillaCatalogVersion inMemory = memoryCache.get(normalizedVersion);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }

        Optional<VanillaCatalogVersion> fromDisk = readPersistentCache(normalizedVersion);
        if (fromDisk.isPresent()) {
            memoryCache.put(normalizedVersion, fromDisk.get());
            return fromDisk;
        }

        Optional<VanillaCatalogVersion> generated = generate(normalizedVersion);
        generated.ifPresent(catalog -> memoryCache.put(normalizedVersion, catalog));
        generated.ifPresent(this::writePersistentCache);
        return generated;
    }

    @Override
    public Optional<VanillaCatalogVersion> resolveCached(String version) {
        String normalizedVersion = normalizeVersion(version);
        if (normalizedVersion.isBlank()) {
            return Optional.empty();
        }

        VanillaCatalogVersion inMemory = memoryCache.get(normalizedVersion);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }

        Optional<VanillaCatalogVersion> fromDisk = readPersistentCache(normalizedVersion);
        fromDisk.ifPresent(catalog -> memoryCache.put(normalizedVersion, catalog));
        return fromDisk;
    }

    @Override
    public void scheduleWarmup(String version) {
        String normalizedVersion = normalizeVersion(version);
        if (normalizedVersion.isBlank()) {
            return;
        }
        if (memoryCache.containsKey(normalizedVersion) || readPersistentCache(normalizedVersion).isPresent()) {
            return;
        }
        warmupsByVersion.computeIfAbsent(normalizedVersion, key ->
                CompletableFuture.supplyAsync(() -> resolve(key), WARMUP_EXECUTOR)
                        .orTimeout(REPORT_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .exceptionally(ignored -> Optional.empty())
                        .whenComplete((ignored, throwable) -> warmupsByVersion.remove(key))
        );
    }

    @Override
    public boolean isWarmupPending(String version) {
        String normalizedVersion = normalizeVersion(version);
        return !normalizedVersion.isBlank() && warmupsByVersion.containsKey(normalizedVersion);
    }

    private Optional<VanillaCatalogVersion> generate(String version) {
        try {
            JsonNode versionJson = mojangAPI.obtenerVersionJson(version);
            int requiredJavaVersion = versionJson.path("javaVersion").path("majorVersion").asInt(17);
            String serverJarUrl = versionJson.path("downloads").path("server").path("url").asText("");
            if (serverJarUrl.isBlank()) {
                return Optional.empty();
            }

            Optional<InstalledJavaRuntime> javaRuntime = javaRuntimeLocator.findCompatibleRuntime(requiredJavaVersion);
            if (javaRuntime.isEmpty()) {
                return Optional.empty();
            }

            Path versionDirectory = cacheDirectory.resolve(version);
            Files.createDirectories(versionDirectory);
            Path bundledJar = versionDirectory.resolve("server-bundled.jar");
            if (!Files.isRegularFile(bundledJar)) {
                mojangAPI.descargar(serverJarUrl, bundledJar.toFile(), null);
            }

            Path workDirectory = versionDirectory.resolve("work");
            recreateDirectory(workDirectory);
            Path generatedReportsDirectory = runReports(javaRuntime.get(), bundledJar, workDirectory);
            if (generatedReportsDirectory == null) {
                return Optional.empty();
            }

            VanillaCatalogVersion catalog = parseGeneratedCatalog(version, generatedReportsDirectory, requiredJavaVersion);
            if (catalog.availableCategories().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(catalog);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Path runReports(
            InstalledJavaRuntime javaRuntime,
            Path bundledJar,
            Path workDirectory
    ) throws IOException, InterruptedException {
        Files.createDirectories(workDirectory);
        List<String> command = List.of(
                javaRuntime.executable().toString(),
                "-Xms256M",
                "-Xmx768M",
                "-Djava.awt.headless=true",
                "-DbundlerMainClass=net.minecraft.data.Main",
                "-jar",
                bundledJar.toString(),
                "--reports"
        );

        Process process = new ProcessBuilder(command)
                .directory(workDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        List<String> output = new ArrayList<>();
        try (InputStream in = process.getInputStream()) {
            output.addAll(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        }

        boolean finished = process.waitFor(REPORT_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null;
        }

        Files.write(workDirectory.resolve("reports-generation.log"), output, StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            return null;
        }

        Path generatedDirectory = workDirectory.resolve("generated");
        Path reportsDirectory = generatedDirectory.resolve("reports");
        return Files.isDirectory(reportsDirectory) ? generatedDirectory : null;
    }

    private VanillaCatalogVersion parseGeneratedCatalog(
            String version,
            Path generatedDirectory,
            int requiredJavaVersion
    ) throws IOException {
        Map<SuggestionCategory, List<String>> categories = new EnumMap<>(SuggestionCategory.class);

        Path reportsDirectory = generatedDirectory.resolve("reports");
        Path commandsFile = reportsDirectory.resolve("commands.json");
        Path registriesFile = reportsDirectory.resolve("registries.json");
        Path blocksFile = reportsDirectory.resolve("blocks.json");
        Path itemsFile = reportsDirectory.resolve("items.json");

        if (Files.isRegularFile(commandsFile)) {
            Map<SuggestionCategory, List<String>> commandsData = parseCommandsReport(commandsFile);
            commandsData.forEach(categories::put);
        }

        if (Files.isRegularFile(registriesFile)) {
            Map<SuggestionCategory, List<String>> registriesData = parseRegistriesReport(registriesFile);
            mergeCategories(categories, registriesData);
        }

        if (Files.isRegularFile(blocksFile)) {
            mergeCategories(categories, parseNamedReport(blocksFile, SuggestionCategory.BLOCK));
        }

        if (Files.isRegularFile(itemsFile)) {
            mergeCategories(categories, parseNamedReport(itemsFile, SuggestionCategory.ITEM));
        }

        Map<SuggestionCategory, List<String>> tagsData = parseGeneratedTags(generatedDirectory.resolve("data"));
        mergeCategories(categories, tagsData);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceKind", "official-server-reports");
        metadata.put("requiredJavaVersion", String.valueOf(requiredJavaVersion));
        metadata.put("generatedFromReports", "true");
        metadata.put("fromPersistentCache", "false");

        return new VanillaCatalogVersion(
                version,
                "official-reports",
                "official-server-reports+generated-datapack",
                Instant.now(),
                categories,
                metadata
        );
    }

    private Map<SuggestionCategory, List<String>> parseCommandsReport(Path commandsFile) throws IOException {
        JsonNode root = objectMapper.readTree(commandsFile.toFile());
        LinkedHashSet<String> commands = new LinkedHashSet<>();
        LinkedHashSet<String> gamerules = new LinkedHashSet<>();

        JsonNode children = root.path("children");
        if (children.isObject()) {
            for (Map.Entry<String, JsonNode> entry : children.properties()) {
                String name = entry.getKey();
                JsonNode node = entry.getValue();
                if (!name.isBlank() && isLiteralNode(node)) {
                    commands.add(name);
                }
                if ("gamerule".equals(name)) {
                    collectLiteralChildren(node.path("children"), gamerules);
                }
            }
        }

        Map<SuggestionCategory, List<String>> categories = new EnumMap<>(SuggestionCategory.class);
        if (!commands.isEmpty()) {
            categories.put(SuggestionCategory.COMMAND, List.copyOf(commands));
        }
        if (!gamerules.isEmpty()) {
            categories.put(SuggestionCategory.GAMERULE, List.copyOf(gamerules));
        }
        return categories;
    }

    private Map<SuggestionCategory, List<String>> parseRegistriesReport(Path registriesFile) throws IOException {
        JsonNode root = objectMapper.readTree(registriesFile.toFile());
        Map<SuggestionCategory, LinkedHashSet<String>> mutable = new EnumMap<>(SuggestionCategory.class);

        if (root.isObject()) {
            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                SuggestionCategory category = mapRegistryName(entry.getKey());
                if (category == null) {
                    continue;
                }
                LinkedHashSet<String> values = mutable.computeIfAbsent(category, ignored -> new LinkedHashSet<>());
                values.addAll(parseRegistryEntries(entry.getValue()));
            }
        }

        Map<SuggestionCategory, List<String>> categories = new EnumMap<>(SuggestionCategory.class);
        mutable.forEach((key, values) -> categories.put(key, List.copyOf(values)));
        return categories;
    }

    private Map<SuggestionCategory, List<String>> parseGeneratedTags(Path dataDirectory) throws IOException {
        if (!Files.isDirectory(dataDirectory)) {
            return Map.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dataDirectory)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        Path relative = dataDirectory.relativize(path);
                        if (relative.getNameCount() < 3 || !"tags".equals(relative.getName(1).toString())) {
                            return;
                        }
                        String namespace = relative.getName(0).toString();
                        Path tagPath = relative.subpath(2, relative.getNameCount());
                        String id = "#" + namespace + ":" + stripJsonSuffix(tagPath.toString().replace('\\', '/'));
                        tags.add(id);
                    });
        }
        if (tags.isEmpty()) {
            return Map.of();
        }
        return Map.of(SuggestionCategory.TAG, List.copyOf(tags));
    }

    private Map<SuggestionCategory, List<String>> parseNamedReport(
            Path reportFile,
            SuggestionCategory category
    ) throws IOException {
        JsonNode root = objectMapper.readTree(reportFile.toFile());
        if (!root.isObject()) {
            return Map.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            if (looksLikeResourceLocation(entry.getKey())) {
                values.add(entry.getKey());
            }
        }
        if (values.isEmpty()) {
            return Map.of();
        }
        return Map.of(category, List.copyOf(values));
    }

    private void collectLiteralChildren(JsonNode childrenNode, Set<String> target) {
        if (!childrenNode.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : childrenNode.properties()) {
            if (isLiteralNode(entry.getValue())) {
                target.add(entry.getKey());
            }
        }
    }

    private boolean isLiteralNode(JsonNode node) {
        return node != null && "literal".equalsIgnoreCase(node.path("type").asText(""));
    }

    private Set<String> parseRegistryEntries(JsonNode registryNode) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JsonNode entriesNode = registryNode.path("entries");
        if (entriesNode.isObject()) {
            for (Map.Entry<String, JsonNode> entry : entriesNode.properties()) {
                if (looksLikeResourceLocation(entry.getKey())) {
                    values.add(entry.getKey());
                }
            }
        } else if (registryNode.isObject()) {
            for (Map.Entry<String, JsonNode> entry : registryNode.properties()) {
                if (looksLikeResourceLocation(entry.getKey())) {
                    values.add(entry.getKey());
                }
            }
        }
        return values;
    }

    private SuggestionCategory mapRegistryName(String registryName) {
        if (registryName == null || registryName.isBlank()) {
            return null;
        }
        String normalized = registryName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":item")) {
            return SuggestionCategory.ITEM;
        }
        if (normalized.endsWith(":block")) {
            return SuggestionCategory.BLOCK;
        }
        if (normalized.endsWith(":entity_type")) {
            return SuggestionCategory.ENTITY;
        }
        if (normalized.endsWith(":mob_effect")) {
            return SuggestionCategory.EFFECT;
        }
        if (normalized.endsWith(":enchantment")) {
            return SuggestionCategory.ENCHANTMENT;
        }
        if (normalized.endsWith(":dimension_type")) {
            return SuggestionCategory.DIMENSION;
        }
        return null;
    }

    private boolean looksLikeResourceLocation(String value) {
        return value != null && value.contains(":") && !value.contains(" ");
    }

    private void mergeCategories(
            Map<SuggestionCategory, List<String>> base,
            Map<SuggestionCategory, List<String>> addition
    ) {
        for (Map.Entry<SuggestionCategory, List<String>> entry : addition.entrySet()) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(base.getOrDefault(entry.getKey(), List.of()));
            merged.addAll(entry.getValue());
            base.put(entry.getKey(), List.copyOf(merged));
        }
    }

    private Optional<VanillaCatalogVersion> readPersistentCache(String version) {
        Path path = cacheDirectory.resolve(version).resolve("catalog.json");
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(path)) {
            JsonNode root = objectMapper.readTree(in);
            String source = root.path("source").asText("official-reports");
            String generationStrategy = root.path("generationStrategy").asText("official-server-reports");
            Instant generatedAt = parseInstant(root.path("generatedAt").asText(""));

            Map<SuggestionCategory, List<String>> categories = new EnumMap<>(SuggestionCategory.class);
            JsonNode categoriesNode = root.path("categories");
            if (categoriesNode.isObject()) {
                for (Map.Entry<String, JsonNode> categoryEntry : categoriesNode.properties()) {
                    try {
                        SuggestionCategory category = SuggestionCategory.valueOf(categoryEntry.getKey());
                        LinkedHashSet<String> values = new LinkedHashSet<>();
                        JsonNode valuesNode = categoryEntry.getValue();
                        if (valuesNode.isArray()) {
                            for (JsonNode valueNode : valuesNode) {
                                String value = valueNode.asText("");
                                if (!value.isBlank()) {
                                    values.add(value.trim());
                                }
                            }
                        }
                        if (!values.isEmpty()) {
                            categories.put(category, List.copyOf(values));
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            JsonNode metadataNode = root.path("metadata");
            if (metadataNode.isObject()) {
                for (Map.Entry<String, JsonNode> metadataEntry : metadataNode.properties()) {
                    metadata.put(metadataEntry.getKey(), metadataEntry.getValue().asText(""));
                }
            }
            metadata.put("fromPersistentCache", "true");

            return Optional.of(new VanillaCatalogVersion(
                    root.path("version").asText(version),
                    source,
                    generationStrategy,
                    generatedAt,
                    categories,
                    metadata
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void writePersistentCache(VanillaCatalogVersion catalog) {
        try {
            Path versionDirectory = cacheDirectory.resolve(catalog.version());
            Files.createDirectories(versionDirectory);

            Map<String, Object> categories = new LinkedHashMap<>();
            for (Map.Entry<SuggestionCategory, List<String>> entry : catalog.categories().entrySet()) {
                categories.put(entry.getKey().name(), entry.getValue());
            }

            Map<String, String> metadata = new LinkedHashMap<>(catalog.metadata());
            metadata.put("fromPersistentCache", "true");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", catalog.version());
            payload.put("source", catalog.source());
            payload.put("generationStrategy", catalog.generationStrategy());
            payload.put("generatedAt", catalog.generatedAt().toString());
            payload.put("categories", categories);
            payload.put("metadata", metadata);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(versionDirectory.resolve("catalog.json").toFile(), payload);
        } catch (IOException ignored) {
        }
    }

    private void recreateDirectory(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        Files.createDirectories(directory);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private String stripJsonSuffix(String value) {
        return value.endsWith(".json") ? value.substring(0, value.length() - 5) : value;
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim();
    }
}
