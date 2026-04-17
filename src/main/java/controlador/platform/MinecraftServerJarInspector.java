package controlador.platform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftServerJarInspector {
    private static final Pattern VERSION_PATTERN = Pattern.compile("([0-9]{1,2}\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?)");
    private static final String[] FORGE_ENTRY_MARKERS = {
            "net/minecraftforge/server/ServerMain.class",
            "net/minecraftforge/common/MinecraftForge.class",
            "net/minecraftforge/fml/loading/FMLLoader.class",
            "cpw/mods/modlauncher/Launcher.class"
    };
    private static final String[] PAPER_ENTRY_MARKERS = {
            "io/papermc/",
            "com/destroystokyo/paper/",
            "META-INF/services/io.papermc.paper.plugin.provider",
            "patch.properties"
    };

    private MinecraftServerJarInspector() {
    }

    static boolean looksLikeMinecraftServerJar(Path jarPath) {
        if (jarPath == null) {
            return false;
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return jarFile.getJarEntry("version.json") != null
                    || jarFile.getJarEntry("net/minecraft/server/MinecraftServer.class") != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static String readMinecraftVersion(Path jarPath) {
        if (jarPath == null) {
            return null;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry versionEntry = jar.getJarEntry("version.json");
            if (versionEntry != null) {
                try (InputStream in = jar.getInputStream(versionEntry)) {
                    String version = readVersionJson(in);
                    if (version != null && !version.isBlank()) {
                        return version;
                    }
                }
            }

            JarEntry serverClass = jar.getJarEntry("net/minecraft/server/MinecraftServer.class");
            if (serverClass != null) {
                try (InputStream in = jar.getInputStream(serverClass)) {
                    return readVersionClass(in);
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static boolean looksLikeForgeServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, FORGE_ENTRY_MARKERS);
    }

    static boolean looksLikePaperServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, PAPER_ENTRY_MARKERS)
                || jarContainsExactEntry(jarPath, "org/bukkit/craftbukkit/Main.class");
    }

    private static String readVersionJson(InputStream in) {
        try {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("name")) {
                return json.get("name").getAsString();
            }
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readVersionClass(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        Matcher matcher = VERSION_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean jarContainsAnyMarker(Path jarPath, String[] markers) {
        if (jarPath == null || markers == null || markers.length == 0) {
            return false;
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                for (String marker : markers) {
                    if (entryName.equals(marker) || entryName.contains(marker)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean jarContainsExactEntry(Path jarPath, String entryName) {
        if (jarPath == null || entryName == null || entryName.isBlank()) {
            return false;
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return jarFile.getJarEntry(entryName) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
