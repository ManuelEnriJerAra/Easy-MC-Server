package controlador.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PaperDownloadsClient {
    private static final String PROJECT = "paper";
    private static final String BASE_URL = "https://fill.papermc.io/v3/projects/" + PROJECT;
    private final PlatformHttpClient httpClient;

    PaperDownloadsClient() {
        this(new UrlConnectionPlatformHttpClient());
    }

    PaperDownloadsClient(PlatformHttpClient httpClient) {
        this.httpClient = new CachedPlatformHttpClient(httpClient);
    }

    List<ServerCreationOption> listCreationOptions() throws IOException {
        List<String> versions = listVersions();
        List<ServerCreationOption> options = new ArrayList<>();
        for (String minecraftVersion : versions) {
            PaperBuild build = latestStableBuild(minecraftVersion);
            if (build == null) {
                continue;
            }
            options.add(new ServerCreationOption(
                    ServerPlatform.PAPER,
                    minecraftVersion,
                    build.id(),
                    "Minecraft " + minecraftVersion + " (Paper #" + build.id() + ")",
                    "paper-" + minecraftVersion + "-server"
            ));
            if (options.size() >= 40) {
                break;
            }
        }
        return options;
    }

    String downloadUrl(String minecraftVersion, String buildId) throws IOException {
        PaperBuild build = latestStableBuild(minecraftVersion);
        if (build == null) {
            throw new IOException("No se ha encontrado una build estable de Paper para Minecraft " + minecraftVersion + ".");
        }
        if (buildId == null || buildId.isBlank() || build.id().equals(buildId)) {
            return build.downloadUrl();
        }
        JsonArray builds = httpClient.getJson(BASE_URL + "/versions/" + minecraftVersion + "/builds").getAsJsonArray();
        for (JsonElement element : builds) {
            JsonObject object = element.getAsJsonObject();
            String id = stringValue(object, "id");
            if (buildId.equals(id)) {
                String url = downloadUrlFromBuild(object);
                if (url != null && !url.isBlank()) {
                    return url;
                }
            }
        }
        throw new IOException("No se ha encontrado la build " + buildId + " de Paper para Minecraft " + minecraftVersion + ".");
    }

    private List<String> listVersions() throws IOException {
        JsonObject project = httpClient.getJson(BASE_URL).getAsJsonObject();
        JsonObject versions = project.getAsJsonObject("versions");
        List<String> values = new ArrayList<>();
        if (versions == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : versions.entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            JsonArray array = entry.getValue().getAsJsonArray();
            for (JsonElement version : array) {
                if (version != null && version.isJsonPrimitive()) {
                    values.add(version.getAsString());
                }
            }
        }
        values.sort(VersionStringComparator.descending());
        return values;
    }

    private PaperBuild latestStableBuild(String minecraftVersion) throws IOException {
        JsonArray builds = httpClient.getJson(BASE_URL + "/versions/" + minecraftVersion + "/builds").getAsJsonArray();
        for (JsonElement element : builds) {
            JsonObject object = element.getAsJsonObject();
            String channel = stringValue(object, "channel");
            if (!"STABLE".equalsIgnoreCase(channel)) {
                continue;
            }
            String url = downloadUrlFromBuild(object);
            String id = stringValue(object, "id");
            if (url != null && !url.isBlank() && id != null && !id.isBlank()) {
                return new PaperBuild(id, url);
            }
        }
        return null;
    }

    private String downloadUrlFromBuild(JsonObject build) {
        JsonObject downloads = build == null ? null : build.getAsJsonObject("downloads");
        JsonObject serverDefault = downloads == null ? null : downloads.getAsJsonObject("server:default");
        return serverDefault == null ? null : stringValue(serverDefault, "url");
    }

    private String stringValue(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private record PaperBuild(String id, String downloadUrl) {
    }
}
