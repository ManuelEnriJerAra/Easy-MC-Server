package controlador.console.providers;

import controlador.console.HybridSuggestionService;
import controlador.console.SuggestionProvider;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Factories y utilidades para registrar el stack híbrido por defecto.
 */
public final class SuggestionProviders {
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "console-suggestions-worker");
        thread.setDaemon(true);
        return thread;
    });

    private SuggestionProviders() {
    }

    public static List<SuggestionProvider> defaultProviders() {
        return List.of(
                new LiveRuntimeSuggestionProvider(),
                new PlayerSuggestionProvider(),
                new VersionedVanillaSuggestionProvider(),
                new ModdedPluginSuggestionProvider(),
                new StaticFallbackSuggestionProvider()
        );
    }

    public static HybridSuggestionService defaultHybridService() {
        return new HybridSuggestionService(defaultProviders());
    }

    public static Executor defaultExecutor() {
        return DEFAULT_EXECUTOR;
    }
}
