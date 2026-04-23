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
 * Stub para futura integración con un helper server-side capaz de exportar Brigadier y registries.
 */
public final class LiveRuntimeSuggestionProvider extends AbstractSuggestionProvider {
    public static final String HELPER_ENDPOINT_KEY = "helperEndpoint";
    public static final String BRIGADIER_EXPORT_KEY = "brigadierExportAvailable";

    private static final SuggestionProviderDescriptor DESCRIPTOR = new SuggestionProviderDescriptor(
            "live-runtime",
            "Runtime en vivo",
            Set.of(SuggestionProviderCoverage.LIVE_RUNTIME, SuggestionProviderCoverage.SERVER_SIDE_HELPER),
            SuggestionProviderFreshness.LIVE,
            SuggestionProviderCost.HIGH,
            false,
            true,
            java.util.Map.of("mode", "helper-ready")
    );

    @Override
    public SuggestionProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
        return context != null && context.serverOnline();
    }

    @Override
    protected SuggestionProviderResult resolve(ConsoleCommandContext context, SuggestionRequest request) {
        boolean helperAvailable = "true".equalsIgnoreCase(context.metadata().get(BRIGADIER_EXPORT_KEY))
                || "true".equalsIgnoreCase(request.metadata().get(BRIGADIER_EXPORT_KEY));

        if (!helperAvailable) {
            return SuggestionProviderResult.degraded(
                    descriptor(),
                    List.of(),
                    "No hay helper server-side disponible para exportar Brigadier/registries."
            );
        }

        List<Suggestion> suggestions = new ArrayList<>();
        for (String command : context.valuesOf("liveCommands")) {
            Suggestion suggestion = buildSuggestion(
                    "live-command:" + command,
                    command,
                    "Comando recibido desde helper runtime",
                    SuggestionCategory.COMMAND,
                    context,
                    300,
                    descriptor().id()
            ).withMatch(buildMatch(command, request));
            if (suggestion.match().isMatch() || request.activeToken().isBlank()) {
                suggestions.add(suggestion);
            }
        }

        List<Suggestion> normalized = normalizeAndLimit(suggestions, request, context);
        if (normalized.isEmpty()) {
            return SuggestionProviderResult.degraded(
                    descriptor(),
                    List.of(),
                    "Helper disponible, pero sin export de comandos cargado todavia."
            );
        }
        return SuggestionProviderResult.success(descriptor(), normalized);
    }
}
