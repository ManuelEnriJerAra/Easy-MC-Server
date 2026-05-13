package vista;

import java.awt.FontMetrics;

public final class FolderPathLayout {
    private static final int EMPTY_FIELD_COLUMNS = 8;
    private static final int FIELD_TEXT_PADDING = 18;
    private static final int MIN_FIELD_WIDTH = 150;

    private FolderPathLayout() {
    }

    public static Bounds calculate(
            int availableTextWidth,
            int fullPrefixWidth,
            String folderName,
            FontMetrics folderNameMetrics
    ) {
        int available = Math.max(0, availableTextWidth);
        if (available == 0) {
            return new Bounds(0, 0);
        }

        int fieldWidth = calculateFieldWidth(available, folderName, folderNameMetrics);
        int maxPrefixWidth = Math.max(0, available - fieldWidth);
        int prefixWidth = Math.min(Math.max(0, fullPrefixWidth), maxPrefixWidth);
        return new Bounds(prefixWidth, Math.max(0, available - prefixWidth));
    }

    private static int calculateFieldWidth(int availableTextWidth, String folderName, FontMetrics metrics) {
        if (metrics == null) {
            return availableTextWidth;
        }
        String text = folderName == null ? "" : folderName;
        int emptyWidth = metrics.charWidth('m') * EMPTY_FIELD_COLUMNS + FIELD_TEXT_PADDING;
        int desiredWidth = text.isBlank()
                ? emptyWidth
                : metrics.stringWidth(text) + FIELD_TEXT_PADDING;
        int minWidth = Math.min(MIN_FIELD_WIDTH, availableTextWidth);
        return Math.min(availableTextWidth, Math.max(minWidth, desiredWidth));
    }

    public record Bounds(int prefixWidth, int fieldWidth) {
    }
}
