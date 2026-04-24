package controlador.extensions;

import controlador.GestorConfiguracion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ExtensionCatalogRegistry {
    private static final ExtensionCatalogRegistry DEFAULT = new ExtensionCatalogRegistry(List.of(
            new ModrinthExtensionCatalogProvider(),
            new CurseForgeExtensionCatalogProvider(GestorConfiguracion.getCurseForgeApiKey()),
            new HangarExtensionCatalogProvider()
    ));

    private final List<ExtensionCatalogProvider> providers;

    public ExtensionCatalogRegistry(List<ExtensionCatalogProvider> providers) {
        this.providers = providers == null
                ? List.of()
                : List.copyOf(providers.stream()
                .filter(Objects::nonNull)
                .toList());
    }

    public static ExtensionCatalogRegistry defaultRegistry() {
        return DEFAULT;
    }

    public static List<ExtensionCatalogProvider> all() {
        return DEFAULT.providers();
    }

    public List<ExtensionCatalogProvider> providers() {
        return providers;
    }

    public Optional<ExtensionCatalogProvider> findById(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return Optional.empty();
        }
        String normalized = providerId.trim().toLowerCase(Locale.ROOT);
        return providers.stream()
                .filter(provider -> normalized.equals(provider.getProviderId().trim().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public List<ExtensionCatalogProviderDescriptor> describeProviders() {
        List<ExtensionCatalogProviderDescriptor> descriptors = new ArrayList<>();
        for (ExtensionCatalogProvider provider : providers) {
            descriptors.add(provider.describeProvider());
        }
        return List.copyOf(descriptors);
    }
}
