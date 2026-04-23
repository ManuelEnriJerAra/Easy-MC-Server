package controlador.console;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionEngineTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void tokenizer_respects_quotes_spaces_and_slash_prefix() {
        CommandTokenizer tokenizer = new CommandTokenizer();

        ParsedCommandLine parsed = tokenizer.parse("/give \"Steve Jobs\" minec", "/give \"Steve Jobs\" minec".length());

        assertThat(parsed.slashPrefixed()).isTrue();
        assertThat(parsed.commandName()).isEqualTo("give");
        assertThat(parsed.tokens()).hasSize(3);
        assertThat(parsed.tokens().get(1).text()).isEqualTo("Steve Jobs");
        assertThat(parsed.tokens().get(1).quoted()).isTrue();
        assertThat(parsed.activeToken()).isEqualTo("minec");
        assertThat(parsed.argumentIndex()).isEqualTo(1);
    }

    @Test
    void tokenizer_detects_trailing_whitespace_as_next_argument_slot() {
        CommandTokenizer tokenizer = new CommandTokenizer();

        ParsedCommandLine parsed = tokenizer.parse("/give Steve ", "/give Steve ".length());

        assertThat(parsed.trailingWhitespace()).isTrue();
        assertThat(parsed.activeTokenIndex()).isEqualTo(-1);
        assertThat(parsed.activeToken()).isEmpty();
        assertThat(parsed.argumentIndex()).isEqualTo(1);
    }

    @Test
    void engine_identifies_expected_item_argument_for_give_command() {
        AtomicReference<SuggestionRequest> capturedRequest = new AtomicReference<>();
        SuggestionProvider capturingProvider = new CapturingProvider(capturedRequest, List.of(
                suggestion("item:diamond_sword", "minecraft:diamond_sword", SuggestionCategory.ITEM, 150, "test")
        ));
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(capturingProvider)));

        SuggestionEngineResult result = engine.suggest(
                "/give Steve minecraft:di",
                "/give Steve minecraft:di".length(),
                sampleContext(),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.expectedCategories()).containsExactlyInAnyOrder(SuggestionCategory.ITEM, SuggestionCategory.TAG);
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().requestedCategories()).containsExactlyInAnyOrder(SuggestionCategory.ITEM, SuggestionCategory.TAG);
        assertThat(capturedRequest.get().activeToken()).isEqualTo("minecraft:di");
        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("minecraft:diamond_sword");
    }

    @Test
    void engine_suppresses_random_suggestions_for_known_terminal_command_root() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(
                        suggestion("cmd:help", "help", SuggestionCategory.COMMAND, 200, "commands"),
                        suggestion("cmd:say", "say", SuggestionCategory.COMMAND, 200, "commands")
                ))
        )));

        SuggestionEngineResult result = engine.suggest("/say", "/say".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void engine_suppresses_random_suggestions_after_known_command_without_profile() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(
                        suggestion("cmd:help", "help", SuggestionCategory.COMMAND, 200, "commands"),
                        suggestion("cmd:give", "give", SuggestionCategory.COMMAND, 200, "commands")
                ))
        )));

        SuggestionEngineResult result = engine.suggest("/help ", "/help ".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void engine_suggests_players_for_give_after_command_is_completed() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(
                        suggestion("player:steve", "Steve", SuggestionCategory.PLAYER, 220, "players"),
                        suggestion("player:alex", "Alex", SuggestionCategory.PLAYER, 210, "players")
                ))
        )));

        SuggestionEngineResult result = engine.suggest("/give ", "/give ".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).containsExactlyInAnyOrder(SuggestionCategory.PLAYER);
        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("Steve", "Alex");
    }

    @Test
    void engine_suggests_players_for_give_even_without_trailing_space() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(
                        suggestion("player:steve", "Steve", SuggestionCategory.PLAYER, 220, "players"),
                        suggestion("player:alex", "Alex", SuggestionCategory.PLAYER, 210, "players")
                ))
        )));

        SuggestionEngineResult result = engine.suggest("give", "give".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).containsExactlyInAnyOrder(SuggestionCategory.PLAYER);
        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("Steve", "Alex");
        assertThat(result.parsedLine().activeToken()).isEmpty();
        assertThat(result.parsedLine().activeTokenStart()).isEqualTo("give".length());
    }

    @Test
    void engine_suggests_players_for_profiled_command_even_without_version_cache() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(
                        suggestion("player:steve", "Steve", SuggestionCategory.PLAYER, 220, "players")
                ))
        )));

        SuggestionEngineResult result = engine.suggest("give", "give".length(), contextWithoutVersion(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).containsExactly(SuggestionCategory.PLAYER);
        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("Steve");
    }

    @Test
    void engine_merges_deduplicates_and_orders_by_relevance() {
        Suggestion lowPriority = suggestion("cmd:give-low", "give", SuggestionCategory.COMMAND, 100, "provider-low");
        Suggestion highPriority = suggestion("cmd:give-high", "give", SuggestionCategory.COMMAND, 300, "provider-high");
        Suggestion second = suggestion("cmd:gamerule", "gamerule", SuggestionCategory.COMMAND, 200, "provider-high");

        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(lowPriority)),
                new StaticProvider(List.of(highPriority, second))
        )));

        SuggestionEngineResult result = engine.suggest("/g", 2, sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText)
                .contains("give", "gamerule");
        assertThat(result.suggestions().stream()
                .filter(suggestion -> suggestion.insertText().equals("give"))
                .count()).isEqualTo(1);
        assertThat(result.suggestions().get(0).priority()).isEqualTo(300);
    }

    @Test
    void tab_completion_uses_common_prefix_when_multiple_suggestions_share_it() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(
                        suggestion("player:steve", "Steve", SuggestionCategory.PLAYER, 220, "players"),
                        suggestion("player:steve123", "Steve123", SuggestionCategory.PLAYER, 210, "players")
                ))
        )));

        SuggestionEngineResult result = engine.suggest("/tp Ste", "/tp Ste".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.tabCompletionPlan().mode()).isEqualTo(TabCompletionPlan.Mode.APPLY_COMMON_PREFIX);
        assertThat(result.tabCompletionPlan().replacementText()).isEqualTo("Steve");
        assertThat(result.tabCompletionPlan().completionSuffix()).isEqualTo("ve");
    }

    @Test
    void tab_completion_applies_selected_suggestion_when_no_extended_common_prefix_exists() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(
                new StaticProvider(List.of(
                        suggestion("player:steve", "Steve", SuggestionCategory.PLAYER, 250, "players"),
                        suggestion("player:alex", "Alex", SuggestionCategory.PLAYER, 240, "players")
                ))
        )));

        SuggestionEngineResult result = engine.suggest("/give ", "/give ".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("Steve", "Alex");
        assertThat(result.tabCompletionPlan().mode()).isEqualTo(TabCompletionPlan.Mode.APPLY_SELECTION);
        assertThat(result.tabCompletionPlan().replacementText()).isEqualTo("Steve");
    }

    @Test
    void engine_identifies_player_target_for_permissions_commands_via_family_profile() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of()));

        SuggestionEngineResult result = engine.suggest("ban St", "ban St".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).containsExactly(SuggestionCategory.PLAYER);
    }

    @Test
    void engine_identifies_entity_hint_for_summon_family_profile() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of()));

        SuggestionEngineResult result = engine.suggest("summon mine", "summon mine".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).containsExactlyInAnyOrder(SuggestionCategory.ENTITY, SuggestionCategory.TAG);
    }

    @Test
    void engine_identifies_world_coordinate_hint_for_setblock_family_profile() {
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of()));

        SuggestionEngineResult result = engine.suggest("setblock 10 64 ", "setblock 10 64 ".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(result.expectedCategories()).containsExactly(SuggestionCategory.COORDINATE);
    }

    @Test
    void engine_exposes_family_metadata_in_request() {
        AtomicReference<SuggestionRequest> capturedRequest = new AtomicReference<>();
        SuggestionProvider capturingProvider = new CapturingProvider(capturedRequest, List.of());
        SuggestionEngine engine = new SuggestionEngine(new HybridSuggestionService(List.of(capturingProvider)));

        engine.suggest("whitelist a", "whitelist a".length(), sampleContext(), DIRECT_EXECUTOR).join();

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().metadata()).containsEntry("commandFamily", "PERMISSIONS_AND_MODERATION");
        assertThat(capturedRequest.get().metadata()).containsEntry("commandHintProfileId", "permissions-moderation");
    }

    private static ConsoleCommandContext sampleContext() {
        return new ConsoleCommandContext(
                "server-1",
                "Servidor Test",
                "C:/servers/test",
                "1.21.1",
                "VANILLA",
                true,
                Set.of("Steve"),
                Set.of("Steve", "Alex"),
                Set.of("minecraft:overworld"),
                Set.of(),
                Set.of(),
                Set.of(),
                "fingerprint",
                java.time.Instant.now(),
                Map.of(),
                Map.of()
        );
    }

    private static ConsoleCommandContext contextWithoutVersion() {
        return new ConsoleCommandContext(
                "server-1",
                "Servidor Test",
                "C:/servers/test",
                "",
                "JAVA",
                true,
                Set.of("Steve"),
                Set.of("Steve"),
                Set.of("minecraft:overworld"),
                Set.of(),
                Set.of(),
                Set.of(),
                "fingerprint",
                java.time.Instant.now(),
                Map.of(),
                Map.of()
        );
    }

    private static Suggestion suggestion(String id, String insertText, SuggestionCategory category, int priority, String source) {
        return new Suggestion(
                id,
                insertText,
                insertText,
                "",
                category,
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

    private static class StaticProvider implements SuggestionProvider {
        private final List<Suggestion> suggestions;

        private StaticProvider(List<Suggestion> suggestions) {
            this.suggestions = List.copyOf(suggestions);
        }

        @Override
        public SuggestionProviderDescriptor descriptor() {
            return new SuggestionProviderDescriptor(
                    "static-test",
                    "Static test",
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
    }

    private static final class CapturingProvider extends StaticProvider {
        private final AtomicReference<SuggestionRequest> capturedRequest;

        private CapturingProvider(AtomicReference<SuggestionRequest> capturedRequest, List<Suggestion> suggestions) {
            super(suggestions);
            this.capturedRequest = capturedRequest;
        }

        @Override
        public CompletableFuture<SuggestionProviderResult> fetchSuggestions(ConsoleCommandContext context, SuggestionRequest request, Executor executor) {
            capturedRequest.set(request);
            return super.fetchSuggestions(context, request, executor);
        }
    }
}
