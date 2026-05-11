package controlador.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class PurpurDownloadsClient {
    private static final String BASE_URL = "https://api.purpurmc.org/v2/purpur";
    private final PlatformHttpClient httpClient;

    PurpurDownloadsClient() {
        this(new UrlConnectionPlatformHttpClient());
    }

    PurpurDownloadsClient(PlatformHttpClient httpClient) {
        this.httpClient = httpClient == null ? new UrlConnectionPlatformHttpClient() : httpClient;
    }

    List<ServerCreationOption> listCreationOptions() throws IOException {
        JsonObject project = httpClient.getJson(BASE_URL).getAsJsonObject();
        JsonArray versions = project.getAsJsonArray("versions");
        List<ServerCreationOption> options = new ArrayList<>();
        if (versions == null) {
            return options;
        }
        List<String> sortedVersions = new ArrayList<>();
        for (JsonElement element : versions) {
            if (element != null && element.isJsonPrimitive()) {
                sortedVersions.add(element.getAsString());
            }
        }
        sortedVersions.sort(VersionStringComparator.descending());
        for (String minecraftVersion : sortedVersions) {
            String latestBuild = latestBuild(minecraftVersion);
            if (latestBuild == null || latestBuild.isBlank()) {
                continue;
            }
            options.add(new ServerCreationOption(
                    ServerPlatform.PURPUR,
                    minecraftVersion,
                    latestBuild,
                    "Minecraft " + minecraftVersion + " (Purpur #" + latestBuild + ")",
                    "purpur-" + minecraftVersion + "-server"
            ));
            if (options.size() >= 40) {
                break;
            }
        }
        return options;
    }

    String downloadUrl(String minecraftVersion, String buildId) throws IOException {
        String resolvedBuild = buildId == null || buildId.isBlank() ? latestBuild(minecraftVersion) : buildId;
        if (resolvedBuild == null || resolvedBuild.isBlank()) {
            throw new IOException("No se ha encontrado una build de Purpur para Minecraft " + minecraftVersion + ".");
        }
        return BASE_URL + "/" + minecraftVersion + "/" + resolvedBuild + "/download";
    }

    private String latestBuild(String minecraftVersion) throws IOException {
        JsonObject version = httpClient.getJson(BASE_URL + "/" + minecraftVersion).getAsJsonObject();
        JsonObject builds = version.getAsJsonObject("builds");
        if (builds == null) {
            return null;
        }
        JsonElement latest = builds.get("latest");
        if (latest != null && !latest.isJsonNull()) {
            return latest.getAsString();
        }
        JsonArray all = builds.getAsJsonArray("all");
        return all == null || all.isEmpty() ? null : all.get(all.size() - 1).getAsString();
    }
}
