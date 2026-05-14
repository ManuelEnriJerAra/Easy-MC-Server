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
    private static final int MAX_CREATION_OPTIONS = 80;
    private final PlatformHttpClient httpClient;

    QuiltMetaClient() {
        this(new UrlConnectionPlatformHttpClient());
    }

    QuiltMetaClient(PlatformHttpClient httpClient) {
        this.httpClient = new CachedPlatformHttpClient(httpClient);
    }

    List<ServerCreationOption> listCreationOptions() throws IOException {
        String loaderVersion = latestVersion(BASE_URL + "/loader");
        QuiltInstaller installer = latestInstaller();
        List<String> gameVersions = gameVersions();
        List<ServerCreationOption> options = new ArrayList<>();
        for (String minecraftVersion : gameVersions) {
            options.add(new ServerCreationOption(
                    ServerPlatform.QUILT,
                    minecraftVersion,
                    encodePlatformVersion(loaderVersion, installer.version()),
                    "Minecraft " + minecraftVersion + " (Quilt Loader " + loaderVersion + ")",
                    "quilt-" + minecraftVersion + "-server"
            ));
            if (options.size() >= MAX_CREATION_OPTIONS) {
                break;
            }
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

    private List<String> gameVersions() throws IOException {
        JsonArray versions = httpClient.getJson(BASE_URL + "/game").getAsJsonArray();
        List<String> result = new ArrayList<>();
        for (JsonElement element : versions) {
            JsonObject object = element.getAsJsonObject();
            if (!booleanValue(object, "stable")) {
                continue;
            }
            String version = stringValue(object, "version");
            if (version != null && !version.isBlank()) {
                result.add(version);
            }
        }
        result.sort(VersionStringComparator.descending());
        return result;
    }

    private String latestVersion(String url) throws IOException {
        JsonArray versions = httpClient.getJson(url).getAsJsonArray();
        if (versions.isEmpty()) {
            throw new IOException("No se han encontrado versiones disponibles.");
        }
        return stringValue(versions.get(0).getAsJsonObject(), "version");
    }

    private QuiltInstaller latestInstaller() throws IOException {
        JsonArray installers = httpClient.getJson(BASE_URL + "/installer").getAsJsonArray();
        if (installers.isEmpty()) {
            throw new IOException("No se ha encontrado ningun instalador de Quilt.");
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
}
