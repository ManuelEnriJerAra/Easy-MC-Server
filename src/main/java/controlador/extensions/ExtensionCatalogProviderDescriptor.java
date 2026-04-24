package controlador.extensions;

import modelo.extensions.ExtensionSourceType;

import java.util.Set;

public record ExtensionCatalogProviderDescriptor(
        String providerId,
        String displayName,
        ExtensionSourceType sourceType,
        Set<ExtensionCatalogCapability> capabilities
) {
}
