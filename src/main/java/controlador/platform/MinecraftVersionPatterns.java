package controlador.platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftVersionPatterns {
    static final String VERSION_PATTERN_BODY =
            "1\\.(?:0|[1-9][0-9]*)(?:\\.\\d+)?(?:-(?:snapshot|pre|rc)-?\\d+)?"
                    + "|[2-9]\\d*\\.\\d+(?:\\.\\d+)?(?:-(?:snapshot|pre|rc)-?\\d+)?"
                    + "|\\d{2}w\\d{2}[a-z]"
                    + "|[ab]\\d+\\.\\d+(?:\\.\\d+)?";
    static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9.])("
                    + VERSION_PATTERN_BODY
                    + ")(?![-a-z0-9.])"
    );

    private static final Pattern PLATFORM_SUFFIX_VERSION_PATTERN = Pattern.compile(
            "(?i)^(" + VERSION_PATTERN_BODY + ")-"
                    + "(?:forge|neoforge|fabric|quilt|paper|purpur|spigot|bukkit)(?:-|$)"
    );

    private MinecraftVersionPatterns() {
    }

    static String extractMinecraftVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        Matcher matcher = PLATFORM_SUFFIX_VERSION_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = VERSION_PATTERN.matcher(trimmed);
        return matcher.find() ? matcher.group(1) : null;
    }
}
