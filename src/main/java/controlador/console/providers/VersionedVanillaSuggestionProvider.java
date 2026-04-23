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
import controlador.console.vanilla.VanillaCatalogQueryResult;
import controlador.console.vanilla.VanillaCatalogService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Catálogo vanilla versionado respaldado por recursos internos y caché persistente.
 */
public final class VersionedVanillaSuggestionProvider extends AbstractSuggestionProvider {
    private static final SuggestionProviderDescriptor DESCRIPTOR = new SuggestionProviderDescriptor(
            "vanilla-versioned",
            "Catalogo vanilla por version",
            Set.of(SuggestionProviderCoverage.VANILLA_VERSIONED_CATALOG),
            SuggestionProviderFreshness.CACHED,
            SuggestionProviderCost.LOW,
            true,
            false,
            java.util.Map.of("catalogKind", "versioned-resource-cache")
    );
    private final VanillaCatalogService catalogService;

    public VersionedVanillaSuggestionProvider() {
        this(new VanillaCatalogService());
    }

    public VersionedVanillaSuggestionProvider(VanillaCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public SuggestionProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
        return context != null && !context.minecraftVersion().isBlank();
    }

    @Override
    protected SuggestionProviderResult resolve(ConsoleCommandContext context, SuggestionRequest request) {
        Set<SuggestionCategory> requestedCategories = request.requestedCategories().isEmpty()
                ? Set.of(
                SuggestionCategory.COMMAND,
                SuggestionCategory.ITEM,
                SuggestionCategory.BLOCK,
                SuggestionCategory.ENTITY,
                SuggestionCategory.EFFECT,
                SuggestionCategory.ENCHANTMENT,
                SuggestionCategory.DIMENSION,
                SuggestionCategory.GAMERULE,
                SuggestionCategory.TAG
        )
                : request.requestedCategories();

        VanillaCatalogQueryResult queryResult = catalogService.queryByPrefix(
                context.minecraftVersion(),
                requestedCategories,
                request.activeToken(),
                request.limit()
        );

        List<Suggestion> suggestions = new ArrayList<>();
        for (var entry : queryResult.matches().entrySet()) {
            SuggestionCategory category = entry.getKey();
            for (String value : entry.getValue()) {
                Suggestion suggestion = buildSuggestion(
                        category.name().toLowerCase() + ":" + value,
                        value,
                        "Catalogo vanilla " + queryResult.version(),
                        category,
                        context,
                        category == SuggestionCategory.COMMAND ? 200 : 150,
                        descriptor().id()
                ).withMatch(buildMatch(value, request));
                if (suggestion.match().isMatch() || request.activeToken().isBlank()) {
                    suggestions.add(suggestion);
                }
            }
        }

        List<Suggestion> normalized = normalizeAndLimit(suggestions, request, context);
        return normalized.isEmpty()
                ? SuggestionProviderResult.degraded(
                descriptor(),
                List.of(),
                "No hay coincidencias en el catalogo vanilla para " + context.minecraftVersion() + "."
        )
                : new SuggestionProviderResult(
                descriptor(),
                normalized,
                false,
                false,
                queryResult.fromPersistentCache(),
                "",
                queryResult.metadata()
        );
    }
}
