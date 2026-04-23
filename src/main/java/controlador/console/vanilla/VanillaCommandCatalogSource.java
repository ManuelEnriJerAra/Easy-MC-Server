package controlador.console.vanilla;

/**
 * Fuente automatizada de comandos vanilla root por version.
 */
public interface VanillaCommandCatalogSource {

    VanillaCommandCatalog resolve(String version);

    default VanillaCommandCatalog resolveCached(String version) {
        return resolve(version);
    }

    default void scheduleWarmup(String version) {
    }

    default boolean isWarmupPending(String version) {
        return false;
    }
}
