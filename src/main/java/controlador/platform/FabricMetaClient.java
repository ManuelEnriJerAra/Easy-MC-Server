package controlador.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class FabricMetaClient {
    private static final String BASE_URL = "https://meta.fabricmc.net/v2/versions";
    private static final String PLATFORM_VERSION_SEPARATOR = "|";
    private final PlatformHttpClient httpClient;

    FabricMetaClient() {
        this(new UrlConnectionPlatformHttpClient());
    }

    FabricMetaClient(PlatformHttpClient httpClient) {
        this.httpClient = new CachedPlatformHttpClient(httpClient);
    }

    List<ServerCreationOption> listCreationOptions() throws IOException {
        String loaderVersion = latestStableVersion(BASE_URL + "/loader", "version");
        String installerVersion = latestStableVersion(BASE_URL + "/installer", "version");
        List<FabricGameVersion> gameVersions = gameVersions();
        List<ServerCreationOption> options = new ArrayList<>();
        for (FabricGameVersion gameVersion : gameVersions) {
            String minecraftVersion = gameVersion.version();
            options.add(new ServerCreationOption(
                    ServerPlatform.FABRIC,
                    minecraftVersion,
                    encodePlatformVersion(loaderVersion, installerVersion),
                    "Minecraft " + minecraftVersion + " (Fabric Loader " + loaderVersion + ")",
                    "fabric-" + minecraftVersion + "-server",
                    ServerCreationOption.versionTypeFromStability(gameVersion.stable())
            ));
        }
        return options;
    }

    String downloadUrl(String minecraftVersion, String platformVersion) throws IOException {
        FabricSelection selection = decodePlatformVersion(platformVersion);
        if (selection.loaderVersion() == null || selection.loaderVersion().isBlank()) {
            throw new IOException("No se ha indicado la versión de Fabric Loader.");
        }
        if (selection.installerVersion() == null || selection.installerVersion().isBlank()) {
            throw new IOException("No se ha indicado la versión del instalador de Fabric.");
        }
        return BASE_URL + "/loader/"
                + path(minecraftVersion) + "/"
                + path(selection.loaderVersion()) + "/"
                + path(selection.installerVersion()) + "/server/jar";
    }

    static String encodePlatformVersion(String loaderVersion, String installerVersion) {
        return (loaderVersion == null ? "" : loaderVersion) + PLATFORM_VERSION_SEPARATOR
                + (installerVersion == null ? "" : installerVersion);
    }

    static FabricSelection decodePlatformVersion(String platformVersion) {
        if (platformVersion == null) {
            return new FabricSelection(null, null);
        }
        String[] parts = platformVersion.split("\\|", 2);
        if (parts.length == 1) {
            return new FabricSelection(parts[0], null);
        }
        return new FabricSelection(parts[0], parts[1]);
    }

    private List<FabricGameVersion> gameVersions() throws IOException {
        JsonArray versions = httpClient.getJson(BASE_URL + "/game").getAsJsonArray();
        List<FabricGameVersion> result = new ArrayList<>();
        for (JsonElement element : versions) {
            JsonObject object = element.getAsJsonObject();
            String version = stringValue(object, "version");
            if (version != null && !version.isBlank()) {
                result.add(new FabricGameVersion(version, booleanValue(object, "stable")));
            }
        }
        result.sort((left, right) -> VersionStringComparator.minecraftVersionsDescending().compare(left.version(), right.version()));
        return result;
    }

    private String latestStableVersion(String url, String field) throws IOException {
        JsonArray versions = httpClient.getJson(url).getAsJsonArray();
        String fallback = null;
        for (JsonElement element : versions) {
            JsonObject object = element.getAsJsonObject();
            String value = stringValue(object, field);
            if (fallback == null) {
                fallback = value;
            }
            if (booleanValue(object, "stable")) {
                return value;
            }
        }
        return fallback;
    }

    private boolean booleanValue(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private String stringValue(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private String path(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    record FabricSelection(String loaderVersion, String installerVersion) {
    }

    private record FabricGameVersion(String version, boolean stable) {
    }
}
