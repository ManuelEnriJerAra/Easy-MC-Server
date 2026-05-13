package controlador;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ApplicationInstanceLock implements AutoCloseable {
    private static final String LOCK_FILE_NAME = "easy-mc-server.lock";
    private static final String PORT_FILE_NAME = "easy-mc-server.port";
    private static final String FOCUS_COMMAND = "FOCUS";
    private static final int HANDOFF_CONNECT_TIMEOUT_MS = 800;

    private final FileChannel channel;
    private final FileLock lock;
    private ServerSocket handoffServerSocket;
    private Thread handoffThread;

    private ApplicationInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static ApplicationInstanceLock tryAcquire() throws IOException {
        Path lockPath = AppPaths.locksDirectory().resolve(LOCK_FILE_NAME);
        Files.createDirectories(lockPath.getParent());

        FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );

        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return null;
            }
            return new ApplicationInstanceLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            channel.close();
            return null;
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    public void startHandoffServer(Runnable focusRequestHandler) throws IOException {
        if (focusRequestHandler == null || handoffServerSocket != null) {
            return;
        }

        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        handoffServerSocket = serverSocket;

        Path portPath = AppPaths.locksDirectory().resolve(PORT_FILE_NAME);
        Files.writeString(portPath, Integer.toString(serverSocket.getLocalPort()), StandardCharsets.UTF_8);

        handoffThread = new Thread(() -> runHandoffServer(serverSocket, focusRequestHandler), "easy-mc-server-instance-handoff");
        handoffThread.setDaemon(true);
        handoffThread.start();
    }

    public static boolean requestExistingInstanceFocus() {
        Path portPath = AppPaths.locksDirectory().resolve(PORT_FILE_NAME);
        if (!Files.isRegularFile(portPath)) {
            return false;
        }

        try {
            int port = Integer.parseInt(Files.readString(portPath, StandardCharsets.UTF_8).trim());
            if (port <= 0 || port > 65535) {
                return false;
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), HANDOFF_CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(HANDOFF_CONNECT_TIMEOUT_MS);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                writer.println(FOCUS_COMMAND);
                return !writer.checkError();
            }
        } catch (IOException | NumberFormatException e) {
            return false;
        }
    }

    private static void runHandoffServer(ServerSocket serverSocket, Runnable focusRequestHandler) {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                String command = new String(socket.getInputStream().readNBytes(64), StandardCharsets.UTF_8).trim();
                if (FOCUS_COMMAND.equals(command)) {
                    focusRequestHandler.run();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("No se pudo atender una peticion de foco: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        IOException closeFailure = null;
        if (handoffServerSocket != null) {
            try {
                handoffServerSocket.close();
            } catch (IOException e) {
                closeFailure = e;
            }
        }
        try {
            if (lock.isValid()) {
                lock.release();
            }
            Files.deleteIfExists(AppPaths.locksDirectory().resolve(PORT_FILE_NAME));
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                if (closeFailure == null) {
                    closeFailure = e;
                } else {
                    closeFailure.addSuppressed(e);
                }
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}
