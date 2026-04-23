package controlador.console.vanilla;

import controlador.console.SuggestionCategory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Repositorio de catálogos vanilla versionados con recursos internos y caché persistente local.
 */
public final class VanillaCatalogRepository {
    private static final String RESOURCE_BASE = "console/vanilla/";
    private static final String MANIFEST_RESOURCE = RESOURCE_BASE + "manifest.json";

    private final ObjectMapper objectMapper;
    private final Path persistentCacheDirectory;
    private final ClassLoader classLoader;
    private final Map<String, VanillaCatalogVersion> memoryCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile Set<String> knownVersions;

    public VanillaCatalogRepository() {
        this(new ObjectMapper(), defaultCacheDirectory(), VanillaCatalogRepository.class.getClassLoader());
    }

    VanillaCatalogRepository(ObjectMapper objectMapper, Path persistentCacheDirectory, ClassLoader classLoader) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.persistentCacheDirectory = Objects.requireNonNull(persistentCacheDirectory, "persistentCacheDirectory");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public Set<String> availableVersions() {
        Set<String> snapshot = knownVersions;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (knownVersions == null) {
                knownVersions = loadManifestVersions();
            }
            return knownVersions;
        }
    }

    public Optional<VanillaCatalogVersion> findCatalog(String version) {
        String normalizedVersion = normalizeVersion(version);
        if (normalizedVersion.isBlank()) {
            return Optional.empty();
        }

        VanillaCatalogVersion inMemory = memoryCache.get(normalizedVersion);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }

        Optional<VanillaCatalogVersion> fromResource = readBundledCatalog(normalizedVersion);
        Optional<VanillaCatalogVersion> fromDisk = readPersistentCache(normalizedVersion);

        VanillaCatalogVersion selected = selectPreferredCatalog(fromResource.orElse(null), fromDisk.orElse(null));
        if (selected == null) {
            return Optional.empty();
        }

        memoryCache.put(normalizedVersion, selected);
        if (fromResource.isPresent() && selected == fromResource.get()) {
            writePersistentCache(selected);
        }
        return Optional.of(selected);
    }

    public VanillaCatalogVersion requireCatalog(String version) {
        return findCatalog(version).orElseThrow(() ->
                new IllegalArgumentException("No hay catalogo vanilla para la version: " + version)
        );
    }

    public void savePersistentCatalog(VanillaCatalogVersion catalog) {
        if (catalog == null || catalog.version().isBlank()) {
            return;
        }
        memoryCache.put(catalog.version(), catalog);
        writePersistentCache(catalog);
    }

    public Path persistentCacheDirectory() {
        return persistentCacheDirectory;
    }

    private Set<String> loadManifestVersions() {
        try (InputStream in = classLoader.getResourceAsStream(MANIFEST_RESOURCE)) {
            if (in == null) {
                return Set.of();
            }
            JsonNode root = objectMapper.readTree(in);
            JsonNode versionsNode = root.path("versions");
            if (!versionsNode.isArray()) {
                return Set.of();
            }
            LinkedHashSet<String> versions = new LinkedHashSet<>();
            for (JsonNode versionNode : versionsNode) {
                String value = versionNode.asText("");
                if (!value.isBlank()) {
                    versions.add(value.trim());
                }
            }
            return Set.copyOf(versions);
        } catch (IOException ex) {
            return Set.of();
        }
    }

    private Optional<VanillaCatalogVersion> readBundledCatalog(String version) {
        String resource = RESOURCE_BASE + version + ".json";
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(parseCatalog(objectMapper.readTree(in), "bundled-resource"));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<VanillaCatalogVersion> readPersistentCache(String version) {
        Path path = persistentCacheDirectory.resolve(version + ".json");
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            VanillaCatalogVersion parsed = parseCatalog(root, "persistent-cache");
            return Optional.of(parsed);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private VanillaCatalogVersion selectPreferredCatalog(
            VanillaCatalogVersion bundled,
            VanillaCatalogVersion persistent
    ) {
        if (bundled == null) {
            return persistent;
        }
        return bundled;
    }

    private void writePersistentCache(VanillaCatalogVersion catalog) {
        try {
            Files.createDirectories(persistentCacheDirectory);
            Path target = persistentCacheDirectory.resolve(catalog.version() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), toJsonMap(catalog));
        } catch (IOException ignored) {
        }
    }

    private VanillaCatalogVersion parseCatalog(JsonNode root, String resolvedSource) {
        String version = root.path("version").asText("");
        String source = root.path("source").asText(resolvedSource);
        String generationStrategy = root.path("generationStrategy").asText("embedded-resource");
        Instant generatedAt = parseInstant(root.path("generatedAt").asText(""));

        Map<SuggestionCategory, List<String>> categories = new EnumMap<>(SuggestionCategory.class);
        JsonNode categoriesNode = root.path("categories");
        if (categoriesNode.isObject()) {
            for (Map.Entry<String, JsonNode> categoryEntry : categoriesNode.properties()) {
                String fieldName = categoryEntry.getKey();
                SuggestionCategory category = parseCategory(fieldName);
                if (category == null) {
                    continue;
                }
                JsonNode valuesNode = categoryEntry.getValue();
                if (!valuesNode.isArray()) {
                    continue;
                }
                LinkedHashSet<String> values = new LinkedHashSet<>();
                for (JsonNode valueNode : valuesNode) {
                    String value = valueNode.asText("");
                    if (!value.isBlank()) {
                        values.add(value.trim());
                    }
                }
                categories.put(category, List.copyOf(new ArrayList<>(values)));
            }
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        JsonNode metadataNode = root.path("metadata");
        if (metadataNode.isObject()) {
            for (Map.Entry<String, JsonNode> metadataEntry : metadataNode.properties()) {
                metadata.put(metadataEntry.getKey(), metadataEntry.getValue().asText(""));
            }
        }

        return new VanillaCatalogVersion(version, source, generationStrategy, generatedAt, categories, metadata);
    }

    private Map<String, Object> toJsonMap(VanillaCatalogVersion catalog) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", catalog.version());
        root.put("source", catalog.source());
        root.put("generationStrategy", catalog.generationStrategy());
        root.put("generatedAt", catalog.generatedAt().toString());

        Map<String, Object> categories = new LinkedHashMap<>();
        for (Map.Entry<SuggestionCategory, List<String>> entry : catalog.categories().entrySet()) {
            categories.put(entry.getKey().name(), entry.getValue());
        }
        root.put("categories", categories);
        root.put("metadata", catalog.metadata());
        return root;
    }

    private SuggestionCategory parseCategory(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }
        try {
            return SuggestionCategory.valueOf(fieldName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    private static String normalizeVersion(String version) {
        return version == null ? "" : version.trim();
    }

    private static Path defaultCacheDirectory() {
        return Path.of(System.getProperty("user.home"), ".easy-mc-server", "cache", "console", "vanilla");
    }
}
