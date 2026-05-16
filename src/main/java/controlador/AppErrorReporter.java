package controlador;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AppErrorReporter {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_FILE = AppPaths.rootDirectory().resolve("logs").resolve("dora-errors.log");

    private AppErrorReporter() {
    }

    public static void installGlobalHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                report("Excepcion no controlada en " + (thread == null ? "hilo desconocido" : thread.getName()), throwable));
    }

    public static void report(String message, Throwable throwable) {
        String rendered = render(message, throwable);
        System.err.print(rendered);
        try {
            Path parent = LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    LOG_FILE,
                    rendered,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
        }
    }

    public static Path logFile() {
        return LOG_FILE;
    }

    public static void reportSwing(String message, Throwable throwable) {
        if (SwingUtilities.isEventDispatchThread()) {
            report(message, throwable);
        } else {
            report(message, throwable);
        }
    }

    private static String render(String message, Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        builder.append('[')
                .append(TIMESTAMP_FORMAT.format(LocalDateTime.now()))
                .append("] ")
                .append(message == null || message.isBlank() ? "Error no controlado" : message)
                .append(System.lineSeparator());
        if (throwable != null) {
            StringWriter stringWriter = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stringWriter));
            builder.append(stringWriter);
        }
        builder.append(System.lineSeparator());
        return builder.toString();
    }
}
