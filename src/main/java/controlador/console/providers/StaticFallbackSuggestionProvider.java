package controlador.console.providers;

import controlador.console.AbstractSuggestionProvider;
import controlador.console.ConsoleCommandContext;
import controlador.console.Suggestion;
import controlador.console.SuggestionCategory;
import controlador.console.SuggestionProviderCoverage;
import controlador.console.SuggestionProviderCost;
import controlador.console.SuggestionProviderDescriptor;
import controlador.console.SuggestionProviderFreshness;
import controlador.console.SuggestionProviderResult;
import controlador.console.SuggestionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Última red de seguridad cuando no hay mejores fuentes disponibles.
 */
public final class StaticFallbackSuggestionProvider extends AbstractSuggestionProvider {
    private static final SuggestionProviderDescriptor DESCRIPTOR = new SuggestionProviderDescriptor(
            "static-fallback",
            "Fallback estatico",
            Set.of(SuggestionProviderCoverage.STATIC_FALLBACK),
            SuggestionProviderFreshness.STATIC,
            SuggestionProviderCost.LOW,
            true,
            false,
            java.util.Map.of("role", "safety-net")
    );

    private static final List<String> FALLBACK_COMMANDS = List.of("help", "list", "say", "stop", "save-all");

    @Override
    public SuggestionProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
        return true;
    }

    @Override
    protected SuggestionProviderResult resolve(ConsoleCommandContext context, SuggestionRequest request) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (String command : FALLBACK_COMMANDS) {
            Suggestion suggestion = buildSuggestion(
                    "fallback:" + command,
                    command,
                    "Sugerencia estatica de emergencia",
                    SuggestionCategory.COMMAND,
                    context == null ? ConsoleCommandContext.empty() : context,
                    50,
                    descriptor().id()
            ).withMatch(buildMatch(command, request));
            if (suggestion.match().isMatch() || request.activeToken().isBlank()) {
                suggestions.add(suggestion);
            }
        }
        return new SuggestionProviderResult(
                descriptor(),
                normalizeAndLimit(suggestions, request, context),
                true,
                false,
                true,
                "Fallback activo; no habia fuentes mas precisas disponibles o no respondieron.",
                java.util.Map.of()
        );
    }
}
