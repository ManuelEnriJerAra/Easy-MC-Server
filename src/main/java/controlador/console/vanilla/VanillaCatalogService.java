package controlador.console.vanilla;

import controlador.console.SuggestionCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;

/**
 * Servicio de consulta rápida por prefijo sobre catálogos vanilla versionados.
 */
public final class VanillaCatalogService {
    private final VanillaCatalogRepository repository;
    private final VanillaReportCatalogSource reportCatalogSource;
    private final VanillaCommandCatalogSource commandCatalogSource;
    private static final VanillaReportCatalogSource NO_OP_REPORT_SOURCE = new VanillaReportCatalogSource() {
        @Override
        public Optional<VanillaCatalogVersion> resolve(String version) {
            return Optional.empty();
        }
    };

    public VanillaCatalogService() {
        this(
                new VanillaCatalogRepository(),
                NO_OP_REPORT_SOURCE,
                new AutomaticVanillaCommandCatalogSource()
        );
    }

    public VanillaCatalogService(VanillaCatalogRepository repository) {
        this(
                repository,
                NO_OP_REPORT_SOURCE,
                new AutomaticVanillaCommandCatalogSource()
        );
    }

    public VanillaCatalogService(
            VanillaCatalogRepository repository,
            VanillaCommandCatalogSource commandCatalogSource
    ) {
        this(repository, NO_OP_REPORT_SOURCE, commandCatalogSource);
    }

    public VanillaCatalogService(
            VanillaCatalogRepository repository,
            VanillaReportCatalogSource reportCatalogSource,
            VanillaCommandCatalogSource commandCatalogSource
    ) {
        this.repository = repository;
        this.reportCatalogSource = reportCatalogSource;
        this.commandCatalogSource = commandCatalogSource;
    }

    public Set<String> availableVersions() {
        return repository.availableVersions();
    }

    public Optional<VanillaCatalogVersion> findCatalog(String version) {
        return repository.findCatalog(version);
    }

    public List<String> queryByPrefix(String version, SuggestionCategory category, String prefix, int limit) {
        return queryCategoryByPrefix(version, category, prefix, limit);
    }

    public VanillaCatalogQueryResult queryByPrefix(
            String version,
            Set<SuggestionCategory> categories,
            String prefix,
            int limit
    ) {
        Set<SuggestionCategory> requested = (categories == null || categories.isEmpty())
                ? defaultRequestedCategories(version)
                : Set.copyOf(new LinkedHashSet<>(categories));

        Map<SuggestionCategory, List<String>> matches = new EnumMap<>(SuggestionCategory.class);
        for (SuggestionCategory category : requested) {
            matches.put(category, queryCategoryByPrefix(version, category, prefix, limit));
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("generationStrategy", "runtime-help-for-commands+seed-catalog-for-vanilla-content");
        metadata.put("generatedAt", Instant.now().toString());
        Optional<VanillaCatalogVersion> automaticCatalog = reportCatalogSource.resolveCached(version);
        automaticCatalog.ifPresent(catalog -> {
            metadata.put("automaticCatalogSource", catalog.source());
            metadata.put("automaticCatalogGeneratedAt", catalog.generatedAt().toString());
            metadata.putAll(catalog.metadata());
        });
        metadata.put("automaticCatalogEnabled", String.valueOf(reportCatalogSource != NO_OP_REPORT_SOURCE));
        metadata.put("automaticCatalogPending", String.valueOf(reportCatalogSource.isWarmupPending(version)));
        Optional<VanillaCatalogVersion> seedCatalog = repository.findCatalog(version);
        seedCatalog.ifPresent(catalog -> metadata.put("seedCatalogSource", catalog.source()));

        if (requested.contains(SuggestionCategory.COMMAND)) {
            VanillaCommandCatalog commandCatalog = commandCatalogSource.resolveCached(version);
            metadata.put("commandFallbackSource", commandCatalog.source());
            metadata.put("commandFallbackGeneratedAt", commandCatalog.generatedAt().toString());
            metadata.put("commandCatalogCache", String.valueOf(commandCatalog.fromPersistentCache()));
            metadata.putAll(commandCatalog.metadata());
        }

        return new VanillaCatalogQueryResult(
                version == null ? "" : version.trim(),
                prefix,
                matches,
                automaticCatalog.map(catalog -> Boolean.parseBoolean(catalog.metadata().getOrDefault("fromPersistentCache", "false")))
                        .orElse(seedCatalog.isPresent()),
                Map.copyOf(metadata)
        );
    }

    public void persistCatalog(VanillaCatalogVersion catalog) {
        repository.savePersistentCatalog(catalog);
    }

    private Set<SuggestionCategory> defaultRequestedCategories(String version) {
        LinkedHashSet<SuggestionCategory> requested = new LinkedHashSet<>();
        requested.add(SuggestionCategory.COMMAND);
        try {
            reportCatalogSource.resolveCached(version).ifPresent(catalog -> requested.addAll(catalog.availableCategories()));
            repository.findCatalog(version).ifPresent(catalog -> requested.addAll(catalog.availableCategories()));
        } catch (RuntimeException ignored) {
        }
        return Set.copyOf(requested);
    }

    public boolean isKnownCommandCached(String version, String commandName) {
        String normalized = commandName == null ? "" : commandName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        VanillaCommandCatalog fallbackCatalog = commandCatalogSource.resolveCached(version);
        if (fallbackCatalog.commands().isEmpty()) {
            commandCatalogSource.scheduleWarmup(version);
            return repository.findCatalog(version)
                    .map(catalog -> catalog.valuesOf(SuggestionCategory.COMMAND).stream()
                            .anyMatch(candidate -> candidate.equalsIgnoreCase(normalized)))
                    .orElse(false);
        }
        return fallbackCatalog.commands().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(normalized));
    }

    public void ensureCommandWarmupScheduled(String version) {
        if (version == null || version.isBlank()) {
            return;
        }
        commandCatalogSource.scheduleWarmup(version);
    }

    public boolean isCommandWarmupPending(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        return commandCatalogSource.isWarmupPending(version)
                || Boolean.parseBoolean(commandCatalogSource.resolveCached(version)
                .metadata()
                .getOrDefault("pendingWarmup", "false"));
    }

    private List<String> queryCategoryByPrefix(String version, SuggestionCategory category, String prefix, int limit) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();

        if (category == SuggestionCategory.COMMAND) {
            VanillaCommandCatalog commandCatalog = commandCatalogSource.resolveCached(version);
            if (commandCatalog.commands().isEmpty()) {
                commandCatalogSource.scheduleWarmup(version);
            } else {
                merged.addAll(PrefixIndex.queryValues(commandCatalog.commands(), prefix, limit));
            }
        } else {
            Optional<VanillaCatalogVersion> automaticCatalog = reportCatalogSource.resolveCached(version);
            if (automaticCatalog.isEmpty()) {
                reportCatalogSource.scheduleWarmup(version);
            } else {
                merged.addAll(PrefixIndex.queryValues(automaticCatalog.get().valuesOf(category), prefix, limit));
            }
        }

        Optional<VanillaCatalogVersion> seedCatalog = repository.findCatalog(version);
        if (seedCatalog.isPresent()) {
            merged.addAll(PrefixIndex.queryValues(seedCatalog.get().valuesOf(category), prefix, limit));
        }

        return merged.stream().limit(limit <= 0 ? 25 : limit).toList();
    }

    private static final class PrefixIndex {
        static List<String> queryValues(List<String> values, String prefix, int limit) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
            int safeLimit = limit <= 0 ? 25 : limit;

            return values.stream()
                    .filter(value -> matchesPrefix(value, normalizedPrefix))
                    .sorted(Comparator
                            .comparingInt((String value) -> score(value, normalizedPrefix)).reversed()
                            .thenComparing(String.CASE_INSENSITIVE_ORDER))
                    .limit(safeLimit)
                    .toList();
        }

        private static boolean matchesPrefix(String value, String prefix) {
            if (prefix.isBlank()) {
                return true;
            }
            String normalizedValue = value.toLowerCase(Locale.ROOT);
            return normalizedValue.startsWith(prefix)
                    || normalizedValue.contains(":" + prefix)
                    || normalizedValue.contains(prefix);
        }

        private static int score(String value, String prefix) {
            if (prefix.isBlank()) {
                return 1;
            }
            String normalizedValue = value.toLowerCase(Locale.ROOT);
            if (normalizedValue.equals(prefix)) {
                return 100;
            }
            if (normalizedValue.startsWith(prefix)) {
                return 80;
            }
            if (normalizedValue.contains(":" + prefix)) {
                return 60;
            }
            if (normalizedValue.contains(prefix)) {
                return 30;
            }
            return 0;
        }
    }
}
