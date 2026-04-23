package controlador.console;

/**
 * Frescura esperada de los datos aportados por un proveedor.
 */
public enum SuggestionProviderFreshness {
    LIVE,
    NEAR_REALTIME,
    SNAPSHOT,
    CACHED,
    STATIC
}
