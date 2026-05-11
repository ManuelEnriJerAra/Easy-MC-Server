package controlador.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

interface PlatformHttpClient {
    JsonElement getJson(String url) throws IOException;
}

final class UrlConnectionPlatformHttpClient implements PlatformHttpClient {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Easy-MC-Server/1.0 (+https://github.com/)";

    @Override
    public JsonElement getJson(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("No se ha indicado la URL.");
        }
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }
}
