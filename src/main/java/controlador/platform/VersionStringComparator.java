package controlador.platform;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionStringComparator {
    private static final Pattern WEEKLY_SNAPSHOT_PATTERN = Pattern.compile("(?i)^(\\d{2})w\\d{2}[a-z]$");

    private VersionStringComparator() {
    }

    static Comparator<String> descending() {
        return (left, right) -> compareVersionStrings(right, left);
    }

    static Comparator<String> minecraftVersionsDescending() {
        return (left, right) -> compareMinecraftVersionStrings(right, left);
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

    private static int compareMinecraftVersionStrings(String left, String right) {
        String leftBase = stableReleaseBase(left);
        String rightBase = stableReleaseBase(right);
        int baseCompare = compareVersionStrings(leftBase, rightBase);
        if (baseCompare != 0) {
            return baseCompare;
        }

        boolean leftRelease = ServerCreationOption.VERSION_TYPE_RELEASE.equals(ServerCreationOption.versionTypeFromText(left));
        boolean rightRelease = ServerCreationOption.VERSION_TYPE_RELEASE.equals(ServerCreationOption.versionTypeFromText(right));
        if (leftRelease != rightRelease) {
            return leftRelease ? 1 : -1;
        }
        return compareVersionStrings(left, right);
    }

    private static String stableReleaseBase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String canonical = ServerCreationOption.canonicalMinecraftVersion(value);
        Matcher weeklySnapshot = WEEKLY_SNAPSHOT_PATTERN.matcher(canonical);
        if (weeklySnapshot.matches()) {
            return Integer.parseInt(weeklySnapshot.group(1)) + ".0";
        }
        return canonical
                .replaceFirst("(?i)-(snapshot|pre|rc)-?\\d+$", "");
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
