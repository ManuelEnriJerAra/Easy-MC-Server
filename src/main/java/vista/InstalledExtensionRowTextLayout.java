package vista;

final class InstalledExtensionRowTextLayout {
    static final int TITLE_GAP = 10;
    static final int COMPACT_TITLE_GAP = 8;
    private static final int MIN_LEADING_WIDTH = 90;
    private static final int MIN_TRAILING_WIDTH = 72;

    private InstalledExtensionRowTextLayout() {
    }

    static Allocation allocateTitleWidths(int availableWidth, int leadingNaturalWidth, int trailingNaturalWidth) {
        return allocateTitleWidths(availableWidth, TITLE_GAP, leadingNaturalWidth, trailingNaturalWidth);
    }

    static Allocation allocateCompactTitleWidths(int availableWidth, int leadingNaturalWidth, int trailingNaturalWidth) {
        return allocateTitleWidths(availableWidth, COMPACT_TITLE_GAP, leadingNaturalWidth, trailingNaturalWidth);
    }

    static Allocation allocateTitleWidths(
            int availableWidth,
            int gap,
            int leadingNaturalWidth,
            int trailingNaturalWidth
    ) {
        int textWidth = Math.max(0, availableWidth - Math.max(0, gap));
        int leadingNatural = Math.max(0, leadingNaturalWidth);
        int trailingNatural = Math.max(0, trailingNaturalWidth);
        if (leadingNatural + trailingNatural <= textWidth) {
            return new Allocation(leadingNatural, trailingNatural);
        }
        if (textWidth <= 0) {
            return new Allocation(0, 0);
        }

        int trailingReserve = Math.min(trailingNatural, Math.min(MIN_TRAILING_WIDTH, textWidth));
        int leadingWidth = Math.min(leadingNatural, Math.max(0, textWidth - trailingReserve));
        int trailingWidth = Math.min(trailingNatural, Math.max(0, textWidth - leadingWidth));

        if (trailingWidth < trailingReserve && leadingWidth > 0) {
            int transfer = Math.min(trailingReserve - trailingWidth, leadingWidth);
            leadingWidth -= transfer;
            trailingWidth += transfer;
        }
        if (leadingWidth < Math.min(leadingNatural, MIN_LEADING_WIDTH) && trailingWidth > trailingReserve) {
            int needed = Math.min(leadingNatural, MIN_LEADING_WIDTH) - leadingWidth;
            int transfer = Math.min(needed, trailingWidth - trailingReserve);
            leadingWidth += transfer;
            trailingWidth -= transfer;
        }

        return new Allocation(leadingWidth, trailingWidth);
    }

    record Allocation(int leadingWidth, int trailingWidth) {
    }
}
