package vista;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ExtensionIconLoader {
    private static final Logger LOGGER = Logger.getLogger(ExtensionIconLoader.class.getName());
    private static final Map<String, Icon> MEMORY_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, PendingLoad> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, FailureInfo> FAILURES = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_EVENTS = ConcurrentHashMap.newKeySet();
    private static final Map<Component, AtomicBoolean> REPAINT_SCHEDULED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            3,
            3,
            30L,
            TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "easy-mc-extension-icons");
                thread.setDaemon(true);
                return thread;
            }
    );
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home", "."), ".easy-mc-server", "cache", "extensions", "icons");
    private static final Duration DISK_CACHE_TTL = Duration.ofDays(7);
    private static final Duration FAILURE_BASE_BACKOFF = Duration.ofSeconds(30);
    private static final int MAX_FAILURE_ATTEMPTS = 3;
    private static final int MAX_REMOTE_ATTEMPTS = 2;
    private static final int CONNECT_TIMEOUT_MILLIS = 6000;
    private static final int READ_TIMEOUT_MILLIS = 10000;
    private static final AtomicBoolean CACHE_READY = new AtomicBoolean();
    private static final boolean CONSOLE_IMAGE_LOGGING = Boolean.parseBoolean(
            System.getProperty("easy.mc.marketplace.imageLogging", "false")
    );

    static {
        ImageIO.scanForPlugins();
        registerImageReaderSpi("com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi");
        registerImageReaderSpi("com.luciad.imageio.webp.WebPImageReaderSpi");
        logIconEvent("imageio-webp-readers", "-", 0, Integer.toString(countWebpReaders()));
    }

    private static void registerImageReaderSpi(String className) {
        try {
            Class<?> type = Class.forName(className);
            Object instance = type.getDeclaredConstructor().newInstance();
            if (instance instanceof ImageReaderSpi spi) {
                IIORegistry.getDefaultInstance().registerServiceProvider(spi, ImageReaderSpi.class);
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOGGER.log(Level.FINE, "ImageIO reader SPI no disponible: " + className, ex);
        }
    }

    private ExtensionIconLoader() {
    }

    static Icon getIcon(String iconUrl, int size, Runnable repaintCallback) {
        if (iconUrl == null || iconUrl.isBlank()) {
            return placeholder(size);
        }
        String normalizedUrl = iconUrl.trim();
        String cacheKey = cacheKey(normalizedUrl, size);
        Icon cached = MEMORY_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        FailureInfo failure = FAILURES.get(cacheKey);
        if (failure != null && !failure.canRetry()) {
            return failurePlaceholder(size);
        }
        enqueue(normalizedUrl, size, repaintCallback, 1);
        return placeholder(size);
    }

    static Icon getIcon(String iconUrl, int size, Component repaintTarget) {
        if (iconUrl == null || iconUrl.isBlank()) {
            return placeholder(size);
        }
        String normalizedUrl = iconUrl.trim();
        String cacheKey = cacheKey(normalizedUrl, size);
        Icon cached = MEMORY_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        FailureInfo failure = FAILURES.get(cacheKey);
        if (failure != null && !failure.canRetry()) {
            return failurePlaceholder(size);
        }
        enqueue(normalizedUrl, size, createRepaintCallback(repaintTarget), 0);
        return failurePlaceholder(size);
    }

    static void prefetchIcons(List<String> iconUrls, int size, Runnable repaintCallback) {
        prefetchIcons(null, iconUrls, size, repaintCallback);
    }

    static void prefetchIcons(JList<?> prioritizedList, List<String> iconUrls, int size, Runnable repaintCallback) {
        if (iconUrls == null || iconUrls.isEmpty()) {
            return;
        }
        List<String> ordered = orderedUrls(prioritizedList, iconUrls);
        int priority = 0;
        for (String iconUrl : ordered) {
            enqueue(iconUrl, size, repaintCallback, priority++);
        }
    }

    private static void enqueue(String iconUrl, int size, Runnable repaintCallback, int priority) {
        if (iconUrl == null || iconUrl.isBlank()) {
            return;
        }
        String normalizedUrl = iconUrl.trim();
        String cacheKey = cacheKey(normalizedUrl, size);
        if (MEMORY_CACHE.containsKey(cacheKey)) {
            return;
        }
        FailureInfo failure = FAILURES.get(cacheKey);
        if (failure != null && !failure.canRetry()) {
            return;
        }

        PendingLoad existing = IN_FLIGHT.computeIfAbsent(cacheKey, ignored -> new PendingLoad());
        existing.addCallback(repaintCallback);
        if (existing.markQueued()) {
            logIconEventOnce("queued", normalizedUrl, size, null);
            EXECUTOR.execute(new IconLoadTask(normalizedUrl, size, cacheKey, priority));
        }
    }

    private static List<String> orderedUrls(JList<?> prioritizedList, List<String> iconUrls) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (prioritizedList != null) {
            int first = prioritizedList.getFirstVisibleIndex();
            int last = prioritizedList.getLastVisibleIndex();
            if (first >= 0 && last >= first) {
                for (int i = first; i <= last && i < iconUrls.size(); i++) {
                    String iconUrl = iconUrls.get(i);
                    if (iconUrl != null && !iconUrl.isBlank()) {
                        ordered.add(iconUrl.trim());
                    }
                }
            }
        }
        for (String iconUrl : iconUrls) {
            if (iconUrl != null && !iconUrl.isBlank()) {
                ordered.add(iconUrl.trim());
            }
        }
        return new ArrayList<>(ordered);
    }

    private static Runnable createRepaintCallback(Component component) {
        if (component == null) {
            return null;
        }
        return () -> scheduleRepaint(component);
    }

    private static void scheduleRepaint(Component component) {
        if (component == null) {
            return;
        }
        AtomicBoolean scheduled;
        synchronized (REPAINT_SCHEDULED) {
            scheduled = REPAINT_SCHEDULED.computeIfAbsent(component, ignored -> new AtomicBoolean());
        }
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            scheduled.set(false);
            if (component.isDisplayable()) {
                component.repaint();
            }
        });
    }

    private static void completeLoad(String cacheKey, Icon icon, boolean successful) {
        PendingLoad pending = IN_FLIGHT.remove(cacheKey);
        if (successful && icon != null) {
            MEMORY_CACHE.put(cacheKey, icon);
            FAILURES.remove(cacheKey);
        } else {
            FAILURES.compute(cacheKey, (ignored, failure) -> FailureInfo.next(failure));
        }
        if (pending == null) {
            return;
        }
        pending.markCompleted();
        for (Runnable callback : pending.callbacks()) {
            if (callback != null) {
                callback.run();
            }
        }
    }

    private static Icon loadIcon(String iconUrl, int size) {
        try {
            ensureCacheDirectory();
            Path cachedFile = resolveCacheFile(iconUrl);
            BufferedImage image = readCachedImage(cachedFile);
            if (image == null) {
                logIconEventOnce("remote-request", iconUrl, size, null);
                image = readRemoteImageWithRetry(iconUrl);
                if (image != null) {
                    writeCachedImage(cachedFile, image);
                }
            } else {
                logIconEventOnce("disk-hit", iconUrl, size, null);
            }
            if (image == null) {
                logIconEventOnce("decode-empty", iconUrl, size, null);
                return null;
            }
            logIconEventOnce("loaded", iconUrl, size, image.getWidth() + "x" + image.getHeight());
            return new ImageIcon(scaleImage(image, size));
        } catch (RuntimeException | IOException ex) {
            LOGGER.log(Level.FINE, "No se ha podido cargar el icono remoto " + iconUrl, ex);
            logIconEventOnce("failed", iconUrl, size, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    static int pendingLoadCount() {
        return IN_FLIGHT.size();
    }

    static Icon loadIconForTesting(String iconUrl, int size) {
        return loadIcon(iconUrl, size);
    }

    private static BufferedImage readRemoteImageWithRetry(String iconUrl) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_REMOTE_ATTEMPTS; attempt++) {
            try {
                return readRemoteImage(iconUrl);
            } catch (IOException ex) {
                lastError = ex;
                LOGGER.log(
                        attempt < MAX_REMOTE_ATTEMPTS ? Level.FINE : Level.WARNING,
                        "Fallo al descargar icono " + iconUrl + " (intento " + attempt + "/" + MAX_REMOTE_ATTEMPTS + ")",
                        ex
                );
            }
        }
        throw lastError == null ? new IOException("No se ha podido descargar el icono.") : lastError;
    }

    private static BufferedImage readRemoteImage(String iconUrl) throws IOException {
        URI uri = URI.create(iconUrl);
        URLConnection connection = uri.toURL().openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "Easy-MC-Server/1.0 (+https://modrinth.com)");
        connection.setRequestProperty("Accept", "image/png,image/jpeg,*/*;q=0.2");
        if (connection instanceof HttpURLConnection http) {
            http.setInstanceFollowRedirects(true);
            int status = http.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " al descargar icono.");
            }
        }
        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                logIconEventOnce("remote-decoded-imageio", iconUrl, 0, bytes.length + " bytes");
                return image;
            }
            String contentType = connection.getContentType();
            int webpReaders = countWebpReaders();
            logIconEventOnce("remote-imageio-null", iconUrl, 0,
                    bytes.length + " bytes"
                            + (contentType == null ? "" : " content-type=" + contentType)
                            + " webp-readers=" + webpReaders
                            + " magic=" + imageMagic(bytes));
            if (isWebp(bytes) && webpReaders == 0) {
                logIconEventOnce("runtime-missing-webp-reader", iconUrl, 0,
                        "ImageIO no tiene lector WebP en este arranque");
            }
            BufferedImage toolkitImage = decodeWithToolkit(iconUrl, bytes);
            if (toolkitImage != null) {
                return toolkitImage;
            }
            if (isWebp(bytes) && webpReaders == 0) {
                return readWebpAsPngThroughImageProxy(iconUrl);
            }
            return null;
        }
    }

    private static BufferedImage readWebpAsPngThroughImageProxy(String iconUrl) {
        URI sourceUri;
        try {
            sourceUri = URI.create(iconUrl);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!"https".equalsIgnoreCase(sourceUri.getScheme()) || sourceUri.getHost() == null
                || !sourceUri.getHost().endsWith("modrinth.com")) {
            return null;
        }
        String source = sourceUri.getHost() + sourceUri.getRawPath()
                + (sourceUri.getRawQuery() == null ? "" : "?" + sourceUri.getRawQuery());
        String encodedSource = URLEncoder.encode(source, StandardCharsets.UTF_8);
        URI proxyUri = URI.create("https://images.weserv.nl/?url=" + encodedSource + "&output=png");
        logIconEventOnce("webp-proxy-request", iconUrl, 0, "png-fallback");
        try {
            URLConnection connection = proxyUri.toURL().openConnection();
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "Easy-MC-Server/1.0 (+https://modrinth.com)");
            connection.setRequestProperty("Accept", "image/png,image/jpeg,*/*;q=0.2");
            if (connection instanceof HttpURLConnection http) {
                http.setInstanceFollowRedirects(true);
                int status = http.getResponseCode();
                if (status < 200 || status >= 300) {
                    logIconEventOnce("webp-proxy-http", iconUrl, 0, Integer.toString(status));
                    return null;
                }
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) {
                    logIconEventOnce("webp-proxy-loaded", iconUrl, 0, image.getWidth() + "x" + image.getHeight());
                    return image;
                }
                logIconEventOnce("webp-proxy-empty", iconUrl, 0,
                        bytes.length + " bytes magic=" + imageMagic(bytes));
            }
        } catch (RuntimeException | IOException ex) {
            LOGGER.log(Level.FINE, "No se ha podido convertir el icono WebP " + iconUrl, ex);
            logIconEventOnce("webp-proxy-failed", iconUrl, 0, ex.getClass().getSimpleName());
        }
        return null;
    }

    private static int countWebpReaders() {
        int count = 0;
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
        while (readers.hasNext()) {
            readers.next();
            count++;
        }
        return count;
    }

    private static void logIconEventOnce(String event, String iconUrl, int size, String detail) {
        if (!CONSOLE_IMAGE_LOGGING) {
            return;
        }
        String key = event + "|" + size + "|" + iconUrl;
        if (LOGGED_EVENTS.add(key)) {
            logIconEvent(event, iconUrl, size, detail);
        }
    }

    private static void logIconEvent(String event, String iconUrl, int size, String detail) {
        if (!CONSOLE_IMAGE_LOGGING) {
            return;
        }
        StringBuilder sb = new StringBuilder("[MarketplaceImages] ");
        sb.append(event);
        if (size > 0) {
            sb.append(" size=").append(size);
        }
        if (detail != null && !detail.isBlank()) {
            sb.append(" detail=").append(detail);
        }
        sb.append(" url=").append(iconUrl == null ? "-" : iconUrl);
        System.out.println(sb);
    }

    private static BufferedImage readCachedImage(Path cachedFile) {
        try {
            if (!Files.isRegularFile(cachedFile)) {
                return null;
            }
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cachedFile).toMillis();
            if (age > DISK_CACHE_TTL.toMillis()) {
                Files.deleteIfExists(cachedFile);
                return null;
            }
            byte[] bytes = Files.readAllBytes(cachedFile);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                Files.deleteIfExists(cachedFile);
            }
            return image;
        } catch (RuntimeException | IOException ex) {
            LOGGER.log(Level.FINE, "No se ha podido leer el icono cacheado " + cachedFile, ex);
            return null;
        }
    }

    private static void writeCachedImage(Path cachedFile, BufferedImage image) {
        try {
            Path tempFile = cachedFile.resolveSibling(cachedFile.getFileName() + ".tmp");
            ImageIO.write(image, "png", tempFile.toFile());
            Files.move(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (RuntimeException | IOException ignored) {
            LOGGER.log(Level.FINE, "No se ha podido guardar el icono cacheado " + cachedFile, ignored);
        }
    }

    private static void ensureCacheDirectory() throws IOException {
        if (CACHE_READY.compareAndSet(false, true)) {
            try {
                Files.createDirectories(CACHE_DIR);
            } catch (IOException ex) {
                CACHE_READY.set(false);
                throw ex;
            }
        }
    }

    private static Path resolveCacheFile(String iconUrl) {
        return CACHE_DIR.resolve(hash(iconUrl) + ".png");
    }

    private static String cacheKey(String iconUrl, int size) {
        return iconUrl + "#" + size;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static Icon placeholder(int size) {
        return SvgIconFactory.create("easymcicons/box-unselected.svg", size, size);
    }

    private static Icon failurePlaceholder(int size) {
        return SvgIconFactory.create("easymcicons/box-unselected.svg", size, size);
    }

    private static String imageMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return "unknown";
        }
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "png";
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) {
            return "jpeg";
        }
        if (isWebp(bytes)) {
            return "webp";
        }
        return "unknown";
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes != null
                && bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private static BufferedImage decodeWithToolkit(String iconUrl, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        Image image = Toolkit.getDefaultToolkit().createImage(bytes);
        MediaTracker tracker = new MediaTracker(new JLabel());
        tracker.addImage(image, 0);
        try {
            tracker.waitForID(0, 4000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0) {
            logIconEventOnce("toolkit-decode-empty", iconUrl, 0, imageMagic(bytes));
            return null;
        }
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buffered.createGraphics();
        g2.drawImage(image, 0, 0, null);
        g2.dispose();
        return buffered;
    }

    private static BufferedImage scaleImage(BufferedImage source, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int sourceWidth = Math.max(1, source.getWidth());
        int sourceHeight = Math.max(1, source.getHeight());
        double scale = Math.min((double) size / sourceWidth, (double) size / sourceHeight);
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int x = (size - targetWidth) / 2;
        int y = (size - targetHeight) / 2;
        g2.drawImage(source, x, y, targetWidth, targetHeight, null);
        g2.dispose();
        return scaled;
    }

    private static final class PendingLoad {
        private final Set<Runnable> callbacks = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean queued = new AtomicBoolean();

        private void addCallback(Runnable callback) {
            if (callback != null && callbacks.size() < 8) {
                callbacks.add(callback);
            }
        }

        private boolean markQueued() {
            return queued.compareAndSet(false, true);
        }

        private void markCompleted() {
            queued.set(false);
        }

        private Set<Runnable> callbacks() {
            return new HashSet<>(callbacks);
        }
    }

    private record FailureInfo(int attempts, long nextRetryAtMillis) {
        private static FailureInfo next(FailureInfo previous) {
            int attempts = previous == null ? 1 : Math.min(MAX_FAILURE_ATTEMPTS, previous.attempts + 1);
            long delay = FAILURE_BASE_BACKOFF.multipliedBy(attempts).toMillis();
            return new FailureInfo(attempts, System.currentTimeMillis() + delay);
        }

        private boolean canRetry() {
            return System.currentTimeMillis() >= nextRetryAtMillis;
        }
    }

    private static final class IconLoadTask implements Runnable, Comparable<IconLoadTask> {
        private final String iconUrl;
        private final int size;
        private final String cacheKey;
        private final int priority;
        private final long sequence;

        private IconLoadTask(String iconUrl, int size, String cacheKey, int priority) {
            this.iconUrl = iconUrl;
            this.size = size;
            this.cacheKey = cacheKey;
            this.priority = priority;
            this.sequence = SEQUENCE.getAndIncrement();
        }

        @Override
        public void run() {
            if (MEMORY_CACHE.containsKey(cacheKey)) {
                completeLoad(cacheKey, MEMORY_CACHE.get(cacheKey), true);
                return;
            }
            FailureInfo failure = FAILURES.get(cacheKey);
            if (failure != null && !failure.canRetry()) {
                completeLoad(cacheKey, failurePlaceholder(size), false);
                return;
            }

            Icon icon = loadIcon(iconUrl, size);
            completeLoad(cacheKey, icon == null ? failurePlaceholder(size) : icon, icon != null);
        }

        @Override
        public int compareTo(IconLoadTask other) {
            int byPriority = Integer.compare(this.priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(this.sequence, other.sequence);
        }
    }
}
