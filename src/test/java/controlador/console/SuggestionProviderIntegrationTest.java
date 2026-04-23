package controlador.console;

import controlador.console.providers.ModdedPluginSuggestionProvider;
import controlador.console.providers.PlayerSuggestionProvider;
import controlador.console.providers.StaticFallbackSuggestionProvider;
import controlador.console.providers.VersionedVanillaSuggestionProvider;
import controlador.console.vanilla.VanillaCatalogService;
import controlador.console.vanilla.VanillaCommandCatalog;
import controlador.console.vanilla.VanillaCommandCatalogSource;
import org.junit.jupiter.api.Test;
import support.ConsoleSuggestionFixtures;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionProviderIntegrationTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void vanillaProviderReturnsVersionedCommandsFor1211() {
        VersionedVanillaSuggestionProvider provider = new VersionedVanillaSuggestionProvider(
                new VanillaCatalogService(new controlador.console.vanilla.VanillaCatalogRepository(), commandSource("1.21.1",
                        List.of("gamemode", "gamerule", "give", "help")))
        );

        SuggestionProviderResult result = provider.fetchSuggestions(
                ConsoleSuggestionFixtures.vanilla1211(),
                SuggestionRequest.ofToken("ga", java.util.Set.of(SuggestionCategory.COMMAND)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText)
                .contains("gamemode", "gamerule");
    }

    @Test
    void vanillaProviderExposesExpandedOfficialRootCommandsFor1211() {
        VersionedVanillaSuggestionProvider provider = new VersionedVanillaSuggestionProvider(
                new VanillaCatalogService(new controlador.console.vanilla.VanillaCatalogRepository(), commandSource("1.21.1",
                        List.of("defaultgamemode", "save-off", "save-on", "transfer")))
        );

        SuggestionProviderResult result = provider.fetchSuggestions(
                ConsoleSuggestionFixtures.vanilla1211(),
                SuggestionRequest.ofToken("def", java.util.Set.of(SuggestionCategory.COMMAND)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText)
                .contains("defaultgamemode");
    }

    @Test
    void vanillaProviderReturnsBlocksWhenBlockCategoryIsRequested() {
        VersionedVanillaSuggestionProvider provider = new VersionedVanillaSuggestionProvider(
                new VanillaCatalogService(new controlador.console.vanilla.VanillaCatalogRepository(), commandSource("1.21.1",
                        List.of("setblock", "fill")))
        );

        SuggestionProviderResult result = provider.fetchSuggestions(
                ConsoleSuggestionFixtures.vanilla1211(),
                SuggestionRequest.ofToken("minecraft:sto", java.util.Set.of(SuggestionCategory.BLOCK)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText)
                .contains("minecraft:stone");
    }

    @Test
    void playerProviderCombinesOnlineAndKnownPlayers() {
        PlayerSuggestionProvider provider = new PlayerSuggestionProvider();

        SuggestionProviderResult result = provider.fetchSuggestions(
                ConsoleSuggestionFixtures.paperPluginServer(),
                SuggestionRequest.ofToken("Ad", java.util.Set.of(SuggestionCategory.PLAYER)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText)
                .contains("Admin");
    }

    @Test
    void moddedProviderExposesPartialButUsefulSuggestions() {
        ModdedPluginSuggestionProvider provider = new ModdedPluginSuggestionProvider();

        SuggestionProviderResult result = provider.fetchSuggestions(
                ConsoleSuggestionFixtures.fabricModded(),
                SuggestionRequest.ofToken("kube", java.util.Set.of(SuggestionCategory.ENTITY, SuggestionCategory.COMMAND)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.degraded()).isTrue();
        assertThat(result.partialCoverage()).isTrue();
    }

    @Test
    void fallbackProviderStillReturnsEmergencyCommands() {
        StaticFallbackSuggestionProvider provider = new StaticFallbackSuggestionProvider();

        SuggestionProviderResult result = provider.fetchSuggestions(
                ConsoleSuggestionFixtures.vanilla1204(),
                SuggestionRequest.ofToken("sa", java.util.Set.of(SuggestionCategory.COMMAND)),
                DIRECT_EXECUTOR
        ).join();

        assertThat(result.suggestions()).extracting(Suggestion::insertText).contains("save-all", "say");
    }

    private static VanillaCommandCatalogSource commandSource(String version, List<String> commands) {
        return requestedVersion -> new VanillaCommandCatalog(
                requestedVersion,
                requestedVersion.equals(version) ? commands : List.of(),
                false,
                "test-source",
                Instant.now(),
                Map.of()
        );
    }
}
