package controlador.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftServerVersionDetector {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9.])("
                    + "1\\.(?:0|[1-9][0-9]*)(?:\\.\\d+)?(?:-(?:pre|rc)\\d+)?"
                    + "|[2-9]\\d*\\.\\d+(?:\\.\\d+)?(?:-(?:pre|rc)\\d+)?"
                    + "|\\d{2}w\\d{2}[a-z]"
                    + "|[ab]\\d+\\.\\d+(?:\\.\\d+)?"
                    + ")(?![a-z0-9.])"
    );
    private static final Pattern FORGE_COORDINATE_PATTERN = Pattern.compile(
            "(?i)(?:net/minecraftforge/forge/|--fml\\.mcversion\\s+)(1\\.(?:0|[1-9][0-9]*)(?:\\.\\d+)?)(?:-|\\s|$)"
    );
    private static final Pattern NEOFORGE_COORDINATE_PATTERN = Pattern.compile(
            "(?i)(?:net/neoforged/neoforge/|--fml\\.neoforgeversion\\s+)(\\d+\\.\\d+\\.\\d+(?:[-+][a-z0-9.]+)?)"
    );
    private static final List<String> VERSION_HINT_FILES = List.of(
            "version_history.json",
            "versions.json",
            "manifest.json",
            "minecraftinstance.json",
            "mmc-pack.json",
            "instance.cfg",
            "server-setup-config.yaml",
            "server-setup-config.yml",
            "fabric-server-launcher.properties",
            "fabric-server-launch.properties",
            "quilt-server-launch.properties",
            "quilt_installer.json",
            "unix_args.txt",
            "win_args.txt",
            "user_jvm_args.txt",
            "run.sh",
            "run.bat",
            "latest.log",
            "debug.log"
    );
    private static final List<String> VERSION_KEYS = List.of(
            "minecraftversion",
            "minecraft_version",
            "mcversion",
            "mc_version",
            "gameversion",
            "game_version",
            "targetversion",
            "target_version",
            "version"
    );

    private MinecraftServerVersionDetector() {
    }

    static String detect(Path serverDir, Path executableJar) {
        String version = firstNonBlank(
                MinecraftServerJarInspector.readMinecraftVersion(executableJar),
                detectFromMetadataFiles(serverDir),
                detectFromAllTopLevelJars(serverDir)
        );
        return version == null ? null : version.trim();
    }

    private static String detectFromAllTopLevelJars(Path serverDir) {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return null;
        }
        try (var stream = Files.list(serverDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(MinecraftServerJarInspector::readMinecraftVersion)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String detectFromMetadataFiles(Path serverDir) {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return null;
        }
        try (var paths = Files.walk(serverDir, 6)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(MinecraftServerVersionDetector::isVersionHintFile)
                    .sorted(Comparator.comparingInt(MinecraftServerVersionDetector::pathPriority))
                    .map(MinecraftServerVersionDetector::detectFromMetadataFile)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isVersionHintFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return VERSION_HINT_FILES.contains(name);
    }

    private static int pathPriority(Path path) {
        String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int index = VERSION_HINT_FILES.indexOf(name);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static String detectFromMetadataFile(Path path) {
        try {
            if (Files.size(path) > 512_000L) {
                return null;
            }
            String text = Files.readString(path);
            String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".json")) {
                return detectFromJson(text);
            }
            if (lowerName.endsWith(".properties") || lowerName.endsWith(".cfg")) {
                String version = detectFromProperties(text);
                if (version != null) {
                    return version;
                }
            }
            return detectFromContextText(text);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String detectFromJson(String text) {
        try {
            JsonElement root = JsonParser.parseString(text);
            return detectFromJsonElement(root, "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String detectFromJsonElement(JsonElement element, String path) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String multiMcVersion = detectMultiMcMinecraftComponent(object);
            if (multiMcVersion != null) {
                return multiMcVersion;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey();
                String childPath = path.isBlank() ? key : path + "." + key;
                String direct = detectDirectJsonVersion(key, entry.getValue(), childPath);
                if (direct != null) {
                    return direct;
                }
                String nested = detectFromJsonElement(entry.getValue(), childPath);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String nested = detectFromJsonElement(child, path);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static String detectMultiMcMinecraftComponent(JsonObject object) {
        JsonElement uid = object.get("uid");
        JsonElement version = object.get("version");
        if (uid != null && version != null && uid.isJsonPrimitive() && version.isJsonPrimitive()) {
            String uidValue = uid.getAsString();
            if ("net.minecraft".equalsIgnoreCase(uidValue) || "net.minecraft.server".equalsIgnoreCase(uidValue)) {
                return normalizeVersion(version.getAsString());
            }
        }
        return null;
    }

    private static String detectDirectJsonVersion(String key, JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        String normalizedKey = normalizeKey(key);
        String normalizedPath = normalizeKey(path);
        if (!VERSION_KEYS.contains(normalizedKey)) {
            return null;
        }
        return normalizeVersion(value.getAsString());
    }

    private static String detectFromProperties(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new java.io.StringReader(text));
            for (String key : properties.stringPropertyNames()) {
                String normalizedKey = normalizeKey(key);
                if (VERSION_KEYS.contains(normalizedKey) || containsMinecraftContext(normalizedKey)) {
                    String version = normalizeVersion(properties.getProperty(key));
                    if (version != null) {
                        return version;
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }

    private static String detectFromContextText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replace('\\', '/');
        String forgeVersion = extractForgeCoordinateVersion(normalized);
        if (forgeVersion != null) {
            return forgeVersion;
        }
        String neoForgeVersion = extractNeoForgeCoordinateVersion(normalized);
        if (neoForgeVersion != null) {
            return neoForgeVersion;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        Matcher matcher = VERSION_PATTERN.matcher(normalized);
        while (matcher.find()) {
            int start = Math.max(0, matcher.start() - 96);
            int end = Math.min(normalized.length(), matcher.end() + 96);
            String context = lower.substring(start, end);
            if (containsMinecraftContext(context) || containsLogVersionContext(context)) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractForgeCoordinateVersion(String text) {
        Matcher matcher = FORGE_COORDINATE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractNeoForgeCoordinateVersion(String text) {
        Matcher matcher = NEOFORGE_COORDINATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return NeoForgeRepositoryClient.inferMinecraftVersion(matcher.group(1));
    }

    private static boolean containsMinecraftContext(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("minecraft")
                || lower.contains("mcversion")
                || lower.contains("mc_version")
                || lower.contains("gameversion")
                || lower.contains("game_version")
                || lower.contains("fml.mcversion")
                || lower.contains("net/minecraft/")
                || lower.contains("net.minecraft");
    }

    private static boolean containsLogVersionContext(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("starting minecraft server version")
                || lower.contains("minecraft server version")
                || lower.contains("server version");
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String normalizeVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(value.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
