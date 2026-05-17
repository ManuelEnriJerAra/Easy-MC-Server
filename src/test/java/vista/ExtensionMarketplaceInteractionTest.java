package vista;

import controlador.extensions.ExtensionCatalogEntry;
import controlador.extensions.ExtensionCatalogQuery;
import controlador.extensions.ExtensionCompatibilityStatus;
import controlador.extensions.ExtensionSideFilter;
import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionMarketplaceInteractionTest {
    @Test
    void relaxedClickAllowsSmallMouseMovement() {
        assertThat(ExtensionMarketplaceDialog.isRelaxedClick(new Point(120, 40), new Point(132, 52))).isTrue();
    }

    @Test
    void relaxedClickRejectsDragMovement() {
        assertThat(ExtensionMarketplaceDialog.isRelaxedClick(new Point(120, 40), new Point(150, 52))).isFalse();
    }

    @Test
    void trailingHitZoneCoversTheVisibleResultActionArea() {
        assertThat(ExtensionMarketplaceDialog.isTrailingHitZone(430, 500, 72)).isTrue();
        assertThat(ExtensionMarketplaceDialog.isTrailingHitZone(427, 500, 72)).isFalse();
    }

    @Test
    void marketplaceFallsBackToPlatformWhenEcosystemIsUnknown() {
        Server server = new Server();
        server.setPlatform(ServerPlatform.FORGE);
        server.setEcosystemType(ServerEcosystemType.UNKNOWN);

        assertThat(ExtensionMarketplaceDialog.ecosystemOf(server)).isEqualTo(ServerEcosystemType.MODS);
        assertThat(ExtensionMarketplaceDialog.extensionTypeFor(server)).isEqualTo(ServerExtensionType.MOD);
    }

    @Test
    void defaultMarketplaceSearchKeepsWarningRowsForDetailReview() {
        MarketplaceSearchSpec spec = new MarketplaceSearchSpec(
                new ExtensionCatalogQuery(
                        "",
                        ServerPlatform.FABRIC,
                        ServerExtensionType.MOD,
                        "26.1.2",
                        20,
                        "downloads",
                        ExtensionSideFilter.ANY,
                        "modrinth"
                ),
                ProviderFilterOption.provider("modrinth", "Modrinth"),
                SideFilterOption.any(),
                true,
                new SearchSortOption("Descargas", "downloads", false),
                20,
                20
        );

        assertThat(ExtensionMarketplaceDialog.shouldDisplaySearchResult(
                spec,
                viewModel(ExtensionCompatibilityStatus.WARNING)
        )).isTrue();
        assertThat(ExtensionMarketplaceDialog.shouldDisplaySearchResult(
                spec,
                viewModel(ExtensionCompatibilityStatus.INCOMPATIBLE)
        )).isFalse();
    }

    @Test
    void metadataPendingWarningsDoNotShowCompatibilityBadge() {
        MarketplaceCompatibilityAssessment assessment = new MarketplaceCompatibilityAssessment(
                ExtensionCompatibilityStatus.WARNING,
                "El proveedor no declara loaders o plataformas compatibles.",
                List.of(
                        "El proveedor no declara loaders o plataformas compatibles.",
                        "El proveedor no declara versiones de Minecraft compatibles."
                )
        );

        assertThat(ExtensionMarketplaceDialog.isMetadataPendingWarning(assessment)).isTrue();
        assertThat(ExtensionMarketplaceDialog.shouldShowCompatibilityBadge(assessment)).isFalse();
    }

    @Test
    void actualWarningsStillAskForReview() {
        MarketplaceCompatibilityAssessment assessment = new MarketplaceCompatibilityAssessment(
                ExtensionCompatibilityStatus.WARNING,
                "La compatibilidad solo coincide por familia de version (26.1).",
                List.of("La compatibilidad solo coincide por familia de version (26.1).")
        );

        assertThat(ExtensionMarketplaceDialog.isMetadataPendingWarning(assessment)).isFalse();
        assertThat(ExtensionMarketplaceDialog.shouldShowCompatibilityBadge(assessment)).isTrue();
        assertThat(ExtensionMarketplaceDialog.compatibilityBadgeText(assessment)).isEqualTo("Revisar");
    }

    @Test
    void typedMarketplaceSearchCanShowIncompatibleRowsForUserReview() {
        MarketplaceSearchSpec spec = new MarketplaceSearchSpec(
                new ExtensionCatalogQuery(
                        "sodium",
                        ServerPlatform.UNKNOWN,
                        ServerExtensionType.MOD,
                        "",
                        20,
                        "downloads",
                        ExtensionSideFilter.ANY,
                        "modrinth"
                ),
                ProviderFilterOption.provider("modrinth", "Modrinth"),
                SideFilterOption.any(),
                true,
                new SearchSortOption("Descargas", "downloads", false),
                20,
                20
        );

        assertThat(ExtensionMarketplaceDialog.shouldDisplaySearchResult(
                spec,
                viewModel(ExtensionCompatibilityStatus.INCOMPATIBLE)
        )).isTrue();
    }

    private MarketplaceEntryViewModel viewModel(ExtensionCompatibilityStatus status) {
        ExtensionCatalogEntry entry = new ExtensionCatalogEntry(
                "modrinth",
                "fabric-api",
                null,
                "Fabric API",
                "modmuss50",
                null,
                "Fabric hooks",
                ExtensionSourceType.MODRINTH,
                ServerExtensionType.MOD,
                Set.of(),
                Set.of(),
                null,
                null,
                null,
                1L
        );
        return new MarketplaceEntryViewModel(
                entry,
                status,
                "Compatibilidad pendiente",
                List.of("El proveedor requiere cargar detalles."),
                null,
                null,
                "Modrinth",
                "-",
                "-",
                "Fabric hooks",
                false
        );
    }
}
