package vista;

import controlador.extensions.ExtensionCatalogEntry;
import controlador.extensions.ExtensionInstallResolution;
import controlador.extensions.ExtensionCompatibilityStatus;

import java.util.List;

record MarketplaceEntryViewModel(
        ExtensionCatalogEntry entry,
        ExtensionCompatibilityStatus compatibilityStatus,
        String compatibilitySummary,
        List<String> compatibilityReasons,
        ExtensionInstallResolution installResolution,
        String queueStateText,
        String providerLabel,
        String platformsSummary,
        String versionsSummary,
        String descriptionPreview
) {
    String key() {
        if (entry == null) {
            return "";
        }
        String provider = entry.providerId() == null ? "" : entry.providerId().trim().toLowerCase();
        String project = entry.projectId() == null ? "" : entry.projectId().trim().toLowerCase();
        return provider + "::" + project;
    }
}
