package controlador.console;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Utilidades comunes para proveedores concretos.
 */
public abstract class AbstractSuggestionProvider implements SuggestionProvider {

    @Override
    public CompletableFuture<SuggestionProviderResult> fetchSuggestions(
            ConsoleCommandContext context,
            SuggestionRequest request,
            Executor executor
    ) {
        if (!supports(context, request)) {
            return CompletableFuture.completedFuture(
                    SuggestionProviderResult.success(descriptor(), List.of())
            );
        }

        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(() -> resolveSafe(context, request), executor);
    }

    protected abstract SuggestionProviderResult resolve(ConsoleCommandContext context, SuggestionRequest request);

    protected List<Suggestion> normalizeAndLimit(
            List<Suggestion> suggestions,
            SuggestionRequest request,
            ConsoleCommandContext context
    ) {
        List<Suggestion> safeSuggestions = suggestions == null ? List.of() : suggestions;
        String version = contextVersion(context, request);
        String type = contextType(context, request);
        return safeSuggestions.stream()
                .filter(Objects::nonNull)
                .filter(suggestion -> suggestion.supports(version, type))
                .sorted(Comparator
                        .comparingInt(Suggestion::priority).reversed()
                        .thenComparing(Suggestion::displayText, String.CASE_INSENSITIVE_ORDER))
                .limit(request.limit())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    protected SuggestionProviderResult resolveSafe(ConsoleCommandContext context, SuggestionRequest request) {
        try {
            SuggestionProviderResult result = resolve(context, request);
            if (result == null) {
                return SuggestionProviderResult.failure(descriptor(), "El proveedor devolvio null.");
            }
            return result;
        } catch (Exception ex) {
            return SuggestionProviderResult.failure(descriptor(), ex.getMessage());
        }
    }

    protected Suggestion buildSuggestion(
            String id,
            String insertText,
            String detailText,
            SuggestionCategory category,
            ConsoleCommandContext context,
            int priority,
            String source
    ) {
        return new Suggestion(
                id,
                insertText,
                insertText,
                detailText,
                category,
                context == null ? "" : context.minecraftVersion(),
                context == null ? "" : context.serverType(),
                source,
                priority,
                buildMatch(insertText, null),
                true,
                false,
                java.util.Map.of()
        );
    }

    protected SuggestionMatch buildMatch(String candidate, SuggestionRequest request) {
        String activeToken = request == null ? "" : request.activeToken();
        String safeCandidate = candidate == null ? "" : candidate;
        if (activeToken.isBlank()) {
            return SuggestionMatch.partial("", safeCandidate, safeCandidate, 1);
        }
        if (safeCandidate.equalsIgnoreCase(activeToken)) {
            return SuggestionMatch.exact(activeToken, safeCandidate, 100);
        }
        if (safeCandidate.regionMatches(true, 0, activeToken, 0, activeToken.length())) {
            return SuggestionMatch.prefix(activeToken, safeCandidate, safeCandidate.substring(activeToken.length()), 80);
        }
        if (safeCandidate.toLowerCase().contains(activeToken.toLowerCase())) {
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

    private String contextVersion(ConsoleCommandContext context, SuggestionRequest request) {
        if (context != null && !context.minecraftVersion().isBlank()) {
            return context.minecraftVersion();
        }
        return request.metadata().getOrDefault("minecraftVersion", "");
    }

    private String contextType(ConsoleCommandContext context, SuggestionRequest request) {
        if (context != null && !context.serverType().isBlank()) {
            return context.serverType();
        }
        return request.metadata().getOrDefault("serverType", "");
    }
}
