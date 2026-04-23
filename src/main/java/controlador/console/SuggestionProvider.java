package controlador.console;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Contrato asíncrono para proveedores de sugerencias.
 */
public interface SuggestionProvider {

    SuggestionProviderDescriptor descriptor();

    boolean supports(ConsoleCommandContext context, SuggestionRequest request);

    CompletableFuture<SuggestionProviderResult> fetchSuggestions(
            ConsoleCommandContext context,
            SuggestionRequest request,
            Executor executor
    );
}
