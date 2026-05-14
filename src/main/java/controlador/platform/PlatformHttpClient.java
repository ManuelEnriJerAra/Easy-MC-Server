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

final class CachedPlatformHttpClient implements PlatformHttpClient {
    private final PlatformHttpClient delegate;

    CachedPlatformHttpClient(PlatformHttpClient delegate) {
        this.delegate = delegate == null ? new UrlConnectionPlatformHttpClient() : delegate;
    }

    @Override
    public JsonElement getJson(String url) throws IOException {
        return PlatformRemoteLookupPolicy.getJson(url, () -> delegate.getJson(url));
    }
}

final class UrlConnectionPlatformHttpClient implements PlatformHttpClient {
    @Override
    public JsonElement getJson(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("No se ha indicado la URL.");
        }
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(PlatformRemoteLookupPolicy.CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(PlatformRemoteLookupPolicy.READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", PlatformRemoteLookupPolicy.USER_AGENT);
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }
}
