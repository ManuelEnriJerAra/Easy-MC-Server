package controlador.platform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftServerJarInspector {
    private static final Pattern VERSION_PATTERN = Pattern.compile("([0-9]{1,2}\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?)");

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
}
