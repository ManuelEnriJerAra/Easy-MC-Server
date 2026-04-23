package controlador.console;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas simples de latencia acumuladas en memoria.
 */
public final class SuggestionEngineMetrics {
    private final AtomicLong sampleCount = new AtomicLong();
    private final AtomicLong totalLatencyMillis = new AtomicLong();
    private final AtomicLong lastLatencyMillis = new AtomicLong();
    private final AtomicLong maxLatencyMillis = new AtomicLong();

    public void recordLatency(long latencyMillis) {
        long safeLatency = Math.max(0L, latencyMillis);
        sampleCount.incrementAndGet();
        totalLatencyMillis.addAndGet(safeLatency);
        lastLatencyMillis.set(safeLatency);
        maxLatencyMillis.accumulateAndGet(safeLatency, Math::max);
    }

    public SuggestionEngineMetricsSnapshot snapshot() {
        long count = sampleCount.get();
        long total = totalLatencyMillis.get();
        long last = lastLatencyMillis.get();
        long max = maxLatencyMillis.get();
        double average = count <= 0 ? 0d : (double) total / count;
        return new SuggestionEngineMetricsSnapshot(count, average, last, max);
    }
}
