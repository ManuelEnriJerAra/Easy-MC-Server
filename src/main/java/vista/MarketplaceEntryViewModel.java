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
        String descriptionPreview,
        boolean loadMoreRow
) {
    static MarketplaceEntryViewModel loadMoreRow(String statusText) {
        return new MarketplaceEntryViewModel(
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                statusText,
                true
        );
    }

    String key() {
        if (loadMoreRow) {
            return "__load_more__";
        }
        if (entry == null) {
            return "";
        }
        String provider = entry.providerId() == null ? "" : entry.providerId().trim().toLowerCase();
        String project = entry.projectId() == null ? "" : entry.projectId().trim().toLowerCase();
        return provider + "::" + project;
    }
}
