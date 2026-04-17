package controlador.platform;

import java.util.Comparator;

final class VersionStringComparator {
    private VersionStringComparator() {
    }

    static Comparator<String> descending() {
        return (left, right) -> compareVersionStrings(right, left);
    }

    static int compareVersionStrings(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        int i = 0;
        int j = 0;
        while (i < a.length() || j < b.length()) {
            String tokenA = nextToken(a, i);
            String tokenB = nextToken(b, j);

            if (tokenA == null) return tokenB == null ? 0 : -1;
            if (tokenB == null) return 1;

            int compare;
            if (isNumeric(tokenA) && isNumeric(tokenB)) {
                compare = Integer.compare(Integer.parseInt(tokenA), Integer.parseInt(tokenB));
            } else {
                compare = tokenA.compareToIgnoreCase(tokenB);
            }
            if (compare != 0) {
                return compare;
            }

            i += tokenA.length();
            j += tokenB.length();
            while (i < a.length() && !Character.isLetterOrDigit(a.charAt(i))) i++;
            while (j < b.length() && !Character.isLetterOrDigit(b.charAt(j))) j++;
        }
        return 0;
    }

    private static String nextToken(String value, int start) {
        int index = start;
        while (index < value.length() && !Character.isLetterOrDigit(value.charAt(index))) {
            index++;
        }
        if (index >= value.length()) {
            return null;
        }
        int end = index + 1;
        boolean numeric = Character.isDigit(value.charAt(index));
        while (end < value.length() && Character.isLetterOrDigit(value.charAt(end))
                && Character.isDigit(value.charAt(end)) == numeric) {
            end++;
        }
        return value.substring(index, end);
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
