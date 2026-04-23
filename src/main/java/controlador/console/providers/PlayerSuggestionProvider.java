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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Proveedor híbrido de jugadores activos e históricos.
 */
public final class PlayerSuggestionProvider extends AbstractSuggestionProvider {
    private static final SuggestionProviderDescriptor DESCRIPTOR = new SuggestionProviderDescriptor(
            "players",
            "Jugadores activos e historicos",
            Set.of(SuggestionProviderCoverage.ACTIVE_PLAYERS, SuggestionProviderCoverage.HISTORICAL_PLAYERS),
            SuggestionProviderFreshness.NEAR_REALTIME,
            SuggestionProviderCost.LOW,
            true,
            false,
            java.util.Map.of("source", "context+files")
    );

    @Override
    public SuggestionProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
        if (context == null) {
            return false;
        }
        return request == null
                || request.requestedCategories().isEmpty()
                || request.requests(SuggestionCategory.PLAYER);
    }

    @Override
    protected SuggestionProviderResult resolve(ConsoleCommandContext context, SuggestionRequest request) {
        Set<String> playerNames = new LinkedHashSet<>();
        playerNames.addAll(context.onlinePlayers());
        playerNames.addAll(context.knownPlayers());
        playerNames.addAll(context.valuesOf("ops"));
        playerNames.addAll(context.valuesOf("whitelist"));

        List<Suggestion> suggestions = new ArrayList<>();
        for (String player : playerNames) {
            if (player == null || player.isBlank()) {
                continue;
            }
            int priority = context.onlinePlayers().contains(player) ? 250 : 180;
            String detail = context.onlinePlayers().contains(player)
                    ? "Jugador conectado actualmente"
                    : "Jugador conocido por usercache/listas";
            Suggestion suggestion = buildSuggestion(
                    "player:" + player.toLowerCase(),
                    player,
                    detail,
                    SuggestionCategory.PLAYER,
                    context,
                    priority,
                    descriptor().id()
            ).withMatch(buildMatch(player, request));
            if (suggestion.match().isMatch() || request.activeToken().isBlank()) {
                suggestions.add(suggestion);
            }
        }

        return SuggestionProviderResult.success(descriptor(), normalizeAndLimit(suggestions, request, context));
    }
}
