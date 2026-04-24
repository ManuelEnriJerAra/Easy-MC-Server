package controlador.extensions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class ExtensionHttpClient {
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Path CACHE_DIR = resolveCacheDirectory();
    private static final int MAX_RETRIES = 2;
    private static final Logger LOGGER = Logger.getLogger(ExtensionHttpClient.class.getName());

    private final HttpClient client;

    ExtensionHttpClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    ExtensionHttpClient(HttpClient client) {
        this.client = client == null ? HttpClient.newHttpClient() : client;
    }

    String get(URI uri, Map<String, String> headers) throws IOException {
        CacheEntry cacheEntry = readCache(uri, headers);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            return cacheEntry.body();
        }
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(12))
                        .header("Accept", "application/json")
                        .header("User-Agent", "Easy-MC-Server/alpha");
                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        if (header == null || header.getKey() == null || header.getKey().isBlank()
                                || header.getValue() == null || header.getValue().isBlank()) {
                            continue;
                        }
                        requestBuilder.header(header.getKey(), header.getValue());
                    }
                }
                HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Respuesta HTTP " + response.statusCode() + " al consultar " + uri + ".");
                }
                String body = response.body() == null ? "" : response.body();
                writeCache(uri, headers, body);
                return body;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.FINE, "Consulta HTTP interrumpida para " + uri, ex);
                if (cacheEntry != null && cacheEntry.body() != null) {
                    return cacheEntry.body();
                }
                throw new IOException("La consulta HTTP ha sido interrumpida.", ex);
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.WARNING, "Peticion HTTP invalida para " + uri, ex);
                throw new IOException("No se ha podido construir la peticion HTTP.", ex);
            } catch (IOException ex) {
                lastError = ex;
                LOGGER.log(
                        attempt < MAX_RETRIES ? Level.FINE : Level.WARNING,
                        "Fallo HTTP al consultar " + uri + " (intento " + attempt + "/" + MAX_RETRIES + ")",
                        ex
                );
                if (attempt < MAX_RETRIES) {
                    continue;
                }
            }
        }
        if (cacheEntry != null && cacheEntry.body() != null) {
            LOGGER.log(Level.INFO, "Usando cache caducada para " + uri + " tras fallo remoto.");
            return cacheEntry.body();
        }
        throw lastError == null ? new IOException("No se ha podido completar la consulta HTTP.") : lastError;
    }

    private CacheEntry readCache(URI uri, Map<String, String> headers) {
        try {
            Path cacheFile = resolveCacheFile(uri, headers);
            if (!Files.isRegularFile(cacheFile)) {
                return null;
            }
            String raw = Files.readString(cacheFile, StandardCharsets.UTF_8);
            int separator = raw.indexOf('\n');
            if (separator <= 0) {
                return null;
            }
            long timestamp = Long.parseLong(raw.substring(0, separator).trim());
            String body = raw.substring(separator + 1);
            return new CacheEntry(timestamp, body);
        } catch (RuntimeException | IOException ex) {
            LOGGER.log(Level.FINE, "No se ha podido leer la cache HTTP local.", ex);
            return null;
        }
    }

    private void writeCache(URI uri, Map<String, String> headers, String body) {
        try {
            Files.createDirectories(CACHE_DIR);
            Path cacheFile = resolveCacheFile(uri, headers);
            Files.writeString(
                    cacheFile,
                    System.currentTimeMillis() + "\n" + (body == null ? "" : body),
                    StandardCharsets.UTF_8
            );
        } catch (RuntimeException | IOException ignored) {
            LOGGER.log(Level.FINE, "No se ha podido persistir la cache HTTP local.", ignored);
        }
    }

    private Path resolveCacheFile(URI uri, Map<String, String> headers) {
        return CACHE_DIR.resolve(hash(uri.toString() + "|" + normalizeHeaders(headers)) + ".cache");
    }

    private String normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        return headers.entrySet().stream()
                .filter(entry -> entry != null && entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> entry.getKey().trim() + "=" + entry.getValue().trim())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static Path resolveCacheDirectory() {
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, ".easy-mc-server", "cache", "extensions", "http");
    }

    private record CacheEntry(long createdAtEpochMillis, String body) {
        private boolean isExpired() {
            return System.currentTimeMillis() - createdAtEpochMillis > CACHE_TTL.toMillis();
        }
    }
}
