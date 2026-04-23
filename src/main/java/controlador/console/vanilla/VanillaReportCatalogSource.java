package controlador.console.vanilla;

import java.util.Optional;

/**
 * Fuente de dataset vanilla completo por version.
 */
public interface VanillaReportCatalogSource {

    Optional<VanillaCatalogVersion> resolve(String version);

    default Optional<VanillaCatalogVersion> resolveCached(String version) {
        return resolve(version);
    }

    default void scheduleWarmup(String version) {
    }

    default boolean isWarmupPending(String version) {
        return false;
    }
}
