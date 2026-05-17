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

final class MinecraftServerJarInspector {
    private static final String[] FORGE_ENTRY_MARKERS = {
            "net/minecraftforge/server/ServerMain.class",
            "net/minecraftforge/common/MinecraftForge.class",
            "net/minecraftforge/fml/loading/FMLLoader.class",
            "cpw/mods/modlauncher/Launcher.class"
    };
    private static final String[] NEOFORGE_ENTRY_MARKERS = {
            "net/neoforged/",
            "net/neoforged/neoforge/",
            "META-INF/neoforge.mods.toml"
    };
    private static final String[] FABRIC_ENTRY_MARKERS = {
            "fabric-server-launch.properties",
            "net/fabricmc/installer/ServerLauncher.class",
            "net/fabricmc/installer/server/ServerInstaller.class",
            "net/fabricmc/loader/",
            "net/fabricmc/loader/impl/launch/server/FabricServerLauncher.class",
            "net/fabricmc/loader/launch/knot/KnotServer.class"
    };
    private static final String[] QUILT_ENTRY_MARKERS = {
            "quilt-server-launch.properties",
            "quilt_installer.json",
            "org/quiltmc/loader/",
            "org/quiltmc/loader/impl/launch/server/QuiltServerLauncher.class"
    };
    private static final String[] PAPER_ENTRY_MARKERS = {
            "io/papermc/",
            "com/destroystokyo/paper/",
            "META-INF/services/io.papermc.paper.plugin.provider",
            "patch.properties"
    };
    private static final String[] PURPUR_ENTRY_MARKERS = {
            "org/purpurmc/",
            "META-INF/maven/org.purpurmc.purpur/"
    };
    private static final String[] PUFFERFISH_ENTRY_MARKERS = {
            "gg/pufferfish/",
            "META-INF/maven/gg.pufferfish/"
    };
    private static final String[] SPIGOT_ENTRY_MARKERS = {
            "org/spigotmc/"
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
                    String version = readVersionClass(in);
                    if (version != null && !version.isBlank()) {
                        return version;
                    }
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String readMinecraftVersionFromServerClass(Path jarPath) {
        if (jarPath == null) {
            return null;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry serverClass = jar.getJarEntry("net/minecraft/server/MinecraftServer.class");
            if (serverClass == null) {
                return null;
            }
            try (InputStream in = jar.getInputStream(serverClass)) {
                String version = readVersionClass(in);
                return version == null || version.isBlank() ? null : version;
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static boolean looksLikeForgeServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, FORGE_ENTRY_MARKERS);
    }

    static boolean looksLikeNeoForgeServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, NEOFORGE_ENTRY_MARKERS);
    }

    static boolean looksLikeFabricServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, FABRIC_ENTRY_MARKERS);
    }

    static boolean looksLikeQuiltServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, QUILT_ENTRY_MARKERS);
    }

    static boolean looksLikePaperServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, PAPER_ENTRY_MARKERS);
    }

    static boolean looksLikePurpurServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, PURPUR_ENTRY_MARKERS);
    }

    static boolean looksLikePufferfishServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, PUFFERFISH_ENTRY_MARKERS);
    }

    static boolean looksLikeSpigotServerJar(Path jarPath) {
        return jarContainsAnyMarker(jarPath, SPIGOT_ENTRY_MARKERS);
    }

    static boolean looksLikeBukkitServerJar(Path jarPath) {
        return jarContainsExactEntry(jarPath, "org/bukkit/craftbukkit/Main.class");
    }

    private static String readVersionJson(InputStream in) {
        try {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String idVersion = json.has("id")
                    ? extractMinecraftVersion(json.get("id").getAsString())
                    : null;
            String nameVersion = json.has("name")
                    ? extractMinecraftVersion(json.get("name").getAsString())
                    : null;
            if (idVersion != null && (nameVersion == null || idVersion.length() >= nameVersion.length())) {
                return idVersion;
            }
            return nameVersion != null ? nameVersion : idVersion;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readVersionClass(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        Matcher matcher = MinecraftVersionPatterns.VERSION_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractMinecraftVersion(String value) {
        return MinecraftVersionPatterns.extractMinecraftVersion(value);
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
