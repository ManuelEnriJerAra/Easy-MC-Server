package controlador.extensions;

import java.util.List;
import java.util.Set;

public record ExtensionCatalogDetails(
        ExtensionCatalogEntry entry,
        String summary,
        String websiteUrl,
        String issuesUrl,
        String licenseName,
        Set<String> categories,
        List<ExtensionCatalogVersion> versions
) {
}
