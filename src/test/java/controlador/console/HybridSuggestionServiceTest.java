package controlador.console;

import org.junit.jupiter.api.Test;
import support.ConsoleSuggestionFixtures;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSuggestionServiceTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void ranksHigherPrioritySuggestionFirstAndDeduplicates() {
        HybridSuggestionService service = new HybridSuggestionService(List.of(
                staticProvider("low", List.of(suggestion("give", 100, "low"))),
                staticProvider("high", List.of(suggestion("give", 300, "high"), suggestion("gamerule", 200, "high")))
        ));

        SuggestionAggregateResult result = service.suggest(
                ConsoleSuggestionFixtures.vanilla1211(),
                SuggestionRequest.ofToken("g", Set.of(SuggestionCategory.COMMAND)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText)
                .containsExactly("give", "gamerule");
        assertThat(result.suggestions().get(0).priority()).isEqualTo(300);
    }

    @Test
    void providerTimeoutProducesDegradedButSafeResult() {
        HybridSuggestionService service = new HybridSuggestionService(List.of(
                hangingProvider("hang"),
                staticProvider("fallback", List.of(suggestion("help", 50, "fallback")))
        ), 50);

        SuggestionAggregateResult result = service.suggest(
                ConsoleSuggestionFixtures.vanilla1211(),
                SuggestionRequest.ofToken("", Set.of(SuggestionCategory.COMMAND)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.degraded()).isTrue();
        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("help");
        assertThat(result.providerResults()).anyMatch(providerResult -> providerResult.stale() && providerResult.message().contains("Timeout"));
    }

    @Test
    void providerFailureDoesNotBreakAggregate() {
        HybridSuggestionService service = new HybridSuggestionService(List.of(
                failingProvider("broken"),
                staticProvider("ok", List.of(suggestion("stop", 100, "ok")))
        ));

        SuggestionAggregateResult result = service.suggest(
                ConsoleSuggestionFixtures.vanilla1211(),
                SuggestionRequest.ofToken("st", Set.of(SuggestionCategory.COMMAND)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.degraded()).isTrue();
        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("stop");
        assertThat(result.providerResults()).anyMatch(providerResult -> providerResult.message().contains("boom"));
    }

    private static Suggestion suggestion(String insertText, int priority, String source) {
        return new Suggestion(
                source + ":" + insertText,
                insertText,
                insertText,
                "",
                SuggestionCategory.COMMAND,
                "",
                "",
                source,
                priority,
                SuggestionMatch.none(),
                true,
                false,
                Map.of()
        );
    }

    private static SuggestionProvider staticProvider(String id, List<Suggestion> suggestions) {
        return new SuggestionProvider() {
            @Override
            public SuggestionProviderDescriptor descriptor() {
                return descriptorOf(id);
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

    private static SuggestionProvider failingProvider(String id) {
        return new SuggestionProvider() {
            @Override
            public SuggestionProviderDescriptor descriptor() {
                return descriptorOf(id);
            }

            @Override
            public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
                return true;
            }

            @Override
            public CompletableFuture<SuggestionProviderResult> fetchSuggestions(ConsoleCommandContext context, SuggestionRequest request, Executor executor) {
                CompletableFuture<SuggestionProviderResult> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("boom"));
                return future;
            }
        };
    }

    private static SuggestionProvider hangingProvider(String id) {
        return new SuggestionProvider() {
            @Override
            public SuggestionProviderDescriptor descriptor() {
                return descriptorOf(id);
            }

            @Override
            public boolean supports(ConsoleCommandContext context, SuggestionRequest request) {
                return true;
            }

            @Override
            public CompletableFuture<SuggestionProviderResult> fetchSuggestions(ConsoleCommandContext context, SuggestionRequest request, Executor executor) {
                return new CompletableFuture<>();
            }
        };
    }

    private static SuggestionProviderDescriptor descriptorOf(String id) {
        return new SuggestionProviderDescriptor(
                id,
                id,
                Set.of(SuggestionProviderCoverage.STATIC_FALLBACK),
                SuggestionProviderFreshness.STATIC,
                SuggestionProviderCost.LOW,
                true,
                false,
                Map.of()
        );
    }
}
