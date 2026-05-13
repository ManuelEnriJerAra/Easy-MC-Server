package vista;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstalledExtensionRowTextLayoutTest {
    @Test
    void givesUnusedMetadataSpaceBackToTheName() {
        InstalledExtensionRowTextLayout.Allocation allocation =
                InstalledExtensionRowTextLayout.allocateTitleWidths(420, 280, 60);

        assertThat(allocation.leadingWidth()).isEqualTo(280);
        assertThat(allocation.trailingWidth()).isEqualTo(60);
    }

    @Test
    void givesUnusedNameSpaceBackToMetadata() {
        InstalledExtensionRowTextLayout.Allocation allocation =
                InstalledExtensionRowTextLayout.allocateTitleWidths(420, 90, 360);

        assertThat(allocation.leadingWidth()).isEqualTo(90);
        assertThat(allocation.trailingWidth()).isGreaterThan(420 / 2);
        assertThat(allocation.leadingWidth() + allocation.trailingWidth() + InstalledExtensionRowTextLayout.TITLE_GAP)
                .isLessThanOrEqualTo(420);
    }

    @Test
    void preservesAReadableMetadataReserveWhenBothTextsOverflow() {
        InstalledExtensionRowTextLayout.Allocation allocation =
                InstalledExtensionRowTextLayout.allocateTitleWidths(320, 500, 260);

        assertThat(allocation.trailingWidth()).isGreaterThanOrEqualTo(72);
        assertThat(allocation.leadingWidth() + allocation.trailingWidth() + InstalledExtensionRowTextLayout.TITLE_GAP)
                .isLessThanOrEqualTo(320);
    }

    @Test
    void collapsesWhenNoTextWidthRemains() {
        InstalledExtensionRowTextLayout.Allocation allocation =
                InstalledExtensionRowTextLayout.allocateTitleWidths(0, 500, 260);

        assertThat(allocation.leadingWidth()).isZero();
        assertThat(allocation.trailingWidth()).isZero();
    }
}
