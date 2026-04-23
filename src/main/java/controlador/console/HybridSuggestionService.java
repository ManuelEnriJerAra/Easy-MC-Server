package controlador.console;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Combina múltiples proveedores y degrada con elegancia si alguno falla o no cubre el contexto.
 */
public final class HybridSuggestionService {
    private static final long DEFAULT_PROVIDER_TIMEOUT_MS = 250L;
    private final List<SuggestionProvider> providers;
    private final long providerTimeoutMillis;

    public HybridSuggestionService(List<SuggestionProvider> providers) {
        this(providers, DEFAULT_PROVIDER_TIMEOUT_MS);
    }

    public HybridSuggestionService(List<SuggestionProvider> providers, long providerTimeoutMillis) {
        this.providers = List.copyOf(providers == null ? List.of() : providers);
        this.providerTimeoutMillis = providerTimeoutMillis <= 0 ? DEFAULT_PROVIDER_TIMEOUT_MS : providerTimeoutMillis;
    }

    public CompletableFuture<SuggestionAggregateResult> suggest(
            ConsoleCommandContext context,
            SuggestionRequest request,
            Executor executor
    ) {
        Objects.requireNonNull(executor, "executor");

        List<CompletableFuture<SuggestionProviderResult>> futures = providers.stream()
                .filter(Objects::nonNull)
                .map(provider -> safeFetch(provider, context, request, executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> aggregate(futures.stream().map(CompletableFuture::join).toList(), request));
    }

    private SuggestionAggregateResult aggregate(List<SuggestionProviderResult> providerResults, SuggestionRequest request) {
        Map<String, Suggestion> deduplicated = new LinkedHashMap<>();
        boolean degraded = false;

        for (SuggestionProviderResult result : providerResults) {
            if (result == null) {
                degraded = true;
                continue;
            }
            degraded |= result.degraded();

            for (Suggestion suggestion : result.suggestions()) {
                if (suggestion == null) {
                    continue;
                }
                String key = suggestion.category() + "::" + suggestion.insertText().toLowerCase();
                Suggestion current = deduplicated.get(key);
                if (current == null || compareSuggestion(suggestion, current) < 0) {
                    deduplicated.put(key, suggestion);
                }
            }
        }

        List<Suggestion> suggestions = new ArrayList<>(deduplicated.values());
        suggestions.sort(Comparator
                .comparingInt(Suggestion::priority).reversed()
                .thenComparing((Suggestion suggestion) -> suggestion.match().score(), Comparator.reverseOrder())
                .thenComparing(Suggestion::displayText, String.CASE_INSENSITIVE_ORDER));
        if (suggestions.size() > request.limit()) {
            suggestions = new ArrayList<>(suggestions.subList(0, request.limit()));
        }

        return new SuggestionAggregateResult(
                suggestions,
                providerResults,
                degraded,
                Map.of(
                        "providerCount", String.valueOf(providerResults.size()),
                        "providerTimeoutMillis", String.valueOf(providerTimeoutMillis)
                )
        );
    }

    private int compareSuggestion(Suggestion left, Suggestion right) {
        if (left.priority() != right.priority()) {
            return Integer.compare(right.priority(), left.priority());
        }
        return Integer.compare(right.match().score(), left.match().score());
    }

    private SuggestionRequest withContextMetadata(ConsoleCommandContext context, SuggestionRequest request) {
        if (request == null) {
            return SuggestionRequest.ofToken("", java.util.Set.of());
        }
        Map<String, String> metadata = new LinkedHashMap<>(request.metadata());
        if (context != null) {
            metadata.putIfAbsent("minecraftVersion", context.minecraftVersion());
            metadata.putIfAbsent("serverType", context.serverType());
        }
        return new SuggestionRequest(
                request.inputText(),
                request.activeToken(),
                request.caretOffset(),
                request.requestedCategories(),
                request.limit(),
                request.slashPrefixed(),
                metadata
        );
    }

    private CompletableFuture<SuggestionProviderResult> safeFetch(
            SuggestionProvider provider,
            ConsoleCommandContext context,
            SuggestionRequest request,
            Executor executor
    ) {
        SuggestionProviderDescriptor descriptor = provider.descriptor();
        SuggestionRequest requestWithContext = withContextMetadata(context, request);
        try {
            return provider.fetchSuggestions(context, requestWithContext, executor)
                    .completeOnTimeout(
                            SuggestionProviderResult.timeout(descriptor, providerTimeoutMillis),
                            providerTimeoutMillis,
                            TimeUnit.MILLISECONDS
                    )
                    .exceptionally(ex -> SuggestionProviderResult.failure(descriptor, ex.getMessage()));
        } catch (Exception ex) {
            return CompletableFuture.completedFuture(SuggestionProviderResult.failure(descriptor, ex.getMessage()));
        }
    }
}
