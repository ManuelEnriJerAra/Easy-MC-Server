package vista;

import java.awt.FontMetrics;

public final class TextEllipsizer {
    private static final String ELLIPSIS = "...";
    private static final String DOT = ".";

    private TextEllipsizer() {
    }

    public static String right(String text, FontMetrics metrics, int maxWidthPx) {
        String value = safe(text);
        if (metrics == null) {
            return value;
        }
        if (maxWidthPx <= 0) {
            return "";
        }
        if (metrics.stringWidth(value) <= maxWidthPx) {
            return value;
        }
        if (metrics.stringWidth(ELLIPSIS) >= maxWidthPx) {
            return ELLIPSIS;
        }
        return rightWithinWidth(value, metrics, maxWidthPx, ELLIPSIS, false);
    }

    public static String rightStrict(String text, FontMetrics metrics, int maxWidthPx) {
        String value = safe(text);
        if (metrics == null || maxWidthPx <= 0 || value.isEmpty()) {
            return value;
        }
        if (metrics.stringWidth(value) <= maxWidthPx) {
            return value;
        }
        int ellipsisWidth = metrics.stringWidth(ELLIPSIS);
        if (maxWidthPx <= ellipsisWidth) {
            return metrics.stringWidth(DOT) <= maxWidthPx ? DOT : "";
        }
        return rightWithinWidth(value, metrics, maxWidthPx, ELLIPSIS, true);
    }

    public static String left(String text, FontMetrics metrics, int maxWidthPx) {
        String value = safe(text);
        if (metrics == null) {
            return value;
        }
        if (maxWidthPx <= 0) {
            return "";
        }
        if (metrics.stringWidth(value) <= maxWidthPx) {
            return value;
        }
        int ellipsisWidth = metrics.stringWidth(ELLIPSIS);
        if (maxWidthPx <= ellipsisWidth) {
            return ELLIPSIS;
        }

        int available = maxWidthPx - ellipsisWidth;
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String suffix = value.substring(value.length() - mid);
            if (metrics.stringWidth(suffix) <= available) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return ELLIPSIS + value.substring(value.length() - low);
    }

    private static String rightWithinWidth(
            String value,
            FontMetrics metrics,
            int maxWidthPx,
            String ellipsis,
            boolean stripTrailing
    ) {
        int target = Math.max(0, maxWidthPx - metrics.stringWidth(ellipsis));
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String prefix = value.substring(0, mid);
            if (metrics.stringWidth(prefix) <= target) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        String prefix = value.substring(0, low);
        return (stripTrailing ? prefix.stripTrailing() : prefix) + ellipsis;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
