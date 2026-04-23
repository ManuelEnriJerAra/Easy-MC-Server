package controlador.console;

import controlador.console.hints.CommandHintRegistry;
import controlador.console.vanilla.VanillaCatalogService;
import controlador.console.vanilla.VanillaCommandTreeAnalyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Motor principal de análisis, consulta y resolución de sugerencias.
 */
public final class SuggestionEngine {
    private final CommandTokenizer tokenizer;
    private final HybridSuggestionService hybridSuggestionService;
    private final CommandHintRegistry commandHintRegistry;
    private final VanillaCatalogService vanillaCatalogService;
    private final VanillaCommandTreeAnalyzer vanillaCommandTreeAnalyzer;
    private final SuggestionEngineMetrics metrics;

    public SuggestionEngine(HybridSuggestionService hybridSuggestionService) {
        this(new CommandTokenizer(), hybridSuggestionService, CommandHintRegistry.defaultRegistry());
    }

    public SuggestionEngine(CommandTokenizer tokenizer, HybridSuggestionService hybridSuggestionService) {
        this(tokenizer, hybridSuggestionService, CommandHintRegistry.defaultRegistry());
    }

    public SuggestionEngine(
            CommandTokenizer tokenizer,
            HybridSuggestionService hybridSuggestionService,
            CommandHintRegistry commandHintRegistry
    ) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.hybridSuggestionService = Objects.requireNonNull(hybridSuggestionService, "hybridSuggestionService");
        this.commandHintRegistry = Objects.requireNonNull(commandHintRegistry, "commandHintRegistry");
        this.vanillaCatalogService = new VanillaCatalogService();
        this.vanillaCommandTreeAnalyzer = new VanillaCommandTreeAnalyzer();
        this.metrics = new SuggestionEngineMetrics();
    }

    public CompletableFuture<SuggestionEngineResult> suggest(
            String text,
            int caretOffset,
            ConsoleCommandContext context,
            Executor executor
    ) {
        Objects.requireNonNull(executor, "executor");
        long startedAtNanos = System.nanoTime();
        ParsedCommandLine parsedLine = tokenizer.parse(text, caretOffset);
        ParsedCommandLine effectiveParsedLine = adaptParsedLineForSuggestionContext(parsedLine, context);
        VanillaCommandTreeAnalyzer.Analysis grammarAnalysis = analyzeVanillaGrammar(effectiveParsedLine, context);
        Set<SuggestionCategory> expectedCategories = inferExpectedCategories(effectiveParsedLine, context, grammarAnalysis);
        if (expectedCategories.isEmpty()) {
            metrics.recordLatency(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
            return CompletableFuture.completedFuture(new SuggestionEngineResult(
                    effectiveParsedLine,
                    expectedCategories,
                    new SuggestionAggregateResult(List.of(), List.of(), false, java.util.Map.of("suppressed", "true")),
                    TabCompletionPlan.none(effectiveParsedLine.activeTokenStart(), effectiveParsedLine.activeTokenEnd())
            ));
        }
        SuggestionRequest request = buildRequest(effectiveParsedLine, expectedCategories);

        return hybridSuggestionService.suggest(context, request, executor)
                .thenApply(aggregate -> {
                    List<Suggestion> mergedSuggestions = new ArrayList<>(grammarAnalysis.literalSuggestions());
                    mergedSuggestions.addAll(aggregate.suggestions());
                    List<Suggestion> refined = refineSuggestions(effectiveParsedLine, mergedSuggestions);
                    SuggestionAggregateResult refinedAggregate = new SuggestionAggregateResult(
                            refined,
                            aggregate.providerResults(),
                            aggregate.degraded(),
                            aggregate.metadata()
                    );
                    TabCompletionPlan tabPlan = buildTabCompletionPlan(effectiveParsedLine, refined);
                    metrics.recordLatency(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
                    return new SuggestionEngineResult(effectiveParsedLine, expectedCategories, refinedAggregate, tabPlan);
                });
    }

    public SuggestionEngineMetricsSnapshot metricsSnapshot() {
        return metrics.snapshot();
    }

    Set<SuggestionCategory> inferExpectedCategories(
            ParsedCommandLine parsedLine,
            ConsoleCommandContext context,
            VanillaCommandTreeAnalyzer.Analysis grammarAnalysis
    ) {
        if (parsedLine == null || !parsedLine.hasCommand()) {
            return Set.of(SuggestionCategory.COMMAND);
        }

        if (grammarAnalysis != null && grammarAnalysis.authoritative() && !grammarAnalysis.expectedCategories().isEmpty()) {
            if (grammarAnalysis.literalSuggestions().isEmpty()
                    && grammarAnalysis.expectedCategories().size() == 1
                    && grammarAnalysis.expectedCategories().contains(SuggestionCategory.FREE_VALUE)) {
                return Set.of();
            }
            return grammarAnalysis.expectedCategories();
        }

        boolean knownRootCommand = isKnownRootCommand(parsedLine.commandName(), context);
        java.util.Optional<controlador.console.hints.CommandHintProfile> profile = commandHintRegistry.profileFor(parsedLine.commandName());
        if (knownRootCommand && isExactCompletedRootToken(parsedLine) && !parsedLine.trailingWhitespace()) {
            return Set.of();
        }
        if (profile.isPresent()) {
            return profile.get().expectedCategories(parsedLine);
        }

        if (isEditingRootToken(parsedLine)) {
            return Set.of(SuggestionCategory.COMMAND);
        }

        if (knownRootCommand) {
            return Set.of();
        }

        return Set.of();
    }

    private VanillaCommandTreeAnalyzer.Analysis analyzeVanillaGrammar(
            ParsedCommandLine parsedLine,
            ConsoleCommandContext context
    ) {
        if (context == null || parsedLine == null || parsedLine.commandName().isBlank()) {
            return VanillaCommandTreeAnalyzer.Analysis.empty();
        }
        return vanillaCommandTreeAnalyzer.analyze(context.minecraftVersion(), parsedLine, context.serverType());
    }

    private ParsedCommandLine adaptParsedLineForSuggestionContext(
            ParsedCommandLine parsedLine,
            ConsoleCommandContext context
    ) {
        if (parsedLine == null || !parsedLine.hasCommand()) {
            return parsedLine;
        }
        if (!isExactCompletedRootToken(parsedLine) || parsedLine.trailingWhitespace()) {
            return parsedLine;
        }
        if (!isRecognizedCompletedRootCommand(parsedLine, context)) {
            return parsedLine;
        }
        return new ParsedCommandLine(
                parsedLine.rawText(),
                parsedLine.caretOffset(),
                parsedLine.tokens(),
                -1,
                "",
                "",
                parsedLine.caretOffset(),
                parsedLine.caretOffset(),
                false,
                parsedLine.unclosedQuote(),
                parsedLine.slashPrefixed(),
                parsedLine.commandName(),
                0
        );
    }

    private SuggestionRequest buildRequest(ParsedCommandLine parsedLine, Set<SuggestionCategory> expectedCategories) {
        java.util.LinkedHashMap<String, String> metadata = new java.util.LinkedHashMap<>();
        metadata.put("commandName", parsedLine.commandName());
        metadata.put("argumentIndex", String.valueOf(parsedLine.argumentIndex()));
        metadata.put("activeTokenRaw", parsedLine.activeTokenRaw());
        metadata.put("trailingWhitespace", String.valueOf(parsedLine.trailingWhitespace()));
        metadata.put("unclosedQuote", String.valueOf(parsedLine.unclosedQuote()));
        commandHintRegistry.profileFor(parsedLine.commandName()).ifPresent(profile -> {
            metadata.put("commandFamily", profile.family().name());
            metadata.put("commandHintProfileId", profile.id());
        });

        return new SuggestionRequest(
                parsedLine.rawText(),
                parsedLine.activeToken(),
                parsedLine.caretOffset(),
                expectedCategories,
                25,
                parsedLine.slashPrefixed(),
                metadata
        );
    }

    private List<Suggestion> refineSuggestions(ParsedCommandLine parsedLine, List<Suggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }

        List<Suggestion> refined = new ArrayList<>();
        for (Suggestion suggestion : suggestions) {
            if (suggestion == null) {
                continue;
            }
            String candidate = suggestion.insertText();
            SuggestionMatch match = buildMatch(parsedLine, candidate);
            if (match.isMatch() || parsedLine.activeToken().isBlank()) {
                refined.add(suggestion.withMatch(match));
            }
        }

        refined.sort(java.util.Comparator
                .comparingInt(Suggestion::priority).reversed()
                .thenComparing((Suggestion suggestion) -> suggestion.match().score(), java.util.Comparator.reverseOrder())
                .thenComparing(Suggestion::displayText, String.CASE_INSENSITIVE_ORDER));
        LinkedHashMap<String, Suggestion> deduplicated = new LinkedHashMap<>();
        for (Suggestion suggestion : refined) {
            String key = suggestion.category().name() + "::" + suggestion.insertText().toLowerCase(Locale.ROOT);
            deduplicated.putIfAbsent(key, suggestion);
        }
        return List.copyOf(deduplicated.values());
    }

    private SuggestionMatch buildMatch(ParsedCommandLine parsedLine, String candidate) {
        String safeCandidate = candidate == null ? "" : candidate;
        String activeToken = parsedLine == null ? "" : parsedLine.activeToken();
        if (activeToken.isBlank()) {
            return SuggestionMatch.partial("", safeCandidate, safeCandidate, 1);
        }
        if (safeCandidate.equalsIgnoreCase(activeToken)) {
            return SuggestionMatch.exact(activeToken, safeCandidate, 100);
        }
        if (safeCandidate.regionMatches(true, 0, activeToken, 0, activeToken.length())) {
            return SuggestionMatch.prefix(activeToken, safeCandidate, safeCandidate.substring(activeToken.length()), 80);
        }
        if (safeCandidate.toLowerCase(Locale.ROOT).contains(activeToken.toLowerCase(Locale.ROOT))) {
            return new SuggestionMatch(
                    SuggestionMatch.MatchKind.CONTAINS,
                    activeToken,
                    safeCandidate,
                    "",
                    30,
                    false
            );
        }
        return SuggestionMatch.none();
    }

    private TabCompletionPlan buildTabCompletionPlan(ParsedCommandLine parsedLine, List<Suggestion> suggestions) {
        if (parsedLine == null) {
            return TabCompletionPlan.none(0, 0);
        }
        if (suggestions == null || suggestions.isEmpty()) {
            return TabCompletionPlan.none(parsedLine.activeTokenStart(), parsedLine.activeTokenEnd());
        }

        Suggestion first = suggestions.get(0);
        String prefix = parsedLine.activeToken();
        List<String> prefixCandidates = suggestions.stream()
                .map(Suggestion::insertText)
                .filter(Objects::nonNull)
                .filter(value -> prefix.isBlank() || value.regionMatches(true, 0, prefix, 0, prefix.length()))
                .toList();

        if (!prefixCandidates.isEmpty()) {
            if (prefixCandidates.size() == 1) {
                String selectedText = applySlashPrefixIfNeeded(parsedLine, first.insertText());
                return new TabCompletionPlan(
                        TabCompletionPlan.Mode.APPLY_SELECTION,
                        selectedText,
                        parsedLine.activeTokenStart(),
                        parsedLine.activeTokenEnd(),
                        first.insertText().startsWith(prefix) ? first.insertText().substring(prefix.length()) : ""
                );
            }
            String commonPrefix = longestCommonPrefixIgnoreCase(prefixCandidates);
            if (!commonPrefix.isBlank() && commonPrefix.length() > prefix.length()) {
                return new TabCompletionPlan(
                        TabCompletionPlan.Mode.APPLY_COMMON_PREFIX,
                        applySlashPrefixIfNeeded(parsedLine, commonPrefix),
                        parsedLine.activeTokenStart(),
                        parsedLine.activeTokenEnd(),
                        commonPrefix.substring(prefix.length())
                );
            }
        }

        String selectedText = applySlashPrefixIfNeeded(parsedLine, first.insertText());
        return new TabCompletionPlan(
                TabCompletionPlan.Mode.APPLY_SELECTION,
                selectedText,
                parsedLine.activeTokenStart(),
                parsedLine.activeTokenEnd(),
                first.insertText().startsWith(prefix) ? first.insertText().substring(prefix.length()) : ""
        );
    }

    private String applySlashPrefixIfNeeded(ParsedCommandLine parsedLine, String candidate) {
        if (candidate == null) {
            return "";
        }
        if (parsedLine.slashPrefixed() && parsedLine.activeTokenStart() == 0) {
            return candidate.startsWith("/") ? candidate : "/" + candidate;
        }
        return candidate;
    }

    private String longestCommonPrefixIgnoreCase(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String first = values.get(0);
        int maxLength = first.length();
        for (int index = 0; index < maxLength; index++) {
            char expected = Character.toLowerCase(first.charAt(index));
            for (int i = 1; i < values.size(); i++) {
                String current = values.get(i);
                if (index >= current.length() || Character.toLowerCase(current.charAt(index)) != expected) {
                    return first.substring(0, index);
                }
            }
        }
        return first;
    }

    private boolean isEditingRootToken(ParsedCommandLine parsedLine) {
        return parsedLine != null
                && parsedLine.activeTokenIndex() == 0
                && !parsedLine.trailingWhitespace();
    }

    private boolean isExactCompletedRootToken(ParsedCommandLine parsedLine) {
        if (parsedLine == null || !parsedLine.hasCommand() || parsedLine.tokens().isEmpty()) {
            return false;
        }
        if (parsedLine.trailingWhitespace() && parsedLine.tokens().size() == 1) {
            return true;
        }
        return parsedLine.activeTokenIndex() == 0
                && parsedLine.commandName().equalsIgnoreCase(parsedLine.activeToken());
    }

    private boolean isKnownRootCommand(String commandName, ConsoleCommandContext context) {
        if (commandName == null || commandName.isBlank()) {
            return false;
        }
        String normalized = commandName.trim().toLowerCase(Locale.ROOT);

        if (commandHintRegistry.profileFor(normalized).isPresent()) {
            return true;
        }

        if (context != null) {
            if (containsIgnoreCase(context.valuesOf("liveCommands"), normalized)
                    || containsIgnoreCase(context.valuesOf("pluginCommands"), normalized)
                    || containsIgnoreCase(context.valuesOf("vanillaCommands"), normalized)) {
                return true;
            }

            if (!context.minecraftVersion().isBlank()) {
                if (vanillaCommandTreeAnalyzer.isKnownRootCommand(context.minecraftVersion(), normalized)) {
                    return true;
                }
                try {
                    return vanillaCatalogService.isKnownCommandCached(context.minecraftVersion(), normalized);
                } catch (RuntimeException ignored) {
                }
            }
        }

        return false;
    }

    private boolean isRecognizedCompletedRootCommand(ParsedCommandLine parsedLine, ConsoleCommandContext context) {
        if (parsedLine == null || !parsedLine.hasCommand()) {
            return false;
        }
        if (isKnownRootCommand(parsedLine.commandName(), context)) {
            return true;
        }
        return commandHintRegistry.profileFor(parsedLine.commandName()).isPresent();
    }

    private boolean containsIgnoreCase(Set<String> values, String expected) {
        if (values == null || values.isEmpty() || expected == null || expected.isBlank()) {
            return false;
        }
        return values.stream().anyMatch(value -> value != null && value.equalsIgnoreCase(expected));
    }
}
