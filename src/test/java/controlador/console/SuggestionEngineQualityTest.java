package controlador.console;

import org.junit.jupiter.api.Test;
import support.ConsoleSuggestionFixtures;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionEngineQualityTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void metricsTrackLatencyAcrossRequests() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                staticProvider(List.of(
                        suggestion("help", SuggestionCategory.COMMAND, 100),
                        suggestion("give", SuggestionCategory.COMMAND, 100)
                ))
        )));

        engine.suggest("g", 1, ConsoleSuggestionFixtures.vanilla1211(), DIRECT_EXECUTOR).join();
        engine.suggest("he", 2, ConsoleSuggestionFixtures.vanilla1211(), DIRECT_EXECUTOR).join();

        SuggestionEngineMetricsSnapshot snapshot = engine.metricsSnapshot();
        assertThat(snapshot.sampleCount()).isEqualTo(2);
        assertThat(snapshot.lastLatencyMillis()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.maxLatencyMillis()).isGreaterThanOrEqualTo(snapshot.lastLatencyMillis());
        assertThat(snapshot.averageLatencyMillis()).isGreaterThanOrEqualTo(0d);
    }

    @Test
    void unknownCommandDoesNotInventSuggestionsPastRoot() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of()));

        SuggestionEngineResult result = engine.suggest("customcmd arg", "customcmd arg".length(), ConsoleSuggestionFixtures.paperPluginServer(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    private static SuggestionProvider staticProvider(List<Suggestion> suggestions) {
        return new SuggestionProvider() {
            @Override
            public SuggestionProviderDescriptor descriptor() {
                return new SuggestionProviderDescriptor(
                        "static",
                        "static",
                        Set.of(SuggestionProviderCoverage.STATIC_FALLBACK),
                        SuggestionProviderFreshness.STATIC,
                        SuggestionProviderCost.LOW,
                        true,
                        false,
                        Map.of()
                );
            }

            @Override
            public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
                return true;
            }

            @Override
            public CompletableFuture<SuggestionProviderResult> fetchSuggestions(ConsoleCommandContext context, SuggestionRequest request, Executor executor) {
                return CompletableFuture.completedFuture(SuggestionProviderResult.success(descriptor(), suggestions));
            }
        };
    }

    private static Suggestion suggestion(String insertText, SuggestionCategory category, int priority) {
        return new Suggestion(
                insertText,
                insertText,
                insertText,
                "",
                category,
                "",
                "",
                "static",
                priority,
                SuggestionMatch.none(),
                true,
                false,
                Map.of()
        );
    }
}
