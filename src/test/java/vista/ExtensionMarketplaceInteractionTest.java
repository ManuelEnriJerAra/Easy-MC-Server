package vista;

import org.junit.jupiter.api.Test;

import java.awt.Point;

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
}
