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
 * Stub funcional para contenido de mods/plugins detectado localmente.
 */
public final class ModdedPluginSuggestionProvider extends AbstractSuggestionProvider {
    private static final SuggestionProviderDescriptor DESCRIPTOR = new SuggestionProviderDescriptor(
            "modded-plugin",
            "Catalogo modded/plugin",
            Set.of(SuggestionProviderCoverage.MODDED_CONTENT, SuggestionProviderCoverage.PLUGIN_CONTENT),
            SuggestionProviderFreshness.SNAPSHOT,
            SuggestionProviderCost.MEDIUM,
            true,
            true,
            java.util.Map.of("mode", "local-detection")
    );

    @Override
    public SuggestionProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
        return context != null && (!context.installedMods().isEmpty() || !context.installedPlugins().isEmpty());
    }

    @Override
    protected SuggestionProviderResult resolve(ConsoleCommandContext context, SuggestionRequest request) {
        List<Suggestion> suggestions = new ArrayList<>();

        for (String mod : context.installedMods()) {
            String namespace = sanitizeNamespace(mod);
            String entityId = namespace + ":example_entity";
            Suggestion entitySuggestion = buildSuggestion(
                    "mod-entity:" + entityId,
                    entityId,
                    "Entidad estimada para mod detectado: " + mod,
                    SuggestionCategory.ENTITY,
                    context,
                    120,
                    descriptor().id()
            ).withMatch(buildMatch(entityId, request));
            if (entitySuggestion.match().isMatch() || request.activeToken().isBlank()) {
                suggestions.add(entitySuggestion);
            }
        }

        for (String plugin : context.installedPlugins()) {
            String command = plugin.toLowerCase() + ":help";
            Suggestion commandSuggestion = buildSuggestion(
                    "plugin-command:" + command,
                    command,
                    "Comando estimado a partir del plugin detectado: " + plugin,
                    SuggestionCategory.COMMAND,
                    context,
                    110,
                    descriptor().id()
            ).withMatch(buildMatch(command, request));
            if (commandSuggestion.match().isMatch() || request.activeToken().isBlank()) {
                suggestions.add(commandSuggestion);
            }
        }

        List<Suggestion> normalized = normalizeAndLimit(suggestions, request, context);
        if (normalized.isEmpty()) {
            return SuggestionProviderResult.degraded(
                    descriptor(),
                    List.of(),
                    "Hay mods/plugins detectados, pero sin helper solo se dispone de cobertura parcial."
            );
        }
        return new SuggestionProviderResult(
                descriptor(),
                normalized,
                true,
                true,
                false,
                "Cobertura parcial basada en deteccion local; para precision completa hace falta helper.",
                java.util.Map.of("fingerprint", context.contentFingerprint())
        );
    }

    private String sanitizeNamespace(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "modded";
        }
        return rawValue.strip().toLowerCase().replace(' ', '_');
    }
}
