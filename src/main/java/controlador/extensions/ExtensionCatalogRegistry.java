package controlador.extensions;

import java.util.List;

public final class ExtensionCatalogRegistry {
    private static final List<ExtensionCatalogProvider> PROVIDERS = List.of();

    private ExtensionCatalogRegistry() {
    }

    public static List<ExtensionCatalogProvider> all() {
        return PROVIDERS;
    }
}
