package controlador.extensions;

import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import java.util.Set;

public record ExtensionCatalogProviderDescriptor(
        String providerId,
        String displayName,
        ExtensionSourceType sourceType,
        Set<ExtensionCatalogCapability> capabilities,
        Set<ServerExtensionType> supportedExtensionTypes,
        Set<ServerPlatform> supportedPlatforms,
        String limitations
) {
    public ExtensionCatalogProviderDescriptor(String providerId,
                                              String displayName,
                                              ExtensionSourceType sourceType,
                                              Set<ExtensionCatalogCapability> capabilities) {
        this(providerId, displayName, sourceType, capabilities, Set.of(), Set.of(), null);
    }

    public ExtensionCatalogProviderDescriptor {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        supportedExtensionTypes = supportedExtensionTypes == null ? Set.of() : Set.copyOf(supportedExtensionTypes);
        supportedPlatforms = supportedPlatforms == null ? Set.of() : Set.copyOf(supportedPlatforms);
        limitations = limitations == null || limitations.isBlank() ? null : limitations.trim();
    }
}
