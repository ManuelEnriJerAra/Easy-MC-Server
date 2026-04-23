package controlador.console;

import java.util.List;
import java.util.Set;

/**
 * Resultado completo del motor de sugerencias.
 */
public record SuggestionEngineResult(
        ParsedCommandLine parsedLine,
        Set<SuggestionCategory> expectedCategories,
        SuggestionAggregateResult aggregateResult,
        TabCompletionPlan tabCompletionPlan
) {

    public SuggestionEngineResult {
        expectedCategories = Set.copyOf(expectedCategories == null ? Set.of() : expectedCategories);
        tabCompletionPlan = tabCompletionPlan == null
                ? TabCompletionPlan.none(
                parsedLine == null ? 0 : parsedLine.activeTokenStart(),
                parsedLine == null ? 0 : parsedLine.activeTokenEnd()
        )
                : tabCompletionPlan;
    }

    public List<Suggestion> suggestions() {
        return aggregateResult == null ? List.of() : aggregateResult.suggestions();
    }
}
