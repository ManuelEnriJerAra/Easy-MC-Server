package controlador.console.vanilla;

import controlador.MojangAPI;
import controlador.console.SuggestionCategory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generador interno de datasets vanilla en resources para distribuirlos ya preparados.
 */
public final class VanillaDatasetGenerator {
    private static final Path RESOURCE_DIRECTORY = Path.of("src", "main", "resources", "console", "vanilla");

    private final ObjectMapper objectMapper;
    private final MojangAPI mojangAPI;
    private final VanillaCatalogRepository seedRepository;
    private final AutomaticVanillaReportCatalogSource reportCatalogSource;
    private final AutomaticVanillaCommandCatalogSource commandCatalogSource;

    public VanillaDatasetGenerator() {
        this.objectMapper = new ObjectMapper();
        this.mojangAPI = new MojangAPI();
        this.seedRepository = new VanillaCatalogRepository();
        this.reportCatalogSource = new AutomaticVanillaReportCatalogSource();
        this.commandCatalogSource = new AutomaticVanillaCommandCatalogSource();
    }

    public static void main(String[] args) throws Exception {
        VanillaDatasetGenerator generator = new VanillaDatasetGenerator();
        List<String> requestedVersions = generator.resolveRequestedVersions(args);
        generator.generateIntoResources(requestedVersions);
    }

    public void generateIntoResources(List<String> versions) throws IOException {
        List<String> normalizedVersions = normalizeVersions(versions);
        if (normalizedVersions.isEmpty()) {
            throw new IOException("No hay versiones para generar.");
        }

        Files.createDirectories(RESOURCE_DIRECTORY);
        List<String> generatedVersions = new ArrayList<>();
        for (String version : normalizedVersions) {
            VanillaCatalogVersion dataset = buildDataset(version);
            writeDataset(dataset);
            generatedVersions.add(version);
        }
        writeManifest(generatedVersions);
    }

    private VanillaCatalogVersion buildDataset(String version) throws IOException {
        Optional<VanillaCatalogVersion> generatedReportCatalog = reportCatalogSource.resolve(version);
        Optional<VanillaCatalogVersion> existingSeedCatalog = seedRepository.findCatalog(version);
        VanillaCommandCatalog commandCatalog = commandCatalogSource.resolve(version);

        Map<SuggestionCategory, List<String>> categories = new LinkedHashMap<>();
        generatedReportCatalog.ifPresent(catalog -> mergeCategories(categories, catalog.categories(), SuggestionCategory.COMMAND));
        existingSeedCatalog.ifPresent(catalog -> mergeCategories(categories, catalog.categories(), SuggestionCategory.COMMAND));
        categories.put(SuggestionCategory.COMMAND, mergeValues(commandCatalog.commands()));

        if (categories.getOrDefault(SuggestionCategory.COMMAND, List.of()).isEmpty()) {
            throw new IOException("No se han podido generar comandos vanilla para " + version);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("notes", "Dataset vanilla generado automaticamente desde sources oficiales y fallback de comandos help.");
        metadata.put("generatedBy", VanillaDatasetGenerator.class.getName());
        metadata.put("generatedAt", Instant.now().toString());
        metadata.put("commandSource", commandCatalog.source());
        metadata.put("commandCount", String.valueOf(categories.get(SuggestionCategory.COMMAND).size()));
        generatedReportCatalog.ifPresent(catalog -> {
            metadata.put("reportSource", catalog.source());
            metadata.put("reportGenerationStrategy", catalog.generationStrategy());
        });
        existingSeedCatalog.ifPresent(catalog -> metadata.put("mergedSeedFallback", catalog.source()));

        return new VanillaCatalogVersion(
                version,
                "generated-resource",
                "official-server-reports+runtime-help-fallback",
                Instant.now(),
                categories,
                metadata
        );
    }

    private void writeDataset(VanillaCatalogVersion catalog) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", catalog.version());
        payload.put("source", catalog.source());
        payload.put("generationStrategy", catalog.generationStrategy());
        payload.put("generatedAt", catalog.generatedAt().toString());

        Map<String, Object> categories = new LinkedHashMap<>();
        for (Map.Entry<SuggestionCategory, List<String>> entry : catalog.categories().entrySet()) {
            categories.put(entry.getKey().name(), entry.getValue());
        }
        payload.put("categories", categories);
        payload.put("metadata", catalog.metadata());

        Path target = RESOURCE_DIRECTORY.resolve(catalog.version() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), payload);
    }

    private void writeManifest(List<String> versions) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("versions", versions);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(RESOURCE_DIRECTORY.resolve("manifest.json").toFile(), manifest);
    }

    private void mergeCategories(
            Map<SuggestionCategory, List<String>> target,
            Map<SuggestionCategory, List<String>> addition,
            SuggestionCategory... excludedCategories
    ) {
        Set<SuggestionCategory> excluded = excludedCategories == null || excludedCategories.length == 0
                ? Set.of()
                : Set.of(excludedCategories);
        for (Map.Entry<SuggestionCategory, List<String>> entry : addition.entrySet()) {
            if (excluded.contains(entry.getKey())) {
                continue;
            }
            target.put(entry.getKey(), mergeValues(
                    target.getOrDefault(entry.getKey(), List.of()),
                    entry.getValue()
            ));
        }
    }

    private List<String> mergeValues(List<String>... lists) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (List<String> list : lists) {
            if (list == null) {
                continue;
            }
            for (String value : list) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                merged.add(value.trim());
            }
        }
        return merged.stream()
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
    }

    private List<String> resolveRequestedVersions(String[] args) {
        if (args == null || args.length == 0) {
            return readManifestVersions();
        }
        List<String> resolved = new ArrayList<>();
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String normalized = arg.trim().toLowerCase(Locale.ROOT);
            if ("--all-releases".equals(normalized)) {
                return mojangAPI.obtenerListaVersiones();
            }
            resolved.add(arg.trim());
        }
        return resolved;
    }

    private List<String> readManifestVersions() {
        try {
            Path manifest = RESOURCE_DIRECTORY.resolve("manifest.json");
            if (!Files.isRegularFile(manifest)) {
                return List.of();
            }
            var root = objectMapper.readTree(manifest.toFile());
            var versionsNode = root.path("versions");
            if (!versionsNode.isArray()) {
                return List.of();
            }
            List<String> versions = new ArrayList<>();
            versionsNode.forEach(node -> {
                String value = node.asText("");
                if (!value.isBlank()) {
                    versions.add(value.trim());
                }
            });
            return versions;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> normalizeVersions(List<String> versions) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (versions != null) {
            for (String version : versions) {
                if (version == null || version.isBlank()) {
                    continue;
                }
                normalized.add(version.trim());
            }
        }
        return List.copyOf(normalized);
    }
}
