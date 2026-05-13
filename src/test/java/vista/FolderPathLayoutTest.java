package vista;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.FontMetrics;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

class FolderPathLayoutTest {
    private final JLabel metricsSource = new JLabel();
    private final FontMetrics metrics = metricsSource.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

    @Test
    void longFolderNameConsumesAllAvailablePathEditorSpace() {
        String folderName = "servidor-supervivencia-con-un-nombre-extremadamente-largo";
        int availableWidth = metrics.stringWidth(folderName) / 2;
        int fullPrefixWidth = metrics.stringWidth("C:/Users/Manuel/Minecraft/Servers/");

        FolderPathLayout.Bounds bounds = FolderPathLayout.calculate(
                availableWidth,
                fullPrefixWidth,
                folderName,
                metrics
        );

        assertThat(bounds.prefixWidth()).isZero();
        assertThat(bounds.fieldWidth()).isEqualTo(availableWidth);
    }

    @Test
    void shortFolderNameLeavesSpaceForLeftEllipsizedParentPrefix() {
        String folderName = "Survival";
        int fullPrefixWidth = metrics.stringWidth("C:/Users/Manuel/Minecraft/Servers/");
        int availableWidth = 150 + (fullPrefixWidth / 2);

        FolderPathLayout.Bounds bounds = FolderPathLayout.calculate(
                availableWidth,
                fullPrefixWidth,
                folderName,
                metrics
        );

        assertThat(bounds.prefixWidth()).isPositive();
        assertThat(bounds.prefixWidth()).isLessThan(fullPrefixWidth);
        assertThat(bounds.prefixWidth() + bounds.fieldWidth()).isEqualTo(availableWidth);
    }

    @Test
    void emptyFolderNameKeepsMinimumEditableFieldVisible() {
        int availableWidth = 120;

        FolderPathLayout.Bounds bounds = FolderPathLayout.calculate(
                availableWidth,
                metrics.stringWidth("C:/Servers/"),
                "",
                metrics
        );

        assertThat(bounds.fieldWidth()).isEqualTo(availableWidth);
        assertThat(bounds.prefixWidth()).isZero();
    }
}
