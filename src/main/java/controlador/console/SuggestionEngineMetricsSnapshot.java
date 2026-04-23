package controlador.console;

/**
 * Snapshot simple de métricas de latencia del autocompletado.
 */
public record SuggestionEngineMetricsSnapshot(
        long sampleCount,
        double averageLatencyMillis,
        long lastLatencyMillis,
        long maxLatencyMillis
) {
}
