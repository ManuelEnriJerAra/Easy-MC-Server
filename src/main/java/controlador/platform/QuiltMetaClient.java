package controlador.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class QuiltMetaClient {
    private static final String BASE_URL = "https://meta.quiltmc.org/v3/versions";
    private static final String PLATFORM_VERSION_SEPARATOR = "|";
    private final PlatformHttpClient httpClient;

    QuiltMetaClient() {
        this(new UrlConnectionPlatformHttpClient());
    }

    QuiltMetaClient(PlatformHttpClient httpClient) {
        this.httpClient = new CachedPlatformHttpClient(httpClient);
    }

    List<ServerCreationOption> listCreationOptions() throws IOException {
        String loaderVersion = latestStableVersion(BASE_URL + "/loader");
        QuiltInstaller installer = latestInstaller();
        List<QuiltGameVersion> gameVersions = gameVersions();
        List<ServerCreationOption> options = new ArrayList<>();
        for (QuiltGameVersion gameVersion : gameVersions) {
            String minecraftVersion = gameVersion.version();
            options.add(new ServerCreationOption(
                    ServerPlatform.QUILT,
                    minecraftVersion,
                    encodePlatformVersion(loaderVersion, installer.version()),
                    "Minecraft " + minecraftVersion + " (Quilt Loader " + loaderVersion + ")",
                    "quilt-" + minecraftVersion + "-server",
                    ServerCreationOption.versionTypeFromStability(gameVersion.stable())
            ));
        }
        return options;
    }

    String installerUrl(String platformVersion) throws IOException {
        QuiltSelection selection = decodePlatformVersion(platformVersion);
        if (selection.installerVersion() == null || selection.installerVersion().isBlank()) {
            return latestInstaller().url();
        }
        JsonArray installers = httpClient.getJson(BASE_URL + "/installer").getAsJsonArray();
        for (JsonElement element : installers) {
            JsonObject object = element.getAsJsonObject();
            if (selection.installerVersion().equals(stringValue(object, "version"))) {
                return stringValue(object, "url");
            }
        }
        throw new IOException("No se ha encontrado el instalador de Quilt " + selection.installerVersion() + ".");
    }

    static String encodePlatformVersion(String loaderVersion, String installerVersion) {
        return (loaderVersion == null ? "" : loaderVersion) + PLATFORM_VERSION_SEPARATOR
                + (installerVersion == null ? "" : installerVersion);
    }

    static QuiltSelection decodePlatformVersion(String platformVersion) {
        if (platformVersion == null) {
            return new QuiltSelection(null, null);
        }
        String[] parts = platformVersion.split("\\|", 2);
        if (parts.length == 1) {
            return new QuiltSelection(parts[0], null);
        }
        return new QuiltSelection(parts[0], parts[1]);
    }

    private List<QuiltGameVersion> gameVersions() throws IOException {
        JsonArray versions = httpClient.getJson(BASE_URL + "/game").getAsJsonArray();
        List<QuiltGameVersion> result = new ArrayList<>();
        for (JsonElement element : versions) {
            JsonObject object = element.getAsJsonObject();
            String version = stringValue(object, "version");
            if (version != null && !version.isBlank()) {
                result.add(new QuiltGameVersion(version, booleanValue(object, "stable")));
            }
        }
        result.sort((left, right) -> VersionStringComparator.minecraftVersionsDescending().compare(left.version(), right.version()));
        return result;
    }

    private String latestStableVersion(String url) throws IOException {
        JsonArray versions = httpClient.getJson(url).getAsJsonArray();
        if (versions.isEmpty()) {
            throw new IOException("No se han encontrado versiones disponibles.");
        }
        String fallback = null;
        for (JsonElement element : versions) {
            String version = stringValue(element.getAsJsonObject(), "version");
            if (version == null || version.isBlank()) {
                continue;
            }
            if (fallback == null) {
                fallback = version;
            }
            if (ServerCreationOption.VERSION_TYPE_RELEASE.equals(ServerCreationOption.versionTypeFromText(version))) {
                return version;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IOException("No se han encontrado versiones disponibles.");
    }

    private QuiltInstaller latestInstaller() throws IOException {
        JsonArray installers = httpClient.getJson(BASE_URL + "/installer").getAsJsonArray();
        if (installers.isEmpty()) {
            throw new IOException("No se ha encontrado ningún instalador de Quilt.");
        }
        JsonObject object = installers.get(0).getAsJsonObject();
        return new QuiltInstaller(stringValue(object, "version"), stringValue(object, "url"));
    }

    private String stringValue(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private boolean booleanValue(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    record QuiltSelection(String loaderVersion, String installerVersion) {
    }

    private record QuiltInstaller(String version, String url) {
    }

    private record QuiltGameVersion(String version, boolean stable) {
    }
}
