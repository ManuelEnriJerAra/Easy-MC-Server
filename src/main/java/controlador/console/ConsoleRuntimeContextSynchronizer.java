package controlador.console;

import controlador.console.content.ServerInstallationReport;
import modelo.Server;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sincroniza el contexto de consola con invalidacion incremental y observacion runtime.
 */
public final class ConsoleRuntimeContextSynchronizer implements AutoCloseable {
    private final ConsoleCommandContextFactory contextFactory;
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Runnable invalidationListener = () -> {};

    private Server currentServer;
    private String currentServerId = "";
    private Path currentServerDir;
    private String lastServerVersion = "";
    private String lastServerType = "";
    private boolean lastProcessAlive;
    private Set<String> lastOnlinePlayers = Set.of();

    private boolean installationDirty = true;
    private boolean dynamicDirty = true;
    private boolean contextDirty = true;

    private ServerInstallationReport cachedInstallationReport;
    private ConsoleDynamicDataSnapshot cachedDynamicData = ConsoleDynamicDataSnapshot.empty();
    private ConsoleCommandContext cachedContext = ConsoleCommandContext.empty();

    private WatchService watchService;
    private Thread watcherThread;

    public ConsoleRuntimeContextSynchronizer(ConsoleCommandContextFactory contextFactory) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    public void setInvalidationListener(Runnable invalidationListener) {
        this.invalidationListener = invalidationListener == null ? () -> {} : invalidationListener;
    }

    public ConsoleCommandContext getCurrentContext(Server server, Set<String> onlinePlayers) {
        synchronized (lock) {
            if (closed.get()) {
                return ConsoleCommandContext.empty();
            }

            handleServerSelection(server);
            updateProcessAndMetadata(server);
            updateOnlinePlayers(onlinePlayers, false);

            if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
                cachedContext = ConsoleCommandContext.empty();
                contextDirty = false;
                return cachedContext;
            }

            if (installationDirty || cachedInstallationReport == null) {
                cachedInstallationReport = contextFactory.detectInstallationReport(server);
                installationDirty = false;
                contextDirty = true;
            }

            if (dynamicDirty || cachedDynamicData == null) {
                cachedDynamicData = contextFactory.loadDynamicDataSnapshot(server);
                dynamicDirty = false;
                contextDirty = true;
            }

            if (contextDirty) {
                cachedContext = contextFactory.build(server, lastOnlinePlayers, cachedInstallationReport, cachedDynamicData);
                contextDirty = false;
            }

            return cachedContext;
        }
    }

    public void notifyOnlinePlayersChanged(Set<String> onlinePlayers) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            updateOnlinePlayers(onlinePlayers, true);
        }
    }

    public void notifyRuntimeStateChanged(Server server) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            handleServerSelection(server);
            updateProcessAndMetadata(server);
        }
    }

    public void notifyServerSelectionChanged(Server server) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            handleServerSelection(server);
            contextDirty = true;
        }
        fireInvalidation();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (lock) {
            closeWatcherLocked();
        }
    }

    private void handleServerSelection(Server server) {
        String nextServerId = server == null || server.getId() == null ? "" : server.getId();
        Path nextServerDir = server == null || server.getServerDir() == null || server.getServerDir().isBlank()
                ? null
                : Path.of(server.getServerDir());

        boolean changed = !Objects.equals(currentServerId, nextServerId)
                || !Objects.equals(normalizePath(currentServerDir), normalizePath(nextServerDir));

        if (!changed) {
            currentServer = server;
            return;
        }

        currentServer = server;
        currentServerId = nextServerId;
        currentServerDir = nextServerDir;
        lastServerVersion = server == null ? "" : normalize(server.getVersion());
        lastServerType = server == null ? "" : normalize(server.getTipo());
        lastProcessAlive = server != null && server.getServerProcess() != null && server.getServerProcess().isAlive();
        lastOnlinePlayers = Set.of();
        installationDirty = true;
        dynamicDirty = true;
        contextDirty = true;
        cachedInstallationReport = null;
        cachedDynamicData = ConsoleDynamicDataSnapshot.empty();
        cachedContext = ConsoleCommandContext.empty();
        restartWatcherLocked(nextServerDir);
    }

    private void updateProcessAndMetadata(Server server) {
        String version = server == null ? "" : normalize(server.getVersion());
        String type = server == null ? "" : normalize(server.getTipo());
        boolean processAlive = server != null && server.getServerProcess() != null && server.getServerProcess().isAlive();

        boolean changed = false;
        if (!Objects.equals(lastServerVersion, version)) {
            lastServerVersion = version;
            installationDirty = true;
            changed = true;
        }
        if (!Objects.equals(lastServerType, type)) {
            lastServerType = type;
            installationDirty = true;
            changed = true;
        }
        if (lastProcessAlive != processAlive) {
            lastProcessAlive = processAlive;
            contextDirty = true;
            changed = true;
        }
        if (changed) {
            fireInvalidation();
        }
    }

    private void updateOnlinePlayers(Set<String> onlinePlayers, boolean notify) {
        Set<String> normalizedPlayers = immutableOrderedPlayers(onlinePlayers);
        if (normalizedPlayers.equals(lastOnlinePlayers)) {
            return;
        }
        lastOnlinePlayers = normalizedPlayers;
        contextDirty = true;
        if (notify) {
            fireInvalidation();
        }
    }

    private void restartWatcherLocked(Path serverDir) {
        closeWatcherLocked();
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerIfDirectory(serverDir);
            registerIfDirectory(serverDir.resolve("mods"));
            registerIfDirectory(serverDir.resolve("plugins"));
            Path worldDir = findLikelyWorldDir(serverDir);
            if (worldDir != null) {
                registerIfDirectory(worldDir.resolve("datapacks"));
            }
            watcherThread = new Thread(this::watchLoop, "console-context-watch-" + currentServerId);
            watcherThread.setDaemon(true);
            watcherThread.start();
        } catch (IOException ignored) {
            closeWatcherLocked();
        }
    }

    private void closeWatcherLocked() {
        WatchService currentWatchService = watchService;
        watchService = null;
        if (currentWatchService != null) {
            try {
                currentWatchService.close();
            } catch (IOException ignored) {
            }
        }
        watcherThread = null;
    }

    private void registerIfDirectory(Path directory) throws IOException {
        if (directory == null || watchService == null || !Files.isDirectory(directory)) {
            return;
        }
        directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
    }

    private void watchLoop() {
        while (!closed.get()) {
            WatchService currentWatchService = watchService;
            if (currentWatchService == null) {
                return;
            }
            final WatchKey key;
            try {
                key = currentWatchService.take();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                return;
            }

            Path watchedDir = key.watchable() instanceof Path path ? path : null;
            boolean invalidateInstallation = false;
            boolean invalidateDynamic = false;

            for (WatchEvent<?> event : key.pollEvents()) {
                Object context = event.context();
                if (!(context instanceof Path relativePath) || watchedDir == null) {
                    continue;
                }
                Path changedPath = watchedDir.resolve(relativePath);
                String fileName = normalize(changedPath.getFileName() == null ? "" : changedPath.getFileName().toString());

                if (isDynamicFile(fileName)) {
                    invalidateDynamic = true;
                }
                if (isInstallationSensitivePath(watchedDir, fileName)) {
                    invalidateInstallation = true;
                }
            }

            key.reset();

            if (invalidateDynamic || invalidateInstallation) {
                synchronized (lock) {
                    if (invalidateDynamic) {
                        dynamicDirty = true;
                        contextDirty = true;
                    }
                    if (invalidateInstallation) {
                        installationDirty = true;
                        contextDirty = true;
                    }
                }
                fireInvalidation();
            }
        }
    }

    private boolean isDynamicFile(String fileName) {
        return switch (fileName) {
            case "usercache.json", "ops.json", "whitelist.json", "banned-players.json", "banned-ips.json" -> true;
            default -> false;
        };
    }

    private boolean isInstallationSensitivePath(Path watchedDir, String fileName) {
        if (watchedDir == null) {
            return false;
        }
        String dirName = normalize(watchedDir.getFileName() == null ? "" : watchedDir.getFileName().toString());
        if ("mods".equals(dirName) || "plugins".equals(dirName) || "datapacks".equals(dirName)) {
            return true;
        }
        return fileName.endsWith(".jar")
                || "server.properties".equals(fileName)
                || "fabric-server-launch.jar".equals(fileName)
                || "paper.yml".equals(fileName)
                || "spigot.yml".equals(fileName)
                || "bukkit.yml".equals(fileName);
    }

    private Path findLikelyWorldDir(Path serverDir) {
        if (serverDir == null) {
            return null;
        }
        Path configured = serverDir.resolve("world");
        return Files.isDirectory(configured) ? configured : null;
    }

    private Set<String> immutableOrderedPlayers(Set<String> onlinePlayers) {
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String player : onlinePlayers) {
            if (player == null || player.isBlank()) {
                continue;
            }
            values.add(player.trim());
        }
        return Set.copyOf(values);
    }

    private void fireInvalidation() {
        Runnable listener = invalidationListener;
        if (listener == null) {
            return;
        }
        SwingUtilities.invokeLater(listener);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePath(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }
}
